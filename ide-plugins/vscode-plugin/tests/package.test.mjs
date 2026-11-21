import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const packageJson = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'))
const zhCnMessages = JSON.parse(readFileSync(new URL('../package.nls.zh-cn.json', import.meta.url), 'utf8'))

test('uses the shared metadata submenu for explorer folders', () => {
  const explorerContext = packageJson.contributes.menus['explorer/context']
  const metadataSubmenus = explorerContext.filter((item) => item.submenu === 'tidestack.metadata.newFileSubmenu')

  assert.deepEqual(metadataSubmenus, [
    {
      submenu: 'tidestack.metadata.newFileSubmenu',
      when: 'explorerResourceIsFolder',
      group: 'navigation@19',
    },
  ])
})

test('exposes workspace config from the explorer context menu', () => {
  const explorerContext = packageJson.contributes.menus['explorer/context']
  const projectSubmenus = explorerContext.filter((item) => item.submenu === 'tidestack.develop.projectSubmenu')
  const projectItems = packageJson.contributes.menus['tidestack.develop.projectSubmenu']
  const projectCommands = packageJson.contributes.commands.filter((item) => item.command.startsWith('tidestack.develop.'))

  assert.deepEqual(projectSubmenus, [
    {
      submenu: 'tidestack.develop.projectSubmenu',
      when: 'explorerResourceIsFolder',
      group: 'navigation@18',
    },
  ])
  assert.deepEqual(projectItems.map((item) => item.command), [
    'tidestack.develop.configure',
    'tidestack.develop.openConfig',
    'tidestack.develop.importConfig',
  ])
  assert.deepEqual(projectCommands.map((item) => [item.command, item.title, item.category]), [
    ['tidestack.develop.configure', '%settings.configureProjectAction%', '%settings.displayName%'],
    ['tidestack.develop.openConfig', '%settings.openConfigFilesAction%', '%settings.displayName%'],
    ['tidestack.develop.importConfig', '%settings.importFromFile%', '%settings.displayName%'],
  ])
})

test('uses TideStack model wording in Chinese menu text', () => {
  assert.equal(zhCnMessages['metadata.new.submenu'], '创建 TideStack 模型')
  assert.equal(zhCnMessages['command.newMetadataFile'], 'TideStack 模型：新建文件')
  assert.equal(zhCnMessages['metadata.type.app-module.command'], '应用模块集合...')
  assert.equal(zhCnMessages['metadata.type.configuration.command'], '配置项集合...')
  assert.equal(zhCnMessages['metadata.type.configuration-group.command'], '配置分组集合...')
  assert.equal(zhCnMessages['metadata.type.configuration-item.command'], '配置项...')
  assert.equal(zhCnMessages['metadata.type.configuration-group-item.command'], '配置分组...')
  assert.equal(zhCnMessages['metadata.type.business.command'], '业务模型...')
  assert.equal(zhCnMessages['metadata.type.field-type.command'], '字段类型...')
  assert.equal(zhCnMessages['metadata.type.flow-node.command'], '逻辑流节点...')
  assert.equal(zhCnMessages['metadata.type.widget.command'], '组件定义...')
  assert.equal(zhCnMessages['metadata.type.micro-web.command'], '微前端...')
  assert.equal(zhCnMessages['metadata.type.rpc.command'], 'RPC 模型...')
  assert.equal(zhCnMessages['metadata.type.rpc-auth.command'], 'RPC 认证...')
  assert.equal(zhCnMessages['metadata.type.webapi.command'], 'WebAPI 模型...')
  assert.equal(zhCnMessages['metadata.type.notification.command'], '通知模板...')
  assert.equal(zhCnMessages['metadata.type.business-log.command'], '业务日志模板...')
  assert.equal(zhCnMessages['metadata.type.data-init.command'], '数据初始化...')
  assert.equal(zhCnMessages['metadata.type.mq.command'], 'MQ 绑定...')
  assert.equal(zhCnMessages['metadata.type.schedule.command'], '定时任务...')
  assert.equal(zhCnMessages['metadata.type.i18n.command'], '国际化语言包...')
  assert.equal(zhCnMessages['metadata.type.data-dict.command'], '数据字典...')
  assert.equal(zhCnMessages['metadata.type.position.command'], '岗位定义...')
  assert.equal(zhCnMessages['metadata.type.error-code.command'], '错误码集合...')
  assert.equal(zhCnMessages['metadata.type.app-metadata.command'], '应用元数据...')
  assert.equal(zhCnMessages['metadata.type.modules-diff.command'], '模块树 Diff...')
})

test('opens formal fixed-name metadata files by basename in custom editors', () => {
  const selectorsByType = Object.fromEntries(
    packageJson.contributes.customEditors.map((editor) => [
      editor.viewType,
      editor.selector.map((selector) => selector.filenamePattern),
    ]),
  )

  assert.ok(selectorsByType['tidestack.metadataEditor.errorCode'].includes('error-codes.error.json'))
  assert.ok(selectorsByType['tidestack.metadataEditor.appMetadata'].includes('app.metadata.json'))
  assert.ok(selectorsByType['tidestack.metadataEditor.modulesDiff'].includes('modules.diff.json'))
  assert.ok(selectorsByType['tidestack.metadataEditor.appModule'].includes('app-modules.json'))
  assert.ok(selectorsByType['tidestack.metadataEditor.configuration'].includes('configurations.json'))
  assert.ok(!selectorsByType['tidestack.metadataEditor.configuration'].includes('configuration.json'))
  assert.ok(selectorsByType['tidestack.metadataEditor.configurationGroup'].includes('configuration-groups.json'))
})
