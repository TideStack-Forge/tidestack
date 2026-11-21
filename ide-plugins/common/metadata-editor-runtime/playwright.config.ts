import { defineConfig } from '@playwright/test'

const port = Number(process.env.OB_IDE_TEST_PORT || 43792)
const evidence = process.env.OB_IDE_EVIDENCE_DIR
const recordVideo = process.env.OB_IDE_RECORD_VIDEO === '1'

export default defineConfig({
  ...(evidence ? {outputDir: `${evidence}/browser-artifacts`, reporter: [['list'], ['json', {outputFile: `${evidence}/browser.json`}]]} : {}),
  workers: 1,
  testDir: './tests/integration',
  timeout: 30_000,
  use: {
    browserName: 'chromium',
    viewport: { width: 1440, height: 1000 },
    baseURL: `http://127.0.0.1:${port}`, headless: true, trace: 'retain-on-failure', screenshot: 'only-on-failure',
    video: recordVideo ? {mode: 'on', size: {width: 1280, height: 720}, show: {actions: {}, test: {level: 'title'}}} : 'off',
  },
  webServer: {
    command: `python3 -m http.server ${port} --bind 127.0.0.1 --directory ../../..`,
    url: `http://127.0.0.1:${port}/ide-plugins/common/metadata-editor-runtime/tests/integration/amis-editor/harness.html`,
  },
})
