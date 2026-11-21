const { test, expect } = require('@playwright/test')
const fs = require('node:fs/promises')

// LF-DBG-UI-001 / CONTAINER-001: isolated workbench + real API/MQ/Redis/container.
// Main-app navigation and the production editor are covered by runtime-debug-platform.browser.spec.cjs.
test('captures normal and exceptional Docker executions and replays actual transfers', async ({ page }) => {
  const errors = []
  const chapters = []
  const exchanges = []
  const responseTasks = []
  const recording = process.env.LOGICFLOW_DEBUG_RECORD === '1'
  const startedAt = Date.now()
  page.on('pageerror', (error) => errors.push(error.message))
  const recordResponse = (response) => {
    if (!/\/(api\/dev|fixture\/execute)\//.test(response.url())) return
    responseTasks.push(response.json().then((body) => {
      exchanges.push({ method: response.request().method(), url: response.url(), status: response.status(), body })
    }).catch((failure) => { errors.push('读取 HTTP 证据失败：' + failure.message) }))
  }
  page.on('response', recordResponse)

  // Pauses only pace the requested recording; behavior always waits on observable assertions.
  async function hold(milliseconds = 1800) {
    if (recording) await page.waitForTimeout(milliseconds)
  }

  async function chapter(label, work) {
    await test.step(label, async () => {
      chapters.push({ title: label, fromTestStartMs: Date.now() - startedAt })
      await page.locator('#fixture-step').evaluate((element, text) => { element.textContent = text }, label)
      await work()
      await hold()
    })
  }

  const targets = await page.request.get('/api/dev/debug-container-it/logicflow/debug/targets')
  expect(targets.ok()).toBeTruthy()
  expect((await targets.json()).data).toEqual([expect.objectContaining({ backend: 'DOCKER', supported: true })])
  await page.goto('/')
  const runs = page.locator('.logicflow-debug-runs button')
  const failed = runs.filter({ hasText: '异常' })
  const canvas = page.getByLabel('执行运行图').locator('.logicflow-canvas').first()
  const node = (name) => canvas.locator('.x6-node').filter({ has: page.getByText(name, { exact: true }) })

  await chapter('01 / 选择运行中的 Docker 测试容器，绑定实际助手进程', async () => {
    const container = page.getByRole('combobox', { name: /^测试容器/ })
    const option = container.locator('option').filter({ hasText: 'DOCKER' })
    await expect(option).toHaveCount(1)
    await container.selectOption(await option.getAttribute('value'))
    await hold(2200)
    await page.getByRole('button', { name: '开始记录', exact: true }).click()
    await expect(page.getByRole('button', { name: '停止记录', exact: true })).toBeEnabled()
  })

  await chapter('02 / 连续触发两次正常执行；每次执行独立记录', async () => {
    for (let count = 1; count <= 2; count++) {
      await page.getByRole('button', { name: '触发正常执行（测试入口）', exact: true }).click()
      await expect(runs).toHaveCount(count, { timeout: 20000 })
      await hold(1800)
    }
    await expect(runs.filter({ hasText: '成功' })).toHaveCount(2)
  })

  await chapter('03 / 选择一次正常执行，立即显示完整路径动画', async () => {
    await runs.filter({ hasText: '成功' }).first().click()
    await expect(canvas.locator('.logicflow-node-body')).toHaveCount(2)
    await expect(canvas.locator('.x6-edge path[stroke="#2563eb"]')).toHaveCount(1)
    await expect(canvas.locator('.x6-edge path[stroke="#2563eb"]')).toHaveCSS('animation-name', 'logicflow-debug-stream', { timeout: 20000 })
    await page.getByRole('button', { name: '适应画布' }).click()
    await page.screenshot({ path: test.info().outputPath('data-transfer.png'), fullPage: true })
  })

  await chapter('04 / 点击节点，查看本次运行的输入与输出', async () => {
    await node('script').getByText('script', { exact: true }).click()
    const detail = page.getByLabel('节点运行详情')
    await expect(detail).toContainText('正常执行')
    await expect(detail).toContainText('hello')
    await expect(node('script').locator('.logicflow-node-body')).toHaveAttribute('stroke', '#059669')
    await page.screenshot({ path: test.info().outputPath('normal-input-output.png'), fullPage: true })
    await hold(3500)
  })

  await chapter('05 / 触发一次真实异常，执行列表单独标记异常结果', async () => {
    await page.getByRole('button', { name: '触发异常执行（测试入口）', exact: true }).click()
    await expect(runs).toHaveCount(3, { timeout: 20000 })
    await expect(failed).toHaveCount(1)
    await failed.click()
    await expect(canvas.locator('.logicflow-node-body')).toHaveCount(2)
    await expect(node('script').locator('.logicflow-node-body')).toHaveAttribute('stroke', '#dc2626', { timeout: 20000 })
  })

  await chapter('06 / 点击红色节点，展开输入与原始异常堆栈', async () => {
    await node('script').getByText('script', { exact: true }).click()
    await expect(page.getByLabel('节点运行详情')).toContainText('fixture business failure')
    await expect(page.getByLabel('节点运行详情')).toContainText('DebugMqFixture')
    await page.screenshot({ path: test.info().outputPath('exception-stack.png'), fullPage: true })
    await hold(4500)
  })

  await chapter('07 / 事件时间线支持定位、单步与回到开始', async () => {
    await page.getByRole('region', { name: '逻辑流在线调试', exact: true }).getByText('事件时间线', { exact: false }).click()
    await page.getByRole('button', { name: /input.transfer/ }).last().click()
    await expect(canvas.locator('.x6-edge path[stroke="#2563eb"]')).toHaveCSS('animation-name', 'logicflow-debug-stream')
    await hold(2500)
    await page.locator('.logicflow-debug-step-controls > summary').click()
    await page.getByRole('button', { name: '单步', exact: true }).click()
    await page.getByRole('button', { name: '回到开始', exact: true }).click()
    await expect(page.getByLabel('回放进度')).toHaveValue('0')
  })

  let captureId
  await chapter('08 / 停止记录并刷新，恢复共享缓存中的三次执行', async () => {
    captureId = await page.getByLabel('历史记录').inputValue()
    await page.getByRole('button', { name: '停止记录', exact: true }).click()
    await expect(page.getByRole('button', { name: '开始记录', exact: true })).toBeEnabled({ timeout: 15000 })
    await hold(1800)
    await page.reload()
    await page.getByLabel('历史记录').selectOption(captureId)
    await expect(runs).toHaveCount(3)
    await failed.click()
    await expect(node('script').locator('.logicflow-node-body')).toHaveAttribute('stroke', '#dc2626', { timeout: 20000 })
    await page.getByRole('button', { name: '适应画布' }).click()
    await node('script').getByText('script', { exact: true }).click()
    await expect(page.getByLabel('节点运行详情')).toContainText('fixture business failure')
    await page.locator('#fixture-step').evaluate((element) => { element.textContent = '完成 / 已恢复三次独立执行；动画、节点输入输出、异常堆栈均来自真实容器采集' })
    await hold(3000)
  })

  page.off('response', recordResponse)
  await Promise.all(responseTasks)
  await fs.writeFile(test.info().outputPath('chapters.json'), JSON.stringify(chapters, null, 2))
  await fs.writeFile(test.info().outputPath('http-exchange.json'), JSON.stringify(exchanges, null, 2))
  expect(exchanges.length).toBeGreaterThan(0)
  expect(exchanges.every((exchange) => exchange.status === 200)).toBeTruthy()
  expect(errors).toEqual([])
})
