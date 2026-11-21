const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const { fixture, deliverCallback, verifyAck } = require('./platform-http.cjs');
const f = fixture(process.argv[2]);
const phase = process.argv[3] || 'all';
const providers = ['dingtalk', 'wecom', 'feishu'];
const connections = '/api/sys/enterprise-connections';
const directory = '/api/sys/enterprise-directory';
const stateFile = path.join(f.root, 'secrets/scenario-state.json');
const savedState = fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile)) : { companies: {}, guests: {} };
const persist = () => fs.writeFileSync(stateFile, JSON.stringify(savedState), { mode: 0o600 });
const current = (provider, token, node = 'node-a') => f.request('GET', connections + '/providers/' + provider + '/configuration', token, undefined, node);
const rpc = (provider, token, node = 'node-a') => f.request('POST', '/api/sys/rpc-runtime/' + provider + '.contact.user.get/actions/test-invoke', token,
  { parameters: provider === 'feishu' ? { userId: f.simulation.providers[provider].memberId, user_id_type: 'user_id', department_id_type: 'department_id' }
    : { userid: f.simulation.providers[provider].memberId } }, node);
function credential(provider) {
  const app = f.simulation.providers[provider];
  const encodingAesKey = Buffer.from(app.aesKey, 'hex').toString('base64').replace(/=+$/, '');
  if (provider === 'dingtalk') return { appKey: app.appId, appSecret: app.secret, callbackToken: app.callbackToken, encodingAesKey };
  if (provider === 'wecom') return { agentId: app.agentId, secret: app.secret, callbackToken: app.callbackToken, encodingAesKey };
  return { appId: app.appId, appSecret: app.secret, verificationToken: app.callbackToken, encryptKey: app.encryptKey };
}
function rejected(result) {
  assert(result.status >= 400 || (typeof result.data === 'object' && result.data.code !== 0), 'Operation unexpectedly succeeded');
}
async function operator(admin, label, authorities) {
  const username = 'sim-' + label + '-' + randomBytes(3).toString('hex');
  const role = await f.request('POST', '/api/sys/role', admin, { name: username });
  await f.request('PUT', '/api/sys/role/' + role.id + '/authority', admin, authorities.map(authority => ({ authority, accessControlParams: {} })));
  await f.request('POST', '/api/sys/user', admin, { userIdentity: username, password: f.acceptance.password, roles: [role.id] });
  return f.login(username);
}
async function clearBindings(admin, provider) {
  const bindings = await f.request('GET', '/api/admin/users/admin/bindings', admin);
  for (const binding of bindings.filter(item => item.providerKey === provider + '-sim')) {
    await f.request('DELETE', '/api/admin/users/admin/bindings/' + binding.id, admin);
  }
}
async function seed(admin) {
  for (const provider of providers) await f.scenario('EIS-CONNECTION-001', provider, async () => {
    const before = await current(provider, admin);
    const app = f.simulation.providers[provider];
    const connection = { connectionId: provider + '-sim', provider, enabled: true, auth: { enabled: true } };
    if (provider !== 'feishu') connection.corpId = app.corpId;
    if (provider === 'dingtalk') { connection.agentId = app.agentId; connection.callbackMode = 'APPLICATION'; }
    await f.request('PUT', connections, admin, { connection, credential: credential(provider), expectedRevisions: before.expectedRevisions });
    const budget = await f.request('GET', connections + '/providers/' + provider + '/budget', admin);
    await f.request('PUT', connections + '/providers/' + provider + '/budget', admin, { requestsPerSecond: 10000, expectedRevision: budget.expectedRevision });
    const verified = await f.request('POST', connections + '/' + provider + '-sim/verify', admin, {});
    assert(verified, 'Diagnostic must return its saved connection scope');
    const read = await current(provider, admin);
    assert(read.credentialState.configured);
    assert(!JSON.stringify(read).includes(app.secret), 'Management projection leaked credential');
  });
  if (!savedState.adminEmployee) {
    const employees = await f.request('GET', '/api/organization/employees', admin);
    const existing = employees.items?.find(item => item.userIdentity === 'admin' || item.user === 'admin');
    savedState.adminEmployee = existing?.id || await f.request('POST', '/api/organization/employees', admin,
      { name: 'Simulation administrator', user: { mode: 'link', userId: 'admin' } });
    persist();
  }
}
async function authentication(admin) {
  for (const provider of providers) await f.scenario('EIS-AUTH-001', provider, async () => {
    await clearBindings(admin, provider);
    const entry = await f.request('GET', '/api/sys/enterprise-workbench/' + provider + '-sim/entry-url?returnUrl=' + encodeURIComponent('/system-setting/integration-management'), admin);
    assert(entry.entryUrl.includes(provider + '-sim/authorize'));
    rejected(await f.response('GET', '/api/sys/enterprise-workbench/' + provider + '-sim/entry-url?returnUrl=' + encodeURIComponent('https://example.invalid'), admin));
    const flow = await f.authorization(provider);
    assert.equal(flow.destination.pathname, '/login/bind');
    assert.equal(flow.destination.searchParams.get('returnUrl'), '/system-setting/integration-management');
    const proof = flow.destination.searchParams.get('token'); assert(proof);
    const badPassword = await f.response('POST', '/api/auth/bind', undefined, { bindingToken: proof, username: 'admin', password: 'intentionally-wrong-password' });
    rejected(badPassword);
    assert(!(await f.request('GET', '/api/admin/users/admin/bindings', admin)).some(item => item.providerKey === provider + '-sim'));
    // Use a new proof to avoid the intentional per-proof dedup window after a failed password.
    const fresh = await f.authorization(provider);
    const bound = await f.request('POST', '/api/auth/bind', undefined,
      { bindingToken: fresh.destination.searchParams.get('token'), username: 'admin', password: f.acceptance.password });
    assert(bound.accessToken?.token, 'Binding must issue a platform session');
    const again = await f.authorization(provider, 'node-b');
    assert.equal(again.destination.pathname, '/login/callback');
    const loginCode = again.destination.searchParams.get('code'); assert(loginCode);
    const exchanged = await f.request('POST', '/api/auth/login-code/exchange', undefined, { loginCode });
    assert(exchanged.accessToken?.token);
    rejected(await f.response('POST', '/api/auth/login-code/exchange', undefined, { loginCode }));
    const duplicate = await f.response('GET', again.callback.pathname + again.callback.search);
    assert.equal(new URL(duplicate.location, f.acceptance.base).pathname, '/login');
    await clearBindings(admin, provider);
    const noFallback = await f.authorization(provider, 'node-b');
    assert.equal(noFallback.destination.pathname, '/login/bind');
    savedState.proofs = { ...savedState.proofs, [provider]: noFallback.destination.searchParams.get('token') };
    persist();
    await f.request('POST', '/api/auth/bind', undefined, { bindingToken: noFallback.destination.searchParams.get('token'), username: 'admin', password: f.acceptance.password });
  });
  for (const provider of providers) await f.scenario('EIS-AUTH-002', provider, async () => {
    const start = await f.response('GET', '/api/auth/oauth2/' + provider + '-sim/authorize?returnUrl=%2F');
    assert.equal(start.status, 302);
    const authorized = await f.external(start.location);
    const callback = new URL(authorized.location);
    const validState = callback.searchParams.get('state');
    callback.searchParams.set('state', 'invalid-simulation-state');
    const before = (await f.control('GET', '/state')).requests.length;
    const refused = await f.response('GET', callback.pathname + callback.search);
    assert.equal(new URL(refused.location, f.acceptance.base).pathname, '/login');
    assert.equal((await f.control('GET', '/state')).requests.length, before, 'Invalid state must not exchange an external code');
    callback.searchParams.set('state', validState);
    const accepted = await f.response('GET', callback.pathname + callback.search);
    assert.equal(new URL(accepted.location, f.acceptance.base).pathname, '/login/callback');
    const secondStart = await f.response('GET', '/api/auth/oauth2/' + provider + '-sim/authorize?returnUrl=%2F');
    const second = new URL((await f.external(secondStart.location)).location);
    const parameter = provider === 'dingtalk' ? 'authCode' : 'code';
    second.searchParams.set(parameter, callback.searchParams.get(parameter));
    const replay = await f.response('GET', second.pathname + second.search);
    assert.equal(new URL(replay.location, f.acceptance.base).pathname, '/login');
  });
}
async function directories(admin) {
  for (const provider of providers) await f.scenario('EIS-DIRECTORY-001', provider, async () => {
    const ssoBefore = await f.request('GET', '/api/admin/users/admin/bindings', admin);
    const snapshot = await f.request('GET', directory + '/snapshot?connectionId=' + provider + '-sim', admin);
    assert.equal(snapshot.snapshot.departments.length, 2);
    assert.equal(snapshot.snapshot.members.length, 2);
    const bindingsBefore = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin)).items;
    if (!bindingsBefore.some(item => item.externalId === f.simulation.providers[provider].memberId && item.externalRouteKey)) {
      await f.request('POST', directory + '/bindings', admin, { connectionId: provider + '-sim', objectType: 'MEMBER',
        externalId: f.simulation.providers[provider].memberId, localId: savedState.adminEmployee });
    }
    if (!savedState.companies[provider]) {
      const company = await f.request('POST', '/api/organization/companies', admin, { name: provider + ' Simulation Company' });
      savedState.companies[provider] = company.id; persist();
    }
    const company = savedState.companies[provider];
    const scopes = (await f.request('GET', directory + '/scopes', admin)).items;
    const scope = scopes.find(item => item.id === company);
    await f.request('PUT', directory + '/scopes', admin, { id: company, connectionId: provider + '-sim',
      externalRootId: provider === 'feishu' ? '0' : '1', enabled: true, syncMobile: false, syncEmail: false, expectedVersion: scope?.version });
    const preview = await f.request('POST', directory + '/scopes/' + company + '/preview', admin, {});
    assert(preview.complete && preview.changes.some(item => item.objectType === 'MEMBER'));
    const choices = preview.primaryDepartmentChoices.map(item => ({ externalId: item.externalId, primaryDepartmentId: '10' }));
    await f.request('POST', directory + '/runs/' + preview.runId + '/apply', admin, { confirmCreation: true, primaryDepartmentChoices: choices });
    const stored = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin, undefined, 'node-b')).items;
    const guest = stored.find(item => item.objectType === 'MEMBER' && item.externalId === provider + '-guest' && item.externalRouteKey);
    assert(guest && guest.scopeId === company && guest.localId);
    savedState.guests[provider] = guest.localId; persist();
    const employee = await f.request('GET', '/api/organization/employees/' + guest.localId, admin);
    assert.equal(employee.name, 'Employee without account');
    assert.equal(employee.userIdentity ?? employee.user ?? null, null, 'Directory sync must not create a User');
    const repeat = await f.request('POST', directory + '/scopes/' + company + '/preview', admin, {});
    await f.request('POST', directory + '/runs/' + repeat.runId + '/apply', admin, { confirmCreation: true,
      primaryDepartmentChoices: repeat.primaryDepartmentChoices.map(item => ({ externalId: item.externalId, primaryDepartmentId: '10' })) });
    const repeated = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin)).items;
    assert.deepEqual(repeated.map(item => item.id).sort(), stored.map(item => item.id).sort(), 'Repeated synchronization duplicated directory associations');
    const existingSso = await f.request('GET', '/api/admin/users/admin/bindings', admin);
    assert.deepEqual(existingSso, ssoBefore, 'Directory synchronization must not create or change login bindings');
  });
  for (const provider of ['dingtalk', 'feishu']) await f.scenario('EIS-DIRECTORY-002', provider, async () => {
    const before = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin)).items;
    const faultPath = provider === 'dingtalk' ? '/topapi/v2/user/list' : '/open-apis/contact/v3/users/find_by_department';
    await f.control('POST', '/faults', { provider, path: faultPath, mode: 'http-error', times: 1 });
    rejected(await f.response('POST', directory + '/scopes/' + savedState.companies[provider] + '/preview', admin, {}));
    const after = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin)).items;
    assert.deepEqual(after, before, 'Partial external read must not mutate directory bindings');
    const external = (await f.control('GET', '/state')).directories[provider];
    await f.control('PUT', '/directory', { provider, directory: { ...external, members: external.members.filter(member => member.id !== provider + '-guest') } });
    const preview = await f.request('POST', directory + '/scopes/' + savedState.companies[provider] + '/preview', admin, {});
    assert(preview.missingPendingReview.includes('MEMBER:' + provider + '-guest'));
    await f.request('POST', directory + '/runs/' + preview.runId + '/apply', admin, { confirmCreation: true, primaryDepartmentChoices: [] });
    const retained = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin)).items.find(item => item.localId === savedState.guests[provider] && item.externalRouteKey);
    assert(retained && retained.active, 'Scope shrink must not mark employee departed');
    await f.control('PUT', '/directory', { provider, directory: external });
  });
}
async function notifications(admin) {
  for (const provider of providers) await f.scenario('EIS-NOTIFICATION-001', provider, async () => {
    const before = (await f.control('GET', '/state')).messages.length;
    const channel = provider === 'dingtalk' ? 'ding-talk' : provider;
    const receipt = await f.request('POST', '/api/sys/enterprise-notifications/' + channel + '/test', admin, {});
    assert.equal(receipt.outcome, 'ACCEPTED');
    const messages = (await f.control('GET', '/state')).messages.slice(before);
    assert.equal(messages.length, 1);
    assert.equal(messages[0].provider, provider);
    assert.equal(messages[0].recipient, f.simulation.providers[provider].memberId);
    assert(messages[0].content.includes('接入测试'));
  });
  await f.scenario('EIS-NOTIFICATION-003', undefined, async () => {
    const before = (await f.control('GET', '/state')).messages.length;
    const result = await f.response('POST', '/api/notification/send', admin, {
      template: 'enterprise-simulation-notice', employeeIds: [savedState.adminEmployee], payload: { marker: 'unauthorized-direct-send' },
    });
    assert.equal(result.status, 403, 'Generic HTTP sending must not acquire a new permission during integration');
    assert.equal((await f.control('GET', '/state')).messages.length, before);
  });
  await f.scenario('EIS-NOTIFICATION-002', 'feishu', async () => {
    const route = '/api/sys/enterprise-notifications/feishu/test';
    const faultPath = '/open-apis/im/v1/messages';
    const before = (await f.control('GET', '/state')).messages.length;
    await f.control('POST', '/faults', { provider: 'feishu', path: faultPath, mode: 'rate-limit' });
    const throttled = await f.request('POST', route, admin, {});
    assert.notEqual(throttled.outcome, 'ACCEPTED');
    assert.equal((await f.control('GET', '/state')).messages.length, before);
    await f.control('POST', '/faults', { provider: 'feishu', path: faultPath, mode: 'accepted-disconnect' });
    const unknown = await f.request('POST', route, admin, {});
    assert.equal(unknown.outcome, 'UNKNOWN');
    const after = (await f.control('GET', '/state')).messages;
    assert.equal(after.length, before + 1, 'Write with unknown result must not replay');
  });
}
async function budgets(admin) {
  await f.scenario('EIS-BUDGET-001', undefined, async () => {
    for (const node of ['node-a', 'node-b']) assert.equal((await rpc('dingtalk', admin, node)).success, true);
    const route = connections + '/providers/dingtalk/budget';
    const original = await f.request('GET', route, admin);
    try {
      await f.request('PUT', route, admin, { requestsPerSecond: 2, expectedRevision: original.expectedRevision });
      const before = (await f.control('GET', '/state')).requests.length;
      const results = await Promise.all(Array.from({ length: 8 }, (_, i) => rpc('dingtalk', admin, i % 2 ? 'node-b' : 'node-a')));
      const external = (await f.control('GET', '/state')).requests.slice(before);
      assert(results.some(result => result.success === false), 'Shared budget must reject excess calls');
      assert(external.length <= 2, 'Two nodes exceeded the configured deployment budget');
    } finally {
      const latest = await f.request('GET', route, admin);
      await f.request('PUT', route, admin, { requestsPerSecond: 10000, expectedRevision: latest.expectedRevision });
    }
  });
}
async function rpcPermissions(admin) {
  const onlyManage = await operator(admin, 'rpc-editor', ['systemSetting.rpcRuntimeManagement']);
  for (const provider of providers) await f.scenario('EIS-RPC-001', provider, async () => {
    const before = (await f.control('GET', '/state')).requests.length;
    const denied = await rpc(provider, onlyManage);
    assert.equal(denied.success, false, 'Model management must not confer enterprise invocation');
    assert.equal((await f.control('GET', '/state')).requests.length, before);
    const result = await rpc(provider, admin); assert.equal(result.success, true, result.errorMessage);
    const serialized = JSON.stringify(result);
    assert(!serialized.includes(f.simulation.providers[provider].secret));
  });
}
function approvalEvent(provider, app) {
  const time = String(Math.floor(Date.now() / 1000));
  if (provider === 'dingtalk') return { EventType: 'bpms_instance_change', CorpId: app.corpId, processInstanceId: 'sim-ding-instance', type: 'finish', result: 'agree', finishTime: new Date(Number(time) * 1000).toISOString() };
  if (provider === 'wecom') return '<xml><ToUserName>' + app.corpId + '</ToUserName><AgentID>' + app.agentId + '</AgentID><CreateTime>' + time + '</CreateTime><Event>sys_approval_change</Event><MsgId>sim-wecom-event</MsgId><ApprovalInfo><SpNo>sim-wecom-instance</SpNo><SpStatus>2</SpStatus><SpApplyer><UserId>' + app.memberId + '</UserId></SpApplyer></ApprovalInfo></xml>';
  return { schema: '2.0', header: { event_id: 'sim-feishu-event', event_type: 'approval.instance.status_changed_v4', token: app.callbackToken, app_id: app.appId, create_time: time },
    event: { instance_code: 'sim-feishu-instance', status: 'APPROVED', operate_time: time } };
}
async function callbacks() {
  for (const provider of providers) await f.scenario('EIS-CALLBACK-001', provider, async () => {
    const app = f.simulation.providers[provider];
    const challenge = provider === 'feishu' ? { type: 'url_verification', challenge: 'sim-challenge', token: app.callbackToken, app_id: app.appId } : 'sim-challenge';
    const verified = await deliverCallback(f, provider, challenge, { challenge: true });
    assert.equal(verified.status, 200);
    assert.equal(provider === 'feishu' ? JSON.parse(verified.text).challenge : verified.text, 'sim-challenge');
    const payload = savedState.events?.[provider] || approvalEvent(provider, app);
    savedState.events = { ...savedState.events, [provider]: payload }; persist();
    verifyAck(provider, app, await deliverCallback(f, provider, payload));
    verifyAck(provider, app, await deliverCallback(f, provider, payload, {}, 'node-b'));
  });
  for (const provider of providers) await f.scenario('EIS-CALLBACK-002', provider, async () => {
    const app = f.simulation.providers[provider];
    const payload = savedState.events[provider];
    for (const options of [{ badSignature: true }, { timestamp: 1 }]) {
      const invalid = await deliverCallback(f, provider, payload, options);
      assert(invalid.status >= 400, 'Invalid signed callback must not receive protocol success');
    }
    if (provider !== 'feishu') assert((await deliverCallback(f, provider, payload, { owner: 'another-enterprise' })).status >= 400);
    else {
      const invalid = { ...payload, header: { ...payload.header, app_id: 'another-enterprise' } };
      assert((await deliverCallback(f, provider, invalid)).status >= 400);
    }
    const oversized = await fetch('http://127.0.0.1:' + f.runtime.ports['node-a'] + '/api/enterprise-callbacks/' + provider + '-sim/callback', {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: 'x'.repeat(1048577), signal: AbortSignal.timeout(10000),
    });
    assert.equal(oversized.status, 413, 'Oversized callback must be rejected before acceptance');
    await oversized.text();
    f.record({ scenario: 'EIS-CALLBACK-002', provider, branch: 'oversized', status: oversized.status });
  });
}
async function nodeTransitions(admin) {
  for (const provider of providers) await f.scenario('EIS-NODE-001', provider, async () => {
    for (const node of ['node-a', 'node-b']) assert.equal((await rpc(provider, admin, node)).success, true);
    await clearBindings(admin, provider);
    const flow = await f.authorization(provider, 'node-b');
    assert.equal(flow.destination.pathname, '/login/bind');
    const proof = flow.destination.searchParams.get('token');
    const before = await current(provider, admin);
    const replacement = randomBytes(32).toString('base64url');
    await f.control('PUT', '/credential', { provider, secret: replacement });
    f.simulation.providers[provider].secret = replacement;
    // Retain only the private simulator fixture state for controlled restart/resume.
    fs.writeFileSync(path.join(f.root, 'secrets/providers.json'), JSON.stringify(f.simulation), { mode: 0o600 });
    const results = await Promise.all(['node-a', 'node-b'].map(node => f.response('PUT', connections, admin,
      { connection: before.connection, credential: credential(provider), expectedRevisions: before.expectedRevisions }, node)));
    assert.equal(results.filter(result => result.data.code === 0).length, 1);
    assert(results.some(result => result.status === 409));
    rejected(await f.response('POST', '/api/auth/bind', undefined, { bindingToken: proof, username: 'admin', password: f.acceptance.password }));
    for (const node of ['node-a', 'node-b']) assert.equal((await rpc(provider, admin, node)).success, true);
    const generation = (await f.control('GET', '/state')).generations[provider];
    const requests = (await f.control('GET', '/state')).requests;
    assert(requests.some(item => item.provider === provider && item.result === 'token-issued' && item.generation === generation));
    let stored = await current(provider, admin);
    await f.request('PUT', connections, admin, { connection: { ...stored.connection, enabled: false }, expectedRevisions: stored.expectedRevisions, acknowledgeDisable: true });
    const count = (await f.control('GET', '/state')).requests.length;
    for (const node of ['node-a', 'node-b']) {
      assert.equal((await rpc(provider, admin, node)).success, false);
      assert((await deliverCallback(f, provider, savedState.events[provider], {}, node)).status >= 400);
    }
    assert.equal((await f.control('GET', '/state')).requests.length, count, 'Disabled nodes must not call external services');
    stored = await current(provider, admin);
    await f.request('PUT', connections, admin, { connection: { ...stored.connection, enabled: true }, expectedRevisions: stored.expectedRevisions });
    const binding = await f.authorization(provider);
    assert.equal(binding.destination.pathname, '/login/bind');
    await f.request('POST', '/api/auth/bind', undefined, { bindingToken: binding.destination.searchParams.get('token'), username: 'admin', password: f.acceptance.password });
  });
}
async function recovery(admin) {
  await f.scenario('EIS-RECOVERY-001', undefined, async () => {
    for (const provider of providers) {
      for (const node of ['node-a', 'node-b']) {
        assert((await current(provider, admin, node)).credentialState.configured);
        const bindings = (await f.request('GET', directory + '/bindings?connectionId=' + provider + '-sim', admin, undefined, node)).items;
        assert(bindings.some(item => item.localId === savedState.adminEmployee && item.externalRouteKey));
        const flow = await f.authorization(provider, node);
        assert.equal(flow.destination.pathname, '/login/callback', 'Persisted login binding must survive node restart');
        const exchanged = await f.request('POST', '/api/auth/login-code/exchange', undefined,
          { loginCode: flow.destination.searchParams.get('code') }, node);
        assert(exchanged.accessToken?.token);
        assert.equal((await rpc(provider, admin, node)).success, true);
        verifyAck(provider, f.simulation.providers[provider], await deliverCallback(f, provider, savedState.events[provider], {}, node));
      }
    }
  });
}
async function reencrypt(admin) {
  await f.scenario('EIS-KEY-001', undefined, async () => {
    const before = await current('dingtalk', admin);
    const stale = await f.response('POST', connections + '/credentials/reencrypt', admin, {
      expectedRevisions: { ...before.expectedRevisions, 'enterprise.connections': 'stale-connection-revision' },
    });
    assert.equal(stale.status, 409);
    assert.deepEqual((await current('dingtalk', admin)).expectedRevisions, before.expectedRevisions,
      'Rejected re-encryption must preserve both configuration revisions');
    await f.request('POST', connections + '/credentials/reencrypt', admin, { expectedRevisions: before.expectedRevisions });
    for (const provider of providers) {
      for (const node of ['node-a', 'node-b']) assert.equal((await rpc(provider, admin, node)).success, true);
    }
  });
}
(async () => {
  const admin = await f.login();
  const phases = { seed, authentication, directories, notifications, rpcPermissions, callbacks, budgets, nodeTransitions, recovery };
  if (phase === 'all') {
    fs.writeFileSync(f.output, '');
    for (const action of Object.values(phases)) await action(admin);
  } else { const action = phases[phase] || (phase === 'reencrypt' ? reencrypt : undefined); assert(action, 'Unknown acceptance phase'); await action(admin); }
  f.record({ phase, result: 'pass' });
})().catch(error => {
  f.record({ phase, result: 'fail', error: error.name, message: error.message.split('\n')[0] });
  console.error(error.stack); process.exitCode = 1;
});
