const config = require('./config.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {randomBytes,createHash,createPublicKey,verify} = require('node:crypto');
const { chromium } = require(path.resolve('frontends/web/main-app/node_modules/playwright'));
const output = path.join(config.evidence,'dcr-requests.jsonl');
fs.writeFileSync(output,'');
const log = value => fs.appendFileSync(output,JSON.stringify({time:new Date().toISOString(),...value})+'\n');
const privateFields = /^(?:authorization|credentials|password|token|client_secret|clientSecretHash|clientSecretRef|newClientSecret|access_token|refresh_token|id_token|code|code_verifier)$/i;
const scrub = value => Array.isArray(value) ? value.map(scrub) : value && typeof value === 'object'
  ? Object.fromEntries(Object.entries(value).map(([key,item])=>[key,privateFields.test(key) && !(key==='code' && typeof item==='number')?'<redacted>':scrub(item)])) : value;
const ok = response => { assert.equal(response.data.code,0,'management response'); return response.data.data; };
const created = [];
let admin;
async function req(method,url,{bearer,body,form,basic,nodeBase=config.base}={}) {
  const response = await fetch(nodeBase+url,{method,headers:{'content-type':form?'application/x-www-form-urlencoded':'application/json',...(bearer?{authorization:'Bearer '+bearer}:{}),
      ...(basic?{authorization:'Basic '+Buffer.from(basic.join(':')).toString('base64')}:{})},
    body:form?new URLSearchParams(form):body===undefined?undefined:JSON.stringify(body),redirect:'manual',signal:AbortSignal.timeout(20000)});
  const data = (response.headers.get('content-type')||'').includes('json') ? await response.json() : undefined;
  log({method,node:nodeBase,path:new URL(url,config.base).pathname,status:response.status,request:scrub(form||body),response:scrub(data),
    cacheControl:response.headers.get('cache-control'),retryAfter:response.headers.get('retry-after'),requestId:response.headers.get('x-request-id')});
  if (response.status===201 && data.client_id) created.push(data.client_id);
  return {status:response.status,data,headers:response.headers};
}
const route='/api/sys/auth-clients/registration';
const getPolicy=async()=>ok(await req('GET',route,{bearer:admin}));
const setPolicy=async changes=>{
  const old=await getPolicy();
  return ok(await req('PUT',route,{bearer:admin,body:{expectedRevision:old.revision,settings:{...old.settings,...changes}}}));
};
const metadata={client_name:'动态注册验收应用',redirect_uris:[config.base+'/rp/callback'],post_logout_redirect_uris:[config.base+'/rp/signed-out'],token_endpoint_auth_method:'none'};
async function discovery(enabled) {
  for (const url of ['/.well-known/openid-configuration','/.well-known/oauth-authorization-server']) {
    const result=await req('GET',url); assert.equal(result.status,200);
    assert.equal(result.data.registration_endpoint,enabled?config.base+'/connect/register':undefined);
  }
}
async function authorizeRegistered(page,client) {
  const verifier=randomBytes(48).toString('base64url'), nonce=randomBytes(16).toString('hex'),state=randomBytes(16).toString('hex');
  const callback=config.base+'/rp/callback';
  await page.goto(config.base+'/oauth2/authorize?'+new URLSearchParams({response_type:'code',client_id:client.client_id,
    redirect_uri:callback,scope:client.scope,state,nonce,code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256'}));
  const login=page.locator('#login-username');
  const consent=page.getByRole('heading',{name:'接入应用正在申请授权'});
  await login.or(consent).first().waitFor({timeout:60000});
  if(await login.isVisible()) {
    await page.locator('#login-username').fill('admin'); await page.locator('#login-password').fill(config.password);
    await page.locator('button[type=submit]').click();
  }
  await page.getByRole('heading',{name:'接入应用正在申请授权'}).waitFor({timeout:25000});
  await page.getByText('同意向该应用提供上述身份信息',{exact:true}).click();
  if(client.scope.includes('offline_access')) await page.getByText('允许离开当前页面后继续访问',{exact:true}).click();
  await page.screenshot({path:path.join(config.evidence,'dcr-consent-'+client.token_endpoint_auth_method+'.png'),fullPage:true});
  await page.getByRole('button',{name:'继续',exact:true}).click();
  await page.waitForURL(url=>url.pathname==='/rp/callback',{timeout:15000});
  const returned=new URL(page.url()); assert.equal(returned.searchParams.get('state'),state); assert(returned.searchParams.get('code'));
  const basic=client.client_secret?[client.client_id,client.client_secret]:undefined;
  const exchange={grant_type:'authorization_code',code:returned.searchParams.get('code'),redirect_uri:callback,code_verifier:verifier,
    ...(!basic?{client_id:client.client_id}:{})};
  const exchanged=await req('POST','/oauth2/token',{basic,form:exchange}); assert.equal(exchanged.status,200);
  const tokens=exchanged.data,parts=tokens.id_token.split('.'),header=JSON.parse(Buffer.from(parts[0],'base64url')),id=JSON.parse(Buffer.from(parts[1],'base64url'));
  const keys=(await req('GET','/oauth2/jwks')).data.keys,key=keys.find(item=>item.kid===header.kid);
  assert.equal(header.alg,'RS256'); assert(verify('RSA-SHA256',Buffer.from(parts[0]+'.'+parts[1]),createPublicKey({key,format:'jwk'}),Buffer.from(parts[2],'base64url')));
  assert.equal(id.iss,config.base); assert([id.aud].flat().includes(client.client_id)); assert.equal(id.nonce,nonce); assert(id.exp>Date.now()/1000);
  const userinfo=await req('GET','/userinfo',{bearer:tokens.access_token}); assert.equal(userinfo.status,200); assert.equal(userinfo.data.sub,id.sub);
  const internal=await req('GET','/api/sys/user/me/profile',{bearer:tokens.access_token}); assert(internal.status>=400 || internal.data?.code!==0);
  assert.notEqual((await req('POST','/oauth2/token',{basic,form:exchange})).status,200);
  if(basic) {
    assert(tokens.refresh_token);
    const refreshed=await req('POST','/oauth2/token',{basic,form:{grant_type:'refresh_token',refresh_token:tokens.refresh_token}});
    assert.equal(refreshed.status,200); assert.notEqual(refreshed.data.refresh_token,tokens.refresh_token);
    assert.notEqual((await req('POST','/oauth2/token',{basic,form:{grant_type:'refresh_token',refresh_token:tokens.refresh_token}})).status,200);
    const logout=await req('GET','/connect/logout?'+new URLSearchParams({id_token_hint:refreshed.data.id_token,post_logout_redirect_uri:config.base+'/rp/signed-out',state}));
    assert.equal(new URL(logout.headers.get('location'),config.base).pathname,'/rp/signed-out');
    assert.notEqual((await req('GET','/userinfo',{bearer:refreshed.data.access_token})).status,200);
  } else assert.equal(tokens.refresh_token,undefined);
}
(async()=>{
  admin=ok(await req('POST','/api/auth/login',{body:{principalType:'username',principal:'admin',credentials:config.password}})).accessToken.token;
  const original=await getPolicy();
  const browser=await chromium.launch();
  const browserContext=await browser.newContext({viewport:{width:1440,height:1100}});
  const page=await browserContext.newPage();
  page.on('response',response=>{const url=new URL(response.url());if(url.origin===config.base && /^\/(api|oauth2|connect|userinfo)\b/.test(url.pathname))log({transport:'browser',method:response.request().method(),path:url.pathname,status:response.status()});});
  try {
    await setPolicy({enabled:false});
    assert.equal((await req('POST','/connect/register',{body:metadata})).status,503);
    await discovery(false);
    for (const method of ['GET','PUT','DELETE']) assert.equal((await req(method,'/connect/register')).status,405);
    const denied=await req('GET',route); assert(denied.status>=400 || denied.data?.code!==0);
    log({caseId:'DCR-01',result:'pass'});
    await setPolicy({enabled:true,mode:'OPEN',allowedIdentityScopes:['openid','profile','email','offline_access'],
      allowedGrantTypes:['authorization_code','refresh_token'],rateLimit:{window:'PT1M',global:200,perSource:200}});
    await discovery(true);
    const open=await req('POST','/connect/register',{bearer:'not-a-valid-jwt',body:{...metadata,apiAuthorization:{enabled:true}}});
    assert.equal(open.status,201); assert.equal(open.data.client_secret,undefined);
    assert.deepEqual(open.data.grant_types,['authorization_code']); assert.equal(open.data.scope,'openid profile');
    for (const field of ['registration_access_token','registration_client_uri','clientSecretHash','registrationId']) assert(!(field in open.data));
    let list=ok(await req('GET','/api/sys/auth-clients',{bearer:admin}));
    let spec=list.clients.find(item=>item.clientId===open.data.client_id);
    assert.equal(spec.apiAuthorization.enabled,false); assert.deepEqual(spec.apiAuthorization.allowedBoundary.permissions,[]);
    assert.deepEqual(spec.introspectionResources,[]); assert.equal(spec.consentPolicy.mode,'EXPLICIT');
    assert.equal(spec.registration.source,'DCR_OPEN');
    const filtered=ok(await req('GET','/api/sys/auth-clients?source=DCR_OPEN',{bearer:admin}));
    assert(filtered.clients.length>0 && filtered.clients.every(item=>item.registration.source==='DCR_OPEN'));
    for (const body of [{...metadata,redirect_uris:['https://app.example/cb#fragment']},{...metadata,scope:'openid authority:admin'},
      {...metadata,jwks_uri:'http://127.0.0.1/internal'},{...metadata,grant_types:['client_credentials']}]) {
      assert.equal((await req('POST','/connect/register',{body})).status,400);
    }
    log({caseId:'DCR-02',result:'pass',assertions:['open registration','unknown metadata cannot enable business','recognized unsupported rejected','provenance and source filter']});
    await setPolicy({mode:'INITIAL_ACCESS_TOKEN'});
    assert.equal((await req('POST','/connect/register',{bearer:admin,body:metadata})).status,401);
    const issued=ok(await req('POST',route+'/credentials',{bearer:admin}));
    const attempts=await Promise.all(Array.from({length:6},(_,index)=>req('POST','/connect/register',
      {bearer:issued.token,body:metadata,nodeBase:index%2 && process.env.SSO_SECONDARY_URL || config.base})));
    assert.equal(attempts.filter(item=>item.status===201).length,1); assert(attempts.filter(item=>item.status!==201).every(item=>item.status===401));
    const revokeUsed=ok(await req('DELETE',route+'/credentials/'+issued.id,{bearer:admin})); assert.equal(revokeUsed.revoked,false);
    const revoked=ok(await req('POST',route+'/credentials',{bearer:admin}));
    assert.equal(ok(await req('DELETE',route+'/credentials/'+revoked.id,{bearer:admin})).revoked,true);
    assert.equal((await req('POST','/connect/register',{bearer:revoked.token,body:metadata})).status,401);
    const confidentialToken=ok(await req('POST',route+'/credentials',{bearer:admin}));
    const confidential=await req('POST','/connect/register',{bearer:confidentialToken.token,body:{...metadata,token_endpoint_auth_method:'client_secret_basic',
      scope:'openid profile offline_access',grant_types:['authorization_code','refresh_token']}});
    assert.equal(confidential.status,201); assert.equal(confidential.data.client_secret.length,43);
    assert.equal(confidential.data.client_secret_expires_at,0);
    assert.deepEqual(confidential.data.grant_types,['authorization_code','refresh_token']);
    log({caseId:'DCR-03',result:'pass',assertions:['six concurrent requests one credential winner',...(process.env.SSO_SECONDARY_URL?['two backend nodes share one Redis claim']:[]),'platform token is not IAT','revoke only unused','confidential actual grants']});
    list=ok(await req('GET','/api/sys/auth-clients',{bearer:admin}));
    const count=list.clients.filter(item=>item.registration?.source?.startsWith('DCR_')).length;
    await setPolicy({mode:'OPEN',maxDynamicClients:count+1});
    const quota=await Promise.all(Array.from({length:4},(_,index)=>req('POST','/connect/register',
      {body:metadata,nodeBase:index%2 && process.env.SSO_SECONDARY_URL || config.base})));
    assert.equal(quota.filter(item=>item.status===201).length,1);
    assert(quota.filter(item=>item.status!==201).every(item=>item.status===429));
    log({caseId:'DCR-04',result:'pass',assertions:['one remaining quota four concurrent attempts exactly one accepted']});
    // A distinct long window avoids an HTTP sequence straddling a fixed-window boundary.
    const accountingWindow='PT'+(3600+Math.floor(Date.now()/1000)%3600)+'S';
    await setPolicy({maxDynamicClients:100,rateLimit:{window:accountingWindow,global:3,perSource:3}});
    for(let i=0;i<3;i++) assert.equal((await req('POST','/connect/register',{body:{}})).status,400);
    const rate=await req('POST','/connect/register',{body:{}}); assert.equal(rate.status,429); assert(rate.headers.get('retry-after'));
    log({caseId:'DCR-05',result:'pass',assertions:['failed requests counted','Retry-After present']});
    await setPolicy({rateLimit:{window:'PT1M',global:200,perSource:200},mode:'INITIAL_ACCESS_TOKEN'});
    await page.goto(config.base+'/login',{waitUntil:'domcontentloaded'});
    await page.locator('#login-username').fill('admin');
    await page.locator('#login-password').fill(config.password);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(url=>url.pathname!=='/login',{timeout:60000});
    await page.goto(config.base+'/system-setting/auth-clients',{waitUntil:'domcontentloaded'});
    await page.getByText('动态注册',{exact:true}).click();
    await page.getByText('启用动态注册',{exact:true}).waitFor({timeout:60000});
    await page.getByRole('button',{name:'签发注册凭证',exact:true}).waitFor({state:'visible',timeout:60000});
    for(const [label,mode] of [['开放注册','OPEN'],['凭证注册','INITIAL_ACCESS_TOKEN']]) {
      const option=page.locator('label[data-role=checkbox]').filter({hasText:label});
      await option.click();
      assert(await option.locator('input[type=radio]').isChecked());
      const saved=page.waitForResponse(response=>response.url().endsWith(route) && response.request().method()==='PUT');
      await page.getByRole('button',{name:'保存',exact:true}).click();
      assert.equal((await saved).status(),200);
      assert.equal((await getPolicy()).settings.mode,mode);
    }
    await page.getByRole('button',{name:'签发注册凭证',exact:true}).waitFor({state:'visible',timeout:10000});
    await page.screenshot({path:path.join(config.evidence,'dcr-settings-credential.png'),fullPage:true});
    await page.getByRole('button',{name:'签发注册凭证',exact:true}).click();
    const dialog=page.getByRole('dialog');
    await dialog.locator('input[name=token]').waitFor();
    assert.equal(await dialog.locator('input[name=token]').getAttribute('type'),'password');
    await page.waitForFunction(()=>document.querySelector('[role=dialog] input[name=token]')?.value.length>40,{},{timeout:15000});
    await page.screenshot({path:path.join(config.evidence,'dcr-issued-credential-masked.png'),fullPage:true});
    await dialog.locator('.cxd-Modal-close').click();
    await page.getByText('应用列表',{exact:true}).click();
    await page.getByText('动态注册验收应用',{exact:true}).first().waitFor();
    await page.screenshot({path:path.join(config.evidence,'dcr-registered-applications.png'),fullPage:true});
    log({caseId:'DCR-06',result:'pass',assertions:['server-level registration tab','credential generated into masked field','multi-client list with provenance']});
    await authorizeRegistered(page,open.data);
    await authorizeRegistered(page,confidential.data);
    log({caseId:'DCR-07',result:'pass',assertions:['registered public and confidential PKCE authorization','ID token verified with JWKS','UserInfo','business API isolation','refresh rotation and replay denial','RP logout revokes grant']});
    await setPolicy({enabled:false}); await discovery(false);
    assert.equal((await req('POST','/connect/register',{body:metadata})).status,503);
    log({result:'pass',caseIds:['DCR-01','DCR-02','DCR-03','DCR-04','DCR-05','DCR-06','DCR-07']});
  } catch(error) {
    await page.screenshot({path:path.join(config.evidence,'dcr-failure.png'),fullPage:true}).catch(screenshotError=>log({screenshotError:screenshotError.name}));
    throw error;
  } finally {
    await browser.close();
    for (const id of created) {
      const current=ok(await req('GET','/api/sys/auth-clients',{bearer:admin}));
      if (current.clients.some(item=>item.clientId===id)) ok(await req('DELETE','/api/sys/auth-clients/'+id,{bearer:admin,body:{expectedRevision:current.revision}}));
    }
    const current=await getPolicy();
    ok(await req('PUT',route,{bearer:admin,body:{expectedRevision:current.revision,settings:{...original.settings,enabled:false}}}));
  }
})().catch(error=>{log({result:'fail',errorType:error.name,message:String(error.message).split('\n')[0],location:error.stack?.split('\n').find(line=>line.includes('dcr-acceptance.cjs:'))});console.error(error.name+': '+String(error.message).split('\n')[0]);process.exitCode=1;});
