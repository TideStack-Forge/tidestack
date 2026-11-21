const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const { randomBytes, createHash, timingSafeEqual } = require('node:crypto');
const assert = require('node:assert/strict');

// Only the external side is simulated. No Ouroboros implementation is imported here.
const settings = JSON.parse(fs.readFileSync(process.env.EIS_CONFIG, 'utf8'));
const hosts = {
  'api.dingtalk.com': 'dingtalk', 'oapi.dingtalk.com': 'dingtalk', 'login.dingtalk.com': 'dingtalk',
  'qyapi.weixin.qq.com': 'wecom', 'open.weixin.qq.com': 'wecom',
  'open.feishu.cn': 'feishu', 'accounts.feishu.cn': 'feishu',
};
const state = { requests: [], messages: [], faults: [], codes: new Map(), tokens: new Map(), generations: {}, directories: {} };
for (const [provider, app] of Object.entries(settings.providers)) {
  state.generations[provider] = 1;
  const root = provider === 'feishu' ? '0' : '1';
  state.directories[provider] = {
    departments: [{ id: root, parent: null, name: provider + ' Corporation' }, { id: '10', parent: root, name: provider + ' Engineering' }],
    members: [{ id: app.memberId, openId: app.openId, name: 'Simulation administrator', departments: ['10'], primary: '10' },
      { id: provider + '-guest', openId: 'ou_' + provider + '_guest', name: 'Employee without account', departments: ['10'], primary: '10' }],
  };
}

function send(res, status, payload, headers = {}) {
  res.writeHead(status, { 'content-type': 'application/json', ...headers });
  res.end(JSON.stringify(payload));
}
async function readBody(req) {
  const chunks = []; let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    assert(size <= 2 * 1024 * 1024, 'Request exceeds simulator limit');
    chunks.push(chunk);
  }
  const text = Buffer.concat(chunks).toString('utf8');
  if (!text) return {};
  return req.headers['content-type']?.includes('application/x-www-form-urlencoded')
    ? Object.fromEntries(new URLSearchParams(text)) : JSON.parse(text);
}
function issueToken(provider, kind, extra = {}) {
  const token = randomBytes(28).toString('base64url');
  state.tokens.set(token, { provider, kind, generation: state.generations[provider], ...extra });
  return token;
}
function checkToken(req, url, provider, kind = 'application') {
  const value = provider === 'feishu' ? req.headers.authorization?.replace(/^Bearer /, '')
    : url.hostname === 'api.dingtalk.com' ? req.headers['x-acs-dingtalk-access-token'] : url.searchParams.get('access_token');
  const entry = state.tokens.get(value);
  assert(entry && entry.provider === provider && entry.kind === kind && entry.generation === state.generations[provider], 'Token rejected');
  return entry;
}
function consumeCode(code, provider, verifier) {
  const grant = state.codes.get(code);
  state.codes.delete(code);
  assert(grant && grant.provider === provider && grant.expires > Date.now(), 'Authorization code rejected');
  if (grant.challenge) assert.equal(createHash('sha256').update(verifier || '').digest('base64url'), grant.challenge, 'PKCE rejected');
  return grant;
}
function authorize(url, provider, res) {
  const app = settings.providers[provider];
  const application = url.searchParams.get(provider === 'wecom' ? 'appid' : 'client_id');
  assert.equal(application, provider === 'wecom' ? app.corpId : app.appId);
  assert.equal(url.searchParams.get('response_type'), 'code');
  const callback = new URL(url.searchParams.get('redirect_uri'));
  assert(['127.0.0.1', 'localhost'].includes(callback.hostname), 'Only local callback origins allowed');
  assert.equal(callback.pathname, '/api/auth/oauth2/' + provider + '-sim/callback');
  const code = randomBytes(24).toString('base64url');
  state.codes.set(code, { provider, challenge: url.searchParams.get('code_challenge'), expires: Date.now() + 120000 });
  callback.searchParams.set('state', url.searchParams.get('state'));
  callback.searchParams.set(provider === 'dingtalk' ? 'authCode' : 'code', code);
  res.writeHead(302, { location: callback.toString() }); res.end();
}
function directoryPage(members, cursor) {
  const index = cursor ? Number(cursor) : 0;
  assert(Number.isInteger(index) && index >= 0 && index <= members.length, 'Invalid page cursor');
  return { items: members.slice(index, index + 1), more: index + 1 < members.length, next: String(index + 1) };
}
function dingUser(member) {
  return { userid: member.id, name: member.name, dept_id_list: member.departments.map(Number), mobile: '13000000001', email: member.id + '@simulation.invalid' };
}
function wecomUser(member) {
  return { userid: member.id, name: member.name, department: member.departments.map(Number), main_department: Number(member.primary), status: 1 };
}
function feishuUser(member) {
  return { user_id: member.id, open_id: member.openId, name: member.name, department_ids: member.departments, status: { is_resigned: false } };
}
function appCredentials(provider, body, url) {
  const app = settings.providers[provider];
  if (provider === 'dingtalk') {
    assert.equal(body.appKey, app.appId); assert.equal(body.appSecret, app.secret);
  } else if (provider === 'feishu') {
    assert.equal(body.app_id, app.appId); assert.equal(body.app_secret, app.secret);
  } else {
    assert.equal(url.searchParams.get('corpid'), app.corpId); assert.equal(url.searchParams.get('corpsecret'), app.secret);
  }
}
function messageReceipt(provider, body, req, res, fault) {
  const app = settings.providers[provider];
  let recipient, content;
  if (provider === 'dingtalk') {
    assert.equal(body.agent_id, Number(app.agentId)); recipient = body.userid_list; content = body.msg.text?.content;
  } else if (provider === 'wecom') {
    assert.equal(body.agentid, Number(app.agentId)); assert.equal(body.msgtype, 'text'); recipient = body.touser; content = body.text.content;
  } else {
    assert.equal(body.msg_type, 'text'); recipient = body.receive_id; content = JSON.parse(body.content).text;
  }
  assert(state.directories[provider].members.some(member => member.id === recipient), 'Recipient outside configured enterprise');
  assert.equal(typeof content, 'string');
  const receipt = { provider, recipient, content, generation: state.generations[provider], id: state.messages.length + 1 };
  state.messages.push(receipt);
  if (fault?.mode === 'accepted-disconnect') { req.socket.destroy(); return; }
  if (provider === 'dingtalk') send(res, 200, { errcode: 0, task_id: receipt.id });
  else if (provider === 'wecom') send(res, 200, { errcode: 0, msgid: 'sim-' + receipt.id, invaliduser: '' });
  else send(res, 200, { code: 0, data: { message_id: 'sim-' + receipt.id } });
}

