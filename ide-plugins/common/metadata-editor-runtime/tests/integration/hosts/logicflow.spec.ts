import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { expect } from '@playwright/test'
import { test, corpus, repo } from './fixture'
import { iconPixels } from './iconPixels'

const builtInNodes = JSON.parse(readFileSync(join(repo, 'ide-plugins/vscode-plugin/dist/media/editor-integrations/logicflow-node-defines.json'), 'utf8')).items as Array<{type: string; label: string; icon: string; scaffold: Record<string, unknown>}>

function expectIconColor(rendered: Awaited<ReturnType<typeof iconPixels>>, expected: number[]) {
  for (const icon of rendered) {
    expect.soft(icon.pixels, icon.href).toBeGreaterThan(0)
    // Overlapping antialiased paths can round a color channel by one unit.
    const wrongColors = icon.colors.filter(color => color.split(',').some((channel, index) => Math.abs(Number(channel) - expected[index]) > 2))
    expect.soft(wrongColors, icon.href).toEqual([])
  }
}

for (const hostKind of ['vscode', 'intellij'] as const) {
  test.describe(hostKind, () => {
    test.use({ hostKind })

    test('IDE-FLOW-013 all node icons inherit the caller color', async ({editor, page}) => {
      editor.backend.nodeDefines = builtInNodes
      await editor.open('logicflow')
      const icons = page.getByTestId('logicflow-panel-left').locator('.svg-icon').filter({has: page.locator('use:not([*|href="#icon-"])')})
      const expectColor = async (color: number[]) => {
        const rendered = await icons.evaluateAll(iconPixels)
        expect(rendered).toHaveLength(builtInNodes.filter(node => node.icon).length)
        expectIconColor(rendered, color)
      }
      await expectColor([255,255,255])
      const approval = page.getByTestId('logicflow-panel-left').locator('.node-group').filter({has: page.getByRole('heading', {name: '审批流', exact: true})})
      await approval.scrollIntoViewIfNeeded()
      await test.info().attach('default-color', {body: await page.screenshot(), contentType: 'image/png'})
      await icons.evaluateAll(elements => elements.forEach(element => (element as SVGElement).style.color = 'rgb(209, 43, 104)'))
      await expectColor([209,43,104])
      await test.info().attach('caller-color', {body: await page.screenshot(), contentType: 'image/png'})
      const types = ['const', 'startProcess', 'submitTask', 'getTaskInfo', 'attachProcessInfo', 'processStatusRouter', 'getConfig', 'callRPC', 'authDisallowedFields']
      const nodes = builtInNodes.filter(node => types.includes(node.type))
      expect(nodes).toHaveLength(types.length)
      editor.backend.nodeDefines = builtInNodes.map(node => ({...node, iconColor: '#d12b68'}))
      await editor.reopen()
      await editor.external({...corpus.logicflow.value,
        entryNode: 'const', connections: [],
        nodes: nodes.map(node => ({...node.scaffold, name: node.type, isEnabled: true})),
        nodePositions: Object.fromEntries(nodes.map((node, index) => [node.type, {x: 100 + (index % 3) * 280, y: 100 + Math.floor(index / 3) * 160}])),
      })
      const canvas = page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first()
      await expect(canvas.locator('.x6-node use')).toHaveCount(nodes.length)
      const rendered = await canvas.locator('.x6-node use').evaluateAll(iconPixels)
      expectIconColor(rendered, [209,43,104])
      await test.info().attach('canvas-iconColor', {body: await page.screenshot(), contentType: 'image/png'})
    })

    for (const online of [true, false]) test(`IDE-FLOW-011 ${online ? 'online' : 'offline'} production node catalog loads with usable palette and icons`, async ({ editor, page }) => {
      editor.onlineService = online
      editor.backend.nodeDefines = builtInNodes
      await editor.open('logicflow')
      const before = await editor.text()
      const palette = page.getByTestId('logicflow-panel-left')
      await expect(palette.locator('.node-list-item')).toHaveCount(builtInNodes.length)
      for (const node of builtInNodes) {
        await expect(palette.locator('.node-name').getByText(node.label, { exact: true })).toHaveCount(1)
      }
      const missingIcons = await palette.locator('use').evaluateAll(uses => uses.map(use => use.getAttribute('href') || use.getAttribute('xlink:href')).filter(href => href !== '#icon-' && (!href || !document.getElementById(href.slice(1)))))
      expect(missingIcons).toEqual([])
      expect(editor.backend.requests.some(request => request.path.includes('/logicflow/node-define'))).toBe(online)
      const canvas = page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first()
      await expect(canvas.locator('.logicflow-node-body')).toHaveCount(3)
      await expect(canvas.locator('use[*|href="#icon-unknown"]')).toHaveCount(0)
      await editor.flush()
      expect(await editor.text()).toBe(before)
      expect(editor.saveCount).toBe(0)

      const approval = palette.locator('.node-group').filter({has: page.getByRole('heading', {name: '审批流', exact: true})})
      await approval.scrollIntoViewIfNeeded()
      await expect(approval.locator('.node-list-item')).toHaveCount(5)
      await expect(approval).toContainText('发起审批流程')
      await page.getByTestId('logicflow-node-search').fill('switch')
      const item = palette.locator('.node-list-item')
      await expect(item).toHaveCount(1)
      await expect(item).toHaveText('条件分支')
      await item.hover()
      await page.mouse.down()
      await page.mouse.move(850, 250, {steps: 20})
      await page.mouse.up()
      await expect(canvas.locator('.logicflow-node-body')).toHaveCount(4)
      await canvas.getByText('条件分支', {exact: true}).click()
      const description = page.locator('textarea[name="description"]')
      await description.fill('Created from the production node catalog')
      await description.blur()
      await expect.poll(async () => (await editor.value()).nodes.find((node: any) => node.type === 'switch')?.description).toBe('Created from the production node catalog')
      await editor.flush()
      expect((await editor.value()).connections).toEqual(corpus.logicflow.value.connections)
      await editor.reopen()
      await expect(page.getByTestId('logicflow-panel-left').locator('.node-list-item')).toHaveCount(builtInNodes.length)
      await page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first().getByText('条件分支', {exact: true}).click()
      await expect(page.locator('textarea[name="description"]')).toHaveValue('Created from the production node catalog')
      expect((await editor.value()).custom).toEqual(corpus.logicflow.value.custom)
    })

    for (const status of [200, 403]) test(`IDE-FLOW-012 online ${status === 200 ? 'empty catalog' : 'authorization failure'} preserves the document without substituting offline nodes`, async ({ editor, page }) => {
      editor.backend.nodeDefines = []
      editor.backend.status = status
      await editor.open('logicflow')
      await expect.poll(() => editor.backend.requests.some(request => request.path.includes('/logicflow/node-define'))).toBe(true)
      const palette = page.getByTestId('logicflow-panel-left')
      await expect(palette.locator('.node-list-item')).toHaveCount(0)
      await expect(palette).toContainText('无匹配节点')
      if (status === 403) await expect(page.getByText('Editor service HTTP 403', {exact: false}).first()).toBeVisible()
      await editor.flush()
      expect(await editor.text()).toBe(JSON.stringify(corpus.logicflow.value))
      expect(editor.saveCount).toBe(0)
    })

    test('IDE-FLOW-009 backend nodes render and node properties survive save and reopen', async ({ editor, page }) => {
      editor.backend.nodeDefines = builtInNodes
      await editor.open('logicflow')
      const before = await editor.text()
      const canvas = page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first()
      await expect(canvas.locator('.logicflow-node-body')).toHaveCount(3)
      // The design graph adds one virtual entry connector; the minimap is a separate SVG.
      await expect(canvas.locator('.x6-edge')).toHaveCount(3)
      expect(editor.backend.requests.some(request => request.path.includes('/logicflow/node-define'))).toBe(true)
      await editor.flush()
      expect(await editor.text()).toBe(before)
      expect(editor.saveCount).toBe(0)
      await canvas.getByText('脚本节点', { exact: true }).click()
      const description = page.locator('textarea[name="description"]')
      await expect(description).toBeVisible()
      await description.fill('Edited in the IDE fixture')
      await description.blur()
      await expect.poll(async () => (await editor.value()).nodes[1].description).toBe('Edited in the IDE fixture')
      await editor.flush()
      expect((await editor.value()).connections).toEqual(corpus.logicflow.value.connections)
      expect((await editor.value()).custom).toEqual(corpus.logicflow.value.custom)
      await editor.reopen()
      await page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first().getByText('脚本节点', { exact: true }).click()
      await expect(page.locator('textarea[name="description"]')).toHaveValue('Edited in the IDE fixture')
    })

    test('IDE-SCRIPT-010 real ScriptEditor uses backend completion, diagnostics, draft and session cleanup', async ({ editor, page }) => {
      test.setTimeout(60_000)
      editor.backend.nodeDefines = builtInNodes
      await page.addInitScript(() => {
        const browser = window as any
        browser.scriptEditors = []
        browser.appliedIntelligence = []
        browser.__OUROBOROS_SCRIPT_EDITOR_DEBUG__ = {
          onMounted: (value: any) => {
            if (value.props.codeSite === 'logicflow.script') browser.scriptEditors.push(value)
          },
          onIntelligenceApplied: (value: any) => {
            if (value.language === 'groovy') browser.appliedIntelligence.push(value)
          },
        }
      })
      await editor.open('logicflow')
      await page.getByLabel('逻辑流设计画布').locator('svg.x6-graph-svg').first().getByText('脚本节点', { exact: true }).click()
      await expect.poll(() => page.evaluate(() => (window as any).scriptEditors.length)).toBeGreaterThan(0)
      const uri = await page.evaluate(() => (window as any).scriptEditors.at(-1).editor.getModel().uri.toString())
      const scriptEditor = page.locator(`.monaco-editor[data-uri="${uri}"]`)
      await expect(scriptEditor).toBeVisible()
      await expect.poll(() => page.evaluate(() => (window as any).appliedIntelligence.length)).toBeGreaterThan(0)
      const input = scriptEditor.locator('textarea.inputarea')
      const replaceCode = async (text: string) => {
        await input.focus()
        await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
        await page.keyboard.insertText(text)
      }
      await replaceCode('return ')
      await page.evaluate(() => (window as any).scriptEditors.at(-1).editor.trigger('fixture', 'editor.action.triggerSuggest', {}))
      const suggestions = scriptEditor.locator('.suggest-widget.visible')
      await expect(suggestions).toContainText('fixtureResult')
      await suggestions.getByText('fixtureResult', { exact: true }).click()
      await page.keyboard.press('Enter')
      await expect.poll(async () => (await editor.value()).nodes[1].script).toContain('fixtureResult')
      await page.keyboard.press('Escape')
      await replaceCode('BROKEN')
      const markers = () => page.evaluate(() => {
        const { editor, monaco } = (window as any).scriptEditors.at(-1)
        return monaco.editor.getModelMarkers({ resource: editor.getModel().uri, owner: 'editor-lsp' }).map((marker: any) => marker.message)
      })
      await expect.poll(markers).toEqual(['Unknown symbol BROKEN'])
      await expect(scriptEditor.locator('.squiggly-error').first()).toBeVisible()
      await replaceCode('return fixtureResult')
      await expect.poll(markers).toEqual([])
      await expect.poll(async () => (await editor.value()).nodes[1].script).toBe('return fixtureResult')
      await editor.flush()
      const contexts = () => editor.backend.requests.map(request => request.body as any).filter(Boolean)
        .map(body => body.context || body).filter(body => body.codeSite === 'logicflow.script')
      await expect.poll(() => contexts().some(context => context.resource.type === 'logicflow' && context.draft.nodes[1].script === 'return fixtureResult')).toBe(true)
      expect(contexts().every(context => context.draft.connections.length === 2)).toBe(true)
      await editor.reopen()
      expect((await editor.value()).nodes[1].script).toBe('return fixtureResult')
      await editor.closeService()
      const opened = editor.backend.requests.filter(request => request.path.endsWith('/sessions') && (request.body as any)?.context).length
      expect(opened).toBeGreaterThan(0)
      expect(editor.backend.requests.filter(request => /\/sessions\/lang-\d+$/.test(request.path))).toHaveLength(opened)
      expect(editor.backend.requests.every(request => request.key === 'fixture-key')).toBe(true)
    })
  })
}
