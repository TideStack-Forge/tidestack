const config = require('./config.cjs');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=config.base;
const file=config.retained;
const fixture=JSON.parse(fs.readFileSync(file));
const stage=process.argv[2]||'normal';
const log=v=>fs.appendFileSync(require('node:path').join(config.evidence, 'resilience.jsonl'),JSON.stringify({time:new Date().toISOString(),stage,...v})+'\n');
(async()=>{
 const started=Date.now();
 let info;
 do {
  info=await fetch(base+'/userinfo',{signal:AbortSignal.timeout(15000),headers:{authorization:'Bearer '+fixture.tokens.access_token}});
  log({method:'GET',path:'/userinfo',status:info.status,elapsedMs:Date.now()-started});
  if(stage!=='after-cache-recovery'||info.status===200||info.status<400||Date.now()-started>=15000)break;
  await info.arrayBuffer();
  await require('node:timers/promises').setTimeout(500);
 }while(Date.now()-started<15000);
 if(stage==='cache-down'){assert(info.status>=400);log({result:'pass',assertions:['cache unavailable: no UserInfo success']});return;}
 assert.equal(info.status,200);assert.equal((await info.json()).sub,fixture.sub);
 if(stage==='after-cache-recovery')assert(Date.now()-started<15000,'cache client recovery must complete within 15 seconds');
 const next=await fetch(base+'/oauth2/token',{method:'POST',headers:{authorization:'Basic '+Buffer.from(fixture.basic.join(':')).toString('base64'),'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'refresh_token',refresh_token:fixture.tokens.refresh_token})});
 log({method:'POST',path:'/oauth2/token',status:next.status});assert.equal(next.status,200);
 const tokens=await next.json();assert(tokens.refresh_token);assert.notEqual(tokens.refresh_token,fixture.tokens.refresh_token);
 const claims=JSON.parse(Buffer.from(tokens.id_token.split('.')[1],'base64url'));assert.equal(claims.sub,fixture.sub);
 fixture.tokens=tokens;fs.writeFileSync(file,JSON.stringify(fixture),{mode:0o600});
 log({result:'pass',assertions:['grant retained','stable public subject','refresh rotation']});
})().catch(e=>{log({result:'fail',message:e.message});process.stderr.write(e.stack+'\n');process.exitCode=1;});
