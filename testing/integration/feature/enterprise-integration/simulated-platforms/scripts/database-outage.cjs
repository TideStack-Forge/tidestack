const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { fixture, deliverCallback, verifyAck } = require('./platform-http.cjs');
const f = fixture(process.argv[2]);
const state = JSON.parse(fs.readFileSync(path.join(f.root, 'secrets/scenario-state.json')));
(async () => {
  if (process.argv[3] === 'unavailable') {
    await f.scenario('EIS-OUTAGE-001', undefined, async () => {
      const outcomes = await Promise.all(['dingtalk', 'wecom', 'feishu'].map(async provider => {
        try {
          const result = await deliverCallback(f, provider, state.events[provider], { timeoutMs: 1500 });
          assert(result.status >= 400, 'Unavailable storage must not acknowledge reliable acceptance');
          return result.status;
        } catch (error) {
          if (error.name !== 'TimeoutError') throw error;
          return 'no-response-before-deadline';
        }
      }));
      f.record({ dependency: 'mysql-paused', successfulAcknowledgements: 0, outcomes });
    });
  } else {
    for (const provider of ['dingtalk', 'wecom', 'feishu']) {
      verifyAck(provider, f.simulation.providers[provider], await deliverCallback(f, provider, state.events[provider]));
    }
    f.record({ dependency: 'mysql-resumed', callbackReplay: 'pass' });
    console.log('Database recovery: callback redelivery accepted');
  }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
