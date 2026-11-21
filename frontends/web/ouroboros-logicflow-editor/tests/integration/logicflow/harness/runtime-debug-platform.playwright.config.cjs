const path = require('node:path')
const { defineConfig } = require('@playwright/test')

for (const name of ['LOGICFLOW_PLATFORM_URL', 'LOGICFLOW_PLATFORM_AUTH', 'LOGICFLOW_RUNTIME_CONTAINER']) {
  if (!process.env[name]) throw new Error(`正式平台验收需要 ${name}，见场景 harness.playbook.md`)
}
const outputDir = process.env.LOGICFLOW_DEBUG_EVIDENCE_DIR || path.resolve(__dirname, '../../../../../../../tmp/testing-evidence/logicflow-online-debugging/platform-browser')

module.exports = defineConfig({
  testDir: '..', testMatch: 'runtime-debug-platform.browser.spec.cjs', workers: 1, timeout: 240000,
  outputDir,
  reporter: [['list'], ['json', { outputFile: path.join(outputDir, 'playwright-report.json') }]],
  use: {
    actionTimeout: 30000, navigationTimeout: 60000,
    baseURL: process.env.LOGICFLOW_PLATFORM_URL,
    storageState: process.env.LOGICFLOW_PLATFORM_AUTH,
    viewport: { width: 1600, height: 1000 },
    video: { mode: 'on', size: { width: 1600, height: 1000 } },
    screenshot: 'only-on-failure', trace: 'off',
  },
})
