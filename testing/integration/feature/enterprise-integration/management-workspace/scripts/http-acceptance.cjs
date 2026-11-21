const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const config = require('../../../auth/sso-dynamic-config-oidc/scripts/config.cjs');

const origin = new URL(config.base);
assert(['127.0.0.1', 'localhost'].includes(origin.hostname), 'Use the dedicated local acceptance backend');
const output = path.join(config.evidence, 'enterprise-management-http.jsonl');
fs.writeFileSync(output, '');
const record = value => fs.appendFileSync(output, JSON.stringify({ time: new Date().toISOString(), ...value }) + '\n');
const prefix = 'systemSetting.integrationManagement.';
const enterprise = prefix + 'connections.enterprise.';
const connectionRoute = '/api/sys/enterprise-connections';
const providerRoute = connectionRoute + '/providers/dingtalk';
const configurationRoute = '/api/sys/configuration';
const authRoute = '/api/sys/external-auth';
const clientsRoute = '/api/sys/auth-clients';
const suffix = randomBytes(4).toString('hex');

async function req(method, url, token, body) {
  const headers = { 'content-type': 'application/json' };
  if (token) headers.authorization = 'Bearer ' + token;
  const response = await fetch(config.base + url, {
    method, headers, signal: AbortSignal.timeout(20000),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); }
  catch { throw new Error('Expected JSON from ' + method + ' ' + url + ' (HTTP ' + response.status + ')'); }
  record({ method, path: new URL(url, config.base).pathname, status: response.status,
    applicationCode: data.code, cacheControl: response.headers.get('cache-control') });
  return { status: response.status, data, cacheControl: response.headers.get('cache-control') };
}

function ok(result) {
  assert(result.status >= 200 && result.status < 300, 'Management operation must return a successful HTTP status');
  assert.equal(result.data?.code, 0, 'Management operation must succeed');
  return result.data.data;
}

function denied(result, status = 403) {
  assert.equal(result.status, status, 'Operation must be rejected for the expected reason');
  assert.notEqual(result.data?.code, 0, 'Rejected operation must not report application success');
}

const login = async user => ok(await req('POST', '/api/auth/login', undefined,
  { principalType: 'username', principal: user, credentials: config.password })).accessToken.token;

async function operator(admin, name, authorities) {
  const role = ok(await req('POST', '/api/sys/role', admin, { name: 'Enterprise HTTP ' + name + ' ' + suffix }));
  ok(await req('PUT', '/api/sys/role/' + role.id + '/authority', admin,
    authorities.map(authority => typeof authority === 'string' ? { authority, accessControlParams: {} } : authority)));
  const identity = 'eim-' + name + '-' + suffix;
  ok(await req('POST', '/api/sys/user', admin, { userIdentity: identity, password: config.password, roles: [role.id] }));
  return login(identity);
}

async function section(token, name) {
  return ok(await req('GET', '/api/sys/integration-management/sections/' + name, token));
}

async function scenario(id, verify) {
  await verify();
  record({ scenario: id, result: 'pass' });
}