async function handleProvider(req, res) {
  const url = new URL(req.url, 'https://' + req.headers.host);
  const provider = hosts[url.hostname];
  assert(provider, 'Host is outside simulator contract');
  const body = await readBody(req);
  const app = settings.providers[provider];
  const record = { provider, method: req.method, path: url.pathname, generation: state.generations[provider], result: 'received' };
  state.requests.push(record);
  const fault = state.faults.find(item => item.provider === provider && item.path === url.pathname && item.remaining > 0);
  if (fault) {
    fault.remaining--; record.fault = fault.mode;
    if (fault.mode === 'http-error') { send(res, fault.status || 503, { error: 'simulated-upstream-error' }); return; }
    if (fault.mode === 'rate-limit') { send(res, 429, { error: 'simulated-rate-limit' }, { 'retry-after': '1' }); return; }
    if (fault.mode === 'disconnect') { req.socket.destroy(); return; }
    if (fault.mode === 'delay') await new Promise(resolve => setTimeout(resolve, fault.milliseconds || 1000));
  }
  if (['/oauth2/auth', '/connect/oauth2/authorize', '/open-apis/authen/v1/authorize'].includes(url.pathname)) {
    authorize(url, provider, res); return;
  }
  const tokenPath = { dingtalk: '/v1.0/oauth2/accessToken', wecom: '/cgi-bin/gettoken', feishu: '/open-apis/auth/v3/tenant_access_token/internal' }[provider];
  if (url.pathname === tokenPath) {
    appCredentials(provider, body, url);
    const token = issueToken(provider, 'application'); record.result = 'token-issued';
    if (provider === 'dingtalk') send(res, 200, { accessToken: token, expireIn: 7200 });
    else if (provider === 'wecom') send(res, 200, { errcode: 0, access_token: token, expires_in: 7200 });
    else send(res, 200, { code: 0, tenant_access_token: token, expire: 7200 });
    return;
  }
  if (url.pathname === '/v1.0/oauth2/userAccessToken' || url.pathname === '/oauth/v3/token') {
    const basic = req.headers.authorization?.startsWith('Basic ')
      ? Buffer.from(req.headers.authorization.slice(6), 'base64').toString('utf8').split(':') : [];
    assert.equal(body.clientId || body.client_id || basic[0], app.appId);
    assert.equal(body.clientSecret || body.client_secret || basic[1], app.secret);
    consumeCode(body.code, provider, body.code_verifier);
    const token = issueToken(provider, 'user');
    if (provider === 'dingtalk') send(res, 200, { accessToken: token, corpId: app.corpId });
    else send(res, 200, { access_token: token, token_type: 'Bearer', expires_in: 7200 });
    return;
  }
  if (url.pathname === '/v1.0/contact/users/me' || url.pathname === '/open-apis/authen/v1/user_info') {
    checkToken(req, url, provider, 'user');
    if (provider === 'dingtalk') send(res, 200, { unionId: app.openId, openId: app.openId, nick: 'Simulation administrator' });
    else send(res, 200, { code: 0, data: { open_id: app.openId, user_id: app.memberId, name: 'Simulation administrator' } });
    return;
  }
  checkToken(req, url, provider);
  if (url.pathname === '/cgi-bin/auth/getuserinfo') {
    consumeCode(url.searchParams.get('code'), provider);
    send(res, 200, { errcode: 0, userid: app.memberId }); return;
  }
  if (url.pathname === '/topapi/user/getbyunionid') {
    assert.equal(body.unionid, app.openId); send(res, 200, { errcode: 0, result: { userid: app.memberId } }); return;
  }
  if (url.pathname.includes('message') && (url.pathname.endsWith('/send') || url.pathname.endsWith('/asyncsend_v2') || url.pathname.endsWith('/messages'))) {
    messageReceipt(provider, body, req, res, fault); return;
  }
  const directory = state.directories[provider];
  if (provider === 'dingtalk') {
    const id = String(body.dept_id);
    if (url.pathname === '/topapi/v2/department/get') {
      const department = directory.departments.find(item => item.id === id); assert(department, 'Department unavailable');
      send(res, 200, { errcode: 0, result: { dept_id: Number(id), name: department.name, parent_id: Number(department.parent || 0) } }); return;
    }
    if (url.pathname === '/topapi/v2/department/listsub') {
      send(res, 200, { errcode: 0, result: directory.departments.filter(item => item.parent === id).map(item => ({ dept_id: Number(item.id), name: item.name })) }); return;
    }
    if (url.pathname === '/topapi/v2/user/list') {
      const page = directoryPage(directory.members.filter(item => item.departments.includes(id)), body.cursor);
      send(res, 200, { errcode: 0, result: { list: page.items.map(dingUser), has_more: page.more, next_cursor: Number(page.next) } }); return;
    }
    if (url.pathname === '/topapi/v2/user/get') { send(res, 200, { errcode: 0, result: dingUser(directory.members.find(item => item.id === body.userid)) }); return; }
  }
  if (provider === 'wecom') {
    if (url.pathname === '/cgi-bin/department/list') {
      send(res, 200, { errcode: 0, department: directory.departments.map(item => ({ id: Number(item.id), parentid: Number(item.parent || 0), name: item.name })) }); return;
    }
    if (url.pathname === '/cgi-bin/user/list') {
      send(res, 200, { errcode: 0, userlist: directory.members.filter(item => item.departments.includes(url.searchParams.get('department_id'))).map(wecomUser) }); return;
    }
    if (url.pathname === '/cgi-bin/user/get') { send(res, 200, { errcode: 0, ...wecomUser(directory.members.find(item => item.id === url.searchParams.get('userid'))) }); return; }
  }
  if (provider === 'feishu') {
    assert.equal(url.searchParams.get('department_id_type'), 'department_id');
    if (url.pathname === '/open-apis/contact/v3/users/find_by_department') {
      assert.equal(url.searchParams.get('user_id_type'), 'user_id');
      const page = directoryPage(directory.members.filter(item => item.departments.includes(url.searchParams.get('department_id'))), url.searchParams.get('page_token'));
      send(res, 200, { code: 0, data: { items: page.items.map(feishuUser), has_more: page.more, page_token: page.next } }); return;
    }
    const department = /^\/open-apis\/contact\/v3\/departments\/([^/]+)(\/children)?$/.exec(url.pathname);
    if (department) {
      if (department[2]) {
        const page = directoryPage(directory.departments.filter(item => item.parent === department[1]), url.searchParams.get('page_token'));
        send(res, 200, { code: 0, data: { items: page.items.map(item => ({ department_id: item.id, parent_department_id: item.parent, name: item.name })), has_more: page.more, page_token: page.next } });
      } else {
        const item = directory.departments.find(item => item.id === department[1]); assert(item, 'Department unavailable');
        send(res, 200, { code: 0, data: { department: { department_id: item.id, name: item.name } } });
      }
      return;
    }
    const user = /^\/open-apis\/contact\/v3\/users\/([^/]+)$/.exec(url.pathname);
    if (user) {
      const kind = url.searchParams.get('user_id_type');
      const member = directory.members.find(item => (kind === 'open_id' ? item.openId : item.id) === user[1]);
      assert(member, 'Member unavailable'); send(res, 200, { code: 0, data: { user: feishuUser(member) } }); return;
    }
  }
  record.result = 'unsupported'; send(res, 404, { error: 'simulator-route-not-defined' });
}

