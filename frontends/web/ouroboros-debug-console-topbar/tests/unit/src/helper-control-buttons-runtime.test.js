import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, test } from 'vitest'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const packageRoot = path.resolve(__dirname, '..', '..', '..')
const repoRoot = path.resolve(packageRoot, '..', '..', '..')

function read(relativePath) {
  return fs.readFileSync(path.join(packageRoot, relativePath), 'utf8')
}

test('helper control buttons still render the canonical schema with route appName data', () => {
  const helperControlButtons = read('src/components/HelperControlButtons.jsx')

  expect(helperControlButtons).toContain("schemaId: '/dev/helper-control-buttons'")
  expect(helperControlButtons).toContain('appName: this.props.appName ||')
  expect(helperControlButtons).toContain('renderAmisSchema(this.helperBtnsRef.current, schema, {')
})

test('topbar still routes development appName into helper control buttons', () => {
  const topBar = read('src/TopBar.jsx')

  expect(topBar).toContain("const developmentRouteMatch = pathname.match(/^\\/(dev|debug)\\/(.+?)\\/(.+)$/)")
  expect(topBar).toContain('appNameInUrl = developmentRouteMatch[2]')
  expect(topBar).toContain('appName={appNameInUrl}')
})

test('helper control schema exposes helper details dialog and helper log contract', () => {
  const schemaPath = path.join(
    repoRoot,
    'dev-module/ouroboros-dev-app-management/src/main/metadata/dev/helper-control-buttons.ui-schema.json'
  )
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8')).schema

  expect(schema.api.url).toBe('/api/dev/${appName}/helper/status?autoCreate=true')
  expect(schema.keepSingleListener).toBe(false)

  const toolbarItems = schema.body[0].body[0].items
  const helperTooltip = toolbarItems.find((item) => item.type === 'tooltip-wrapper')
  const launchingButton = helperTooltip.body.find((button) => button.visibleOn === "${status == 'launching'}")
  const dropdownButtons = toolbarItems.find((item) => item.type === 'dropdown-button').buttons
  const dropdownButton = toolbarItems.find((item) => item.type === 'dropdown-button')
  const detailsButton = dropdownButtons.find((button) => button.label === '助手详情')

  expect(helperTooltip.content).toBe("<div>${status === 'notFound' ? '未启用助手' : status === 'running' ? '助手已启动' : status === 'stopped' ? '助手已停止' : '助手热加载中'}</div><div>刷新时间: ${ lastLoadTime != undefined ? ${lastLoadTime|date:YYYY-MM-DD HH\\:mm\\:ss:YYYY-MM-DDTHH\\:mm\\:ssZ} : '-'}</div>")
  expect(launchingButton.actionType).toBe('ajax')
  expect(launchingButton.api).toBe('post:/api/dev/${appName}/helper/restart')

  expect(dropdownButton.closeOnClick).toBe(true)
  expect(detailsButton).toBeTruthy()
  expect(detailsButton.dialog.title).toBe('助手详情')
  expect(detailsButton.dialog.size).toBe('lg')
  expect(detailsButton.dialog.actions).toEqual([])

  const detailsService = detailsButton.dialog.body[0]
  expect(detailsService.type).toBe('service')
  expect(detailsService.name).toBe('helperDetailsService')
  expect(detailsService.stomp).toBe('/topic/dev-helper/${appName}')
  expect(detailsService.keepSingleListener).toBe(false)
  expect(detailsService.api.url).toBe('/api/dev/${appName}/helper/status')

  expect(detailsService.body[0].type).toBe('tpl')
  expect(detailsService.body[0].tpl).toContain('基本信息')

  const propertySchema = detailsService.body[1]
  expect(propertySchema.type).toBe('property')
  expect(propertySchema.mode).toBe('simple')

  const propertyItems = propertySchema.items
  expect(propertyItems.map((item) => item.label)).toEqual([
    '镜像名',
    '状态',
    '最后刷新时间',
  ])
  expect(propertyItems[0].content).toBe("${imageReference || '-'}")
  expect(propertyItems[1].content).toBe("${status === 'notFound' ? '未启用助手' : status === 'running' ? '运行中' : status === 'stopped' ? '已停止' : status === 'launching' ? '启动中' : '-'}")
  expect(propertyItems[2].content).toBe("${lastLoadTime ? (lastLoadTime | date:YYYY-MM-DD HH\\:mm\\:ss:YYYY-MM-DDTHH\\:mm\\:ssZ) : '-'}")

  const actionsWrapper = detailsService.body[2]
  expect(actionsWrapper.type).toBe('wrapper')
  expect(actionsWrapper.className).toBe('my-4 text-center')

  const actionButtons = actionsWrapper.body[0].buttons
  expect(actionsWrapper.body[0].type).toBe('button-group')
  expect(actionsWrapper.body[0].className).toBe('justify-center')
  expect(actionButtons.map((button) => button.label)).toEqual([
    '重新获取元数据',
    '重启',
    '停止',
    '更新镜像',
    '停止并删除',
  ])
  expect(actionButtons.every((button) => !Object.hasOwn(button, 'level'))).toBe(true)
  expect(actionButtons[0].api).toBe('post:/api/dev/${appName}/helper/reload')
  expect(actionButtons[0].icon).toBe('fa fa-refresh text-primary w-4')
  expect(actionButtons[1].icon).toBe('fa fa-repeat text-success w-4')
  expect(actionButtons[1]).not.toHaveProperty('disabledOn')
  expect(actionButtons[2].icon).toBe('fa-solid fa-power-off text-danger w-4')
  expect(actionButtons[2].visibleOn).toBe("${status == 'running' || status == 'launching'}")
  expect(actionButtons[3].icon).toBe('fa-solid fa-anchor-circle-exclamation')
  expect(actionButtons[3].visibleOn).toBe('${imageState && imageState.updateRecommended}')
  expect(actionButtons[4].icon).toBe('fa fa-trash text-danger w-4')
  expect(actionButtons[4].confirmText).toContain('删除助手后，应用的元数据将无法同步')

  expect(detailsService.body).toHaveLength(3)

  const logWrapper = detailsButton.dialog.body[1]
  expect(logWrapper.type).toBe('wrapper')
  expect(logWrapper.data).toEqual({
    appName: '${appName}',
  })
  expect(logWrapper.body[0].type).toBe('tpl')
  expect(logWrapper.body[0].tpl).toContain('运行日志')

  const logSchema = logWrapper.body[1]
  expect(logSchema.type).toBe('log')
  expect(logSchema.stomp).toBe('/user/queue/app/${appName}/helper/logs')
  expect(logSchema.keepSingleListener).toBe(false)
})