(async () => {
  const admin = await login('admin');
  const ordinary = await operator(admin, 'ordinary', ['systemSetting.configurationManagement']);
  const reader = await operator(admin, 'reader', [enterprise + 'view']);
  const writer = await operator(admin, 'writer', [enterprise + 'view', enterprise + 'configure']);
  const rotator = await operator(admin, 'rotator', [enterprise + 'view', enterprise + 'rotate']);
  const checker = await operator(admin, 'checker', [enterprise + 'view', enterprise + 'verify']);
  const catalog = await operator(admin, 'catalog', [prefix + 'connections.rpc.catalog']);
  const loginManager = await operator(admin, 'login', [prefix + 'connections.login.configure']);
  const serverManager = await operator(admin, 'server', [prefix + 'applications.authServer.settings']);

  await scenario('EIM-HTTP-001', async () => {
    for (const url of [connectionRoute, providerRoute + '/configuration', providerRoute + '/management-schema', authRoute + '/ldap']) {
      denied(await req('GET', url), 401);
      denied(await req('GET', url, ordinary));
    }
    for (const key of ['enterprise.connections', 'enterprise.credentials', 'enterprise.api-budgets', 'security.auth.externalAuth']) {
      denied(await req('GET', configurationRoute + '/' + key, ordinary), 404);
    }
    denied(await req('GET', '/api/admin/users/admin/bindings', ordinary));
    assert(Array.isArray(ok(await req('GET', '/api/admin/users/admin/bindings', admin))));
  });

  const stored = ok(await req('GET', providerRoute + '/configuration', admin));
  assert.equal(stored.isExisting, false, 'Start with a fresh isolated fixture database');
  const secret = randomBytes(24).toString('base64url');
  const connection = { connectionId: 'dingtalk-main', provider: 'dingtalk', corpId: 'enterprise-http-corp',
    enabled: false, auth: { enabled: false } };
  let saved;
  await scenario('EIM-HTTP-002', async () => {
    saved = ok(await req('PUT', connectionRoute, admin, { connection,
      credential: { appKey: 'enterprise-http-app', appSecret: secret }, expectedRevisions: stored.expectedRevisions }));
    assert.equal(saved.connection.credentialRef, 'managed:dingtalk-main');
    assert.equal(saved.credentialState.configured, true);
    const response = await req('GET', providerRoute + '/configuration', reader);
    ok(response);
    assert.equal(response.cacheControl, 'no-store');
    assert(!JSON.stringify(response.data).includes(secret), 'Management read must not contain the saved application secret');
    const schema = JSON.stringify(ok(await req('GET', providerRoute + '/management-schema', reader)));
    assert(!schema.includes('保存企业连接') && !schema.includes('填写或替换应用凭据') && !schema.includes('检查已保存连接'));
  });

  await scenario('EIM-HTTP-003', async () => {
    denied(await req('PUT', connectionRoute, reader, { connection: saved.connection, expectedRevisions: saved.expectedRevisions }));
    denied(await req('PUT', connectionRoute, writer, { connection: saved.connection,
      credential: { appKey: 'enterprise-http-app', appSecret: randomBytes(24).toString('base64url') },
      expectedRevisions: saved.expectedRevisions }));
    denied(await req('PUT', connectionRoute, rotator, { connection: { ...saved.connection, corpId: 'another-corp' },
      expectedRevisions: saved.expectedRevisions }));
    const after = ok(await req('GET', providerRoute + '/configuration', admin));
    assert.deepEqual(after.expectedRevisions, saved.expectedRevisions, 'Denied mutations must not advance either configuration revision');
    denied(await req('PUT', configurationRoute, admin, [
      { key: 'enterprise.connections', operation: 'SET', expectedRevision: saved.expectedRevisions['enterprise.connections'], value: [] },
    ]), 422);
    denied(await req('GET', configurationRoute + '/workbench/item/enterprise.connections', admin), 404);
  });

  await scenario('EIM-HTTP-004', async () => {
    const results = await Promise.all([1, 2].map(() => req('PUT', connectionRoute, rotator, {
      connection: saved.connection, expectedRevisions: saved.expectedRevisions,
      credential: { appKey: 'enterprise-http-app', appSecret: randomBytes(24).toString('base64url') },
    })));
    assert.equal(results.filter(result => result.status < 400 && result.data.code === 0).length, 1,
      'Exactly one concurrent credential replacement may commit');
    denied(results.find(result => result.data.code !== 0), 409);
    const winner = ok(results.find(result => result.data.code === 0));
    const after = ok(await req('GET', providerRoute + '/configuration', admin));
    assert.deepEqual(after.expectedRevisions, winner.expectedRevisions);
    assert.equal(after.credentialState.credentialVersion, winner.credentialState.credentialVersion);
    const versions = after.expectedRevisions;
    denied(await req('PUT', connectionRoute, rotator, { connection: after.connection,
      credential: { appKey: 'changed-application', appSecret: randomBytes(24).toString('base64url') }, expectedRevisions: versions }), 422);
    assert.deepEqual(ok(await req('GET', providerRoute + '/configuration', admin)).expectedRevisions, versions,
      'Rejected application-identity changes must roll back both writes');
    saved = after;
  });

  await scenario('EIM-HTTP-005', async () => {
    const before = ok(await req('GET', providerRoute + '/budget', reader));
    denied(await req('PUT', providerRoute + '/budget', reader, { requestsPerSecond: 3, expectedRevision: before.expectedRevision }));
    const updated = ok(await req('PUT', providerRoute + '/budget', writer,
      { requestsPerSecond: 3, expectedRevision: before.expectedRevision }));
    assert.equal(updated.requestsPerSecond, 3);
    assert.deepEqual(ok(await req('GET', providerRoute + '/configuration', admin)).expectedRevisions, saved.expectedRevisions,
      'Saving the independent budget must not mutate connection or credential revisions');
    denied(await req('PUT', providerRoute + '/budget', writer, { requestsPerSecond: 4, expectedRevision: before.expectedRevision }), 409);
  });

  await scenario('EIM-HTTP-006', async () => {
    const route = connectionRoute + '/dingtalk-main/verify';
    const unauthorized = await req('POST', route, writer, {});
    denied(unauthorized);
    const disabled = await req('POST', route, checker, {});
    assert.equal(disabled.status, 200, 'Domain errors retain the platform HTTP response contract');
    assert.equal(disabled.data.code, -4000, 'Disabled connection must return the platform business failure code');
    assert(String(disabled.data.msg).includes('停用'), 'Authorized diagnostic must reject the saved disabled connection before any remote exchange');
    assert.deepEqual(ok(await req('GET', providerRoute + '/configuration', admin)).expectedRevisions, saved.expectedRevisions);
  });

  await scenario('EIM-HTTP-007', async () => {
    assert(JSON.stringify(await section(catalog, 'connections')).includes('enterprise-rpc'));
    denied(await req('GET', connectionRoute, catalog));
    denied(await req('GET', '/api/sys/rpc-runtime', catalog));
    assert(!JSON.stringify(await section(catalog, 'applications')).includes('/applications/clients'));
    const clientManager = await operator(admin, 'client', [{
      authority: prefix + 'applications.manage',
      accessControlParams: { clientIds: ['enterprise-http-unassigned'], grantableAuthorityNames: [] },
    }]);
    assert.deepEqual(ok(await req('GET', clientsRoute, clientManager)).clients, []);
    denied(await req('GET', connectionRoute, clientManager));
    denied(await req('GET', authRoute + '/ldap', clientManager));
    denied(await req('GET', clientsRoute + '/registration', clientManager));
  });

  await scenario('EIM-HTTP-008', async () => {
    const initial = ok(await req('GET', authRoute + '/ldap', loginManager));
    denied(await req('GET', clientsRoute, loginManager));
    denied(await req('GET', authRoute + '/settings', loginManager));
    assert(JSON.stringify(await section(loginManager, 'connections')).includes('external-auth/ldap'));
    const edited = ok(await req('PUT', authRoute + '/ldap', loginManager, {
      expectedRevision: initial.expectedRevision,
      changes: { providers: [{ key: 'enterprise-http-directory', enabled: false, displayName: 'HTTP acceptance directory',
        urls: [], attributeMapping: {} }] },
    }));
    assert(edited.changes.providers.some(item => item.key === 'enterprise-http-directory'));
    denied(await req('PUT', authRoute + '/ldap', loginManager,
      { expectedRevision: initial.expectedRevision, changes: initial.changes }), 409);
    ok(await req('PUT', authRoute + '/ldap', loginManager,
      { expectedRevision: edited.expectedRevision, changes: initial.changes }));
    ok(await req('GET', clientsRoute + '/settings', serverManager));
    denied(await req('GET', clientsRoute, serverManager));
    denied(await req('GET', connectionRoute, serverManager));
    assert(JSON.stringify(await section(serverManager, 'applications')).includes('/applications/settings'));
  });
  record({ result: 'pass', scenarios: 8, boundary: 'Real local HTTP authentication, RBAC, configuration commands and MySQL persistence; no enterprise API exchange performed' });
  console.log('Enterprise management HTTP acceptance: 8 scenarios passed');
})().catch(error => {
  record({ result: 'fail', errorType: error.name, message: error.message.split('\n')[0] });
  console.error(error.name + ': ' + error.message.split('\n')[0]);
  process.exitCode = 1;
});
