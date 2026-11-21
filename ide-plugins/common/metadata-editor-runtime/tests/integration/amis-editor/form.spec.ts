import { expect } from '@playwright/test'
import { harness, test } from './harness'

const form = {
  type: 'form', title: 'Regression form', actions: [],
  // Existing AMIS panel defaults isolate form-purpose inference in this case.
  dsType: 'api', labelAlign: 'left',
  body: [{ type: 'input-text', name: 'name', label: 'Name' }],
  customProperty: { preserved: true },
}

test('opens a form without adding its inferred purpose and persists a real edit', async ({ page }) => {
  const errors: string[] = []
  const workers: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('worker', worker => workers.push(worker.url()))
  await page.goto(harness)
  await page.evaluate(value => (window as any).openEditor(value), form)
  await expect(page.getByText('Regression form', { exact: true }).first()).toBeVisible()
  await expect(page.locator('input[name="title"]')).toHaveValue('Regression form')
  await page.getByText('Name', { exact: true }).last().click()
  const label = page.locator('input[name="label"]')
  await expect(label).toHaveValue('Name')
  for (const value of await page.evaluate(() => (window as any).changes)) {
    expect(value).toEqual(form)
  }
  await label.fill('Customer name')
  await label.blur()
  await expect.poll(() => page.evaluate(() => (window as any).changes.at(-1)?.body[0].label))
    .toBe('Customer name')
  const saved = await page.evaluate(() => (window as any).changes.at(-1))
  expect(saved).not.toHaveProperty('feat')
  expect(saved.customProperty).toEqual(form.customProperty)
  await expect.poll(() => workers.length).toBeGreaterThan(0)
  expect(workers.every(url => url.startsWith('blob:'))).toBe(true)
  expect(errors).toEqual([])
})

test('keeps an explicit purpose even when the form API suggests another', async ({ page }) => {
  await page.goto(harness)
  await page.evaluate(value => (window as any).openEditor(value), {
    ...form, feat: 'View', api: '/submit', initApi: '/load',
  })
  await expect(page.getByText('Regression form', { exact: true }).first()).toBeVisible()
  await expect.poll(() => page.evaluate(() => (window as any).changes.length)).toBeGreaterThan(0)
  for (const value of await page.evaluate(() => (window as any).changes)) {
    expect(value.feat).toBe('View')
  }
})

test('does not add purpose to nested forms and keeps upstream submit-button editing', async ({ page }) => {
  await page.goto(harness)
  await page.evaluate(value => (window as any).openEditor(value), {
    type: 'page', asideResizor: false, pullRefresh: { disabled: true },
    body: [{ ...form, actions: [{type: 'submit', label: 'Send', primary: true, disabledOnAction: false}] }],
  })
  await expect(page.getByText('Regression form', { exact: true }).first()).toBeVisible()
  await page.waitForTimeout(600)
  await page.getByRole('button', {name: 'Send', exact: true}).click()
  const label = page.locator('input[name="label"]')
  await expect(label).toHaveValue('Send')
  await label.fill('Send edited')
  await label.blur()
  await expect.poll(() => page.evaluate(() => (window as any).changes.at(-1)?.body[0].actions?.[0]?.label)).toBe('Send edited')
  const saved = await page.evaluate(() => (window as any).changes.at(-1).body[0])
  expect(saved).not.toHaveProperty('feat')
  expect(saved.actions[0]).toEqual({type: 'submit', label: 'Send edited', primary: true, disabledOnAction: false})
})

for (const authored of [
  { title: 'Page without defaults' },
  { title: 'Page with explicit defaults', asideResizor: false, pullRefresh: { disabled: true } },
  { title: 'Page with enabled features', asideResizor: true, pullRefresh: { disabled: false, pullingText: 'Refresh' } },
]) {
  test(`opens and edits ${authored.title} without inserting panel defaults`, async ({ page }) => {
    const initial = { type: 'page', ...authored, customProperty: { preserved: true },
      body: [{ type: 'tpl', tpl: 'Hello' }] }
    await page.goto(harness)
    await page.evaluate(value => (window as any).openEditor(value), initial)
    const title = page.locator('input[name="title"]')
    await expect(title).toHaveValue(authored.title)
    // AMIS asynchronously initializes its property forms and debounces changes by 250ms.
    await page.waitForTimeout(800)
    for (const value of await page.evaluate(() => (window as any).changes)) {
      expect(value).toEqual(initial)
    }
    await title.fill('Updated page')
    await title.blur()
    await expect.poll(() => page.evaluate(() => (window as any).changes.at(-1)?.title)).toBe('Updated page')
    expect(await page.evaluate(() => (window as any).changes.at(-1))).toEqual({ ...initial, title: 'Updated page' })
  })
}

