import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

const bundle = readFileSync(resolve(__dirname, '../../dist/browser/metadata-editor-host.js'), 'utf8')
const cases = JSON.parse(readFileSync(resolve(__dirname, '../../../metadata-editor-schema/tests/fixtures/metadata-schema-conformance.json'), 'utf8')) as { id: string; type: string; path?: string; valid: boolean; document: unknown }[]
const manifest = JSON.parse(readFileSync(resolve(__dirname, '../../src/schema-resources/metadataTypeContributions.json'), 'utf8'))

test('bundled host validates public contracts offline without unsafe-eval', async ({ page }) => {
  const browserErrors: string[] = []
  const requests: string[] = []
  page.on('pageerror', (error) => browserErrors.push(error.message))
  await page.route('**/*', async (route) => {
    requests.push(route.request().url())
    await route.fulfill({ contentType: 'text/html', body: `<!doctype html>
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-schema-test'; connect-src 'none'">
      <script nonce="schema-test">${bundle.replace(/<\/script/gi, '<\\/script')}</script>` })
  })
  await page.goto('http://schema-conformance.test/')
  const examples = cases.filter((example) => example.type !== 'ui-schema-content').map((example) => {
    const contribution = manifest.types.find((item: { type: string }) => item.type === example.type)
    return { ...example, uri: example.path ?? `/repo/src/main/metadata/${contribution.fixedFileName ?? `probe${contribution.fileSuffix}`}` }
  })
  const results = await page.evaluate(async (examples) => {
    const core = (window as unknown as { OuroborosMetadataEditorHostCore: {
      createBrowserMetadataEditorHost(options: unknown): { validate(text: string): Promise<{ severity: string }[]> }
    } }).OuroborosMetadataEditorHostCore
    return Promise.all(examples.map(async (example) => {
      const text = JSON.stringify(example.document)
      const host = core.createBrowserMetadataEditorHost({
        uri: example.uri, metadataType: example.type, initialDocument: { text, revision: '1' },
        saveText: async () => { throw new Error('Validation must not save the document') },
      })
      const diagnostics = await host.validate(text)
      return { id: example.id, valid: !diagnostics.some((diagnostic) => diagnostic.severity === 'error') }
    }))
  }, examples)
  expect(results).toEqual(examples.map(({ id, valid }) => ({ id, valid })))
  expect(browserErrors).toEqual([])
  expect(requests).toEqual(['http://schema-conformance.test/'])
})
