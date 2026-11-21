const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const { createCipheriv, createDecipheriv, createHash, randomBytes } = require('node:crypto');

function fixture(root) {
  root = path.resolve(root);
  const runtime = JSON.parse(fs.readFileSync(path.join(root, 'secrets/runtime.json')));
  const acceptance = JSON.parse(fs.readFileSync(path.join(root, 'secrets/acceptance.json')));
  const simulation = JSON.parse(fs.readFileSync(path.join(root, 'secrets/providers.json')));
  fs.mkdirSync(acceptance.evidence, { recursive: true });
  const output = path.join(acceptance.evidence, 'internal-mechanisms.jsonl');
  const record = value => fs.appendFileSync(output, JSON.stringify({ time: new Date().toISOString(), ...value }) + '\n');
  async function response(method, route, token, body, node = 'node-a') {
    const base = 'http://127.0.0.1:' + runtime.ports[node];
    const headers = { 'content-type': 'application/json' };
    if (token) headers.authorization = 'Bearer ' + token;
    const res = await fetch(base + route, { method, headers, redirect: 'manual', signal: AbortSignal.timeout(30000),
      body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    let data;
    try { data = JSON.parse(text); } catch { data = text; }
    record({ node, method, path: route.split('?')[0], status: res.status, applicationCode: data?.code });
    return { status: res.status, data, location: res.headers.get('location') };
  }
  function ok(result) {
    assert(result.status >= 200 && result.status < 300, 'HTTP operation failed: ' + result.status);
    assert.equal(result.data.code, 0, result.data.msg || 'Platform operation failed');
    return result.data.data;
  }
  const request = async (...args) => ok(await response(...args));
  async function control(method, route, body) {
    const res = await fetch('http://127.0.0.1:' + runtime.ports.control + route, {
      method, headers: { authorization: 'Bearer ' + simulation.controlKey, 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000),
    });
    assert.equal(res.status, 200); return res.json();
  }
  function external(authorizeUrl) {
    const url = new URL(authorizeUrl);
    return new Promise((resolve, reject) => {
      const req = https.get({ hostname: '127.0.0.1', port: runtime.ports.tls, servername: url.hostname,
        path: url.pathname + url.search, headers: { host: url.hostname }, ca: fs.readFileSync(simulation.cert) }, res => {
        res.resume(); res.on('end', () => resolve({ status: res.statusCode, location: res.headers.location }));
      });
      req.setTimeout(10000, () => req.destroy(new Error('Simulated authorization timed out'))); req.on('error', reject);
    });
  }
  async function authorization(provider, node = 'node-a', returnUrl = '/system-setting/integration-management') {
    const start = await response('GET', '/api/auth/oauth2/' + provider + '-sim/authorize?returnUrl=' + encodeURIComponent(returnUrl), undefined, undefined, node);
    assert.equal(start.status, 302);
    assert(start.location && !new URL(start.location).hostname.match(/^(127|localhost)/), 'Expected official provider authorization URL');
    const consent = await external(start.location);
    assert.equal(consent.status, 302);
    const callback = new URL(consent.location);
    const completed = await response('GET', callback.pathname + callback.search, undefined, undefined, node);
    assert.equal(completed.status, 302);
    return { start, callback, destination: new URL(completed.location, acceptance.base), node };
  }
  const login = async (username = 'admin', node = 'node-a') => (await request('POST', '/api/auth/login', undefined,
    { principalType: 'username', principal: username, credentials: acceptance.password }, node)).accessToken.token;
  async function scenario(id, provider, action) {
    await action(); record({ scenario: id, provider, result: 'pass' }); console.log(id + (provider ? ' ' + provider : '') + ': passed');
  }
  return { root, runtime, acceptance, simulation, output, record, response, request, ok, control, external, authorization, login, scenario };
}

function encryptFramed(app, message, owner) {
  const key = Buffer.from(app.aesKey, 'hex');
  const bytes = Buffer.from(message);
  const length = Buffer.alloc(4); length.writeUInt32BE(bytes.length);
  const frame = Buffer.concat([randomBytes(16), length, bytes, Buffer.from(owner)]);
  const padding = 32 - frame.length % 32;
  const cipher = createCipheriv('aes-256-cbc', key, key.subarray(0, 16)); cipher.setAutoPadding(false);
  return Buffer.concat([cipher.update(Buffer.concat([frame, Buffer.alloc(padding, padding)])), cipher.final()]).toString('base64');
}
function signature(...values) { return createHash('sha1').update(values.sort().join('')).digest('hex'); }
function callbackRequest(provider, app, payload, options = {}) {
  const time = String(options.timestamp || Math.floor(Date.now() / 1000));
  const nonce = randomBytes(12).toString('hex');
  if (provider === 'feishu') {
    const iv = randomBytes(16); const key = createHash('sha256').update(app.encryptKey).digest();
    const cipher = createCipheriv('aes-256-cbc', key, iv);
    const encrypted = Buffer.concat([iv, cipher.update(JSON.stringify(payload)), cipher.final()]).toString('base64');
    const body = JSON.stringify({ encrypt: encrypted });
    return { method: 'POST', query: '', body, headers: { 'content-type': 'application/json',
      'X-Lark-Request-Timestamp': time, 'X-Lark-Request-Nonce': nonce,
      'X-Lark-Signature': options.badSignature ? 'wrong' : createHash('sha256').update(time + nonce + app.encryptKey + body).digest('hex') } };
  }
  const owner = options.owner || (provider === 'dingtalk' ? app.appId : app.corpId);
  const encrypted = encryptFramed(app, typeof payload === 'string' ? payload : JSON.stringify(payload), owner);
  const query = new URLSearchParams({ timestamp: time, nonce, msg_signature: options.badSignature ? 'wrong' : signature(app.callbackToken, time, nonce, encrypted) });
  if (options.challenge) query.set('echostr', encrypted);
  return { method: options.challenge ? 'GET' : 'POST', query: '?' + query,
    body: options.challenge ? undefined : provider === 'dingtalk' ? JSON.stringify({ encrypt: encrypted }) : '<xml><Encrypt><![CDATA[' + encrypted + ']]></Encrypt></xml>',
    headers: { 'content-type': provider === 'wecom' ? 'application/xml' : 'application/json' } };
}
function verifyAck(provider, app, answer) {
  assert.equal(answer.status, 200);
  if (provider === 'wecom') { assert.equal(answer.text, 'success'); return; }
  const reply = JSON.parse(answer.text);
  if (provider === 'feishu') { assert.equal(reply.success, true); return; }
  assert.equal(reply.msg_signature, signature(app.callbackToken, String(reply.timeStamp), reply.nonce, reply.encrypt));
  const key = Buffer.from(app.aesKey, 'hex');
  const decipher = createDecipheriv('aes-256-cbc', key, key.subarray(0, 16)); decipher.setAutoPadding(false);
  const padded = Buffer.concat([decipher.update(Buffer.from(reply.encrypt, 'base64')), decipher.final()]);
  const padding = padded.at(-1); assert(padding >= 1 && padding <= 32);
  assert(padded.subarray(-padding).every(value => value === padding));
  const length = padded.readUInt32BE(16);
  assert.equal(padded.subarray(20, 20 + length).toString(), 'success');
  assert.equal(padded.subarray(20 + length, -padding).toString(), app.appId);
}
async function deliverCallback(f, provider, payload, options = {}, node = 'node-a') {
  const input = callbackRequest(provider, f.simulation.providers[provider], payload, options);
  const route = '/api/enterprise-callbacks/' + provider + '-sim/callback';
  const response = await fetch('http://127.0.0.1:' + f.runtime.ports[node] + route + input.query,
    { method: input.method, headers: input.headers, body: input.body, signal: AbortSignal.timeout(options.timeoutMs || 10000) });
  const text = await response.text();
  f.record({ node, method: input.method, path: route, status: response.status, protocol: provider });
  return { status: response.status, text };
}
module.exports = { fixture, callbackRequest, deliverCallback, verifyAck };
