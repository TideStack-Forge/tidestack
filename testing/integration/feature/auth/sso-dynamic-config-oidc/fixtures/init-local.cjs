const config = require('../scripts/config.cjs');
const fs = require('node:fs');
const path = require('node:path');
const { generateKeyPairSync, randomBytes } = require('node:crypto');
const secretDir=path.join(config.fixtureRoot,'secrets');
fs.mkdirSync(secretDir,{recursive:true,mode:0o700});
const jwkFile=path.join(secretDir,'jwks.json');
if(!fs.existsSync(jwkFile)) {
  const {privateKey}=generateKeyPairSync('rsa',{modulusLength:2048});
  fs.writeFileSync(jwkFile,JSON.stringify({keys:[{...privateKey.export({format:'jwk'}),kid:'sso-fixture-key',use:'sig',alg:'RS256'}]}),{mode:0o600});
}
const contactKey=path.join(secretDir,'contact-key');
if(!fs.existsSync(contactKey)) fs.writeFileSync(contactKey,randomBytes(48).toString('base64url'),{mode:0o600});
const cacheKey=path.join(secretDir,'cache-admin-key');
if(!fs.existsSync(cacheKey)) fs.writeFileSync(cacheKey,randomBytes(48).toString('base64url'),{mode:0o600});
const modelDir=path.join(config.fixtureRoot,'metadata/business-model');
fs.mkdirSync(modelDir,{recursive:true});
fs.copyFileSync(path.join(__dirname,'ssoFixture.record.business.json'),path.join(modelDir,'ssoFixture.record.business.json'));
const templateDir=path.join(config.fixtureRoot,'metadata/notification-template');
fs.mkdirSync(templateDir,{recursive:true});
fs.writeFileSync(path.join(templateDir,'sso-contact-email.notification-template.json'),JSON.stringify({
 name:'sso-contact-email',title:'Acceptance email verification',isEnabled:true,
 channel:{email:{schema:{titleTemplate:'Ouroboros email verification',contentTemplate:'<a href="${confirmationUrl}">Verify</a>'}}}
},null,2));
if (!process.env.SSO_DB_PASSWORD) throw new Error('Set SSO_DB_PASSWORD for the dedicated database');
const local={server:{address:'127.0.0.1',port:Number(process.env.SSO_BACKEND_PORT||18371)},spring:{cache:{type:'redis'},datasource:{url:process.env.SSO_DB_URL||'jdbc:mysql://127.0.0.1:13371/ouroboros_sso?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',username:process.env.SSO_DB_USER||'sso_fixture',password:process.env.SSO_DB_PASSWORD,'driver-class-name':'com.mysql.cj.jdbc.Driver'},data:{redis:{host:'127.0.0.1',port:Number(process.env.SSO_REDIS_PORT||16371),timeout:'2s','connect-timeout':'2s'}}},'root-path':config.fixtureRoot,ouroboros:{metadata:{path:path.join(config.fixtureRoot,'metadata')},microWeb:{autoDeploy:false,deployPath:config.fixtureRoot},'document-management':{storage:{filesystem:{'base-path':path.join(config.fixtureRoot,'documents'),'temp-path':path.join(config.fixtureRoot,'documents/tmp')}}},upload:{fileSystem:{rootPath:path.join(config.fixtureRoot,'uploads')}},security:{rbac:{'public-subject':{'verify-on-startup':true},autoCreateUser:{password:config.password}},'external-auth':{'public-base-url':config.base},oidc:{enabled:true,issuer:config.base,'jwk-set-ref':'file:'+jwkFile},profile:{'email-template':'sso-contact-email','public-origin':config.base,'verification-key-ref':'file:'+contactKey}}}};
local.ouroboros.platform={cache:{token:{secret:fs.readFileSync(cacheKey,'utf8')}}};
const localFile=path.join(config.fixtureRoot,'local.yml');
if(fs.existsSync(localFile)) throw new Error('Fixture local.yml already exists; preserve it and select a new SSO_FIXTURE_ROOT');
fs.writeFileSync(localFile,JSON.stringify(local,null,2)+'\n',{mode:0o600});
console.log('Created fixture configuration, signing keys and metadata under '+config.fixtureRoot);