const service = https.createServer({ key: fs.readFileSync(settings.key), cert: fs.readFileSync(settings.cert) }, (req, res) => {
  handleProvider(req, res).catch(error => {
    state.requests.push({ method: req.method, path: req.url.split('?')[0], result: 'rejected', reason: error.message.split('\n')[0] });
    if (!res.headersSent) send(res, 400, { error: 'simulator-contract-rejected' });
    else res.destroy();
  });
});
const control = http.createServer(async (req, res) => {
  const supplied = Buffer.from(req.headers.authorization || '');
  const expected = Buffer.from('Bearer ' + settings.controlKey);
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) { send(res, 403, { error: 'forbidden' }); return; }
  try {
    if (req.method === 'GET' && req.url === '/state') {
      send(res, 200, { requests: state.requests, messages: state.messages, generations: state.generations, directories: state.directories }); return;
    }
    const body = await readBody(req);
    assert(settings.providers[body.provider], 'Unknown provider');
    if (req.method === 'POST' && req.url === '/faults') {
      assert(['http-error', 'rate-limit', 'disconnect', 'accepted-disconnect', 'delay'].includes(body.mode));
      state.faults.push({ ...body, remaining: body.times || 1 }); send(res, 200, { ok: true }); return;
    }
    if (req.method === 'PUT' && req.url === '/directory') {
      assert(Array.isArray(body.directory.departments) && Array.isArray(body.directory.members));
      state.directories[body.provider] = body.directory; send(res, 200, { ok: true }); return;
    }
    if (req.method === 'PUT' && req.url === '/credential') {
      assert.equal(typeof body.secret, 'string'); assert(body.secret.length >= 16);
      settings.providers[body.provider].secret = body.secret; state.generations[body.provider]++;
      send(res, 200, { generation: state.generations[body.provider] }); return;
    }
    send(res, 404, { error: 'control-route-not-found' });
  } catch (error) { send(res, 400, { error: error.message.split('\n')[0] }); }
});
service.listen(443, '0.0.0.0');
control.listen(8080, '0.0.0.0');
function close() { service.closeAllConnections(); control.closeAllConnections(); service.close(); control.close(); }
process.on('SIGTERM', close); process.on('SIGINT', close);
console.log('Enterprise provider simulation: TLS 443, authenticated control 8080');
