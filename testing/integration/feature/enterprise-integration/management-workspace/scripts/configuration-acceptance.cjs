const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const config = require('../../../auth/sso-dynamic-config-oidc/scripts/config.cjs');
const { chromium, expect } = require(path.resolve('frontends/web/main-app/node_modules/@playwright/test'));

assert(['127.0.0.1', 'localhost'].includes(new URL(config.base).hostname), 'Use the dedicated local acceptance gateway');
const output = path.join(config.evidence, 'enterprise-configuration-browser.jsonl');
fs.writeFileSync(output, '');
const record = value => fs.appendFileSync(output, JSON.stringify({ time: new Date().toISOString(), ...value }) + '\n');
const clients = '/api/sys/auth-clients';
const ldap = '/api/sys/external-auth/ldap';
const settings = clients + '/settings';
const registration = clients + '/registration';
const prefix = 'systemSetting.integrationManagement.';
const suffix = randomBytes(4).toString('hex');

async function response(method, url, token, body) {
  const headers = { 'content-type': 'application/json' };
  if (token) headers.authorization = 'Bearer ' + token;
  const res = await fetch(config.base + url, { method, headers, signal: AbortSignal.timeout(20000),
    body: body === undefined ? undefined : JSON.stringify(body) });
  const data = await res.json();
  record({ method, path: url, status: res.status, applicationCode: data.code });
  return { status: res.status, data };
}

function ok(result) {
  assert(result.status >= 200 && result.status < 300 && result.data.code === 0, 'Management operation must succeed');
  return result.data.data;
}

const request = async (...args) => ok(await response(...args));
const login = async user => (await request('POST', '/api/auth/login', undefined,
  { principalType: 'username', principal: user, credentials: config.password })).accessToken.token;

async function operator(admin, name, authority, accessControlParams = {}) {
  const role = await request('POST', '/api/sys/role', admin, { name: 'Workspace ' + name + ' ' + suffix });
  await request('PUT', '/api/sys/role/' + role.id + '/authority', admin, [{ authority, accessControlParams }]);
  const username = 'eim-settings-' + name + '-' + suffix;
  await request('POST', '/api/sys/user', admin, { userIdentity: username, password: config.password, roles: [role.id] });
  return { username, token: await login(username) };
}

async function open(browser, username) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const paths = [];
  page.on('request', req => paths.push(new URL(req.url()).pathname));
  page.on('response', res => {
    const url = new URL(res.url());
    if (url.pathname.startsWith('/api/')) record({ transport: 'browser', method: res.request().method(),
      path: url.pathname, status: res.status() });
  });
  await page.goto(config.base + '/login');
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(config.password);
  await page.locator('button[type=submit]').click();
  await page.waitForURL(url => url.pathname !== '/login', { timeout: 60000 });
  await page.goto(config.base + '/system-setting/integration-management');
  await expect(page.getByRole('heading', { name: '外部连接', exact: true })).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole('heading', { name: '应用接入', exact: true })).toBeVisible();
  return { context, page, paths };
}

async function submit(page, button, method, route, confirmation = false) {
  const completed = page.waitForResponse(res => res.request().method() === method && new URL(res.url()).pathname === route);
  await button.click();
  if (confirmation) await page.getByRole('button', { name: /^(确定|Confirm)$/ }).click();
  const res = await completed;
  const data = await res.json();
  ok({ status: res.status(), data });
  await expect(page.locator('.cxd-Spinner-overlay:visible')).toHaveCount(0);
  return data.data;
}

function isolated(paths, forbidden) {
  for (const route of forbidden) assert(!paths.some(url => url === route || url.startsWith(route + '/')),
    'Independent configuration must not request ' + route);
}

async function screenshot(page, name) {
  await expect(page.locator('.cxd-Spinner-overlay:visible')).toHaveCount(0);
  await page.screenshot({ path: path.join(config.evidence, name), fullPage: true, animations: 'disabled' });
}

