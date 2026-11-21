const config = require('./config.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { randomBytes } = require('node:crypto');
const base = config.base;
const mailbox = config.mailbox;
const evidence = require('node:path').join(config.evidence, 'email-requests.jsonl');
const cfg = '/api/sys/configuration';
const profile = '/api/sys/user/me/profile';
const contact = '/api/sys/user/me/contacts/email/changes';
const policyKey = 'security.user.profile';
const fixturePassword = config.password;
fs.writeFileSync(evidence,'');
function record(row) { fs.appendFileSync(evidence, JSON.stringify({time:new Date().toISOString(), ...row})+'\n'); }
async function req(method, path, {body, token}={}) {
  const headers = {'content-type':'application/json'};
  if (token) headers.authorization = 'Bearer ' + token;
  const res = await fetch(base + path, {method,headers,body:body === undefined ? undefined : JSON.stringify(body)});
  const out = await res.json();
  record({method,path:path.replace(/\/changes\/[^/]+/,'/changes/<redacted>'),status:res.status,applicationCode:out.code,
    errorCode:typeof out.msg==='string' ? out.msg.match(/^\[([A-Z_]+)\]/)?.[1] : undefined});
  return {res,out};
}
function requireOk({res,out}, step) {
  assert.equal(out.code, 0, step + ' HTTP ' + res.status + ' ' + (out.msg || ''));
  return out.data;
}
async function login() {
  return requireOk(await req('POST','/api/auth/login',{body:{principalType:'username',principal:'admin',credentials:fixturePassword}}),'login').accessToken.token;
}
const denied = ({res,out}) => !res.ok || out.code !== 0;
async function run() {
  const token = await login();
  const getProfile = async () => requireOk(await req('GET',profile,{token}),'profile');
  const getConfig = async () => {
    const data = requireOk(await req('GET',cfg,{token}),'configuration');
    return Object.fromEntries((data.items ?? data).map(x => [x.key,x]));
  };
  const update = async (items) => requireOk(await req('PUT',cfg,{token,body:items}),'write configuration');
  const templateRoute = '/api/sys/notification-template';
  const templateName = 'sso-contact-email';
  if (denied(await req('GET', templateRoute + '/' + templateName, {token}))) {
    requireOk(await req('POST',templateRoute,{token,body:{name:templateName,title:'Acceptance email verification',
      isEnabled:true,channel:{email:{schema:{titleTemplate:'Ouroboros email verification',
        contentTemplate:'<a href="\${confirmationUrl}">Verify</a>'}}}}}),'create notification template');
  }
  const start = await getConfig();
  const initial = await getProfile();
  const previousMail = initial.values.email ?? null;
  const smtp = {
    'notification.email.protocol':'smtp',
    'notification.email.host':'127.0.0.1',
    'notification.email.port':Number(process.env.SSO_SMTP_PORT || 11025),
    'notification.email.username':'acceptance@example.test',
    'notification.email.authorizationCode':'only-the-local-smtp-sink',
  };
  await update(Object.entries(smtp).map(([key,value])=>({key,value,expectedRevision:start[key]?.revision})));
  try {
    const policy = start[policyKey];
    assert(policy?.value?.verification,'profile rules unavailable');
    await update([{key:policyKey,expectedRevision:policy.revision,
      value:{...policy.value,verification:{...policy.value.verification,email:'REQUIRED'}}}]);
    fs.rmSync(mailbox,{force:true});
    const before = await getProfile();
    const email = 'sso-' + randomBytes(4).toString('hex') + '@example.test';
    const input = {schemaRevision:before.schemaRevision,expectedProfileRevision:before.profileRevision,newValue:email,password:fixturePassword};
    const requested = requireOk(await req('POST',contact,{token,body:input}),'request email verification');
    assert.equal(requested.status,'PENDING');
    assert.equal((await getProfile()).values.email ?? null,previousMail);
    const raw = fs.readFileSync(mailbox,'utf8');
    const separator = raw.search(/\r?\n\r?\n/);
    const headers = raw.slice(0, separator);
    const encoded = raw.slice(separator).trim();
    const mail = /Content-Transfer-Encoding:\s*base64/i.test(headers)
      ? Buffer.from(encoded.replace(/\s/g,''), 'base64').toString('utf8')
      : /Content-Transfer-Encoding:\s*quoted-printable/i.test(headers)
        ? encoded.replace(/=\r?\n/g,'').replace(/=([0-9A-F]{2})/g,(_m,hex)=>String.fromCharCode(parseInt(hex,16))) : encoded;
    record({mailType: /Content-Transfer-Encoding:\s*([^\r\n]+)/i.exec(headers)?.[1], hasHttpsLink:mail.includes('http'), hasUnrenderedPlaceholder:mail.includes('${confirmationUrl}')});
    assert(headers.includes(email));
    const link = mail.match(/https?:[^"\s]+#contactRequestId=[^"\s]+/)?.[0];
    assert(link,'verification link unavailable');
    const url = new URL(link.replaceAll('&amp;','&'));
    assert.equal(url.origin,base);
    assert.equal(url.pathname,'/personal-settings/profile');
    const fragment = new URLSearchParams(url.hash.slice(1));
    assert.equal(fragment.get('contactRequestId'),requested.requestId);
    const body = {ticket:fragment.get('ticket')};
    assert(body.ticket);
    const confirm = contact+'/'+encodeURIComponent(requested.requestId)+'/confirm';
    assert(denied(await req('POST',confirm,{token:await login(),body})));
    assert(denied(await req('POST',confirm,{token,body:{ticket:'invalid-proof'}})));
    const accepted = requireOk(await req('POST',confirm,{token,body}),'confirmation');
    assert.equal(accepted.status,'APPLIED');
    const saved = await getProfile();
    assert.equal(saved.values.email,email);
    assert.equal(saved.contactStatus.emailVerified,true);
    assert(denied(await req('POST',confirm,{token,body})));
    record({result:'pass',assertions:['SMTP delivered to the pending address','old address retained until proof','another session denied','bad proof denied','verified account value persisted','replay denied']});
  } finally {
    const latest = await getConfig();
    if (latest[policyKey].value.verification.email === 'REQUIRED') {
      await update([{key:policyKey,expectedRevision:latest[policyKey].revision,value:start[policyKey].value}]);
    }
    fs.rmSync(mailbox,{force:true});
  }
}
run().catch(error=>{
  record({result:'fail',errorType:error.name});
  console.error(error.name + ': ' + error.message);
  process.exitCode=1;
});
