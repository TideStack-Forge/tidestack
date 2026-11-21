import { test as base } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createMetadataEditorWebviewHtml } from '../../../dist'

export const harness = '/ide-plugins/common/metadata-editor-runtime/tests/integration/amis-editor/harness.html'
export const test = base.extend({
  page: async ({ page }, use) => {
    const sdk = '/frontends/web/node_modules/amis/sdk'
    const runtime = '/ide-plugins/common/metadata-editor-runtime/runtime'
    const html = createMetadataEditorWebviewHtml({
      template: readFileSync(join(__dirname, 'harness.html'), 'utf8'),
      fileName: 'test.ui-schema.json', nonce: 'browser-test', cspSource: "'self'",
      amisSdkScriptUri: `${sdk}/sdk.js`, amisSdkCssUri: `${sdk}/sdk.css`,
      amisThemeCssUri: `${sdk}/cxd.css`, amisDarkThemeCssUri: `${sdk}/dark.css`,
      amisEditorRuntimeScriptUri: `${runtime}/metadata-editor-amis-editor.js`,
      amisEditorCssUri: `${runtime}/metadata-editor-amis-editor.css`,
      runtimeScriptUri: `${runtime}/metadata-editor-runtime.js`,
      hostScript: 'window.testHarness = true;',
    })
    await page.route('**/harness.html', route => route.fulfill({ contentType: 'text/html', body: html }))
    await use(page)
  },
})
