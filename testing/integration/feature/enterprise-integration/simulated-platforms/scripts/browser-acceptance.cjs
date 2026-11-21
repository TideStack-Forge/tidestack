const assert = require('node:assert/strict');
const path = require('node:path');
const { fixture } = require('./platform-http.cjs');
const { chromium, expect } = require(path.resolve('frontends/web/main-app/node_modules/@playwright/test'));
const f = fixture(process.argv[2]);
(async () => {
  const admin = await f.login();
  const browser = await chromium.launch();
  try {
    for (const provider of ['dingtalk', 'wecom', 'feishu']) {
      await f.scenario('EIS-BROWSER-001', provider, async () => {
        const bindings = await f.request('GET', '/api/admin/users/admin/bindings', admin);
        for (const binding of bindings.filter(value => value.providerKey === provider + '-sim')) {
          await f.request('DELETE', '/api/admin/users/admin/bindings/' + binding.id, admin);
        }
        const flow = await f.authorization(provider);
        assert.equal(flow.destination.pathname, '/login/bind');
        const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
        try {
          const page = await context.newPage();
          await page.goto(f.acceptance.base + flow.destination.pathname + flow.destination.search);
          await expect(page.locator('#login-bind-username')).toBeVisible();
          await page.locator('#login-bind-username').fill('admin');
          await page.locator('#login-bind-password').fill(f.acceptance.password);
          await page.locator('button[type=submit]').click();
          await page.waitForURL(url => url.pathname === '/system-setting/integration-management', { timeout: 60000 });
          await expect(page.getByRole('heading', { name: '外部连接', exact: true })).toBeVisible({ timeout: 30000 });
          await expect(page.getByRole('heading', { name: '应用接入', exact: true })).toBeVisible();
          await page.screenshot({ path: path.join(f.acceptance.evidence, 'workbench-' + provider + '.png'), fullPage: true, animations: 'disabled' });
        } finally { await context.close(); }
        const rebound = await f.request('GET', '/api/admin/users/admin/bindings', admin);
        assert.equal(rebound.filter(value => value.providerKey === provider + '-sim').length, 1);
        const again = await f.authorization(provider, 'node-b');
        assert.equal(again.destination.pathname, '/login/callback');
        const secondContext = await browser.newContext();
        try {
          const page = await secondContext.newPage();
          await page.goto(f.acceptance.base + again.destination.pathname + again.destination.search);
          await page.waitForURL(url => url.pathname === '/system-setting/integration-management', { timeout: 60000 });
          await expect(page.getByRole('heading', { name: '外部连接', exact: true })).toBeVisible({ timeout: 30000 });
        } finally { await secondContext.close(); }
      });
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
