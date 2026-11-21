const config=require('./config.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
assert(process.env.SSO_REDIS_CONTAINER,'SSO_REDIS_CONTAINER must name the owned acceptance cache');
const output=path.join(config.evidence,'dcr-cache-recovery.jsonl');fs.writeFileSync(output,'');
const log=value=>fs.appendFileSync(output,JSON.stringify({time:new Date().toISOString(),...value})+'\n');
const route='/api/sys/auth-clients/registration',clients='/api/sys/auth-clients';
const created=[];
let admin,stopped=false;
async function api(method,url,body,bearer=admin) {
  const response=await fetch(config.base+url,{method,headers:{'content-type':'application/json',...(bearer?{authorization:'Bearer '+bearer}:{})},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  const data=await response.json();log({method,path:url,status:response.status,code:data.code,error:data.error});
  if(response.status===201)created.push(data.client_id);
  return {status:response.status,data};
}
const ok=result=>{assert.equal(result.data.code,0);return result.data.data;};
async function policy(change) {const old=ok(await api('GET',route));return ok(await api('PUT',route,{expectedRevision:old.revision,settings:{...old.settings,...change}}));}
function docker(action) {
  const result=spawnSync('docker',[action,process.env.SSO_REDIS_CONTAINER],{encoding:'utf8',timeout:30000});
  assert.equal(result.status,0,'owned Redis '+action);log({fixture:'owned Redis',action,exitCode:result.status});
}
async function ready() {
  const start=Date.now();
  do {
    const response=await api('GET',clients);if(response.data.code===0){assert(Date.now()-start<15000);return;}
    await new Promise(resolve=>setTimeout(resolve,300));
  }while(Date.now()-start<15000);
  throw new Error('cache recovery exceeded 15 seconds');
}
(async()=>{
  admin=ok(await api('POST','/api/auth/login',{principalType:'username',principal:'admin',credentials:config.password},undefined)).accessToken.token;
  const original=ok(await api('GET',route));
  try {
    await policy({enabled:true,mode:'INITIAL_ACCESS_TOKEN',allowedClientTypes:['PUBLIC','CONFIDENTIAL'],allowedIdentityScopes:['openid','profile'],
      allowedGrantTypes:['authorization_code'],maxDynamicClients:100,rateLimit:{global:200,perSource:200,window:'PT15M'}});
    const used=ok(await api('POST',route+'/credentials'));
    const unused=ok(await api('POST',route+'/credentials'));
    const metadata={redirect_uris:[config.base+'/rp/callback'],token_endpoint_auth_method:'none'};
    assert.equal((await api('POST','/connect/register',metadata,used.token)).status,201);
    const count=ok(await api('GET',clients)).clients.length;
    docker('stop');stopped=true;
    assert.equal((await api('POST','/connect/register',metadata,unused.token)).status,503);
    docker('start');stopped=false;await ready();
    assert.equal(ok(await api('GET',clients)).clients.length,count);
    assert.equal((await api('POST','/connect/register',metadata,used.token)).status,401);
    assert.equal((await api('POST','/connect/register',metadata,unused.token)).status,201);
    log({caseId:'DCR-05',result:'pass',assertions:['real cache outage rejects registration','no client written while cache unavailable',
      'graceful cache restart retains consumed credential denial','unclaimed credential remains usable after recovery','recovery within 15 seconds']});
  } finally {
    if(stopped){docker('start');stopped=false;await ready();}
    for(const id of created){const current=ok(await api('GET',clients));ok(await api('DELETE',clients+'/'+id,{expectedRevision:current.revision}));}
    await policy({...original.settings,enabled:false});
  }
})().catch(error=>{log({result:'fail',error:error.message});console.error(error.stack);process.exitCode=1;});
