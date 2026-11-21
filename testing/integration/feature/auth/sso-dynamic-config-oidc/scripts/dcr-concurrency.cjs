// Isolated-fixture fault injection only: hold the real row lock while official APIs race.
const config=require('./config.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {spawn}=require('node:child_process');
assert(process.env.SSO_MYSQL_CONTAINER,'SSO_MYSQL_CONTAINER must name the owned acceptance database');
const output=path.join(config.evidence,'dcr-policy-races.jsonl'); fs.writeFileSync(output,'');
const log=value=>fs.appendFileSync(output,JSON.stringify({time:new Date().toISOString(),...value})+'\n');
const policyPath='/api/sys/auth-clients/registration';
const clientsPath='/api/sys/auth-clients';
let admin;
async function api(method,url,body) {
  const result=await fetch(config.base+url,{method,headers:{'content-type':'application/json',...(admin?{authorization:'Bearer '+admin}:{})},
    body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  const data=await result.json();
  log({method,path:url,status:result.status,error:data.error,code:data.code,revision:data.data?.revision});
  return {status:result.status,data};
}
const ok=result=>{assert.equal(result.data.code,0);return result.data.data;};
const getPolicy=async()=>ok(await api('GET',policyPath));
const policy=async change=>{const old=await getPolicy();return ok(await api('PUT',policyPath,{expectedRevision:old.revision,settings:{...old.settings,...change}}));};
function connection(root=false) {
  const child=spawn('docker',['exec','-i',process.env.SSO_MYSQL_CONTAINER,'sh','-c',
    root ? 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -N -B --unbuffered' :
      'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -B --unbuffered'],{stdio:['pipe','pipe','pipe']});
  let buffer='',failure=''; const pending=new Map();
  child.stdout.on('data',chunk=>{buffer+=chunk; for(const [marker,resolve] of pending) if(buffer.includes(marker)){pending.delete(marker);resolve();}});
  child.stderr.on('data',chunk=>{failure+=chunk;});
  child.on('error',error=>{failure=error.message;});
  return {
    async execute(sql,marker) {
      const completion=new Promise((resolve,reject)=>{
        const timer=setTimeout(()=>{pending.delete(marker);reject(new Error('database barrier timeout: '+marker+' '+failure.slice(0,120)));},15000);
        pending.set(marker,()=>{clearTimeout(timer);resolve();});
      });
      child.stdin.write(sql+"; SELECT '"+marker+"';\n"); await completion;
    },
    async blocked(state) {
      const marker='BLOCKED_'+Date.now();
      await this.execute((state?"SELECT IF(COUNT(*)>0,'REGISTRATION_WAITING','NO_WAITER') FROM information_schema.PROCESSLIST WHERE STATE='User lock'": "SELECT IF(COUNT(*)>0,'REGISTRATION_WAITING','NO_WAITER') FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND INFO LIKE '%t_sys_configuration_value%' AND LOWER(INFO) LIKE '%for update%'"),marker);
      return buffer.slice(buffer.lastIndexOf('REGISTRATION_WAITING'))?.startsWith('REGISTRATION_WAITING') && !buffer.slice(buffer.lastIndexOf('REGISTRATION_WAITING')).includes('NO_WAITER');
    },
    close(){child.stdin.end('ROLLBACK;\n');},
  };
}
(async()=>{
  admin=ok(await api('POST','/api/auth/login',{principalType:'username',principal:'admin',credentials:config.password})).accessToken.token;
  const original=await getPolicy();
  try {
    for(const [name,tighten] of [['disable',{enabled:false}],['mode',{mode:'INITIAL_ACCESS_TOKEN'}],['scope',{allowedIdentityScopes:['openid']}]] ) {
      await policy({enabled:true,mode:'OPEN',allowedIdentityScopes:['openid','profile'],maxDynamicClients:100,
        rateLimit:{global:200,perSource:200,window:'PT1M'}});
      const before=ok(await api('GET',clientsPath)).clients.length;
      const db=connection();
      try {
        await db.execute("START TRANSACTION; SELECT `key` FROM t_sys_configuration_value WHERE `key`='security.auth.oauth2.clients' FOR UPDATE",'CLIENT_ROW_LOCKED');
        const registration=api('POST','/connect/register',{client_name:'策略竞态验收',redirect_uris:[config.base+'/rp/callback'],token_endpoint_auth_method:'none',scope:'openid profile'});
        let waiting=false;
        for(let attempt=0;attempt<100;attempt++) {
          if(await db.blocked()){waiting=true;break;}
          await new Promise(resolve=>setTimeout(resolve,30));
        }
        assert(waiting,'registration must be observed waiting on the real configuration row lock');
        await policy(tighten);
        await db.execute('COMMIT','CLIENT_ROW_RELEASED');
        const result=await registration;
        assert.equal(result.status,503);
        assert.equal(ok(await api('GET',clientsPath)).clients.length,before);
        log({caseId:'DCR-04',race:name,result:'pass',assertions:['real registration waits on original configuration row','admin policy commits first','pending registration rejected','client count unchanged']});
      } finally {db.close();}
    }
    await policy({enabled:true,mode:'OPEN',allowedIdentityScopes:['openid','profile']});
    const barrier=connection(true);
    let dynamicClient;
    try {
      await barrier.execute("DROP TRIGGER IF EXISTS sso_dcr_test_barrier",'TRIGGER_CLEAN');
      await barrier.execute("CREATE TRIGGER sso_dcr_test_barrier BEFORE UPDATE ON t_sys_configuration_value FOR EACH ROW SET @sso_dcr_test_lock = IF(NEW.`key`='security.auth.oauth2.clients', GET_LOCK('sso_dcr_test_barrier',20)+RELEASE_LOCK('sso_dcr_test_barrier'),0)",'TRIGGER_READY');
      await barrier.execute("SELECT GET_LOCK('sso_dcr_test_barrier',10)",'BARRIER_HELD');
      const registering=api('POST','/connect/register',{client_name:'注册先持锁验收',redirect_uris:[config.base+'/rp/callback'],token_endpoint_auth_method:'none'});
      let waiting=false;
      for(let attempt=0;attempt<100;attempt++) {if(await barrier.blocked('User lock')){waiting=true;break;}await new Promise(resolve=>setTimeout(resolve,30));}
      assert(waiting,'registration must reach UPDATE after locking the policy');
      const closing=policy({enabled:false});
      let adminWaiting=false;
      for(let attempt=0;attempt<100;attempt++) {if(await barrier.blocked()){adminWaiting=true;break;}await new Promise(resolve=>setTimeout(resolve,30));}
      assert(adminWaiting,'administrator must wait on registration policy row lock');
      await barrier.execute("SELECT RELEASE_LOCK('sso_dcr_test_barrier')",'BARRIER_RELEASED');
      const result=await registering; assert.equal(result.status,201); dynamicClient=result.data.client_id;
      await closing;
      assert.equal((await api('POST','/connect/register',{redirect_uris:[config.base+'/rp/callback']})).status,503);
      log({caseId:'DCR-04',race:'registration-locks-first',result:'pass',assertions:['registration blocked inside actual UPDATE after policy validation','admin close observed waiting on policy row','registration commits once','close completes and subsequent registration rejected']});
    } finally {
      await barrier.execute("SELECT RELEASE_LOCK('sso_dcr_test_barrier'); DROP TRIGGER IF EXISTS sso_dcr_test_barrier",'BARRIER_REMOVED');barrier.close();
      if(dynamicClient){const current=ok(await api('GET',clientsPath));ok(await api('DELETE',clientsPath+'/'+dynamicClient,{expectedRevision:current.revision}));}
    }
  } finally {await policy({...original.settings,enabled:false});}
})().catch(error=>{log({result:'fail',error:error.message});console.error(error.message);process.exitCode=1;});
