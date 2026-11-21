import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from '@playwright/test'

test('standalone IDE bundle edits schema, applies host state and disposes without Web host assets', async ({ page }) => {
  const errors: string[] = []
  const requestedAssets: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const bundle = resolve('dist/amis-editor')
  await page.route('http://ide.local/**', async route => {
    const pathname = new URL(route.request().url()).pathname
    requestedAssets.push(pathname)
    if (pathname === '/editor.js' || pathname === '/editor.css') {
      const script = pathname.endsWith('.js')
      await route.fulfill({
        contentType: script ? 'text/javascript; charset=utf-8' : 'text/css; charset=utf-8',
        body: readFileSync(resolve(bundle, script ? 'metadata-editor-amis-editor.js' : 'ouroboros-metadata-editor.css')),
      })
      return
    }
    if (pathname !== '/') {
      await route.fulfill({ status: 404, body: 'Unexpected external IDE asset' })
      return
    }
    await route.fulfill({
      contentType: 'text/html; charset=utf-8',
      body: '<html><head><link rel="stylesheet" href="/editor.css"></head><body><div id="editor" style="height:900px"></div><script src="/editor.js"></script></body></html>',
    })
  })
  await page.goto('http://ide.local/')
  await page.evaluate(() => {
    const browser = window as any
    browser.changes = []
    browser.app = browser.OuroborosAmisEditorRuntime.mount({
      root: document.getElementById('editor'),
      value: { type: 'page', title: 'Before', body: [{ type: 'tpl', tpl: 'initial body' }] },
      theme: 'light', locale: 'zh-CN',
      onChange: (value: unknown) => browser.changes.push(value),
      onError: (error: unknown) => { throw error },
    })
  })
  await page.getByText('Before', { exact: true }).first().click()
  await page.locator('input[value="Before"]').fill('Edited')
  await page.keyboard.press('Tab')
  await expect.poll(() => page.evaluate(() => (window as any).changes.some((schema: any) => schema.title === 'Edited'))).toBe(true)
  const edited = await page.evaluate(() => (window as any).changes.findLast((schema: any) => schema.title === 'Edited'))
  expect(edited.body).toEqual([{ type: 'tpl', tpl: 'initial body' }])
  await page.evaluate(() => (window as any).app.applyState({
    value: { type: 'page', title: 'After', body: [{ type: 'tpl', tpl: 'updated body' }] },
    theme: 'dark', locale: 'zh-CN',
  }))
  await expect(page.getByText('After', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('updated body', { exact: true }).first()).toBeVisible()
  await page.evaluate(() => (window as any).app.dispose())
  await expect(page.locator('#editor')).toBeEmpty()
  expect(errors).toEqual([])
  expect(requestedAssets.sort()).toEqual(['/', '/editor.css', '/editor.js'])
})
