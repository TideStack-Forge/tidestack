const path = require('node:path')
const { defineConfig } = require('@playwright/test')
const recording = process.env.LOGICFLOW_DEBUG_RECORD === '1'
const evidenceDirectory = process.env.LOGICFLOW_DEBUG_EVIDENCE_DIR || path.resolve(__dirname, '../../../../../../../tmp/testing-evidence/logicflow-online-debugging/browser-results')
module.exports = defineConfig({
  testDir: '..', testMatch: 'runtime-debug.browser.spec.cjs', timeout: recording ? 120000 : 60000, workers: 1,
  outputDir: evidenceDirectory,
  reporter: [['list'], ['json', { outputFile: path.join(evidenceDirectory, 'playwright-report.json') }]],
  use: {
    baseURL: 'http://127.0.0.1:18879', viewport: { width: 1440, height: 960 },
    trace: recording ? 'on' : 'retain-on-failure', screenshot: 'only-on-failure',
    video: { mode: recording ? 'on' : 'off', size: { width: 1440, height: 960 } },
  },
  webServer: {
    cwd: path.resolve(__dirname, '../../../..'),
    command: 'webpack serve --config tests/integration/logicflow/harness/runtime-debug-browser.webpack.cjs',
    url: 'http://127.0.0.1:18879', reuseExistingServer: true, timeout: 60000,
  },
})