test('persists explicit changes to Page controls whose defaults are display-only', async ({ page }) => {
  await page.goto(harness)
  await page.evaluate(() => (window as any).openEditor({ type: 'page', title: 'Page controls', body: [] }))
  await expect(page.locator('input[name="title"]')).toHaveValue('Page controls')
  await page.getByText('边栏宽度可调节', { exact: true })
    .locator('xpath=ancestor::div[.//input[@type="checkbox"]][1]').getByRole('checkbox').check({ force: true })
  await expect.poll(() => page.evaluate(() => (window as any).changes.at(-1)?.asideResizor)).toBe(true)
  await page.getByText('下拉刷新', { exact: true })
    .locator('xpath=ancestor::div[.//input[@type="checkbox"]][1]').getByRole('checkbox').check({ force: true })
  await expect.poll(() => page.evaluate(() => (window as any).changes.at(-1)?.pullRefresh?.disabled)).toBe(false)
})

test('uses the platform ScriptEditor and sends the complete unsaved draft through the IDE host', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.stack || error.message))
  await page.goto(harness)
  await page.addScriptTag({ url: '/ide-plugins/common/metadata-editor-runtime/runtime/metadata-editor-runtime.js' })
  await page.evaluate(async () => {
    const w = window as any
    w.serviceRequests = []
    w.appliedSnapshots = []
    w.__OUROBOROS_SCRIPT_EDITOR_DEBUG__ = { onIntelligenceApplied: (value: unknown) => w.appliedSnapshots.push(value) }
    w.OuroborosMetadataEditorHost = {
      service: { appName: 'demo' }, locale: 'zh-CN',
      requestService: async (request: any) => {
        w.serviceRequests.push(request)
        if (request.url.endsWith('/environment')) return { status: 0, data: { identity: 'app-instance' } }
        return { status: 0, data: { ...request.data, contextVersion: 'context-v1', runtimeStatus: 'CURRENT', snapshot: {
          source: 'dev-helper-runtime', versionToken: 'v1', runtimeProfile: 'spel', language: 'spel',
          globals: [{ name: 'draftOnly', kind: 'variable', typeDisplayName: 'string', shape: { type: 'string' } }],
          javaImports: [], javaTypes: [], diagnostics: [], languageProjectionHints: { embeddedExpressionMode: 'template' },
        } } }
      },
    }
    const raw = w.OuroborosMetadataEditorRuntime.__test__.createEditorIntegrationFetcher()
    await w.OuroborosAmisEditorRuntime.renderSchema(document.getElementById('root'), {
      type: 'form', actions: [], body: [{ type: 'script-editor', name: 'expression', language: 'spel',
        codeSite: 'test.expression', codeResourceType: 'field-type', codePointer: '/expression',
        intelligenceApi: '/api/dev/demo/code-intelligence/context' }],
    }, { data: { expression: '${draftOnly}', __CODE_RESOURCE_DRAFT: {
      name: 'unsaved-model', expression: '${old}', sibling: { changedWithoutSaving: true },
    } } }, { fetcher: (api: unknown, data: unknown) => raw(api, data).then((payload: unknown) => ({ data: payload })) })
  })
  await expect.poll(() => page.evaluate(() => (window as any).serviceRequests.filter((request: any) => request.url.endsWith('/context')).length))
    .toBeGreaterThan(0)
  const request = await page.evaluate(() => (window as any).serviceRequests.find((request: any) => request.url.endsWith('/context')))
  expect(request.data.draft).toEqual({ name: 'unsaved-model', expression: '${draftOnly}', sibling: { changedWithoutSaving: true } })
  expect(request.data.codeSite).toBe('test.expression')
  expect(request.data.documentId).toBeTruthy()
  await expect.poll(() => page.evaluate(() => (window as any).appliedSnapshots.length)).toBeGreaterThan(0)
  expect(errors).toEqual([])
})
