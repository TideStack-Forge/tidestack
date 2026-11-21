const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const config = require('../../../auth/sso-dynamic-config-oidc/scripts/config.cjs');
const { chromium, expect } = require(path.resolve('frontends/web/main-app/node_modules/@playwright/test'));

assert(['localhost', '127.0.0.1'].includes(new URL(config.base).hostname), 'Use the isolated local acceptance gateway');
const output = path.join(config.evidence, 'enterprise-management-browser.jsonl');
fs.writeFileSync(output, '');
const record = value => fs.appendFileSync(output, JSON.stringify({ time: new Date().toISOString(), ...value }) + '\n');
const route = '/api/sys/enterprise-connections';
const provider = route + '/providers/dingtalk/configuration';

async function request(method, url, token, body) {
  const response = await fetch(config.base + url, {
    method, headers: { 'content-type': 'application/json', authorization: 'Bearer ' + token },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000),
  });
  const result = await response.json();
  record({ method, path: url, status: response.status, applicationCode: result.code });
  assert(response.ok && result.code === 0, 'Fixture management operation must succeed: ' + method + ' ' + url);
  return result.data;
}

async function login(page, username) {
  await page.goto(config.base + '/login');
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(config.password);
  await page.locator('button[type=submit]').click();
  await page.waitForURL(url => url.pathname !== '/login', { timeout: 60000 });
  await page.goto(config.base + '/system-setting/integration-management');
  await expect(page.getByRole('heading', { name: '外部连接', exact: true })).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole('heading', { name: '应用接入', exact: true })).toBeVisible();
}

async function selectDingtalk(page) {
  await page.getByText('钉钉', { exact: true }).click();
  await expect(page.locator('input[name="connection.connectionId"]')).toHaveValue('dingtalk-main');
  await expect(page.locator('.cxd-Spinner-overlay:visible')).toHaveCount(0);
}

async function screenshot(page, name) {
  await expect(page.locator('.cxd-Spinner-overlay:visible')).toHaveCount(0);
  await page.getByRole('heading', { name: '外部连接', exact: true }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: path.join(config.evidence, name), fullPage: true, animations: 'disabled' });
}

(async () => {
  const loggedIn = await fetch(config.base + '/api/auth/login', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ principalType: 'username', principal: 'admin', credentials: config.password }),
    signal: AbortSignal.timeout(20000),
  });
  const loginResult = await loggedIn.json();
  assert(loggedIn.ok && loginResult.code === 0, 'Isolated administrator login must succeed');
  const admin = loginResult.data.accessToken.token;
  const initial = await request('GET', provider, admin);
  assert(initial.isExisting && initial.connection.enabled === false, 'Run HTTP acceptance first; keep the fixture connection disabled');
  const suffix = randomBytes(4).toString('hex');
  const role = await request('POST', '/api/sys/role', admin, { name: 'Enterprise browser reader ' + suffix });
  await request('PUT', '/api/sys/role/' + role.id + '/authority', admin,
    [{ authority: 'systemSetting.integrationManagement.connections.enterprise.view', accessControlParams: {} }]);
  const reader = 'eim-browser-reader-' + suffix;
  await request('POST', '/api/sys/user', admin, { userIdentity: reader, password: config.password, roles: [role.id] });

  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    page.on('response', response => {
      const url = new URL(response.url());
      if (url.pathname.startsWith('/api/')) record({ transport: 'browser', method: response.request().method(),
        path: url.pathname, status: response.status(),
        authenticated: Boolean(response.request().headers().authorization) });
    });
    await login(page, 'admin');
    for (const name of ['钉钉', '企业微信', '飞书']) {
      await expect(page.getByText(name, { exact: true })).toBeVisible();
    }
    for (const [name, api, text] of [
      ['组织关联与同步', '/api/sys/enterprise-directory/management-state', '组织关联与第三方登录绑定相互独立。'],
      ['企业通知', '/api/sys/enterprise-notifications', '企业通知复用现有多渠道通知模板。'],
      ['工作台入口', '/api/sys/enterprise-workbench', '此处生成连接级登录入口 URL'],
    ]) {
      const loaded = page.waitForResponse(response => new URL(response.url()).pathname === api);
      await page.getByText(name, { exact: true }).click();
      const response = await loaded;
      assert.equal(response.request().method(), 'GET', 'Management reads must use the registered HTTP method');
      assert.equal(response.status(), 200);
      assert.equal((await response.json()).code, 0);
      await expect(page.getByText(text, { exact: false }).first()).toBeVisible();
    }
    await selectDingtalk(page);
    await screenshot(page, 'enterprise-live-workspace.png');
    record({ scenario: 'EIM-LIVE-001', result: 'pass' });

    let previous = initial;
    for (let turn = 0; turn < 2; turn++) {
      await page.locator('.cxd-Form-item').filter({ has: page.getByText('填写或替换应用凭据', { exact: true }) })
        .locator('[data-role="switch"]').click();
      await page.locator('input[name="credential.appKey"]').fill('enterprise-http-app');
      await page.locator('input[name="credential.appSecret"]').fill(randomBytes(24).toString('base64url'));
      const submitted = page.waitForResponse(response => response.request().method() === 'PUT'
        && new URL(response.url()).pathname === route);
      await page.getByRole('button', { name: '保存企业连接', exact: true }).click();
      const response = await submitted;
      const value = await response.json();
      assert.equal(response.status(), 200, 'Consecutive browser credential replacement must commit');
      assert.equal(value.code, 0);
      await expect(page.locator('input[name="credential.appSecret"]')).toHaveCount(0);
      const current = await request('GET', provider, admin);
      assert.deepEqual(current.expectedRevisions, value.data.expectedRevisions);
      assert.notDeepEqual(current.expectedRevisions, previous.expectedRevisions);
      assert.equal(current.connection.enabled, false);
      previous = current;
    }
    await screenshot(page, 'enterprise-live-credential-saved.png');
    record({ scenario: 'EIM-LIVE-002', result: 'pass' });
    await context.close();

    const readContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const readPage = await readContext.newPage();
    const paths = [];
    readPage.on('request', req => paths.push(new URL(req.url()).pathname));
    await login(readPage, reader);
    await selectDingtalk(readPage);
    for (const name of ['保存企业连接', '检查已保存连接']) {
      await expect(readPage.getByRole('button', { name, exact: true })).toHaveCount(0);
    }
    await expect(readPage.getByText('填写或替换应用凭据', { exact: true })).toHaveCount(0);
    await expect(readPage.locator('input[name="connection.connectionId"]')).toBeDisabled();
    assert(!paths.some(url => url.startsWith('/api/sys/auth-clients')), 'Read-only connections must not request application management');
    await screenshot(readPage, 'enterprise-live-readonly.png');
    record({ scenario: 'EIM-LIVE-003', result: 'pass' });
    await readContext.close();
    record({ result: 'pass', scenarios: 3, boundary: 'Production frontend and backend with isolated MySQL/Redis; no mocked routes or enterprise API exchange' });
    console.log('Enterprise management live browser acceptance: 3 scenarios passed');
  } finally {
    await browser.close();
  }
})().catch(error => {
  record({ result: 'fail', errorType: error.name, message: error.message.split('\n')[0] });
  console.error(error.stack);
  process.exitCode = 1;
});
