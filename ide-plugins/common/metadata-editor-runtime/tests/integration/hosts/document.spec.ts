import { expect } from '@playwright/test'
import { test, corpus } from './fixture'

for (const hostKind of ['vscode', 'intellij'] as const) {
  test.describe(hostKind, () => {
    test.use({hostKind})
    for (const name of ['page', 'form']) {
      test(`IDE-OPEN-001 ${name}: opening property panels preserves exact file bytes`, async ({editor, page}) => {
        await editor.open(name)
        const before = await editor.text()
        await expect(page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)')).toHaveValue(name === 'page' ? 'Page fixture' : 'Form fixture')
        // Observe beyond upstream's delayed property-panel normalization (250ms).
        await page.waitForTimeout(800)
        await editor.flush()
        expect(await editor.text()).toBe(before)
        expect(editor.saveCount).toBe(0)
      })
    }

    test('IDE-SAVE-002 flush persists the pending edit and reopen retains unknown fields', async ({editor, page}) => {
      await editor.open('page')
      const title = page.locator('.metadata-ui-schema-wrapper-input[name="title"]')
      await title.fill('Saved page')
      await title.blur()
      await expect(title).toHaveValue('Saved page')
      await editor.flush()
      expect(await editor.value()).toEqual({...corpus.page.value, title: 'Saved page'})
      await editor.reopen()
      await expect(page.locator('.metadata-ui-schema-wrapper-input[name="title"]')).toHaveValue('Saved page')
    })

    test('IDE-CONFLICT-003 stale saves merge local edits with unbroadcast external changes', async ({editor, page}) => {
      await editor.open('page')
      await editor.external({...corpus.page.value, externalOnly: 'retained'}, false)
      await page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)').fill('Local change')
      await page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)').blur()
      await expect.poll(async () => (await editor.value()).schema.title).toBe('Local change')
      await editor.flush()
      expect((await editor.value()).externalOnly).toBe('retained')
      expect((await editor.value()).custom).toEqual({keep: [1, 2]})
    })

    test('IDE-EXTERNAL-004 external state updates the visual editor without a write-back', async ({editor, page}) => {
      await editor.open('page')
      await editor.external({...corpus.page.value, schema: {...corpus.page.value.schema as object, title: 'External title'}})
      await expect(page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)')).toHaveValue('External title')
      await page.waitForTimeout(800)
      await editor.flush()
      expect(editor.saveCount).toBe(0)
    })

    for (const name of ['inlinePopup', 'sharedPopup']) {
      test(`IDE-POPUP-005 ${name}: three save/reopen rounds preserve definitions`, async ({editor, page}) => {
        // Three full production-page loads exceed the single-page test budget.
        test.setTimeout(60_000)
        await editor.open(name)
        for (let round = 0; round < 3; round++) {
          if (round) await editor.reopen()
          await expect(page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)')).toHaveValue('Popup fixture')
          await page.waitForTimeout(600)
          await page.getByRole('button', {name: 'Open popup', exact: true}).click()
          await page.locator('[data-tooltip="配置弹框内容"]').click()
          const title = page.locator('input[name="title"]:not(.metadata-ui-schema-wrapper-input)')
          await expect(title).toHaveValue(round ? `Edited ${round - 1}` : name === 'sharedPopup' ? 'Shared popup' : 'Inline popup')
          await page.waitForTimeout(600)
          await title.fill(`Edited ${round}`)
          await title.blur()
          await expect(page.getByText(`Edited ${round}`, {exact: true})).toBeVisible()
          await page.getByRole('button', {name: '保存', exact: true}).click()
          await expect.poll(async () => {
            const value = await editor.value()
            return name === 'sharedPopup' ? value.definitions.shared.title : value.body[0].dialog.title
          }).toBe(`Edited ${round}`)
          await editor.flush()
          const value = await editor.value()
          expect(Object.keys(value.definitions || {}).sort()).toEqual(name === 'sharedPopup' ? ['authored', 'shared'] : [])
          if (name === 'sharedPopup') {
            expect(value.body.map((button: {dialog: {$ref: string}}) => button.dialog.$ref)).toEqual(['shared', 'shared'])
            expect(value.definitions.authored.tpl).toBe('Keep this unused definition')
          } else expect(value.body[1].dialog.body[0].tpl).toBe('Other body')
        }
      })
    }

    test('IDE-SERVICE-006 formal host bridge authenticates, refreshes catalogs and closes parallel sessions', async ({editor, page}) => {
      await editor.open('page')
      const service = (request: {url: string; method?: string; data?: unknown}) => page.evaluate(request => window.OuroborosMetadataEditorHost.requestService(request), request)
      const route = '/api/dev/fixture/'
      const sessions = await Promise.all([1, 2].map(index => service({url: route + 'code-intelligence/sessions', method: 'POST', data: {documentId: `document-${index}`}})))
      expect(sessions).toEqual([{status: 0, data: {sessionId: 'lang-1'}}, {status: 0, data: {sessionId: 'lang-2'}}])
      expect(await service({url: route + 'logicflow/node-define', data: {search: 'custom', names: ['one', 'two']}})).toEqual({status: 0, data: [{name: 'custom-1', title: 'Custom node'}]})
      const catalogRequest = editor.backend.requests.find(request => request.path.includes('node-define'))!
      const query = new URL(catalogRequest.path, 'http://fixture.invalid').searchParams
      expect(query.getAll('names[]')).toEqual(['one', 'two'])
      expect(query.get('search')).toBe('custom')
      editor.backend.status = 401
      await expect(service({url: route + 'code-intelligence/environment'})).rejects.toThrow('401')
      editor.backend.status = 200
      editor.backend.catalogVersion = 2
      expect(await service({url: route + 'logicflow/node-define'})).toEqual({status: 0, data: [{name: 'custom-2', title: 'Custom node'}]})
      await editor.closeService()
      expect(editor.backend.requests.filter(request => /\/sessions\/lang-/.test(request.path))).toHaveLength(2)
      expect(editor.backend.requests.every(request => request.key === 'fixture-key')).toBe(true)
      expect(editor.backend.requests.slice(1).every(request => request.cookie === 'JSESSIONID=fixture-owner')).toBe(true)
      expect(await page.evaluate(() => document.body.textContent?.includes('fixture-key'))).toBe(false)
    })
  })
}
