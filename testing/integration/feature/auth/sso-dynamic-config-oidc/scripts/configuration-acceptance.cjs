const config = require('./config.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const output = path.join(config.evidence, 'configuration-requests.jsonl');
const key = 'security.auth.oauth2.clients';
const route = '/api/sys/configuration';
const clients = '/api/sys/auth-clients';
fs.writeFileSync(output, '');
const log = row => fs.appendFileSync(output, JSON.stringify({time:new Date().toISOString(), ...row})+'\n');
const ok = result => { assert.equal(result.data?.code, 0); return result.data.data; };
const denied = result => result.status >= 400 || result.data?.code !== 0;
async function req(method, url, bearer, body) {
  const headers = {'content-type':'application/json'};
  if (bearer) headers.authorization = 'Bearer '+bearer;
  const response = await fetch(config.base+url, {method,headers,body:body === undefined ? undefined : JSON.stringify(body)});
  const data = await response.json();
  log({method,path:new URL(url,config.base).pathname,status:response.status,applicationCode:data.code,
    cacheControl:response.headers.get('cache-control'),
    errorCode:typeof data.msg==='string' ? data.msg.match(/^\[([A-Z_]+)\]/)?.[1] : undefined});
  return {status:response.status,data};
}
const login = async user => ok(await req('POST','/api/auth/login',undefined,
  {principalType:'username',principal:user,credentials:config.password})).accessToken.token;
function projected(value) {
  assert(!/"(?:clientSecretHash|clientSecretRef|registrationId|newClientSecret)"\s*:/.test(JSON.stringify(value)), 'sensitive field in management projection');
}
(async () => {
  const admin = await login('admin');
  const initial = ok(await req('GET',clients,admin));
  assert(initial.clients.length >= 2, 'run browser acceptance first');
  const target = initial.clients.find(item => item.clientId === 'sso-acceptance-a');
  assert(target);
  const other = initial.clients.find(item => item.clientId !== target.clientId);
  const suffix = randomBytes(4).toString('hex');
  async function operator(name, authorities) {
    const role = ok(await req('POST','/api/sys/role',admin,{name:'SSO '+name+' '+suffix}));
    ok(await req('PUT','/api/sys/role/'+role.id+'/authority',admin,authorities));
    const user = 'sso-'+name+'-'+suffix;
    ok(await req('POST','/api/sys/user',admin,{userIdentity:user,password:config.password,roles:[role.id]}));
    return login(user);
  }
  const ordinary = await operator('config', [{authority:'systemSetting.configurationManagement',accessControlParams:{}}]);
  const list = ok(await req('GET',route,ordinary));
  assert(!(list.items || list).some(item => item.key === key));
  assert(denied(await req('GET',route+'/'+key,ordinary)));
  assert(denied(await req('GET',route+'/workbench/item/'+key,ordinary)));
  assert(denied(await req('GET',clients,ordinary)));
  for (const mutation of [{key,expectedRevision:initial.revision,value:[]},
    {key,expectedRevision:initial.revision,operation:'RESTORE_DEFAULT'}]) {
    assert(denied(await req('PUT',route,ordinary,[mutation])));
  }
  assert(denied(await req('GET',route+'/'+key)));
  const manager = await operator('manager', [
    {authority:'systemSetting.configurationManagement',accessControlParams:{}},
    {authority:'systemSetting.integrationManagement.applications.manage',accessControlParams:{clientIds:[target.clientId],grantableAuthorityNames:[],$fieldControl:['clientName']}}
  ]);
  const managed = ok(await req('GET',clients,manager));
  assert.deepEqual(managed.clients.map(item=>item.clientId), [target.clientId]);
  assert.equal(managed.canCreate,false);
  projected(managed);
  for(const bearer of [ordinary,manager]) {
    const registration=clients+'/registration';
    assert(denied(await req('GET',registration,bearer)));
    assert(denied(await req('PUT',registration,bearer,{expectedRevision:'0',settings:{}})));
    assert(denied(await req('POST',registration+'/credentials',bearer)));
    assert(denied(await req('DELETE',registration+'/credentials/00000000-0000-0000-0000-000000000000',bearer)));
  }
  for (const url of [route+'/'+key,route]) {
    const value = ok(await req('GET',url,manager));
    projected(value);
    assert(!JSON.stringify(value).includes('"clientId":"'+other.clientId+'"'), 'another client exposed');
  }
  for(const bearer of [manager,admin]) {
    const takenOver=await req('GET',route+'/workbench/item/'+key,bearer);
    assert.equal(takenOver.data.code,404,'dedicated management excludes the generic workbench item');
  }
  assert(denied(await req('GET',clients+'/'+other.clientId,manager)));
  assert(denied(await req('POST',clients+'/'+target.clientId+'/secret/generate',manager)));
  assert(denied(await req('PATCH',clients+'/'+target.clientId,manager,
    {expectedRevision:managed.revision,changes:{enabled:false}})));
  assert(denied(await req('PUT',route,manager,[{key,expectedRevision:managed.revision,value:[]}])));
  assert(denied(await req('POST',clients+'/'+target.clientId+'/restore-defaults',manager,{expectedRevision:managed.revision})));
  const renamed = target.clientName+'验收';
  ok(await req('PATCH',clients+'/'+target.clientId,manager,{expectedRevision:managed.revision,changes:{clientName:renamed}}));
  const current = ok(await req('GET',clients,admin));
  assert.equal(current.clients.find(item=>item.clientId===target.clientId).clientName,renamed);
  assert.deepEqual(current.clients.find(item=>item.clientId===other.clientId),other);
  const parallel = await Promise.all(Array.from({length:4},(_,i)=>req('PATCH',clients+'/'+target.clientId,admin,
    {expectedRevision:current.revision,changes:{clientName:'CAS '+i}})));
  assert.equal(parallel.filter(result => !denied(result)).length,1, 'exactly one competing configuration mutation must commit');
  const committed = ok(await req('GET',clients,admin));
  ok(await req('PATCH',clients+'/'+target.clientId,admin,{expectedRevision:committed.revision,changes:{clientName:target.clientName}}));
  const {chromium}=require(path.resolve('frontends/web/main-app/node_modules/playwright'));
  const browser=await chromium.launch();
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    await page.goto(config.base+'/login');
    await page.locator('#login-username').fill('sso-manager-'+suffix);
    await page.locator('#login-password').fill(config.password);
    await page.locator('button[type=submit]').click();
    await page.waitForURL(url=>url.pathname!=='/login',{timeout:60000});
    await page.goto(config.base+'/system-setting/auth-clients');
    await page.getByText(target.clientName,{exact:true}).first().waitFor({timeout:30000});
    assert.equal(await page.getByText('动态注册',{exact:true}).count(),0);
    await page.screenshot({path:path.join(config.evidence,'client-manager-no-registration.png'),fullPage:true});
  } finally {await browser.close();}
  log({result:'pass',assertions:['ordinary configuration authority cannot access protected clients',
    'anonymous protected read denied','generic list shares projection and dedicated client configuration is excluded from workbench','client and field isolation enforced',
    'secret generation and restore cannot bypass security authority','unrelated client retained','database CAS: one winner out of four']});
})().catch(error => { log({result:'fail',errorType:error.name,message:error.message.split('\n')[0]}); console.error(error.stack); process.exitCode=1; });
