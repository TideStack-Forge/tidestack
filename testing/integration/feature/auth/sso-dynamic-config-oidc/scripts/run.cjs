const config = require('./config.cjs');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
(async () => {
  const result = { time:new Date().toISOString(), scenario:'auth/sso-dynamic-config-oidc', checks:[] };
  const check = (name, passed) => { result.checks.push({name,passed}); assert(passed,name); };
  try {
    check('dedicated acceptance instance explicitly selected', process.env.SSO_ISOLATED_FIXTURE === '1');
    check('browser production build present', fs.existsSync('frontends/web/main-app/dist/index.html'));
    check('business model fixture present', fs.existsSync(path.join(config.fixtureRoot, 'metadata/business-model/ssoFixture.record.business.json')));
    check('browser library present', fs.existsSync('frontends/web/main-app/node_modules/playwright/package.json'));
    const discovery = await fetch(config.base+'/.well-known/openid-configuration',{signal:AbortSignal.timeout(10000)});
    check('discovery reachable', discovery.status === 200);
    check('issuer matches acceptance origin', (await discovery.json()).issuer === config.base);
    result.status = 'pass';
  } catch (error) { result.status='fail'; result.error=error.message; throw error; }
  finally { fs.writeFileSync(path.join(config.evidence,'fixture-preflight.json'),JSON.stringify(result,null,2)+'\n'); }
  if (process.argv.includes('--preflight')) return;
  const batch = {startedAt:new Date().toISOString(), steps:[]};
  const scripts=['browser-acceptance.cjs','business-acceptance.cjs','email-acceptance.cjs','configuration-acceptance.cjs','dcr-acceptance.cjs'];
  if(process.env.SSO_MYSQL_CONTAINER) scripts.push('dcr-concurrency.cjs');
  if(process.env.SSO_REDIS_CONTAINER) scripts.push('dcr-resilience.cjs');
  for (const file of scripts) {
    const execution=spawnSync(process.execPath,[path.join(__dirname,file)],{stdio:'inherit',env:process.env});
    batch.steps.push({script:file,status:execution.status===0?'pass':'fail',exitCode:execution.status});
    batch.completedAt=new Date().toISOString();
    batch.status=batch.steps.every(step=>step.status==='pass')?'pass':'fail';
    fs.writeFileSync(path.join(config.evidence,'system-batch.json'),JSON.stringify(batch,null,2)+'\n');
    if (execution.error) throw execution.error;
    assert.equal(execution.status,0,file);
  }
})().catch(error => { console.error(error.message); process.exitCode=1; });
