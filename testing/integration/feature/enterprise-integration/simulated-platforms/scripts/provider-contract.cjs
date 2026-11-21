const assert = require('node:assert/strict');
const fs = require('node:fs');
const https = require('node:https');
const { createHash } = require('node:crypto');
const { fixture } = require('./platform-http.cjs');
const f = fixture(process.argv[2]);
function request(host, method, route, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request({ hostname: '127.0.0.1', port: f.runtime.ports.tls, servername: host,
      path: route, method, ca: fs.readFileSync(f.simulation.cert),
      headers: { host, 'content-type': 'application/json', ...headers } }, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString();
        resolve({ status: res.statusCode, data: text ? JSON.parse(text) : undefined });
      });
    });
    req.on('error', reject); req.setTimeout(10000, () => req.destroy(new Error('Provider contract timed out')));
    req.end(body === undefined ? undefined : JSON.stringify(body));
  });
}
(async () => {
  await f.scenario('EIS-SIM-001', undefined, async () => {
    const tokens = {};
    for (const provider of ['dingtalk', 'wecom', 'feishu']) {
      const app = f.simulation.providers[provider];
      let route, host, body;
      if (provider === 'dingtalk') { host = 'api.dingtalk.com'; route = '/v1.0/oauth2/accessToken'; body = { appKey: app.appId, appSecret: app.secret }; }
      else if (provider === 'feishu') { host = 'open.feishu.cn'; route = '/open-apis/auth/v3/tenant_access_token/internal'; body = { app_id: app.appId, app_secret: app.secret }; }
      else { host = 'qyapi.weixin.qq.com'; route = '/cgi-bin/gettoken?corpid=' + app.corpId + '&corpsecret=' + app.secret; }
      const invalidBody = body && Object.fromEntries(Object.entries(body).map(([key, value]) => [key, /[Ss]ecret/.test(key) ? 'wrong-secret' : value]));
      const invalid = await request(host, body ? 'POST' : 'GET', body ? route : route.replace(app.secret, 'wrong-secret'), invalidBody);
      assert.equal(invalid.status, 400, 'Simulator must reject incorrect credentials');
      const valid = await request(host, body ? 'POST' : 'GET', route, body);
      assert.equal(valid.status, 200);
      tokens[provider] = valid.data.accessToken || valid.data.access_token || valid.data.tenant_access_token;
      assert(tokens[provider]);
    }
    assert.equal((await request('qyapi.weixin.qq.com', 'GET', '/cgi-bin/department/list?access_token=' + tokens.dingtalk)).status, 400);
    assert.equal((await request('open.feishu.cn', 'GET', '/open-apis/contact/v3/departments/0?department_id_type=department_id', undefined,
      { authorization: 'Bearer unknown-token' })).status, 400);
    const app = f.simulation.providers.feishu;
    const verifier = 'simulator-contract-verifier-with-more-than-forty-three-characters';
    const grant = async () => {
      const url = new URL('https://open.feishu.cn/open-apis/authen/v1/authorize');
      url.search = new URLSearchParams({ client_id: app.appId, response_type: 'code', state: 'sim-contract',
        redirect_uri: f.acceptance.base + '/api/auth/oauth2/feishu-sim/callback',
        code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' });
      const result = await f.external(url); assert.equal(result.status, 302);
      return new URL(result.location).searchParams.get('code');
    };
    const exchange = (code, proof) => request('open.feishu.cn', 'POST', '/oauth/v3/token',
      { client_id: app.appId, client_secret: app.secret, code, code_verifier: proof });
    assert.equal((await exchange(await grant(), 'wrong-verifier')).status, 400);
    const code = await grant(); assert.equal((await exchange(code, verifier)).status, 200);
    assert.equal((await exchange(code, verifier)).status, 400, 'Simulator must reject code replay');
    await f.control('POST', '/faults', { provider: 'wecom', path: '/cgi-bin/department/list', mode: 'http-error' });
    const route = '/cgi-bin/department/list?access_token=' + tokens.wecom;
    assert.equal((await request('qyapi.weixin.qq.com', 'GET', route)).status, 503);
    assert.equal((await request('qyapi.weixin.qq.com', 'GET', route)).status, 200, 'One-shot failure must recover');
  });
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
