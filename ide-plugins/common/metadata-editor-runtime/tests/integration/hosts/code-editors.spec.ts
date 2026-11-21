import { expect } from '@playwright/test'
import { test, corpus } from './fixture'

for (const hostKind of ['vscode', 'intellij'] as const) {
  test.describe(hostKind, () => {
    test.use({hostKind})

    for (const online of [true, false]) test(`IDE-SCRIPT-014 ${online ? 'online' : 'offline'} business CRUD scripts remain code editors and save independently`, async ({editor, page}) => {
      test.setTimeout(90_000)
      editor.onlineService = online
      await page.addInitScript(() => {
        const browser = window as any
        browser.scriptEditors = []
        browser.appliedIntelligence = []
        browser.__OUROBOROS_SCRIPT_EDITOR_DEBUG__ = {
          onMounted: (value: any) => browser.scriptEditors.push(value),
          onIntelligenceApplied: (value: any) => browser.appliedIntelligence.push(value),
        }
      })
      await editor.open('business')
      await page.getByText('接口配置', {exact: true}).first().click()
      const apis = [
        ['search', '搜索', '查询'], ['getOne', '获取单条', '查询'],
        ['add', '新建', '插入'], ['update', '更新', '更新'], ['delete', '删除', '删除'],
      ]
      for (const [api, tab, title] of apis) {
        if (api !== 'search') await page.getByText(tab, {exact: true}).first().click()
        for (const [phase, label, language] of [['before', '前', 'groovy'], ['after', '后', 'javascript']]) {
          const name = `apis.${api}.${phase}Script`
          await page.getByText(api === 'getOne' ? `${title}${label}执行脚本` : `${title}执行${label}脚本`, {exact: true}).click()
          await expect.poll(() => page.evaluate(name => (window as any).scriptEditors.some((value: any) => value.props.name === name), name)).toBe(true)
          const snapshot = await page.evaluate(name => {
            const {editor} = (window as any).scriptEditors.findLast((value: any) => value.props.name === name)
            return {uri: editor.getModel().uri.toString(), language: editor.getModel().getLanguageId(), text: editor.getValue()}
          }, name)
          expect(snapshot.language).toBe(language)
          expect(snapshot.text).toBe(phase === 'before' ? 'return input' : 'return result')
          const control = page.locator(`.monaco-editor[data-uri="${snapshot.uri}"]`)
          await expect(control).toBeVisible()
          await control.locator('textarea.inputarea').focus()
          if (online && api === 'search' && phase === 'before') {
            await expect.poll(() => page.evaluate(() => (window as any).appliedIntelligence.length)).toBeGreaterThan(0)
            await page.evaluate(name => {
              const {editor} = (window as any).scriptEditors.findLast((value: any) => value.props.name === name)
              editor.trigger('fixture', 'editor.action.triggerSuggest', {})
            }, name)
            await expect(control.locator('.suggest-widget.visible')).toContainText('fixtureResult')
            await page.keyboard.press('Escape')
          }
          await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
          const code = `return "${api}-${phase}"`
          await page.keyboard.insertText(code)
          await expect.poll(async () => (await editor.value()).apis[api][`${phase}Script`]).toBe(code)
        }
      }
      await editor.flush()
      const saved = await editor.value()
      expect(saved.custom).toEqual(corpus.business.value.custom)
      for (const [api] of apis) {
        expect(saved.apis[api].beforeScript).toBe(`return "${api}-before"`)
        expect(saved.apis[api].afterScript).toBe(`return "${api}-after"`)
      }
      if (online) {
        const contexts = () => editor.backend.requests.map(request => request.body as any)
          .filter(Boolean).map(body => body.context || body)
          .filter(context => context.codeSite === 'business-model.api-script')
        await expect.poll(() => apis.every(([api]) => contexts().some(context =>
          context.resource.type === 'business-model' && context.location.pointer === `/apis/${api}/beforeScript` &&
          context.draft.apis[api].beforeScript === `return "${api}-before"` && context.draft.custom.keep,
        ))).toBe(true)
      }
      await test.info().attach('business-scripts', {body: await page.screenshot(), contentType: 'image/png'})
      await editor.reopen()
      await page.getByText('接口配置', {exact: true}).first().click()
      await page.getByText('查询执行前脚本', {exact: true}).click()
      await expect.poll(() => page.evaluate(() => (window as any).scriptEditors.findLast((value: any) => value.props.name === 'apis.search.beforeScript')?.editor.getValue())).toBe('return "search-before"')
    })
  })
}
