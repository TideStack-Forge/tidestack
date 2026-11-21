const { test, expect } = require('@playwright/test')
const fs = require('node:fs/promises')
const { execFile } = require('node:child_process')
const { promisify } = require('node:util')
const execute = promisify(execFile)

// LF-DBG-UI-003: main-app -> production editor -> production TEST_APP HTTP endpoint.
// No component harness, route interception, authentication replacement or fixture execution endpoint.
test('formal editor reuses its design canvas for readonly debug paths, node results and exception stacks', async ({ page }) => {
  const appName = process.env.LOGICFLOW_DEBUG_APP || 'lf-debug-demo'
  const flowName = 'order.calculate'
  const errors = []
  const exchanges = []
  const chapters = []
  const responses = []
  const notifications = []
  const startedAt = Date.now()
  let captureId
  let originalCanvas
  let originalNode
  let originalPositions
  let originalZoom
  let originalStroke
  let originalViewport
  let paletteBox
  let toolbarBox
  const viewportEvidence = []
  let paletteClass
  let inspectorClass
  let subscribed = false
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('websocket', (socket) => {
    socket.on('framesent', ({ payload }) => {
      if (String(payload).includes('/user/queue/dev/logicflow/debug/')) subscribed = true
    })
    socket.on('framereceived', ({ payload }) => {
      if (String(payload).includes('/queue/dev/logicflow/debug/')) notifications.push({ receivedAt: Date.now() })
    })
  })
  const recordResponse = (response) => {
    if (!response.url().includes(`/api/dev/${appName}/logicflow/debug/`)) return
    responses.push(response.json().then((body) => {
      exchanges.push({ method: response.request().method(), url: response.url(), status: response.status(), body })
    }).catch((error) => errors.push(error.message)))
  }
  page.on('response', recordResponse)
  const workbench = page.getByRole('region', { name: '逻辑流在线调试', exact: true })
  const runs = workbench.locator('.logicflow-debug-runs button')
  const editor = page.locator('.wrapper-editor-container')
  const canvas = editor.locator('.logicflow-canvas').first()
  const detail = workbench.getByLabel('节点运行详情')
  const node = (name) => canvas.locator('.x6-node').filter({ has: page.getByText(name, { exact: true }) })
  const activeEdges = canvas.locator('.x6-edge path[stroke="#2563eb"]')
  async function selectNode(name, clickCount = 1) {
    const label = node(name).getByText(name, { exact: true })
    await expect(label).toBeVisible()
    await expect.poll(() => label.evaluate((element) => {
      const rect = element.getBoundingClientRect()
      return element.closest('.x6-node').contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2))
    })).toBe(true)
    const box = await label.boundingBox()
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { clickCount })
  }
  const positions = () => canvas.locator('.x6-node').evaluateAll((items) => items.map((item) => ({ id: item.getAttribute('data-cell-id'), position: item.getAttribute('transform') })))
  const viewportState = () => editor.evaluate((element) => {
    const area = element.querySelector('.logicflow-canvas-container')
    const scroller = area.querySelector('.x6-graph-scroller')
    const box = area.getBoundingClientRect()
    return {
      bounds: { x: box.x, y: box.y, width: box.width, height: box.height },
      scroll: { left: scroller.scrollLeft, top: scroller.scrollTop },
      transform: area.querySelector('.x6-graph-svg-viewport').getAttribute('transform'),
      nodes: [...area.querySelectorAll('.logicflow-canvas .logicflow-node-body')].map((body) => {
        const rect = body.getBoundingClientRect()
        return { x: Math.round(rect.x * 100) / 100, y: Math.round(rect.y * 100) / 100, width: Math.round(rect.width * 100) / 100, height: Math.round(rect.height * 100) / 100 }
      }),
    }
  })
  async function assertStableCanvas(stage, expected = originalViewport) {
    const actual = await viewportState()
    viewportEvidence.push({ stage, actual })
    expect(actual, stage).toEqual(expected)
  }
  async function dragNode(name) {
    const box = await node(name).locator('.logicflow-node-body').boundingBox()
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2 - 24, { steps: 10 })
    await page.mouse.up()
  }
  async function hold(ms = 2000) { await page.waitForTimeout(ms) }
  async function chapter(title, work) {
    console.log(title)
    chapters.push({ title, fromTestStartMs: Date.now() - startedAt })
    await test.step(title, work)
    await hold()
  }
  async function business(amount, orderId) {
    const url = `http://127.0.0.1:8080/public-api/debug/order?amount=${amount}&orderId=${orderId}`
    // The image's Nginx does not expose /public-api. Call the ordinary backend HTTP route
    // inside the actual running container; authentication/authorization and the flow engine still run.
    const { stdout } = await execute('docker', ['exec', process.env.LOGICFLOW_RUNTIME_CONTAINER,
      'curl', '--silent', '--show-error', '--max-time', '15', '--write-out', '\n%{http_code}', url])
    const split = stdout.lastIndexOf('\n')
    const body = JSON.parse(stdout.slice(0, split))
    exchanges.push({ method: 'GET', url, transport: 'docker exec curl', status: Number(stdout.slice(split + 1)), body })
    return body
  }
  try {
    await page.goto('/')
    await expect(page.getByText('打开', { exact: true })).toBeVisible({ timeout: 90000 })
    const buildOverlay = page.locator('#webpack-dev-server-client-overlay')
    if (await buildOverlay.isVisible()) {
      const overlay = page.frameLocator('#webpack-dev-server-client-overlay')
      await expect(overlay.locator('body')).not.toContainText('ERROR in')
      await expect(overlay.locator('body')).toContainText('The legacy JS API is deprecated')
      await overlay.getByRole('button', { name: 'Dismiss' }).click()
    }
    await chapter('01 主应用打开验收应用，进入现有逻辑流编辑器', async () => {
      await page.getByText('打开', { exact: true }).click()
      await page.getByText('业务逻辑', { exact: true }).click()
      await page.getByText('逻辑流', { exact: true }).click()
      const row = page.locator('tr').filter({ hasText: flowName })
      // The same edit action becomes icon-only (继续编辑) while this user owns the model lock.
      await row.locator('button').filter({ has: page.locator('.fa-edit') }).click()
      await expect(page.locator('.x6-node').filter({ hasText: '校验金额' }).first()).toBeVisible({ timeout: 45000 })
      await editor.getByRole('button', { name: '适配画布', exact: true }).click()
      await hold(500)
      await editor.getByRole('button', { name: '居中画布', exact: true }).click()
      await hold(500)
      originalViewport = await viewportState()
      paletteBox = await editor.locator('[data-testid="logicflow-panel-left"]').boundingBox()
      toolbarBox = await editor.locator('.toolbar').boundingBox()
      originalCanvas = await canvas.elementHandle()
      originalNode = await node('计算含税金额').elementHandle()
      originalPositions = await positions()
      originalZoom = await editor.locator('.mini-map-zoom-percent').textContent()
      originalStroke = await node('计算含税金额').locator('.logicflow-node-body').getAttribute('stroke')
      paletteClass = await editor.locator('[data-testid="logicflow-panel-left"]').getAttribute('class')
      inspectorClass = await editor.locator('[data-testid="logicflow-panel-inspector"]').getAttribute('class')
      await page.screenshot({ path: test.info().outputPath('production-editor.png') })
    })
    await chapter('02 在线调试选择实际运行的测试容器并开始记录', async () => {
      await page.getByRole('button', { name: '在线调试', exact: true }).click()
      expect(await originalCanvas.evaluate((element) => element.isConnected && element.closest('[aria-label="执行运行图"]') !== null)).toBe(true)
      expect(await originalNode.evaluate((element) => element.isConnected)).toBe(true)
      expect(await positions()).toEqual(originalPositions)
      await expect(workbench.locator('.toolbar')).toBeVisible()
      await assertStableCanvas('进入调试：画布与所有节点屏幕位置不变')
      const debugPalette = await workbench.locator('.left-panel').boundingBox()
      expect(debugPalette).toEqual(paletteBox)
      const debugToolbar = await workbench.locator('.toolbar').boundingBox()
      expect(debugToolbar.x).toBe(toolbarBox.x)
      expect(debugToolbar.y).toBe(toolbarBox.y)
      await expect(editor.locator('[data-testid="logicflow-panel-left"]')).toHaveClass(/collapsed/)
      await expect(editor.locator('[data-testid="logicflow-panel-inspector"]')).toHaveClass(/collapsed/)
      await expect(editor.locator('.mini-map-zoom-percent')).toHaveText(originalZoom)
      await expect(editor.locator('.mini-map')).toBeVisible()
      const container = workbench.getByLabel('测试容器')
      const option = container.locator('option').filter({ hasText: process.env.LOGICFLOW_RUNTIME_CONTAINER })
      await expect(option).toHaveCount(1)
      await container.selectOption(await option.getAttribute('value'))
      await expect(workbench.getByRole('button', { name: '开始记录', exact: true })).toHaveCSS('background-color', 'rgb(37, 99, 235)')
      await page.screenshot({ path: test.info().outputPath('debug-empty-state.png') })
      await hold()
      await workbench.getByRole('button', { name: '开始记录', exact: true }).click()
      await expect(workbench.getByRole('status')).toHaveText('记录中', { timeout: 15000 })
      captureId = await workbench.getByLabel('历史记录').inputValue()
      await assertStableCanvas('采集与历史操作不改变画布')
    })
    await chapter('03 实际业务 HTTP 请求连续执行两次，执行列表实时增长', async () => {
      for (let i = 1; i <= 2; i++) {
        const result = await business(i * 100, `ORDER-${i}`)
        expect(result.code).toBe(0)
        expect(result.data.total).toBe(i * 106)
        await expect(runs).toHaveCount(i, { timeout: 20000 })
        await hold()
      }
      await expect(runs.filter({ hasText: '执行成功' })).toHaveCount(2)
    })
    await chapter('04 选择执行后四条实际连线立即同时流动，全部节点显示最终状态', async () => {
      await runs.first().click()
      await assertStableCanvas('选择执行后视口不变')
      await expect(canvas.locator('.logicflow-node-body')).toHaveCount(5)
      await expect(activeEdges).toHaveCount(4)
      for (const edge of await activeEdges.all()) await expect(edge).toHaveCSS('animation-name', 'logicflow-debug-stream')
      await expect(node('计算含税金额').locator('.logicflow-node-body')).toHaveAttribute('stroke', '#059669')
      await selectNode('计算含税金额')
      await assertStableCanvas('点击节点详情后视口不变')
      await expect(detail).toContainText('正常执行')
      await expect(detail).toContainText('106')
      await expect(detail.getByRole('button', { name: '输入 / 输出', exact: true })).toHaveCSS('border-top-width', '0px')
      await selectNode('计算含税金额', 2)
      await expect(editor.locator('[data-testid="logicflow-panel-inspector"]')).toHaveClass(/collapsed/)
      await assertStableCanvas('双击不会打开编辑属性或移动视口')
      await page.screenshot({ path: test.info().outputPath('whole-path-input-output.png') })
      await hold(4500)
      await workbench.getByRole('button', { name: '暂停流动', exact: true }).click()
      for (const edge of await activeEdges.all()) await expect(edge).toHaveCSS('animation-name', 'none')
      await expect(activeEdges).toHaveCount(4)
      await workbench.getByRole('button', { name: '继续流动', exact: true }).click()
    })
    await chapter('05 真实负数金额触发异常，红色节点、输入和堆栈可查看', async () => {
      const result = await business(-5, 'ORDER-ERROR')
      expect(result.code).not.toBe(0)
      await expect(runs).toHaveCount(3, { timeout: 20000 })
      const failed = runs.filter({ hasText: '执行异常' })
      await expect(failed).toHaveCount(1)
      await failed.click()
      await expect(node('校验金额').locator('.logicflow-node-body')).toHaveAttribute('stroke', '#dc2626')
      await expect(activeEdges).toHaveCount(2)
      await selectNode('校验金额')
      await expect(detail).toContainText('抛出异常')
      await expect(detail).toContainText('订单金额不能小于零')
      await expect(detail).toContainText('IllegalArgumentException')
      await expect(detail).toContainText('Caused by: java.lang.IllegalArgumentException')
      await expect(detail).toContainText('Script1.run(Script1.groovy:1)')
      await assertStableCanvas('异常结果叠加时画布不变')
      await expect(detail.locator('.logicflow-debug-exception-summary')).toContainText('订单金额不能小于零: -5')
      await detail.getByRole('button', { name: '输入 / 输出', exact: true }).click()
      await expect(detail).toContainText('ORDER-ERROR')
      await detail.getByRole('button', { name: '异常堆栈', exact: true }).click()
      await page.screenshot({ path: test.info().outputPath('actual-exception-stack.png') })
      await hold(2500)
      await detail.getByRole('button', { name: '展开节点详情' }).click()
      await page.screenshot({ path: test.info().outputPath('exception-expanded.png') })
      await hold(2500)
      await detail.getByRole('button', { name: '收起节点详情' }).click()
      await selectNode('计算含税金额')
      await expect(detail).toContainText('上游异常，未执行')
      await hold()
      await selectNode('校验金额')
    })
    await chapter('06 停止记录后返回设计器，再次进入恢复历史', async () => {
      await workbench.getByRole('button', { name: '停止记录', exact: true }).click()
      await expect(workbench.getByRole('button', { name: '开始记录', exact: true })).toBeEnabled({ timeout: 15000 })
      await workbench.getByRole('button', { name: '返回编辑', exact: true }).click()
      await expect(workbench).toHaveCount(0)
      expect(await originalCanvas.evaluate((element) => element.isConnected && element.closest('[aria-label="逻辑流设计画布"]') !== null)).toBe(true)
      expect(await originalNode.evaluate((element) => element.isConnected)).toBe(true)
      expect(await positions()).toEqual(originalPositions)
      await expect(node('计算含税金额').locator('.logicflow-node-body')).toHaveAttribute('stroke', originalStroke)
      await expect(editor.locator('[data-testid="logicflow-panel-left"]')).toHaveAttribute('class', paletteClass)
      await expect(editor.locator('[data-testid="logicflow-panel-inspector"]')).toHaveAttribute('class', inspectorClass)
      await expect(editor.locator('.toolbar')).toBeVisible()
      await assertStableCanvas('退出调试：不居中、不回写视口')
      await page.screenshot({ path: test.info().outputPath('editing-restored.png') })
      await hold()
      await page.getByRole('button', { name: '在线调试', exact: true }).click()
      await workbench.getByLabel('历史记录').selectOption(captureId)
      await expect(runs).toHaveCount(3)
      await runs.filter({ hasText: '执行异常' }).click()
      await assertStableCanvas('采集与历史操作不改变画布')
      await selectNode('校验金额')
      await expect(detail).toContainText('订单金额不能小于零')
      await page.screenshot({ path: test.info().outputPath('history-restored.png') })
      await hold(1500)
      await page.setViewportSize({ width: 1280, height: 900 })
      await hold(500)
      await expect(detail).toBeVisible()
      const narrowToolbar = await workbench.locator('.toolbar').boundingBox()
      const narrowInspector = await detail.boundingBox()
      expect(narrowToolbar.x + narrowToolbar.width).toBeLessThanOrEqual(narrowInspector.x - 12)
      await page.screenshot({ path: test.info().outputPath('responsive-1280.png') })
      const overflow = await editor.evaluate((element) => ({ width: element.clientWidth, scrollWidth: element.scrollWidth, height: element.clientHeight, scrollHeight: element.scrollHeight }))
      expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.width)
      expect(overflow.scrollHeight).toBeLessThanOrEqual(overflow.height)
      await page.setViewportSize({ width: 1600, height: 1000 })
      await hold(1500)
    })
    await chapter('07 手动平移只改变视野，退出调试保留当前视口', async () => {
      const beforeDrag = await positions()
      await dragNode('计算含税金额')
      expect(await positions()).toEqual(beforeDrag)
      const beforeNavigation = await viewportState()
      await page.mouse.move(beforeNavigation.bounds.x + 400, beforeNavigation.bounds.y + 300)
      await page.mouse.wheel(80, 60)
      await expect.poll(async () => (await viewportState()).scroll).toEqual({ left: beforeNavigation.scroll.left + 80, top: beforeNavigation.scroll.top + 60 })
      const manuallyNavigatedViewport = await viewportState()
      await workbench.getByRole('button', { name: '返回编辑', exact: true }).click()
      await assertStableCanvas('退出保留用户手动导航后的视口', manuallyNavigatedViewport)
    })
    await chapter('08 同一原画布恢复节点拖动能力', async () => {
      expect(await originalNode.evaluate((element) => element.isConnected)).toBe(true)
      const beforeEdit = await positions()
      await dragNode('计算含税金额')
      await expect.poll(positions).not.toEqual(beforeEdit)
      await page.screenshot({ path: test.info().outputPath('editing-drag-enabled.png') })
      // This is an unsaved change in the task's demo model; the browser context discards it.
      // History preservation is verified through the editor's public Graph API in component integration.
    })
    expect(subscribed, 'SDK must subscribe to the private capture destination').toBe(true)
    expect(notifications.length, 'The real WebSocket must deliver capture update hints').toBeGreaterThan(0)
    expect(errors).toEqual([])
  } finally {
    if (captureId && await workbench.getByRole('button', { name: '停止记录', exact: true }).isEnabled().catch(() => false)) {
      await workbench.getByRole('button', { name: '停止记录', exact: true }).click()
    }
    page.off('response', recordResponse)
    await Promise.all(responses)
    await fs.writeFile(test.info().outputPath('viewport-stability.json'), JSON.stringify(viewportEvidence, null, 2))
    await fs.writeFile(test.info().outputPath('chapters.json'), JSON.stringify(chapters, null, 2))
    await fs.writeFile(test.info().outputPath('http-exchange.json'), JSON.stringify(exchanges, null, 2))
    await fs.writeFile(test.info().outputPath('browser-result.json'), JSON.stringify({ errors, subscribed, notifications, captureId }, null, 2))
  }
})
