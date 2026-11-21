const config = require('./config.cjs');
const assert=require('node:assert/strict');
const {randomBytes,createHash}=require('node:crypto');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require(path.resolve('frontends/web/main-app/node_modules/playwright'));
const base=config.base;
const ev=config.evidence;
const output=path.join(ev,'business-requests.jsonl');
fs.writeFileSync(output,'');
const hidden=/token|secret|password|credentials|verifier|challenge|code$|contextId|state|csrf|registrationId/i;
const scrub=v=>Array.isArray(v)?v.map(scrub):v&&typeof v==='object'?Object.fromEntries(Object.entries(v).map(([k,x])=>[k,hidden.test(k)&&typeof x!=='number'?'<redacted>':scrub(x)])):v;
const log=v=>fs.appendFileSync(output,JSON.stringify({time:new Date().toISOString(),...v})+'\n');
const ok=(v,step)=>{assert.equal(v?.code,0,step+': '+v?.msg);return v.data;};
const denied=v=>v.status>=400 || (typeof v.data?.code==='number' && v.data.code!==0);
(async()=>{
 const browser=await chromium.launch();const ctx=await browser.newContext({viewport:{width:1440,height:1000}});const page=await ctx.newPage();
 async function req(method,route,{bearer,basic,json,form}={}) {
  const headers={};if(bearer)headers.authorization='Bearer '+bearer;if(basic)headers.authorization='Basic '+Buffer.from(basic.join(':')).toString('base64');
  const res=await ctx.request.fetch(base+route,{method,headers,data:json,form,maxRedirects:0});
  const data=(res.headers()['content-type']||'').includes('json')?await res.json():undefined;
  log({method,path:new URL(route,base).pathname,status:res.status(),request:scrub(json||form),response:scrub(data)});
  return {status:res.status(),data,location:res.headers().location};
 }
 try {
  const password=config.password;
  const admin=ok((await req('POST','/api/auth/login',{json:{principalType:'username',principal:'admin',credentials:password}})).data,'admin login').accessToken.token;
  const role=ok((await req('POST','/api/sys/role',{bearer:admin,json:{name:'SSO fixture '+Date.now()}})).data,'create role');
  const user='sso-user-'+randomBytes(4).toString('hex');
  const rolePath='/api/sys/role/'+role.id+'/authority';
  const permissions=category=>[{authority:'ssoFixture.record.search',accessControlParams:{category}}, {authority:'ssoFixture.record.edit',accessControlParams:{category}}];
  ok((await req('PUT',rolePath,{bearer:admin,json:permissions('a')})).data,'role permissions');
  ok((await req('POST','/api/sys/user',{bearer:admin,json:{userIdentity:user,password,roles:[role.id]}})).data,'create user');
  const userBearer=ok((await req('POST','/api/auth/login',{json:{principalType:'username',principal:user,credentials:password}})).data,'user login').accessToken.token;
  const api='/api/sso-fixture/record';
  for(const category of ['a','b']) ok((await req('POST',api,{bearer:admin,json:{name:'SSO '+category,category}})).data,'seed record');
  const getRows=async bearer=>{const v=await req('GET',api,{bearer}); const d=ok(v.data,'query');return d.items||d;};
  const before=await getRows(userBearer);assert(before.length>0);assert(before.every(r=>r.category==='a'));
  assert(denied(await req('GET','/api/sys/auth-clients',{bearer:userBearer})));
  const clients=()=>req('GET','/api/sys/auth-clients',{bearer:admin}).then(v=>ok(v.data,'clients'));
  const id='sso-business-'+randomBytes(3).toString('hex');
  const secret=ok((await req('POST','/api/sys/auth-clients/'+id+'/secret/generate',{bearer:admin})).data,'secret').clientSecret;
  const spec={clientId:id,clientName:'业务委托验收应用',clientType:'CONFIDENTIAL',enabled:true,redirectUris:[base+'/rp/callback'],oidc:{enabled:false,scopes:[],postLogoutRedirectUris:[]},apiAuthorization:{enabled:true,allowedBoundary:{permissions:['ssoFixture.record.search']}},consentPolicy:{mode:'EXPLICIT'},introspectionResources:[]};
  ok((await req('POST','/api/sys/auth-clients',{bearer:admin,json:{expectedRevision:(await clients()).revision,client:spec,newClientSecret:secret}})).data,'business client');
  const scope='authority:'+Buffer.from('ssoFixture.record.search').toString('base64url');
  async function code(scopes=scope, evidenceName='consent-business.png') {
   const verifier=randomBytes(48).toString('base64url');
   const route='/oauth2/authorize?'+new URLSearchParams({client_id:id,response_type:'code',redirect_uri:base+'/rp/callback',scope:scopes,code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',state:randomBytes(10).toString('hex')});
   await page.goto(base+route,{waitUntil:'domcontentloaded'});
   await Promise.race([page.locator('#login-username').waitFor({timeout:25000}),page.getByRole('heading',{name:'接入应用正在申请授权'}).waitFor({timeout:25000})]);
   if(await page.locator('#login-username').isVisible()){await page.locator('#login-username').fill(user);await page.locator('#login-password').fill(password);await page.locator('button[type=submit]').click();}
   await page.getByRole('heading',{name:'接入应用正在申请授权'}).waitFor({timeout:30000});
   await page.getByText('同意上述全部业务功能',{exact:true}).waitFor();
   const identityChoice=page.getByText('同意向该应用提供上述身份信息',{exact:true});
   if(scopes.split(' ').includes('openid')) await identityChoice.waitFor();
   assert.equal(await identityChoice.count(),scopes.split(' ').includes('openid')?1:0);
   await page.screenshot({path:path.join(ev,evidenceName),fullPage:true});
   if(scopes.split(' ').includes('openid')) await identityChoice.click();
   await page.getByText('同意上述全部业务功能',{exact:true}).click();
   await page.getByRole('button',{name:'继续',exact:true}).click();await page.waitForURL(u=>u.pathname==='/rp/callback',{timeout:15000});
   const value=new URL(page.url()).searchParams.get('code');assert(value);return {value,verifier};
  }
  const exchange=c=>req('POST','/oauth2/token',{basic:[id,secret],form:{grant_type:'authorization_code',code:c.value,code_verifier:c.verifier,redirect_uri:base+'/rp/callback'}});
  const c=await code();
  const parallel=await Promise.all(Array.from({length:6},()=>exchange(c)));
  assert.equal(parallel.filter(v=>v.status===200).length,1);
  log({assertions:['concurrent redemption: one success, five denied']});
  const tokens=parallel.find(v=>v.status===200).data;assert(!tokens.id_token);assert(tokens.refresh_token);
  const granted=await getRows(tokens.access_token);assert(granted.length>0);assert(granted.every(r=>r.category==='a'));
  assert(denied(await req('GET','/userinfo',{bearer:tokens.access_token})));
  assert(denied(await req('PATCH',api+'/'+granted[0].id,{bearer:tokens.access_token,json:{name:'forbidden'}})));
  ok((await req('PUT',rolePath,{bearer:admin,json:permissions(['a','b'])})).data,'expand current role');
  const snapshot=await getRows(tokens.access_token);assert(snapshot.every(r=>r.category==='a'));
  ok((await req('PUT',rolePath,{bearer:admin,json:permissions('b')})).data,'narrow role');
  assert.equal((await getRows(tokens.access_token)).length,0);
  ok((await req('PUT',rolePath,{bearer:admin,json:permissions('a')})).data,'restore role');
  const current=await clients();
  ok((await req('PATCH','/api/sys/auth-clients/'+id,{bearer:admin,json:{expectedRevision:current.revision,changes:{apiAuthorization:{enabled:true,allowedBoundary:{permissions:[]}}}}})).data,'narrow client');
  assert(denied(await req('GET',api,{bearer:tokens.access_token})));
  const nextSecret=ok((await req('POST','/api/sys/auth-clients/'+id+'/secret/generate',{bearer:admin})).data,'rotate secret').clientSecret;
  ok((await req('PATCH','/api/sys/auth-clients/'+id,{bearer:admin,json:{expectedRevision:(await clients()).revision,changes:{},newClientSecret:nextSecret}})).data,'commit rotation');
  assert(denied(await req('POST','/oauth2/token',{basic:[id,secret],form:{grant_type:'refresh_token',refresh_token:tokens.refresh_token}})));
  ok((await req('PATCH','/api/sys/auth-clients/'+id,{bearer:admin,json:{expectedRevision:(await clients()).revision,
   changes:{oidc:{enabled:true,scopes:['openid','profile'],postLogoutRedirectUris:[]},apiAuthorization:{enabled:true,allowedBoundary:{permissions:['ssoFixture.record.search']}}}}})).data,'enable identity alongside business delegation');
  const combinedCode=await code('openid profile '+scope,'consent-combined.png');
  const combined=await req('POST','/oauth2/token',{basic:[id,nextSecret],form:{grant_type:'authorization_code',code:combinedCode.value,code_verifier:combinedCode.verifier,redirect_uri:base+'/rp/callback'}});
  assert.equal(combined.status,200);assert(combined.data.id_token);
  const identity=await req('GET','/userinfo',{bearer:combined.data.access_token});assert.equal(identity.status,200);assert(identity.data.sub);
  const combinedRows=await getRows(combined.data.access_token);assert(combinedRows.length>0);assert(combinedRows.every(row=>row.category==='a'));
  log({result:'pass',assertions:['OAuth2 without OIDC','U∩C∩G∩T at original business API and data plugin','snapshot prevents role expansion','live role narrowing','client boundary narrowing','secret rotation','separate identity and business groups in one consent','combined identity and bounded business token']});
 }catch(e){await page.screenshot({path:path.join(ev,'business-failure.png')});log({result:'fail',message:e.message.split('\n')[0]});throw e;}finally{await browser.close();}
})().catch(e=>{process.stderr.write(e.stack+'\n');process.exitCode=1;});
