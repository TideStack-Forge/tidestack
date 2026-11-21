const path = require('node:path');
const fs = require('node:fs');
const repo = path.resolve(__dirname, '../../../../../..');
process.chdir(repo);
if (process.env.SSO_ISOLATED_FIXTURE !== '1' || !process.env.SSO_ADMIN_PASSWORD) {
  throw new Error('Set SSO_ISOLATED_FIXTURE=1 and SSO_ADMIN_PASSWORD for the dedicated acceptance instance');
}
const evidence = path.resolve(process.env.SSO_EVIDENCE_DIR || 'tmp/testing-evidence/sso-dynamic-config-oidc/system');
const fixtureRoot = path.resolve(process.env.SSO_FIXTURE_ROOT || 'tmp/sso-local');
fs.mkdirSync(evidence, { recursive:true });
module.exports = {
  base: process.env.SSO_BASE_URL || 'http://127.0.0.1:18372',
  evidence, fixtureRoot, password: process.env.SSO_ADMIN_PASSWORD,
  mailbox: path.join(fixtureRoot, 'secrets/last-email.eml'),
  retained: path.join(fixtureRoot, 'secrets/resilience.json'),
};
