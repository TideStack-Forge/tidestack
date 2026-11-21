import { expect, type Page } from '@playwright/test'
import { harness, test } from './harness'

async function reopen(page: Page, schema: unknown) {
  await page.waitForFunction(() => typeof (window as any).OuroborosAmisEditorRuntime?.mount === 'function')
  await page.evaluate(value => {
    const w = window as any
    w.editor?.dispose()
    w.openEditor(value)
  }, schema)
  await expect(page.locator('input[name="title"]')).toHaveValue('Popup regression')
  // AMIS auto-focuses the root after mounting and debounces its property panel.
  await page.waitForTimeout(600)
}

for (const shared of [false, true]) {
  test(`repeated popup editing preserves ${shared ? 'shared references' : 'inline content'} without extra definitions`, async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    page.on('requestfailed', request => errors.push(`${request.url()}: ${request.failure()?.errorText}`))
    let schema: any = {
      type: 'page', title: 'Popup regression',
      body: [{ type: 'button', label: 'Open popup', actionType: 'dialog',
        dialog: shared ? { $ref: 'shared' } : { title: 'Inline popup', body: [{ type: 'tpl', tpl: 'Body' }] } },
      { type: 'button', label: 'Other popup', actionType: 'dialog',
        dialog: shared ? { $ref: 'shared' } : { title: 'Other popup', body: [{ type: 'tpl', tpl: 'Other body' }] } }],
      ...(shared ? { definitions: {
        shared: { type: 'dialog', title: 'Shared popup', body: [{ type: 'tpl', tpl: 'Body' }] },
        authored: { type: 'tpl', tpl: 'Keep this unused definition' },
      } } : {}),
    }
    await page.goto(harness)
    for (let round = 0; round < 3; round++) {
      await reopen(page, schema)
      await page.getByRole('button', { name: 'Open popup', exact: true }).click()
      await page.locator('[data-tooltip="配置弹框内容"]').click()
      const title = page.locator('input[name="title"]')
      await expect(title).toHaveValue(round ? `Edited popup ${round - 1}` : shared ? 'Shared popup' : 'Inline popup')
      await page.waitForTimeout(600)
      await title.fill(`Edited popup ${round}`)
      await title.blur()
      await expect(page.getByText(`Edited popup ${round}`, { exact: true })).toBeVisible()
      await page.getByRole('button', { name: '保存', exact: true }).click()
      await expect.poll(() => page.evaluate(shared => {
        const saved = (window as any).changes.at(-1)
        return shared ? saved?.definitions?.shared?.title : saved?.body?.[0]?.dialog?.title
      }, shared)).toBe(`Edited popup ${round}`)
      schema = await page.evaluate(() => (window as any).changes.at(-1))
      expect(Object.keys(schema.definitions || {}).sort()).toEqual(shared ? ['authored', 'shared'] : [])
      if (shared) {
        expect(schema.body.map((button: any) => button.dialog.$ref)).toEqual(['shared', 'shared'])
        expect(schema.definitions.authored.tpl).toBe('Keep this unused definition')
      } else {
        expect(schema.body[1].dialog.body[0].tpl).toBe('Other body')
      }
    }
    expect(errors).toEqual([])
  })
}
