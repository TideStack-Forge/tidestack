const config = require('./config.cjs');
const assert = require('node:assert/strict');
const { createHash, createPublicKey, randomBytes, verify } = require('node:crypto');
const { appendFileSync, mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');
const { chromium } = require(path.resolve('frontends/web/main-app/node_modules/playwright'));
const base = config.base;
const evidence = config.evidence;
const records = path.join(evidence, 'requests.jsonl');
const browserPassword = config.password;
const secretName = /^(?:authorizationCode|clientSecret(?:Ref|Hash)?|newClientSecret|(?:access|refresh|id)[_-]?token|code|code_verifier|code_challenge|csrfToken|token|ticket|credentials|password|(?:context|request|loginRequest)Id|state)$/i;
const scrub = obj => Array.isArray(obj) ? obj.map(scrub)
  : obj && typeof obj === 'object' ? Object.fromEntries(Object.entries(obj).map(([k,v]) => [k, secretName.test(k) && typeof v !== 'number' ? '<redacted>' : scrub(v)])) : obj;
function log(row) { appendFileSync(records, JSON.stringify({ time:new Date().toISOString(), ...row }) + '\n'); }
const ok = (v, where) => { assert.equal(v?.code, 0, where + ': ' + (v?.msg || v?.code)); return v.data; };
function isDenied(v) { return v.status >= 400 || (typeof v.data?.code === 'number' && v.data.code !== 0); }
async function run() {
  mkdirSync(evidence, {recursive:true});
  writeFileSync(records, '');
  const browser = await chromium.launch();
  const browserContext = await browser.newContext({viewport:{width:1440,height:1000}});
  const page = await browserContext.newPage();
  page.on('response', response => {
    const u = new URL(response.url());
    if (u.origin === base && /^\/(api|oauth2|connect|userinfo|rp)\b/.test(u.pathname))
      log({transport:'browser',method:response.request().method(),path:u.pathname,status:response.status()});
  });
  page.on('pageerror', error => log({transport:'browser',errorType:error.name,message:error.message.replace(/([?&](?:contextId|state|requestId|loginRequestId)=)[^&\s]+/g,'$1<redacted>')}));
  try {
    async function request(method, route, { bearer, basic, json, form, status, brief } = {}) {
      const headers = {};
      if (bearer) headers.Authorization = 'Bearer ' + bearer;
      if (basic) headers.Authorization = 'Basic ' + Buffer.from(basic.join(':')).toString('base64');
      if (json) headers['content-type'] = 'application/json';
      const response = await browserContext.request.fetch(base + route, {
        method, headers, data:json ? JSON.stringify(json) : undefined, form, maxRedirects:0,
      });
      const type = response.headers()['content-type'] || '';
      const data = type.includes('json') ? await response.json() : undefined;
      const u = new URL(route, base);
      const location = response.headers()['location'];
      log({transport:'http',method,path:u.pathname,status:response.status(),
        queryNames:[...u.searchParams.keys()], location:location ? new URL(location,base).pathname : undefined,
        request:scrub(json || form), auth:bearer?'Bearer':basic?'Basic':undefined,
        response:brief ? brief(data) : scrub(data)});
      if (status !== undefined) assert.equal(response.status(), status, method + ' ' + u.pathname);
      return {status:response.status(), data, location};
    }
    const req = request;
    const discovery = (await req('GET','/.well-known/openid-configuration',{status:200})).data;
    assert.equal(discovery.issuer,base);
    assert.deepEqual(discovery.grant_types_supported,['authorization_code','refresh_token']);
    assert.deepEqual(discovery.token_endpoint_auth_methods_supported,['client_secret_basic','none']);
    assert(!('device_authorization_endpoint' in discovery));
    const keys = (await req('GET','/oauth2/jwks',{status:200})).data.keys;
    assert(keys.length > 0);
    assert(keys.every(k => !('d' in k) && !('p' in k)));
    const admin = ok((await req('POST','/api/auth/login',{json:{principalType:'username',principal:'admin',credentials:browserPassword}})).data,'admin login');
    const bearer = admin.accessToken.token;
    const list = async () => ok((await req('GET','/api/sys/auth-clients',{bearer})).data,'clients');
    const a = 'sso-acceptance-a', b = 'sso-acceptance-b';
    for (const id of [a,b]) {
      const previous = await list();
      if (previous.clients.some(c => c.clientId === id))
        ok((await req('DELETE','/api/sys/auth-clients/'+id,{bearer,json:{expectedRevision:previous.revision}})).data,'delete fixture');
    }
    async function create(id, name, type) {
      const previous = await list();
      const secret = type === 'CONFIDENTIAL' ? ok((await req('POST','/api/sys/auth-clients/'+id+'/secret/generate',{bearer})).data,'generate').clientSecret : undefined;
      const client = {clientId:id,clientName:name,enabled:true,clientType:type,redirectUris:[base+'/rp/callback'],
        oidc:{enabled:true,scopes:type==='CONFIDENTIAL'?['openid','profile','email','offline_access']:['openid'],postLogoutRedirectUris:[base+'/rp/signed-out']},
        apiAuthorization:{enabled:false,allowedBoundary:{permissions:[]}},
        consentPolicy:{mode:'EXPLICIT'},introspectionResources:[]};
      const saved = ok((await req('POST','/api/sys/auth-clients',{bearer,json:{expectedRevision:previous.revision,client,newClientSecret:secret}})).data,'create '+id);
      assert.equal(saved.client.clientId,id);
      assert(!JSON.stringify(saved).includes('clientSecretHash'));
      return {secret,client,revision:previous.revision};
    }
    const first = await create(a,'验收应用甲','CONFIDENTIAL');
    await create(b,'验收应用乙','PUBLIC');
    assert.deepEqual((await list()).clients.filter(c => [a,b].includes(c.clientId)).map(c=>c.clientId).sort(),[a,b]);
    assert(isDenied(await req('PATCH','/api/sys/auth-clients/'+a,{bearer,json:{expectedRevision:first.revision,changes:{clientName:'stale'}}})));
    const profileRoute='/api/sys/user/me/profile';
    const profile = ok((await req('GET',profileRoute,{bearer})).data,'own profile');
    const changes = {nickname:'验收账号'};
    let savedProfile = profile;
    if (profile.values.nickname !== changes.nickname) {
      savedProfile = ok((await req('PATCH',profileRoute,{bearer,json:{schemaRevision:profile.schemaRevision,expectedProfileRevision:profile.profileRevision,changes}})).data,'save profile');
      assert(savedProfile.profileRevision > profile.profileRevision);
      assert(isDenied(await req('PATCH',profileRoute,{bearer,json:{schemaRevision:profile.schemaRevision,expectedProfileRevision:profile.profileRevision,changes:{nickname:'stale'}}})));
    }
    assert.equal(savedProfile.values.nickname,changes.nickname);
    const verifier = randomBytes(48).toString('base64url');
    const nonce = randomBytes(12).toString('hex'), state = randomBytes(12).toString('hex');
    const callback = base+'/rp/callback';
    const authorize = '/oauth2/authorize?' + new URLSearchParams({response_type:'code',client_id:a,
      redirect_uri:callback,scope:'openid profile email offline_access',nonce,state,
      code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256'});
    await page.goto(base + authorize, {waitUntil:'domcontentloaded'});
    await page.locator('#login-username').waitFor({timeout:60000});
    await page.locator('#login-username').fill('admin');
    await page.locator('#login-password').fill(browserPassword);
    await page.locator('button[type=submit]').click();
    await page.getByRole('heading',{name:'接入应用正在申请授权'}).waitFor({timeout:60000});
    await page.getByRole('heading',{name:'验收应用甲'}).waitFor({timeout:12000});
    await page.screenshot({path:path.join(evidence,'consent-identity.png'),fullPage:true});
    await page.getByText('同意向该应用提供上述身份信息',{exact:true}).click();
    await page.getByText('允许离开当前页面后继续访问',{exact:true}).click();
    log({transport:'browser',checkedChoices:await page.locator('input[type=checkbox]:checked').count()});
    await page.getByRole('button',{name:'继续',exact:true}).click();
    await page.waitForURL(u => u.pathname === '/rp/callback',{timeout:15000});
    const returning = new URL(page.url());
    assert.equal(returning.searchParams.get('state'),state);
    const code = returning.searchParams.get('code');
    assert(code, 'callback did not contain code: '+returning.searchParams.get('error'));
    const basic=[a,first.secret];
    const tokens = (await req('POST','/oauth2/token',{basic,form:{grant_type:'authorization_code',code,redirect_uri:callback,code_verifier:verifier},status:200})).data;
    const parts=tokens.id_token.split('.');
    const header=JSON.parse(Buffer.from(parts[0],'base64url'));
    const id=JSON.parse(Buffer.from(parts[1],'base64url'));
    const key=keys.find(k=>k.kid===header.kid);
    assert.equal(header.alg,'RS256');
    assert(verify('RSA-SHA256',Buffer.from(parts[0]+'.'+parts[1]),createPublicKey({key,format:'jwk'}),Buffer.from(parts[2],'base64url')));
    assert.equal(id.iss,base); assert(id.aud.includes(a)); assert.equal(id.nonce,nonce);
    assert(id.exp > Math.floor(Date.now()/1000));
    assert.match(id.sub,/^[0-9a-f-]{36}$/);
    assert.equal(id.nickname,'验收账号');
    if (savedProfile.values.formalName) assert.equal(id.name,savedProfile.values.formalName);
    else assert(!('name' in id));
    assert(!('displayName' in id));
    log({transport:'verification',assertions:['RS256+JWKS','iss','aud','nonce','exp','public UUID sub','current account profile']});
    const userinfo=(await req('GET','/userinfo',{bearer:tokens.access_token,status:200})).data;
    assert.equal(userinfo.sub,id.sub); assert.equal(userinfo.name,id.name); assert.equal(userinfo.nickname,id.nickname);
    assert(isDenied(await req('GET',profileRoute,{bearer:tokens.id_token})));
    assert(isDenied(await req('GET',profileRoute,{bearer:tokens.access_token})));
    assert(isDenied(await req('POST','/oauth2/token',{basic,form:{grant_type:'authorization_code',code,redirect_uri:callback,code_verifier:verifier}})));
    const refresh = token => req('POST','/oauth2/token',{basic,form:{grant_type:'refresh_token',refresh_token:token}});
    assert(tokens.refresh_token,'no refresh token');
    const refreshed=await refresh(tokens.refresh_token);
    assert.equal(refreshed.status,200);
    assert.notEqual(refreshed.data.refresh_token,tokens.refresh_token);
    assert(isDenied(await refresh(tokens.refresh_token)));
    if (process.env.SSO_PHASE === 'prepare-resilience') {
      writeFileSync(config.retained, JSON.stringify({ basic, tokens:refreshed.data, sub:id.sub }), { mode:0o600 });
      log({transport:'verification',result:'pass',assertions:['retained grant for restart and dependency fault validation']});
      return;
    }
    const configRoute='/api/sys/configuration';
    const settingsKey='security.auth.oauth2.settings';
    const getConfig = async key => {
      const result = await req('GET',configRoute,{bearer,brief:x=>{
        const items=x?.data?.items || x?.items || x?.data || [];
        return {code:x?.code,selected:Array.isArray(items)?items.filter(i=>i.key===key).map(i=>({key:i.key,revision:i.revision,value:scrub(i.value)})):[]};
      }});
      const items = result.data.data?.items || result.data.items || result.data.data || [];
      const config = Array.isArray(items) && items.find(i=>i.key===key);
      assert(config,'configuration key not visible: '+key);
      return config;
    };
    const put = (key, expectedRevision, value) => req('PUT',configRoute,{bearer,json:[{key,expectedRevision,value}],brief:x=>({code:x?.code,msg:x?.msg})});
    const settings = await getConfig(settingsKey);
    const externalKey = 'security.auth.externalAuth';
    const external = await getConfig(externalKey);
    assert.equal(external.value.publicBaseUrl,base);
    const extProvider = {key:'sso-fixture-cas',displayName:'验收 CAS',enabled:true,casBaseUrl:base+'/rp/cas',externalIdField:'user'};
    const providerUrl = '/api/auth/cas/'+extProvider.key;
    try {
      ok((await put(externalKey,external.revision,{...external.value,cas:[extProvider]})).data,'configure CAS');
      assert(JSON.stringify((await req('GET','/api/auth/providers',{status:200})).data).includes(extProvider.key));
      const casStart = await req('GET',providerUrl+'/login?returnUrl=%2F');
      assert.equal(casStart.status,302);
      const service = new URL(new URL(casStart.location,base).searchParams.get('service'));
      assert.equal(service.origin,base);
      const changed = await getConfig(externalKey);
      ok((await put(externalKey,changed.revision,{...changed.value,cas:[{...extProvider,displayName:'已变更 CAS'}]})).data,'update CAS');
      service.searchParams.set('ticket','ST-fixture-never-validated');
      const stale = await req('GET',service.pathname+service.search);
      assert.equal(new URL(stale.location,base).pathname,'/login');
      assert(new URL(stale.location,base).searchParams.has('error'));
      log({transport:'verification',assertions:['CAS appears immediately','public callback default','stale CAS callback refused']});
    } finally {
      const current = await getConfig(externalKey);
      if (current.value.cas.some(c=>c.key===extProvider.key)) {
        ok((await put(externalKey,current.revision,external.value)).data,'restore CAS config');
        assert(!JSON.stringify((await req('GET','/api/auth/providers',{status:200})).data).includes(extProvider.key));
      }
    }
    try {
      ok((await put(settingsKey,settings.revision,{...settings.value,enabled:false})).data,'pause');
      assert.equal((await req('GET',authorize)).status,503);
      assert.equal((await refresh(refreshed.data.refresh_token)).status,503);
      await req('GET','/.well-known/openid-configuration',{status:200});
      await req('GET','/userinfo',{bearer:refreshed.data.access_token,status:200});
    } finally {
      const current = await getConfig(settingsKey);
      if (current.value.enabled !== true) ok((await put(settingsKey,current.revision,{...settings.value,enabled:true})).data,'resume');
    }
    const afterResume=await refresh(refreshed.data.refresh_token);
    assert.equal(afterResume.status,200);
    await page.goto(base+'/system-setting/auth-clients',{waitUntil:'domcontentloaded'});
    await page.getByText('验收应用甲',{exact:true}).waitFor({timeout:60000});
    await page.getByText('验收应用乙',{exact:true}).waitFor({timeout:10000});
    await page.screenshot({path:path.join(evidence,'registered-clients.png')});
    await page.locator('tr').filter({hasText:'验收应用甲'}).getByRole('button',{name:'配置',exact:true}).click();
    let dialog = page.getByRole('dialog');
    await dialog.locator('input[name=newClientSecret]').waitFor();
    assert.equal(await dialog.locator('input[name=newClientSecret]').inputValue(), '');
    await page.screenshot({path:path.join(evidence,'client-settings.png'),fullPage:true});
    await dialog.locator('.cxd-Modal-close').click();
    await page.getByRole('button',{name:'新增应用',exact:true}).click();
    dialog = page.getByRole('dialog');
    const secretInput = dialog.locator('input[name=newClientSecret]');
    await secretInput.waitFor();
    await page.waitForFunction(() => { const input=document.querySelector('[role=dialog] input[name=newClientSecret]'); return input && input.value.length>=40; });
    const initialSecret = await secretInput.inputValue();
    assert.equal(await secretInput.getAttribute('type'), 'password');
    await dialog.getByRole('button',{name:'重新生成',exact:true}).click();
    await page.waitForFunction(previous => { const input=document.querySelector('[role=dialog] input[name=newClientSecret]'); return input && input.value.length>=40 && input.value!==previous; }, initialSecret);
    await page.screenshot({path:path.join(evidence,'client-secret-generation.png'),fullPage:true});
    await dialog.locator('.cxd-Modal-close').click();
    log({transport:'verification',assertions:['existing client secret not returned to UI','new client initial secret generated','regenerate updates masked field','cancel discards secret draft']});
    await page.goto(base+'/personal-settings/profile',{waitUntil:'domcontentloaded'});
    await page.getByRole('heading',{name:'个人资料',exact:true}).waitFor({timeout:15000});
    await page.getByText('联系邮箱',{exact:true}).first().waitFor({timeout:10000});
    const nameInput = page.locator('input[name=nickname]');
    await nameInput.fill('浏览器验收账号');
    await page.getByRole('button',{name:'保存资料',exact:true}).click();
    await page.getByRole('status').filter({hasText:'资料已保存'}).waitFor({timeout:10000});
    assert.equal(ok((await req('GET',profileRoute,{bearer})).data,'saved self profile').values.displayName,'浏览器验收账号');
    await page.screenshot({path:path.join(evidence,'account-profile.png'),fullPage:true});
    await page.goto(base+'/system-setting/user-management/profile?userIdentity=admin',{waitUntil:'domcontentloaded'});
    await page.getByRole('heading',{name:'用户资料：admin',exact:true}).waitFor({timeout:15000});
    await page.locator('input[name=nickname]').fill('验收账号');
    await page.getByRole('button',{name:'保存资料',exact:true}).click();
    await page.getByRole('status').filter({hasText:'资料已保存'}).waitFor({timeout:10000});
    await page.screenshot({path:path.join(evidence,'admin-account-profile.png'),fullPage:true});
    assert.equal(ok((await req('GET',profileRoute,{bearer})).data,'saved admin profile').values.displayName,'验收账号');
    const logout = '/connect/logout?' + new URLSearchParams({id_token_hint:afterResume.data.id_token,post_logout_redirect_uri:base+'/rp/signed-out',state:'signed-out'});
    const loggedOut = await req('GET',logout);
    assert.equal(new URL(loggedOut.location,base).pathname,'/rp/signed-out');
    assert(isDenied(await refresh(afterResume.data.refresh_token)));
    assert(isDenied(await req('GET','/userinfo',{bearer:afterResume.data.access_token})));
    log({transport:'verification',result:'pass'});
  } catch(error) {
    await page.screenshot({path:path.join(evidence,'failure.png')}).catch(() => {});
    log({transport:'verification',result:'fail',error:error.name,step:error.message.split('\n')[0]});
    throw error;
  } finally {
    await browser.close();
  }
}
run().catch(error => { process.stderr.write(error.name + ': ' + error.message + '\n'); process.exitCode=1; });