(async () => {
  const admin = await login('admin');
  const originals = new Map();
  for (const route of [ldap, settings, registration]) originals.set(route, await request('GET', route, admin));
  assert.equal(originals.get(registration).settings.enabled, false, 'Keep dynamic registration disabled in this fixture');
  const browser = await chromium.launch();
  try {
    const providerKey = 'workspace-directory-' + suffix;
    await request('PUT', ldap, admin, { expectedRevision: originals.get(ldap).expectedRevision,
      changes: { providers: [...originals.get(ldap).changes.providers,
        { key: providerKey, enabled: false, displayName: 'Workspace directory', urls: [], attributeMapping: {} }] } });
    const loginManager = await operator(admin, 'login', prefix + 'connections.login.configure');
    const loginPage = await open(browser, loginManager.username);
    await loginPage.page.getByText('LDAP / AD', { exact: true }).click();
    await expect(loginPage.page.locator('input[name="key"]').last()).toHaveValue(providerKey);
    await loginPage.page.locator('input[name="displayName"]').last().fill('Updated workspace directory');
    await submit(loginPage.page, loginPage.page.getByRole('button', { name: '保存登录配置', exact: true }), 'PUT', ldap);
    const saved = await request('GET', ldap, admin);
    const selected = saved.changes.providers.find(item => item.key === providerKey);
    assert.equal(selected.displayName, 'Updated workspace directory');
    assert.equal(selected.enabled, false);
    assert.deepEqual(saved.changes.providers.filter(item => item.key !== providerKey), originals.get(ldap).changes.providers);
    isolated(loginPage.paths, [clients, '/api/sys/enterprise-connections']);
    await screenshot(loginPage.page, 'enterprise-live-login-configuration.png');
    await loginPage.context.close();
    record({ scenario: 'EIM-LIVE-004', result: 'pass' });

    const serverManager = await operator(admin, 'server', prefix + 'applications.authServer.settings');
    const serverPage = await open(browser, serverManager.username);
    const ttl = serverPage.page.locator('input[name="settings.accessTokenTimeToLive"]');
    await expect(ttl).toHaveValue(originals.get(settings).settings.accessTokenTimeToLive);
    for (const value of ['PT31M', originals.get(settings).settings.accessTokenTimeToLive]) {
      await ttl.fill(value);
      await submit(serverPage.page, serverPage.page.getByRole('button', { name: '保存授权服务器设置', exact: true }), 'PUT', settings, true);
      const current = await request('GET', settings, admin);
      assert.equal(current.settings.accessTokenTimeToLive, value);
      assert.equal(current.settings.enabled, originals.get(settings).settings.enabled);
    }
    assert(!serverPage.paths.includes(clients), 'Settings-only role must not request the application list');
    isolated(serverPage.paths, ['/api/sys/external-auth', '/api/sys/enterprise-connections', registration]);
    await screenshot(serverPage.page, 'enterprise-live-server-settings.png');
    await serverPage.context.close();
    record({ scenario: 'EIM-LIVE-005', result: 'pass' });

    const securityManager = await operator(admin, 'security', prefix + 'applications.security');
    const dcrPage = await open(browser, securityManager.username);
    await dcrPage.page.getByText('动态注册 DCR', { exact: true }).click();
    const quota = dcrPage.page.locator('input[name="maxDynamicClients"]');
    const initialQuota = originals.get(registration).settings.maxDynamicClients;
    await expect(quota).toHaveValue(String(initialQuota));
    for (const value of [initialQuota + 1, initialQuota]) {
      await quota.fill(String(value));
      await submit(dcrPage.page, dcrPage.page.getByRole('button', { name: '保存', exact: true }), 'PUT', registration);
      const current = await request('GET', registration, admin);
      assert.equal(current.settings.maxDynamicClients, value);
      assert.equal(current.settings.enabled, false);
    }
    isolated(dcrPage.paths, [settings, '/api/sys/external-auth', '/api/sys/enterprise-connections']);
    await screenshot(dcrPage.page, 'enterprise-live-dcr-settings.png');
    await dcrPage.context.close();
    record({ scenario: 'EIM-LIVE-006', result: 'pass' });

    const identifiers = ['workspace-a-' + suffix, 'workspace-b-' + suffix];
    for (const [index, id] of identifiers.entries()) {
      const before = await request('GET', clients, admin);
      await request('POST', clients, admin, { expectedRevision: before.revision, client: {
        clientId: id, clientName: 'Workspace application ' + index, enabled: false, clientType: 'PUBLIC',
        redirectUris: [config.base + '/rp/callback'], oidc: { enabled: true, scopes: ['openid'], postLogoutRedirectUris: [] },
        apiAuthorization: { enabled: false, allowedBoundary: { permissions: [] } },
        consentPolicy: { mode: 'EXPLICIT' }, introspectionResources: [],
      } });
    }
    const beforeClients = await request('GET', clients, admin);
    const other = beforeClients.clients.find(item => item.clientId === identifiers[1]);
    const scoped = await operator(admin, 'scoped', prefix + 'applications.manage', {
      clientIds: [identifiers[0]], grantableAuthorityNames: [], $fieldControl: ['clientName'],
    });
    assert.deepEqual((await request('GET', clients, scoped.token)).clients.map(item => item.clientId), [identifiers[0]]);
    const scopedPage = await open(browser, scoped.username);
    await expect(scopedPage.page.getByText('Workspace application 0', { exact: true })).toBeVisible();
    await expect(scopedPage.page.getByText('Workspace application 1', { exact: true })).toHaveCount(0);
    await expect(scopedPage.page.getByRole('button', { name: '新增应用', exact: true })).toHaveCount(0);
    await scopedPage.page.getByRole('button', { name: '配置', exact: true }).click();
    const dialog = scopedPage.page.getByRole('dialog');
    const name = dialog.locator('input[name="clientName"]');
    await expect(name).toBeEnabled();
    await expect(dialog.locator('input[name="clientId"]')).toBeDisabled();
    await name.fill('Scoped workspace application');
    await submit(scopedPage.page, dialog.getByRole('button', { name: /^(提交|Submit)$/ }), 'PATCH', clients + '/' + identifiers[0]);
    await expect(scopedPage.page.getByText('Scoped workspace application', { exact: true })).toBeVisible();
    const afterClients = await request('GET', clients, admin);
    assert.deepEqual(afterClients.clients.find(item => item.clientId === identifiers[1]), other);
    const hidden = await response('GET', clients + '/' + identifiers[1], scoped.token);
    assert.equal(hidden.status, 404);
    assert.equal(hidden.data.code, 404);
    assert.equal((await response('PATCH', clients + '/' + identifiers[0], scoped.token,
      { expectedRevision: afterClients.revision, changes: { enabled: true } })).status, 403);
    const unchanged = await request('GET', clients, admin);
    assert.equal(unchanged.revision, afterClients.revision);
    assert.deepEqual(unchanged.clients, afterClients.clients);
    isolated(scopedPage.paths, [registration, settings, '/api/sys/external-auth', '/api/sys/enterprise-connections']);
    await screenshot(scopedPage.page, 'enterprise-live-scoped-client.png');
    await scopedPage.context.close();
    record({ scenario: 'EIM-LIVE-007', result: 'pass' });
  } finally {
    await browser.close();
    for (const [route, original] of originals) {
      const field = route === ldap ? 'changes' : 'settings';
      const current = await request('GET', route, admin);
      if (JSON.stringify(current[field]) !== JSON.stringify(original[field])) {
        await request('PUT', route, admin, { expectedRevision: current.expectedRevision || current.revision, [field]: original[field] });
      }
    }
  }
  record({ result: 'pass', scenarios: 4, boundary: 'Real scoped roles, production configuration pages and MySQL persistence; no enterprise API exchange' });
  console.log('Enterprise configuration live acceptance: 4 scenarios passed');
})().catch(error => {
  record({ result: 'fail', errorType: error.name, message: error.message.split('\n')[0] });
  console.error(error.stack);
  process.exitCode = 1;
});
