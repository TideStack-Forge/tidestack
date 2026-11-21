import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import metadataCreationCasesJson from '../../src/schema-resources/metadataCreationCases.json'
import {
  capabilitiesForIde,
  capabilityStatus,
  developConfigImportDocument,
  InMemoryMetadataFileBridge,
  createMetadataFileCreationPlan,
  createMetadataFileCreationFlowModel,
  createMetadataFileCreationPlanFromFlow,
  createMetadataFileTemplateDefaultValues,
  createBrowserMetadataEditorHost,
  createMetadataEditorWebviewHtml,
  createVsCodeMetadataEditorHostScript,
  createMetadataEditorHost,
  createMetadataEditorSession,
  defaultMetadataFilePath,
  effectiveDevelopConfig,
  getContributionByType,
  getPureSchema,
  handleMetadataEditorSessionRequest,
  localizeMetadataTypeContribution,
  localDevelopConfigDocument,
  localDevelopConfigFromDocument,
  mapMetadataSourcePath,
  metadataCreationContract,
  metadataCreationDirectoryForPath,
  metadataDirectoryScopeForPath,
  metadataEditorRegistrationDefinitions,
  metadataFileCreationInputForPath,
  metadataFilePathPrompt,
  metadataSourceRootForDirectory,
  metadataTemplateFieldPrompt,
  metadataTypeContributions,
  newMetadataFileCommandDefinitions,
  normalizeMetadataFileCreationPath,
  normalizeMetadataFilePath,
  projectDevelopConfigDocument,
  projectDevelopConfigFromDocument,
  serializeMetadataDocument,
  serializeMetadataFileTemplate,
  tideStackDevelopCapabilities,
  type JsonObject,
  type TideStackDevelopConfigContract,
  type TideStackDevelopLocalConfig,
  type TideStackDevelopProjectConfig,
} from '../../src'
import {
  getAmisEditorFallbackDropTarget,
  getAmisEditorFallbackRendererId,
  hasExceededAmisEditorFallbackDragThreshold,
  shouldUseAmisEditorDragFallback,
} from '../../src/adapters/amis/amisEditorDragFallback'
import { createVisualEditorChangeValue } from '../../src/adapters/amis/visualEditorSchemaValue'

const metadataEditorWebviewTemplate = readFileSync(
  fileURLToPath(new URL('../../runtime/metadata-editor-webview.html', import.meta.url)),
  'utf8',
)

type MetadataCreationCases = {
  pathCases: Array<{
    name: string
    metadataType: string
    selectedDirectory: string
    input: string
    expectedCreationDirectory: string
    expectedDefaultInputPath: string
    expectedDefaultPathInput: string
    expectedPath?: string
    expectedErrorContains?: string[]
  }>
  templateDefaultCases: Array<{
    name: string
    metadataType: string
    targetPath: string
    expectedValues: Record<string, unknown>
  }>
  templateSerializationCases: Array<{
    name: string
    metadataType: string
    targetPath: string
    values: Record<string, unknown>
    expectedDocument: unknown
  }>
}

const metadataCreationCases = metadataCreationCasesJson as MetadataCreationCases
const developConfigContract = JSON.parse(readFileSync(
  fileURLToPath(new URL('../../../contracts/tidestack-develop-config.json', import.meta.url)),
  'utf8',
)) as TideStackDevelopConfigContract
const developConfigCases = JSON.parse(readFileSync(
  fileURLToPath(new URL('../../../contracts/tidestack-develop-config-cases.json', import.meta.url)),
  'utf8',
)) as TideStackDevelopConfigCases

type TideStackDevelopConfigCases = {
  contract: {
    schemaVersion: number
    projectPath: string
    localPath: string
    projectFields: string[]
    localFields: string[]
  }
  projectReadCases: Array<{
    name: string
    document: JsonObject
    legacy: Partial<TideStackDevelopProjectConfig>
    expected: TideStackDevelopProjectConfig
  }>
  localReadCases: Array<{
    name: string
    document: JsonObject
    legacy: Partial<TideStackDevelopLocalConfig>
    expected: TideStackDevelopLocalConfig
  }>
  projectDocumentCases: Array<{
    name: string
    existing: JsonObject
    config: TideStackDevelopProjectConfig
    expectedKeys: string[]
    expected: JsonObject
  }>
  localDocumentCases: Array<{
    name: string
    existing: JsonObject
    config: TideStackDevelopLocalConfig
    expectedKeys: string[]
    expected: JsonObject
  }>
  importDocumentCases: Array<{
    name: string
    document: JsonObject
    expected: JsonObject
  }>
  effectiveCases: Array<{
    name: string
    project: TideStackDevelopProjectConfig
    local: TideStackDevelopLocalConfig
    expected: Record<string, string>
  }>
}

function collectStringValidatePaths(value: unknown, path = '$'): string[] {
  if (Array.isArray(value)) {
    return value.flatMap((item, index) => collectStringValidatePaths(item, `${path}[${index}]`))
  }
  if (!value || typeof value !== 'object') {
    return []
  }
  return Object.entries(value as Record<string, unknown>).flatMap(([key, item]) => {
    const itemPath = `${path}.${key}`
    return [
      ...(key === 'validate' && typeof item === 'string' ? [itemPath] : []),
      ...collectStringValidatePaths(item, itemPath),
    ]
  })
}

describe('metadata source path mapping', () => {
  it('maps MVP metadata files from src/main/metadata to META-INF/ouroboros', () => {
    const mappings = [
      mapMetadataSourcePath('/repo/src/main/metadata/user-management.authority.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/system-setting.menu.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/all-models.dev-menu.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/system-setting.ui-model.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/system-setting.ui-schema.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/business-model/order.business.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/logic/order.logicflow.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/workflow/purchase.workflow.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/approval-form/purchase.approval-form.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/data/example.field-type.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/logic/example.flow-node.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/dashboard/example.widget.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/micro-web/admin.micro-web.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/rpc/open-api.rpc.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/rpc/auth/shared-credential.rpc.auth.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/webapi/orders.webapi.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/notification/welcome.notification.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/audit/create-order.business-log.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/seed/status.data-init.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/mq/orders-created.mq.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/schedule/daily-sync.schedule.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/i18n/zh-cn.i18n.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/data-dict/customer-status.dict.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/organization/member.position.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/error-codes.error.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/app.metadata.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/modules.diff.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/app-modules.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/configurations.json'),
      mapMetadataSourcePath('/repo/src/main/metadata/configuration-groups.json'),
      mapMetadataSourcePath('/repo/configuration/key0.config.json'),
      mapMetadataSourcePath('/repo/configuration-group/security-session.config-group.json'),
    ]

    expect(mappings.map((mapping) => mapping?.type)).toEqual([
      'authority',
      'menu',
      'dev-menu',
      'ui-model',
      'ui-schema',
      'business',
      'logicflow',
      'approval-workflow',
      'approval-form',
      'field-type',
      'flow-node',
      'widget',
      'micro-web',
      'rpc',
      'rpc-auth',
      'webapi',
      'notification',
      'business-log',
      'data-init',
      'mq',
      'schedule',
      'i18n',
      'data-dict',
      'position',
      'error-code',
      'app-metadata',
      'modules-diff',
      'app-module',
      'configuration',
      'configuration-group',
      'configuration-item',
      'configuration-group-item',
    ])
    expect(mappings.map((mapping) => mapping?.packagedPath)).toEqual([
      'META-INF/ouroboros/metadata/user-management.authority.json',
      'META-INF/ouroboros/metadata/system-setting.menu.json',
      'META-INF/ouroboros/metadata/all-models.dev-menu.json',
      'META-INF/ouroboros/metadata/system-setting.ui-model.json',
      'META-INF/ouroboros/metadata/system-setting.ui-schema.json',
      'META-INF/ouroboros/metadata/business-model/order.business.json',
      'META-INF/ouroboros/metadata/logic/order.logicflow.json',
      'META-INF/ouroboros/metadata/workflow/purchase.workflow.json',
      'META-INF/ouroboros/metadata/approval-form/purchase.approval-form.json',
      'META-INF/ouroboros/metadata/data/example.field-type.json',
      'META-INF/ouroboros/metadata/logic/example.flow-node.json',
      'META-INF/ouroboros/metadata/dashboard/example.widget.json',
      'META-INF/ouroboros/metadata/micro-web/admin.micro-web.json',
      'META-INF/ouroboros/metadata/rpc/open-api.rpc.json',
      'META-INF/ouroboros/metadata/rpc/auth/shared-credential.rpc.auth.json',
      'META-INF/ouroboros/metadata/webapi/orders.webapi.json',
      'META-INF/ouroboros/metadata/notification/welcome.notification.json',
      'META-INF/ouroboros/metadata/audit/create-order.business-log.json',
      'META-INF/ouroboros/metadata/seed/status.data-init.json',
      'META-INF/ouroboros/metadata/mq/orders-created.mq.json',
      'META-INF/ouroboros/metadata/schedule/daily-sync.schedule.json',
      'META-INF/ouroboros/metadata/i18n/zh-cn.i18n.json',
      'META-INF/ouroboros/metadata/data-dict/customer-status.dict.json',
      'META-INF/ouroboros/metadata/organization/member.position.json',
      'META-INF/ouroboros/metadata/error-codes.error.json',
      'META-INF/ouroboros/metadata/app.metadata.json',
      'META-INF/ouroboros/metadata/modules.diff.json',
      'META-INF/ouroboros/metadata/app-modules.json',
      'META-INF/ouroboros/metadata/configurations.json',
      'META-INF/ouroboros/metadata/configuration-groups.json',
      'META-INF/ouroboros/metadata/key0.config.json',
      'META-INF/ouroboros/metadata/security-session.config-group.json',
    ])
    expect(mappings.flatMap((mapping) => mapping?.diagnostics ?? [])).toEqual([])
  })

  it('resolves new metadata files by suffix even when the directory name differs', () => {
    const mapping = mapMetadataSourcePath('/repo/src/main/metadata/dev-menu-model/main.menu.json')

    expect(mapping?.type).toBe('menu')
    expect(mapping?.packagedPath).toBe('META-INF/ouroboros/metadata/dev-menu-model/main.menu.json')
    expect(mapping?.diagnostics).toEqual([])
  })

  it('keeps legacy fixed configuration file names readable', () => {
    const mapping = mapMetadataSourcePath('/repo/src/main/metadata/configuration.json')

    expect(mapping).toMatchObject({
      type: 'configuration',
      packagedPath: 'META-INF/ouroboros/metadata/configuration.json',
      diagnostics: [],
    })
  })

  it('matches new metadata suffixes without a source directory prefix', () => {
    expect(mapMetadataSourcePath('/repo/custom/ui-schema/home.ui-schema.json')).toMatchObject({
      type: 'ui-schema',
      packagedPath: 'META-INF/ouroboros/metadata/home.ui-schema.json',
      diagnostics: [],
    })
  })

  it('matches formal fixed metadata file names without a source directory prefix', () => {
    const mappings = [
      mapMetadataSourcePath('/repo/custom/error-codes.error.json'),
      mapMetadataSourcePath('/repo/custom/app.metadata.json'),
      mapMetadataSourcePath('/repo/custom/modules.diff.json'),
      mapMetadataSourcePath('/repo/custom/app-modules.json'),
      mapMetadataSourcePath('/repo/custom/configurations.json'),
      mapMetadataSourcePath('/repo/custom/configuration-groups.json'),
    ]

    expect(mappings.map((mapping) => mapping?.type)).toEqual([
      'error-code',
      'app-metadata',
      'modules-diff',
      'app-module',
      'configuration',
      'configuration-group',
    ])
    expect(mappings.map((mapping) => mapping?.packagedPath)).toEqual([
      'META-INF/ouroboros/metadata/error-codes.error.json',
      'META-INF/ouroboros/metadata/app.metadata.json',
      'META-INF/ouroboros/metadata/modules.diff.json',
      'META-INF/ouroboros/metadata/app-modules.json',
      'META-INF/ouroboros/metadata/configurations.json',
      'META-INF/ouroboros/metadata/configuration-groups.json',
    ])
    expect(mapMetadataSourcePath('/repo/custom/configuration.json')).toBeUndefined()
  })

  it('loads new packaged metadata by metadata directory suffixes and fixed file names', async () => {
    const menuUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/systemSetting/resourceManagement.menu.json'
    const uiSchemaUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/resourceManagement.ui-schema.json'
    const businessUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/business-model/order.business.json'
    const logicflowUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/logic/order.logicflow.json'
    const approvalWorkflowUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/workflow/purchase.workflow.json'
    const fieldTypeUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/fields/text.field-type.json'
    const flowNodeUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/logic/create.flow-node.json'
    const widgetUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/widgets/summary.widget.json'
    const microWebUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/web/admin.micro-web.json'
    const rpcUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/rpc/open-api.rpc.json'
    const rpcAuthUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/rpc/auth/shared.rpc.auth.json'
    const webapiUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/webapi/orders.webapi.json'
    const notificationUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/notifications/welcome.notification.json'
    const businessLogUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/audit/create-order.business-log.json'
    const dataInitUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/seed/status.data-init.json'
    const mqUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/mq/orders-created.mq.json'
    const scheduleUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/schedule/daily-sync.schedule.json'
    const i18nUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/i18n/zh-cn.i18n.json'
    const dataDictUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/data-dict/customer-status.dict.json'
    const positionUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/organization/member.position.json'
    const errorCodesUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/error-codes.error.json'
    const appMetadataUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/app.metadata.json'
    const modulesDiffUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/modules.diff.json'
    const appModulesUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/app-modules.json'
    const configurationUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/configurations.json'
    const configurationGroupsUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/metadata/configuration-groups.json'
    const configurationItemUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/configuration/key0.config.json'
    const configurationGroupItemUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/configuration-group/security-session.config-group.json'
    const bridge = new InMemoryMetadataFileBridge({
      [menuUri]: '{"title":"Resource Management","fullName":"systemSetting.resourceManagement"}\n',
      [uiSchemaUri]: '{"name":"/resource-management","schema":{"type":"page","body":[]}}\n',
      [businessUri]: '{"modelType":"default","name":"order","title":"订单","fields":[]}\n',
      [logicflowUri]: '{"name":"order","nodes":[],"connections":[],"nodePositions":{},"isEnabled":true}\n',
      [approvalWorkflowUri]: '{"formatVersion":1,"name":"purchase","title":"采购审批","version":1,"enabled":true,"form":{"formType":"business-model","formId":"purchase"},"nodes":[],"edges":[]}\n',
      [fieldTypeUri]: '{"name":"text","label":"Text","dataType":{"type":"String"}}\n',
      [flowNodeUri]: '{"type":"create","label":"Create","scaffold":{"type":"create"}}\n',
      [widgetUri]: '{"name":"Summary","title":"Summary"}\n',
      [microWebUri]: '{"name":"admin","type":"app","publicPath":"/admin/"}\n',
      [rpcUri]: '{"name":"openApi","title":"OpenAPI","type":"OpenApi","isEnabled":true,"request":{"method":"get","url":"https://example.invalid"}}\n',
      [rpcAuthUri]: '{"name":"shared","title":"Shared Credential","type":"accessToken","isEnabled":true}\n',
      [webapiUri]: '{"method":"GET","path":"/orders","summary":"List orders"}\n',
      [notificationUri]: '{"name":"welcome","title":"Welcome","isEnabled":true,"channel":{}}\n',
      [businessLogUri]: '{"name":"create-order","httpMethod":"POST","url":"/orders","moduleName":"Orders","operation":"Create"}\n',
      [dataInitUri]: '{"id":"status","version":"v1","blocks":[{"id":"statuses","targetModel":"Status","matchFields":["code"],"records":[]}]}\n',
      [mqUri]: '{"bindingType":"producer","bindingName":"ordersCreated","destination":"orders.created"}\n',
      [scheduleUri]: '{"name":"dailySync","cron":"0 0 2 * * ?","flowName":"daily.sync","data":{}}\n',
      [i18nUri]: '{"locale":"zh-CN","name":"简体中文","enabled":true,"isDefault":true,"messages":{"common":{"hello":"你好"}}}\n',
      [dataDictUri]: '{"name":"customerStatus","title":"Customer Status","type":"STATIC","group":"customer","editable":false,"treeMode":false,"items":[]}\n',
      [positionUri]: '{"key":"member","name":"成员","description":"默认成员岗位"}\n',
      [errorCodesUri]: '[{"code":40000,"title":"Example","defaultMessageTemplate":"Example"}]\n',
      [appMetadataUri]: '{"title":"Sample App","defaultTheme":"dark"}\n',
      [modulesDiffUri]: '[{"diffType":"add","fullName":"System.Sample","title":"Sample"}]\n',
      [appModulesUri]: '[{"fullName":"resource","title":"Resource Management"}]\n',
      [configurationUri]: '[{"key":"resource.enabled","label":"Resource enabled","valueType":"boolean"}]\n',
      [configurationGroupsUri]: '[{"path":"resource","title":"Resource"}]\n',
      [configurationItemUri]: '{"key":"key0","label":"Key 0","valueType":"string","defaultValue":"key0-default"}\n',
      [configurationGroupItemUri]: '{"path":"security.session","title":"Session"}\n',
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(menuUri)).resolves.toMatchObject({ type: 'menu', diagnostics: [] })
    await expect(host.loadDocument(uiSchemaUri)).resolves.toMatchObject({ type: 'ui-schema', diagnostics: [] })
    await expect(host.loadDocument(businessUri)).resolves.toMatchObject({ type: 'business', diagnostics: [] })
    await expect(host.loadDocument(logicflowUri)).resolves.toMatchObject({ type: 'logicflow', diagnostics: [] })
    await expect(host.loadDocument(approvalWorkflowUri)).resolves.toMatchObject({ type: 'approval-workflow', diagnostics: [] })
    await expect(host.loadDocument(fieldTypeUri)).resolves.toMatchObject({ type: 'field-type', diagnostics: [] })
    await expect(host.loadDocument(flowNodeUri)).resolves.toMatchObject({ type: 'flow-node', diagnostics: [] })
    await expect(host.loadDocument(widgetUri)).resolves.toMatchObject({ type: 'widget', diagnostics: [] })
    await expect(host.loadDocument(microWebUri)).resolves.toMatchObject({ type: 'micro-web', diagnostics: [] })
    await expect(host.loadDocument(rpcUri)).resolves.toMatchObject({ type: 'rpc', diagnostics: [] })
    await expect(host.loadDocument(rpcAuthUri)).resolves.toMatchObject({ type: 'rpc-auth', diagnostics: [] })
    await expect(host.loadDocument(webapiUri)).resolves.toMatchObject({ type: 'webapi', diagnostics: [] })
    await expect(host.loadDocument(notificationUri)).resolves.toMatchObject({ type: 'notification', diagnostics: [] })
    await expect(host.loadDocument(businessLogUri)).resolves.toMatchObject({ type: 'business-log', diagnostics: [] })
    await expect(host.loadDocument(dataInitUri)).resolves.toMatchObject({ type: 'data-init', diagnostics: [] })
    await expect(host.loadDocument(mqUri)).resolves.toMatchObject({ type: 'mq', diagnostics: [] })
    await expect(host.loadDocument(scheduleUri)).resolves.toMatchObject({ type: 'schedule', diagnostics: [] })
    await expect(host.loadDocument(i18nUri)).resolves.toMatchObject({ type: 'i18n', diagnostics: [] })
    await expect(host.loadDocument(dataDictUri)).resolves.toMatchObject({ type: 'data-dict', diagnostics: [] })
    await expect(host.loadDocument(positionUri)).resolves.toMatchObject({ type: 'position', diagnostics: [] })
    await expect(host.loadDocument(errorCodesUri)).resolves.toMatchObject({ type: 'error-code', diagnostics: [] })
    await expect(host.loadDocument(appMetadataUri)).resolves.toMatchObject({ type: 'app-metadata', diagnostics: [] })
    await expect(host.loadDocument(modulesDiffUri)).resolves.toMatchObject({ type: 'modules-diff', diagnostics: [] })
    await expect(host.loadDocument(appModulesUri)).resolves.toMatchObject({ type: 'app-module', diagnostics: [] })
    await expect(host.loadDocument(configurationUri)).resolves.toMatchObject({ type: 'configuration', diagnostics: [] })
    await expect(host.loadDocument(configurationGroupsUri)).resolves.toMatchObject({ type: 'configuration-group', diagnostics: [] })
    await expect(host.loadDocument(configurationItemUri)).resolves.toMatchObject({ type: 'configuration-item', diagnostics: [] })
    await expect(host.loadDocument(configurationGroupItemUri)).resolves.toMatchObject({ type: 'configuration-group-item', diagnostics: [] })
  })

  it('loads legacy packaged metadata by resources subpath without requiring the new suffix', async () => {
    const menuUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/menu-model/systemSetting/resourceManagement.json'
    const authorityUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/authority/systemSetting/resourceManagement.json'
    const devMenuUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/dev-menu-model/AllModels.Resource.json'
    const uiModelUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/ui-model/system-setting/resourceManagement.json'
    const uiSchemaUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/ui-schema/system-setting/resourceManagement.json'
    const rpcUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/rpc-model/openApi.rpc.json'
    const rpcAuthUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/rpc-model/auth/sharedCredential.rpc.auth.json'
    const appMetadataUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/app.metadata.json'
    const modulesDiffUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/modules.diff.json'
    const appModulesUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/app-modules.json'
    const configurationUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/configuration.json'
    const configurationGroupsUri = '/repo/core/sample/src/main/resources/META-INF/ouroboros/configuration-groups.json'
    const bridge = new InMemoryMetadataFileBridge({
      [menuUri]: '{"title":"Resource Management","fullName":"systemSetting.resourceManagement"}\n',
      [authorityUri]: '{"title":"Resource Management","fullName":"systemSetting.resourceManagement"}\n',
      [devMenuUri]: '{"title":"Resource","fullName":"AllModels.Resource"}\n',
      [uiModelUri]: '{"path":"/resource-management","type":"amis","title":"Resource Management"}\n',
      [uiSchemaUri]: '{"type":"page","body":[],"customField":{"enabled":true}}\n',
      [rpcUri]: '{"name":"openApi","title":"OpenAPI","type":"OpenApi","isEnabled":true,"request":{"method":"get","url":"https://example.invalid"}}\n',
      [rpcAuthUri]: '{"name":"sharedCredential","title":"Shared Credential","type":"accessToken","isEnabled":true}\n',
      [appMetadataUri]: '{"title":"Sample App","defaultTheme":"dark"}\n',
      [modulesDiffUri]: '[{"diffType":"add","fullName":"System.Sample","title":"Sample"}]\n',
      [appModulesUri]: '[{"fullName":"resource","title":"Resource Management"}]\n',
      [configurationUri]: '[{"key":"resource.enabled","label":"Resource enabled","valueType":"boolean"}]\n',
      [configurationGroupsUri]: '[{"path":"resource","title":"Resource"}]\n',
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(menuUri)).resolves.toMatchObject({
      type: 'menu',
      packagedPath: 'META-INF/ouroboros/menu-model/systemSetting/resourceManagement.json',
      diagnostics: [],
    })
    await expect(host.getTypeContribution(menuUri)).resolves.toMatchObject({ type: 'menu' })
    await expect(host.loadDocument(authorityUri)).resolves.toMatchObject({ type: 'authority', diagnostics: [] })
    await expect(host.loadDocument(devMenuUri)).resolves.toMatchObject({ type: 'dev-menu', diagnostics: [] })
    await expect(host.loadDocument(uiModelUri)).resolves.toMatchObject({ type: 'ui-model', diagnostics: [] })
    await expect(host.loadDocument(uiSchemaUri)).resolves.toMatchObject({ type: 'ui-schema', diagnostics: [] })
    await expect(host.loadDocument(rpcUri)).resolves.toMatchObject({ type: 'rpc', diagnostics: [] })
    await expect(host.loadDocument(rpcAuthUri)).resolves.toMatchObject({ type: 'rpc-auth', diagnostics: [] })
    await expect(host.loadDocument(appMetadataUri)).resolves.toMatchObject({ type: 'app-metadata', diagnostics: [] })
    await expect(host.loadDocument(modulesDiffUri)).resolves.toMatchObject({ type: 'modules-diff', diagnostics: [] })
    await expect(host.loadDocument(appModulesUri)).resolves.toMatchObject({ type: 'app-module', diagnostics: [] })
    await expect(host.loadDocument(configurationUri)).resolves.toMatchObject({ type: 'configuration', diagnostics: [] })
    await expect(host.loadDocument(configurationGroupsUri)).resolves.toMatchObject({ type: 'configuration-group', diagnostics: [] })
  })

  it('diagnoses legacy packaged path and suffix conflicts', async () => {
    const uri = '/repo/src/main/resources/META-INF/ouroboros/dev-menu-model/main.menu.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: '{"title":"Main","fullName":"main"}\n',
    })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)

    expect(loaded.type).toBe('dev-menu')
    expect(loaded.diagnostics).toEqual([
      expect.objectContaining({ code: 'metadata-path-suffix-conflict', severity: 'error' }),
    ])
  })

  it('diagnoses conflicts between a legacy metadata directory and a different suffix', async () => {
    const uri = '/repo/src/main/resources/META-INF/ouroboros/configuration-group/configuration-groups.menu.json'
    const bridge = new InMemoryMetadataFileBridge({ [uri]: '[]\n' })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)

    expect(loaded.type).toBe('configuration-group')
    expect(loaded.diagnostics).toEqual([
      expect.objectContaining({ code: 'metadata-path-suffix-conflict', severity: 'error' }),
    ])
  })
})

describe('metadata type contributions', () => {
  it('registers all supported metadata types through one contribution contract', () => {
    expect(metadataTypeContributions.map((contribution) => contribution.type)).toEqual([
      'authority',
      'menu',
      'dev-menu',
      'ui-model',
      'ui-schema',
      'business',
      'logicflow',
      'approval-workflow',
      'approval-form',
      'field-type',
      'flow-node',
      'widget',
      'micro-web',
      'rpc',
      'rpc-auth',
      'webapi',
      'notification',
      'business-log',
      'data-init',
      'mq',
      'schedule',
      'i18n',
      'data-dict',
      'position',
      'error-code',
      'app-metadata',
      'modules-diff',
      'app-module',
      'configuration',
      'configuration-group',
      'configuration-item',
      'configuration-group-item',
    ])
    expect(metadataTypeContributions.slice(0, 3).map((contribution) => contribution.displayName)).toEqual(['权限项模型', '菜单模型', '开发菜单模型'])
    expect(metadataTypeContributions.slice(3).map((contribution) => contribution.displayName)).toEqual(['UI 模型', 'UI Schema', '业务模型', '逻辑流模型', '审批流', '自定义表单描述', '字段类型', '逻辑流节点', '组件定义', '微前端', 'RPC 模型', 'RPC 认证', 'WebAPI 模型', '通知模板', '业务日志模板', '数据初始化', 'MQ 绑定', '定时任务', '国际化语言包', '数据字典', '岗位定义', '错误码', '应用元数据', '模块树 Diff', '应用模块', '配置项', '配置分组', '配置项', '配置分组'])
    expect(getContributionByType('authority')?.editorKinds).toEqual(['form', 'table'])
    expect(getContributionByType('ui-schema')?.editorKinds).toEqual(['amis-editor'])
    expect(getContributionByType('menu')?.createEditorSchema({ uri: 'main.menu.json', document: {} })).toMatchObject({
      type: 'form',
      title: '菜单模型',
      actions: [],
    })
    expect(getContributionByType('authority')?.createEditorSchema({ uri: 'auth.authority.json', document: {} })).toMatchObject({
      title: '权限项模型',
    })
    expect(getContributionByType('dev-menu')?.createEditorSchema({ uri: 'dev.dev-menu.json', document: {} })).toMatchObject({
      title: '开发菜单模型',
    })
    expect(getContributionByType('ui-model')?.createEditorSchema({ uri: 'main.ui-model.json', document: {} })).toMatchObject({
      title: 'UI 模型',
    })
    expect(getContributionByType('ui-schema')?.createEditorSchema({ uri: 'main.ui-schema.json', document: {} })).toMatchObject({
      title: 'UI Schema',
    })
    const businessEditorSchema = getContributionByType('business')?.createEditorSchema({ uri: 'order.business.json', document: {} })
    expect(businessEditorSchema).toMatchObject({
      type: 'form',
      id: 'edit-business-model-form',
    })
    const serializedBusinessEditorSchema = JSON.stringify(businessEditorSchema)
    expect(serializedBusinessEditorSchema).toContain('ide://editor-integrations/business/field-types/')
    expect(serializedBusinessEditorSchema).not.toContain('/api/app/${appName}/field-types')
    expect(serializedBusinessEditorSchema).not.toContain('宿主系统标签')
    expect(serializedBusinessEditorSchema).not.toContain('"openPage"')
    expect(getContributionByType('logicflow')?.createEditorSchema({ uri: 'order.logicflow.json', document: {} })).toMatchObject({
      type: 'form',
      metadataRootField: '__metadataDocument',
      body: [expect.objectContaining({
        type: 'logic-flow-editor',
        name: '__metadataDocument',
        nodeDefineApi: 'ide://editor-integrations/logicflow/node-defines',
      })],
    })
    expect(getContributionByType('approval-workflow')?.createEditorSchema({ uri: 'purchase.workflow.json', document: {} })).toMatchObject({
      type: 'form',
      metadataRootField: '__metadataDocument',
      body: [expect.objectContaining({
        type: 'workflow-editor',
        name: '__metadataDocument',
        candidateSourceApi: 'ide://editor-integrations/approval-workflow/candidate',
        formFieldSourceApi: 'ide://editor-integrations/approval-workflow/form-fields',
      })],
    })
    expect(getContributionByType('field-type')?.createEditorSchema({ uri: 'text.field-type.json', document: {} })).toMatchObject({
      title: '字段类型',
    })
    expect(getContributionByType('flow-node')?.createEditorSchema({ uri: 'create.flow-node.json', document: {} })).toMatchObject({
      title: '逻辑流节点',
    })
    expect(getContributionByType('widget')?.createEditorSchema({ uri: 'summary.widget.json', document: {} })).toMatchObject({
      title: '组件定义',
    })
    expect(getContributionByType('micro-web')?.createEditorSchema({ uri: 'admin.micro-web.json', document: {} })).toMatchObject({
      title: '微前端',
    })
    expect(getContributionByType('rpc')?.createEditorSchema({ uri: 'open-api.rpc.json', document: {} })).toMatchObject({
      title: 'RPC 模型',
    })
    expect(getContributionByType('rpc-auth')?.createEditorSchema({ uri: 'shared.rpc.auth.json', document: {} })).toMatchObject({
      title: 'RPC 认证',
    })
    expect(getContributionByType('webapi')?.createEditorSchema({ uri: 'orders.webapi.json', document: {} })).toMatchObject({
      title: 'WebAPI 模型',
    })
    expect(getContributionByType('notification')?.createEditorSchema({ uri: 'welcome.notification.json', document: {} })).toMatchObject({
      title: '通知模板',
    })
    expect(getContributionByType('business-log')?.createEditorSchema({ uri: 'create-order.business-log.json', document: {} })).toMatchObject({
      title: '业务日志模板',
    })
    expect(getContributionByType('data-init')?.createEditorSchema({ uri: 'status.data-init.json', document: {} })).toMatchObject({
      title: '数据初始化',
    })
    expect(getContributionByType('mq')?.createEditorSchema({ uri: 'orders-created.mq.json', document: {} })).toMatchObject({
      title: 'MQ 绑定',
    })
    expect(getContributionByType('schedule')?.createEditorSchema({ uri: 'daily-sync.schedule.json', document: {} })).toMatchObject({
      title: '定时任务',
    })
    expect(getContributionByType('i18n')?.createEditorSchema({ uri: 'zh-cn.i18n.json', document: {} })).toMatchObject({
      title: '国际化语言包',
    })
    expect(getContributionByType('data-dict')?.createEditorSchema({ uri: 'customer-status.dict.json', document: {} })).toMatchObject({
      title: '数据字典',
    })
    expect(getContributionByType('position')?.createEditorSchema({ uri: 'member.position.json', document: {} })).toMatchObject({
      title: '岗位定义',
    })
    expect(getContributionByType('error-code')?.createEditorSchema({ uri: 'error-codes.error.json', document: [] })).toMatchObject({
      title: '错误码',
    })
    expect(getContributionByType('app-metadata')?.createEditorSchema({ uri: 'app.metadata.json', document: {} })).toMatchObject({
      title: '应用元数据',
    })
    expect(getContributionByType('modules-diff')?.createEditorSchema({ uri: 'modules.diff.json', document: [] })).toMatchObject({
      title: '模块树 Diff',
    })
    expect(getContributionByType('app-module')?.createEditorSchema({ uri: 'app-modules.json', document: [] })).toMatchObject({
      title: '应用模块',
    })
    expect(getContributionByType('configuration')?.createEditorSchema({ uri: 'configurations.json', document: [] })).toMatchObject({
      title: '配置项',
    })
    expect(getContributionByType('configuration-group')?.createEditorSchema({ uri: 'configuration-groups.json', document: [] })).toMatchObject({
      title: '配置分组',
    })
    expect(getContributionByType('configuration-item')?.createEditorSchema({ uri: 'key0.config.json', document: {} })).toMatchObject({
      title: '配置项',
    })
    expect(getContributionByType('configuration-group-item')?.createEditorSchema({ uri: 'security-session.config-group.json', document: {} })).toMatchObject({
      title: '配置分组',
    })
    expect(getContributionByType('app-module')?.documentShape).toBe('array')
    expect(getContributionByType('error-code')?.documentShape).toBe('array')
    expect(getContributionByType('modules-diff')?.documentShape).toBe('array')
    expect(getContributionByType('configuration')?.documentShape).toBe('array')
    expect(getContributionByType('configuration-group')?.documentShape).toBe('array')
    expect(getContributionByType('configuration-item')?.documentShape).toBe('object')
    expect(getContributionByType('configuration-group-item')?.documentShape).toBe('object')
    expect(getContributionByType('business')?.documentShape).toBe('object')
    expect(getContributionByType('logicflow')?.documentShape).toBe('object')
    expect(getContributionByType('approval-workflow')?.documentShape).toBe('object')
    expect(getContributionByType('webapi')?.documentShape).toBe('object')
    expect(getContributionByType('position')?.documentShape).toBe('object')
  })

  it('derives IDE editor and new-file registrations from metadata contributions', () => {
    expect(metadataEditorRegistrationDefinitions).toEqual([
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.authority', metadataType: 'authority', schemaName: 'authority' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.menu', metadataType: 'menu', schemaName: 'menu' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.devMenu', metadataType: 'dev-menu', schemaName: 'dev-menu' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.uiModel', metadataType: 'ui-model', schemaName: 'ui-model' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.uiSchema', metadataType: 'ui-schema', schemaName: 'ui-schema' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.business', metadataType: 'business', schemaName: 'business' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.logicflow', metadataType: 'logicflow', schemaName: 'logicflow' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.approvalWorkflow', metadataType: 'approval-workflow', schemaName: 'approval-workflow' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.fieldType', metadataType: 'field-type', schemaName: 'field-type' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.flowNode', metadataType: 'flow-node', schemaName: 'flow-node' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.widget', metadataType: 'widget', schemaName: 'widget' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.microWeb', metadataType: 'micro-web', schemaName: 'micro-web' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.rpc', metadataType: 'rpc', schemaName: 'rpc' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.rpcAuth', metadataType: 'rpc-auth', schemaName: 'rpc-auth' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.webapi', metadataType: 'webapi', schemaName: 'webapi' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.notification', metadataType: 'notification', schemaName: 'notification' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.businessLog', metadataType: 'business-log', schemaName: 'business-log' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.dataInit', metadataType: 'data-init', schemaName: 'data-init' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.mq', metadataType: 'mq', schemaName: 'mq' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.schedule', metadataType: 'schedule', schemaName: 'schedule' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.i18n', metadataType: 'i18n', schemaName: 'i18n' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.dataDict', metadataType: 'data-dict', schemaName: 'data-dict' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.position', metadataType: 'position', schemaName: 'position' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.errorCode', metadataType: 'error-code', schemaName: 'error-code' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.appMetadata', metadataType: 'app-metadata', schemaName: 'app-metadata' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.modulesDiff', metadataType: 'modules-diff', schemaName: 'modules-diff' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.appModule', metadataType: 'app-module', schemaName: 'app-module' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.configuration', metadataType: 'configuration', schemaName: 'configuration' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.configurationGroup', metadataType: 'configuration-group', schemaName: 'configuration-group' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.configurationItem', metadataType: 'configuration-item', schemaName: 'configuration-item' }),
      expect.objectContaining({ viewType: 'tidestack.metadataEditor.configurationGroupItem', metadataType: 'configuration-group-item', schemaName: 'configuration-group-item' }),
    ])
    expect(metadataEditorRegistrationDefinitions.map((definition) => definition.fileMatchPatterns)).toEqual([
      ['*.authority.json', '**/resources/META-INF/ouroboros/authority/**/*.json'],
      ['*.menu.json', '**/resources/META-INF/ouroboros/menu-model/**/*.json'],
      ['*.dev-menu.json', '**/resources/META-INF/ouroboros/dev-menu-model/**/*.json'],
      ['*.ui-model.json', '**/resources/META-INF/ouroboros/ui-model/**/*.json'],
      ['*.ui-schema.json', '**/resources/META-INF/ouroboros/ui-schema/**/*.json'],
      ['*.business.json'],
      ['*.logicflow.json'],
      ['*.workflow.json'],
      ['*.field-type.json'],
      ['*.flow-node.json'],
      ['*.widget.json'],
      ['*.micro-web.json'],
      ['*.rpc.json', '**/resources/META-INF/ouroboros/rpc-model/**/*.rpc.json'],
      ['*.rpc.auth.json', '**/resources/META-INF/ouroboros/rpc-model/**/*.rpc.auth.json'],
      ['*.webapi.json'],
      ['*.notification.json'],
      ['*.business-log.json'],
      ['*.data-init.json'],
      ['*.mq.json'],
      ['*.schedule.json'],
      ['*.i18n.json'],
      ['*.dict.json'],
      ['*.position.json'],
      ['error-codes.error.json', '**/src/main/metadata/error-codes.error.json', '**/resources/META-INF/ouroboros/metadata/error-codes.error.json'],
      ['app.metadata.json', '**/src/main/metadata/app.metadata.json', '**/resources/META-INF/ouroboros/metadata/app.metadata.json', '**/resources/META-INF/ouroboros/app.metadata.json'],
      ['modules.diff.json', '**/src/main/metadata/modules.diff.json', '**/resources/META-INF/ouroboros/metadata/modules.diff.json', '**/resources/META-INF/ouroboros/modules.diff.json'],
      ['app-modules.json', '**/src/main/metadata/app-modules.json', '**/resources/META-INF/ouroboros/metadata/app-modules.json', '**/resources/META-INF/ouroboros/app-modules.json'],
      [
        'configurations.json',
        '**/src/main/metadata/configurations.json',
        '**/src/main/metadata/configuration.json',
        '**/resources/META-INF/ouroboros/metadata/configurations.json',
        '**/resources/META-INF/ouroboros/metadata/configuration.json',
        '**/resources/META-INF/ouroboros/configurations.json',
        '**/resources/META-INF/ouroboros/configuration.json',
      ],
      ['configuration-groups.json', '**/src/main/metadata/configuration-groups.json', '**/resources/META-INF/ouroboros/metadata/configuration-groups.json', '**/resources/META-INF/ouroboros/configuration-groups.json'],
      ['*.config.json'],
      ['*.config-group.json'],
    ])
    expect(newMetadataFileCommandDefinitions.map((definition) => definition.command)).toEqual([
      'tidestack.metadata.newAuthorityFile',
      'tidestack.metadata.newMenuFile',
      'tidestack.metadata.newDevMenuFile',
      'tidestack.metadata.newUiModelFile',
      'tidestack.metadata.newUiSchemaFile',
      'tidestack.metadata.newBusinessFile',
      'tidestack.metadata.newLogicflowFile',
      'tidestack.metadata.newApprovalWorkflowFile',
      'tidestack.metadata.newApprovalFormFile',
      'tidestack.metadata.newFieldTypeFile',
      'tidestack.metadata.newFlowNodeFile',
      'tidestack.metadata.newWidgetFile',
      'tidestack.metadata.newMicroWebFile',
      'tidestack.metadata.newRpcFile',
      'tidestack.metadata.newRpcAuthFile',
      'tidestack.metadata.newWebapiFile',
      'tidestack.metadata.newNotificationFile',
      'tidestack.metadata.newBusinessLogFile',
      'tidestack.metadata.newDataInitFile',
      'tidestack.metadata.newMqFile',
      'tidestack.metadata.newScheduleFile',
      'tidestack.metadata.newI18nFile',
      'tidestack.metadata.newDataDictFile',
      'tidestack.metadata.newPositionFile',
      'tidestack.metadata.newErrorCodeFile',
      'tidestack.metadata.newAppMetadataFile',
      'tidestack.metadata.newModulesDiffFile',
      'tidestack.metadata.newAppModuleFile',
      'tidestack.metadata.newConfigurationFile',
      'tidestack.metadata.newConfigurationGroupFile',
      'tidestack.metadata.newConfigurationItemFile',
      'tidestack.metadata.newConfigurationGroupItemFile',
    ])
  })

  it('creates Chinese AMIS form labels and option labels by default', () => {
    const authoritySchema = getContributionByType('authority')?.createEditorSchema({ uri: 'auth.authority.json', document: {} }) as { body: Array<{ label?: string }> }
    const menuSchema = getContributionByType('menu')?.createEditorSchema({ uri: 'main.menu.json', document: {} }) as { body: Array<{ name?: string, label?: string, options?: unknown[] }> }
    const devMenuSchema = getContributionByType('dev-menu')?.createEditorSchema({ uri: 'dev.dev-menu.json', document: {} }) as { body: Array<{ name?: string, label?: string, options?: unknown[] }> }

    expect(authoritySchema.body.map((item) => item.label)).toEqual([
      '完整名称',
      '标题',
      '排序',
      '关联接口',
      '关联界面',
      '关联菜单',
    ])
    expect(menuSchema.body.map((item) => item.label)).toEqual([
      '完整名称',
      '标题',
      '访问路径',
      '图标',
      '位置',
      '显示方式',
      '排序',
    ])
    expect(menuSchema.body.find((item) => item.name === 'showMode')?.options).toEqual([
      { label: '标签页', value: 'tab' },
      { label: '全屏', value: 'fullScreen' },
      { label: '新窗口', value: 'newWindow' },
    ])
    expect(devMenuSchema.body.find((item) => item.name === 'stage')?.options).toEqual([
      { label: '欢迎页', value: 'welcome' },
      { label: '开发', value: 'dev' },
      { label: '全部', value: 'all' },
    ])
  })

  it('provides valid create-file templates for every metadata type', async () => {
    const expectedTemplateFiles = [
      'example.authority.json',
      'example.menu.json',
      'example.dev-menu.json',
      'example.ui-model.json',
      'example.ui-schema.json',
      'example.business.json',
      'example.logicflow.json',
      'example.workflow.json',
      'example.approval-form.json',
      'example.field-type.json',
      'example.flow-node.json',
      'example.widget.json',
      'example.micro-web.json',
      'example.rpc.json',
      'example.rpc.auth.json',
      'example.webapi.json',
      'example.notification.json',
      'example.business-log.json',
      'example.data-init.json',
      'example.mq.json',
      'example.schedule.json',
      'example.i18n.json',
      'example.dict.json',
      'example.position.json',
      'error-codes.error.json',
      'app.metadata.json',
      'modules.diff.json',
      'app-modules.json',
      'configurations.json',
      'configuration-groups.json',
      'example.config.json',
      'example.config-group.json',
    ]

    expect(metadataTypeContributions.map((contribution) => contribution.fileTemplate?.defaultFileName)).toEqual(expectedTemplateFiles)

    for (const contribution of metadataTypeContributions) {
      const uri = `/repo/src/main/metadata/${contribution.fileTemplate?.defaultFileName}`
      const templateText = serializeMetadataFileTemplate(contribution)
      const bridge = new InMemoryMetadataFileBridge({ [uri]: templateText })
      const host = createMetadataEditorHost(bridge)

      expect(mapMetadataSourcePath(uri)?.type).toBe(contribution.type)
      await expect(host.loadDocument(uri)).resolves.toMatchObject({
        type: contribution.type,
        diagnostics: [],
      })
    }
  })

  it('recognizes custom form paths and applies the shared host validation', async () => {
    const uri = '/repo/src/main/metadata/approval-form/purchase.approval-form.json'
    const document = { formatVersion: 1, name: 'purchase', title: '采购申请', fields: [] }
    const bridge = new InMemoryMetadataFileBridge({ [uri]: JSON.stringify(document) })
    const host = createMetadataEditorHost(bridge)
    expect(mapMetadataSourcePath(uri)?.type).toBe('approval-form')
    await expect(host.loadDocument(uri)).resolves.toMatchObject({ type: 'approval-form', diagnostics: [] })
    const invalid = new InMemoryMetadataFileBridge({ [uri]: JSON.stringify({ ...document, fields: 'invalid' }) })
    const result = await createMetadataEditorHost(invalid).loadDocument(uri)
    expect(result.diagnostics).toContainEqual(expect.objectContaining({ severity: 'error', path: 'fields' }))
  })

  it('applies create-file field values through the shared template contract', () => {
    const uiSchema = getContributionByType('ui-schema')!
    const targetPath = 'src/main/metadata/admin/user-list.ui-schema.json'
    const nameField = uiSchema.fileTemplate?.fields?.find((field) => field.name === 'name')

    expect(nameField?.labelRemark).toBe('按低代码页面命名习惯填写点分名称，例如 Admin.UserList；系统运行时会映射成 /admin/user-list。')

    expect(createMetadataFileTemplateDefaultValues(uiSchema, targetPath)).toMatchObject({
      name: 'Admin.UserList',
      title: 'User List',
    })

    expect(JSON.parse(serializeMetadataFileTemplate(uiSchema, {
      name: 'Admin.UserList',
      title: '用户列表',
      description: '用户列表页面',
    }))).toEqual({
      name: 'Admin.UserList',
      title: '用户列表',
      description: '用户列表页面',
      schema: { type: 'page', body: [] },
    })
  })

  it('plans metadata file creation through the shared creation module', () => {
    const menu = getContributionByType('menu')!
    const appModule = getContributionByType('app-module')!
    const uiSchema = getContributionByType('ui-schema')!
    const localizedUiSchema = localizeMetadataTypeContribution(uiSchema, 'en-US')
    const localizedAppModule = localizeMetadataTypeContribution(appModule, 'en-US')
    const targetPath = 'src/main/metadata/admin/user-list.ui-schema.json'

    expect(defaultMetadataFilePath(undefined, menu)).toBe('src/main/metadata/example.menu.json')
    expect(defaultMetadataFilePath('src/main/metadata/admin', menu)).toBe('src/main/metadata/admin/example.menu.json')
    expect(defaultMetadataFilePath('custom/admin', menu)).toBe('custom/admin/example.menu.json')
    expect(defaultMetadataFilePath('custom/admin', appModule)).toBe('src/main/metadata/app-modules.json')
    expect(metadataDirectoryScopeForPath('module/src/main/metadata')).toBe('root')
    expect(metadataDirectoryScopeForPath('module/src/main/metadata/admin')).toBe('nested')
    expect(metadataSourceRootForDirectory('module/src/main/java')).toBe('module/src/main/metadata')
    expect(metadataCreationDirectoryForPath('module/src/main/java/com/acme', menu, 'module')).toBe('module/src/main/metadata')
    expect(metadataCreationDirectoryForPath('module/src/main/metadata/admin', menu, 'module')).toBe('module/src/main/metadata/admin')
    expect(metadataCreationDirectoryForPath('module/src/main/metadata/admin', appModule, 'module')).toBe('module/src/main/metadata')

    expect(normalizeMetadataFilePath('admin/user-list', uiSchema, undefined, 'zh-CN')).toEqual({ path: 'src/main/metadata/admin/user-list.ui-schema.json' })
    expect(normalizeMetadataFilePath('user-list', uiSchema, 'src/main/metadata/admin', 'zh-CN')).toEqual({ path: targetPath })
    expect(normalizeMetadataFilePath('configuration', localizedAppModule, undefined, 'en-US').error).toBe('App Modules must be src/main/metadata/app-modules.json.')
    expect(normalizeMetadataFilePath('app-modules.json', appModule, undefined, 'zh-CN')).toEqual({ path: 'src/main/metadata/app-modules.json' })
    expect(normalizeMetadataFilePath('app-modules.json', appModule, 'module/src/main/metadata/admin', 'zh-CN')).toEqual({ path: 'module/src/main/metadata/app-modules.json' })

    const plan = createMetadataFileCreationPlan(localizedUiSchema, targetPath)
    expect(plan.targetPath).toBe(targetPath)
    expect(plan.templateValues).toMatchObject({ name: 'Admin.UserList', title: 'User List' })
    expect(JSON.parse(plan.text)).toMatchObject({ name: 'Admin.UserList', title: 'User List' })
    expect(metadataFilePathPrompt(localizedUiSchema, 'en-US')).toBe('New UI Schema file name')
    expect(metadataTemplateFieldPrompt(localizedUiSchema, localizedUiSchema.fileTemplate!.fields![0], 'en-US')).toContain('Admin.UserList')

    const flow = createMetadataFileCreationFlowModel(localizedUiSchema, {
      selectedDirectory: 'src/main/metadata/admin',
      locale: 'en-US',
    })
    expect(flow).toMatchObject({
      metadataType: 'ui-schema',
      displayName: 'UI Schema',
      creationDirectory: 'src/main/metadata/admin',
      defaultPath: 'src/main/metadata/admin/example.ui-schema.json',
      defaultPathInput: 'example.ui-schema.json',
      pathInputLocked: false,
      pathPrompt: 'New UI Schema file name',
    })
    expect(metadataFileCreationInputForPath(targetPath, flow.creationDirectory)).toBe('user-list.ui-schema.json')
    expect(flow.fields.map((field) => [field.name, field.defaultValue])).toEqual([
      ['name', 'Admin.Example'],
      ['title', 'Example'],
      ['description', '示例 UI Schema'],
    ])
    expect(normalizeMetadataFileCreationPath(localizedUiSchema, flow, 'user-list', 'en-US')).toEqual({ path: targetPath })
    const flowPlan = createMetadataFileCreationPlanFromFlow(localizedUiSchema, flow, 'user-list', {
      name: 'Admin.UserList',
      title: 'User List',
      description: 'Users page',
    }, 'en-US')
    expect(flowPlan.plan?.targetPath).toBe(targetPath)
    expect(JSON.parse(flowPlan.plan!.text)).toMatchObject({
      name: 'Admin.UserList',
      title: 'User List',
      description: 'Users page',
    })
    expect(createMetadataFileCreationPlanFromFlow(localizedUiSchema, flow, 'user-list', {
      name: '',
      title: 'User List',
      description: 'Users page',
    }, 'en-US').error).toBe('Name is required.')

    const fixedFileFlow = createMetadataFileCreationFlowModel(localizedAppModule, {
      selectedDirectory: 'src/main/metadata/admin',
      locale: 'en-US',
    })
    expect(fixedFileFlow).toMatchObject({
      creationDirectory: 'src/main/metadata',
      defaultPath: 'src/main/metadata/app-modules.json',
      defaultPathInput: 'app-modules.json',
      pathInputLocked: true,
    })
  })

  it('loads metadata creation path and default-value rules from the shared contract', () => {
    expect(metadataCreationContract.pathRules).toMatchObject({
      relativeCurrentDirectoryPrefix: './',
      jsonExtension: '.json',
      fallbackFileName: 'metadata.json',
      sourceMainSegments: ['src', 'main'],
    })
    expect(metadataCreationContract.templateDefaultRules).toMatchObject({
      fixedFileNameStem: 'example',
      fallbacks: {
        'camel-name': 'example',
        'dot-name': 'Example',
        'slash-path': 'example',
        title: 'Example',
      },
    })
  })

  it('matches the shared metadata creation path contract cases', () => {
    for (const pathCase of metadataCreationCases.pathCases) {
      const contribution = getContributionByType(pathCase.metadataType)!
      const creationDirectory = metadataCreationDirectoryForPath(pathCase.selectedDirectory, contribution)

      expect(creationDirectory, pathCase.name).toBe(pathCase.expectedCreationDirectory)
      const defaultPath = defaultMetadataFilePath(creationDirectory, contribution)
      expect(defaultPath, pathCase.name).toBe(pathCase.expectedDefaultInputPath)
      expect(metadataFileCreationInputForPath(defaultPath, creationDirectory), pathCase.name).toBe(pathCase.expectedDefaultPathInput)

      const result = normalizeMetadataFilePath(pathCase.input, contribution, creationDirectory, 'zh-CN')
      if (pathCase.expectedPath) {
        expect(result, pathCase.name).toEqual({ path: pathCase.expectedPath })
      } else {
        expect(result.error, pathCase.name).toBeTruthy()
        pathCase.expectedErrorContains?.forEach((expected) => {
          expect(result.error, pathCase.name).toContain(expected)
        })
      }
    }
  })

  it('matches the shared metadata creation template contract cases', () => {
    for (const templateCase of metadataCreationCases.templateDefaultCases) {
      const contribution = getContributionByType(templateCase.metadataType)!

      expect(createMetadataFileTemplateDefaultValues(contribution, templateCase.targetPath), templateCase.name)
        .toMatchObject(templateCase.expectedValues)
    }

    for (const templateCase of metadataCreationCases.templateSerializationCases) {
      const contribution = getContributionByType(templateCase.metadataType)!

      expect(JSON.parse(serializeMetadataFileTemplate(contribution, templateCase.values)), templateCase.name)
        .toEqual(templateCase.expectedDocument)
    }
  })

  it('provides structured controls for the new metadata types', () => {
    const uiModelSchema = getContributionByType('ui-model')?.createEditorSchema({ uri: 'main.ui-model.json', document: {} }) as { body: Array<{ name?: string }> }
    const uiSchemaSchema = getContributionByType('ui-schema')?.createEditorSchema({ uri: 'main.ui-schema.json', document: {} }) as { body: Array<{ name?: string }> }
    const businessSchema = getContributionByType('business')?.createEditorSchema({ uri: 'order.business.json', document: {} }) as { type?: string, id?: string, body?: unknown[] }
    const logicflowSchema = getContributionByType('logicflow')?.createEditorSchema({ uri: 'order.logicflow.json', document: {} }) as { metadataRootField?: string, body?: Array<{ type?: string, name?: string, nodeDefineApi?: string }> }
    const approvalWorkflowSchema = getContributionByType('approval-workflow')?.createEditorSchema({ uri: 'purchase.workflow.json', document: {} }) as { metadataRootField?: string, body?: Array<{ type?: string, name?: string, candidateSourceApi?: string, formFieldSourceApi?: string }> }
    const rpcSchema = getContributionByType('rpc')?.createEditorSchema({ uri: 'open-api.rpc.json', document: {} }) as { body: Array<{ name?: string }> }
    const rpcAuthSchema = getContributionByType('rpc-auth')?.createEditorSchema({ uri: 'shared.rpc.auth.json', document: {} }) as { body: Array<{ name?: string }> }
    const webapiSchema = getContributionByType('webapi')?.createEditorSchema({ uri: 'orders.webapi.json', document: {} }) as { body: Array<{ name?: string }> }
    const notificationSchema = getContributionByType('notification')?.createEditorSchema({ uri: 'welcome.notification.json', document: {} }) as { body: Array<{ name?: string }> }
    const businessLogSchema = getContributionByType('business-log')?.createEditorSchema({ uri: 'create-order.business-log.json', document: {} }) as { body: Array<{ name?: string }> }
    const dataInitSchema = getContributionByType('data-init')?.createEditorSchema({ uri: 'status.data-init.json', document: {} }) as { body: Array<{ name?: string }> }
    const mqSchema = getContributionByType('mq')?.createEditorSchema({ uri: 'orders-created.mq.json', document: {} }) as { body: Array<{ name?: string }> }
    const scheduleSchema = getContributionByType('schedule')?.createEditorSchema({ uri: 'daily-sync.schedule.json', document: {} }) as { body: Array<{ name?: string }> }
    const i18nSchema = getContributionByType('i18n')?.createEditorSchema({ uri: 'zh-cn.i18n.json', document: {} }) as { body: Array<{ name?: string }> }
    const dataDictSchema = getContributionByType('data-dict')?.createEditorSchema({ uri: 'customer-status.dict.json', document: {} }) as { body: Array<{ name?: string }> }
    const positionSchema = getContributionByType('position')?.createEditorSchema({ uri: 'member.position.json', document: {} }) as { body: Array<{ name?: string }> }
    const appModuleSchema = getContributionByType('app-module')?.createEditorSchema({ uri: 'app-modules.json', document: [] }) as { body: Array<{ name?: string }> }
    const appMetadataSchema = getContributionByType('app-metadata')?.createEditorSchema({ uri: 'app.metadata.json', document: {} }) as { body: Array<{ name?: string }> }
    const modulesDiffSchema = getContributionByType('modules-diff')?.createEditorSchema({ uri: 'modules.diff.json', document: [] }) as { body: Array<{ name?: string }> }
    const configurationSchema = getContributionByType('configuration')?.createEditorSchema({ uri: 'configurations.json', document: [] }) as { body: Array<{ name?: string }> }
    const groupSchema = getContributionByType('configuration-group')?.createEditorSchema({ uri: 'configuration-groups.json', document: [] }) as { body: Array<{ name?: string }> }

    expect(uiModelSchema.body.map((item) => item.name)).toEqual(['path', 'type', 'title', 'icon', 'description'])
    expect(uiSchemaSchema.body.map((item) => item.name)).toContain('body')
    expect(businessSchema).toMatchObject({ type: 'form', id: 'edit-business-model-form' })
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('fields-n-relations'))
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('workflow'))
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('workflow.bindings[0].processId'))
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('businessModelFeaturesPanel'))
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('自动生成并列归档模型'))
    expect(JSON.stringify(businessSchema)).toEqual(expect.stringContaining('自动生成并列历史模型'))
    expect(collectStringValidatePaths(businessSchema)).toEqual([])
    const businessShell = businessSchema.body[0] as { className?: string, body: Array<Record<string, unknown>> }
    expect(businessShell.body[1].tabs).toEqual(expect.arrayContaining([
      expect.objectContaining({ title: '行为契约' }),
    ]))
    expect(businessShell.className).toBe('h-full max-h-full p-0 flex flex-col')
    expect(businessShell.body[0]).toMatchObject({
      type: 'panel',
      className: 'p-0 flex-grow-0',
      header: {
        type: 'wrapper',
      },
    })
    expect((businessShell.body[0].body as Array<Record<string, unknown>>).map((item) => item.type)).toEqual([
      'fieldSet',
      'hidden',
      'hidden',
      'hidden',
      'hidden',
    ])
    expect((businessShell.body[0].body as Array<Record<string, unknown>>)[0]).toMatchObject({
      type: 'fieldSet',
      collapsed: true,
      collapsable: true,
      titlePosition: 'bottom',
      showArrow: false,
    })
    const basicFieldSet = (businessShell.body[0].body as Array<{ body?: Array<Record<string, unknown>> }>)[0]
    expect(basicFieldSet.body?.[0]).toMatchObject({
      type: 'group',
      className: 'mt-4',
      body: [
        expect.objectContaining({
          name: 'title',
          label: '模型标题',
        }),
        expect.objectContaining({
          type: 'ouroboros-icon-picker',
          label: '图标',
        }),
      ],
    })
    const basicRow = basicFieldSet.body?.[0] as { body?: Array<Record<string, unknown>> }
    expect(basicRow.body?.[0]).not.toHaveProperty('className')
    expect(basicRow.body?.[0]).not.toHaveProperty('labelWidth')
    expect(basicRow.body?.[1]).not.toHaveProperty('className')
    expect(JSON.stringify(businessShell.body[0])).toEqual(expect.stringContaining('更多'))
    expect(JSON.stringify(businessShell.body[0])).toEqual(expect.stringContaining('收起'))
    expect(businessShell.body[1]).toMatchObject({
      type: 'tabs',
      className: 'flex flex-col flex-grow h-full',
      style: { height: '200px' },
      contentClassName: 'flex-grow h-full overflow-y-auto',
      linksClassName: 'flex justify-center',
    })
    expect(JSON.stringify(businessShell)).not.toEqual(expect.stringContaining('business-model-editor-shell'))
    expect(JSON.stringify(businessShell)).not.toEqual(expect.stringContaining('business-model-tabs'))
    expect(logicflowSchema).toMatchObject({
      metadataRootField: '__metadataDocument',
      body: [{
        type: 'logic-flow-editor',
        name: '__metadataDocument',
        nodeDefineApi: 'ide://editor-integrations/logicflow/node-defines',
      }],
    })
    expect(approvalWorkflowSchema).toMatchObject({
      metadataRootField: '__metadataDocument',
      body: [{
        type: 'workflow-editor',
        name: '__metadataDocument',
        candidateSourceApi: 'ide://editor-integrations/approval-workflow/candidate',
        formFieldSourceApi: 'ide://editor-integrations/approval-workflow/form-fields',
      }],
    })
    expect(rpcSchema.body.map((item) => item.name)).toEqual(['name', 'title', 'group', 'type', 'isEnabled', 'authCredential', 'description'])
    expect(rpcAuthSchema.body.map((item) => item.name)).toEqual(['name', 'title', 'type', 'isEnabled', 'description'])
    expect(webapiSchema.body.map((item) => item.name)).toEqual(['method', 'path', 'scope', 'handlerType', 'summary', 'description', 'operationId', 'tags', 'deprecated', 'parameters', 'requestBody', 'responses', 'security', 'servers', 'externalDocs', 'extraProps'])
    expect(notificationSchema.body.map((item) => item.name)).toEqual(['name', 'title', 'isEnabled'])
    expect(businessLogSchema.body.map((item) => item.name)).toEqual(['name', 'httpMethod', 'url', 'moduleName', 'operation', 'level', 'businessKeyName', 'summaryTemplate', 'shouldSaveResponseBody'])
    expect(dataInitSchema.body.map((item) => item.name)).toEqual(['id', 'version'])
    expect(mqSchema.body.map((item) => item.name)).toEqual(['bindingType', 'bindingName', 'destination', 'binder', 'group', 'flowName', 'contentType', 'broadcast', 'routingKey', 'description'])
    expect(scheduleSchema.body.map((item) => item.name)).toEqual(['name', 'cron', 'flowName', 'data', 'description'])
    expect(i18nSchema.body.map((item) => item.name)).toEqual(['locale', 'name', 'version', 'enabled', 'isDefault', 'direction', 'messages'])
    expect(dataDictSchema.body.map((item) => item.name)).toEqual(['name', 'title', 'type', 'group', 'editable', 'treeMode', 'items'])
    expect(positionSchema.body.map((item) => item.name)).toEqual(['key', 'name', 'level', 'description'])
    expect(appModuleSchema.body.map((item) => item.name)).toEqual(['items'])
    expect(appMetadataSchema.body.map((item) => item.name)).toEqual(['name', 'title', 'icon', 'logo', 'homePage', 'defaultTheme', 'description'])
    expect(modulesDiffSchema.body.map((item) => item.name)).toEqual(['items'])
    expect(configurationSchema.body.map((item) => item.name)).toEqual(['items'])
    expect(groupSchema.body.map((item) => item.name)).toEqual(['items'])

    const configurationItems = (configurationSchema.body[0] as { items?: Array<{ name?: string, type?: string }> }).items ?? []
    expect(configurationItems.find((item) => item.name === 'defaultValue')).toMatchObject({ type: 'json-editor' })
  })

  it('removes unused generated AMIS ids while keeping styled or referenced ids', () => {
    const schema = {
      type: 'page',
      id: 'u:page',
      body: [
        { type: 'input-text', id: 'u:unused', name: 'title' },
        { type: 'button', id: 'u:styled', themeCss: { baseControlClassName: { color: 'red' } } },
        { type: 'button', id: 'custom-id' },
        { type: 'container', id: 'u:referenced' },
        { type: 'action', componentId: 'u:referenced' },
      ],
      data: { id: 'u:data-id' },
    }

    expect(getPureSchema(schema)).toEqual({
      type: 'page',
      body: [
        { type: 'input-text', name: 'title' },
        { type: 'button', id: 'u:styled', themeCss: { baseControlClassName: { color: 'red' } } },
        { type: 'button', id: 'custom-id' },
        { type: 'container', id: 'u:referenced' },
        { type: 'action', componentId: 'u:referenced' },
      ],
      data: { id: 'u:data-id' },
    })
  })

  it('orders visual editor schema changes through the base AMIS key order helper', () => {
    const previous = {
      type: 'page',
      title: 'Legacy',
      body: [{ type: 'tpl', tpl: 'old' }],
      className: 'legacy',
    }

    const next = createVisualEditorChangeValue(previous, {
      body: [{ tpl: 'new', type: 'tpl', id: 'u:auto' }],
      aside: [],
      type: 'page',
    }) as Record<string, unknown>

    expect(Object.keys(next)).toEqual(['type', 'body', 'aside'])
    expect(next).toEqual({
      type: 'page',
      body: [{ type: 'tpl', tpl: 'new' }],
      aside: [],
    })
  })

  it('keeps source editor schema key order when code panel changes', () => {
    const previous = {
      type: 'page',
      title: 'Legacy',
      body: [{ type: 'tpl', tpl: 'old' }],
    }

    const next = createVisualEditorChangeValue(previous, {
      body: [{ tpl: 'new', type: 'tpl', id: 'u:auto' }],
      title: 'Changed',
      type: 'page',
    }, 'code') as Record<string, unknown>

    expect(Object.keys(next)).toEqual(['body', 'title', 'type'])
    expect(Object.keys((next.body as Array<Record<string, unknown>>)[0])).toEqual(['tpl', 'type'])
    expect(next).toEqual({
      body: [{ tpl: 'new', type: 'tpl' }],
      title: 'Changed',
      type: 'page',
    })
  })
})

describe('TideStack Develop Config contract', () => {
  it('matches the shared config contract shape', () => {
    expect(developConfigContract.schemaVersion).toBe(developConfigCases.contract.schemaVersion)
    expect(developConfigContract.project.path).toBe(developConfigCases.contract.projectPath)
    expect(developConfigContract.local.path).toBe(developConfigCases.contract.localPath)
    expect(developConfigContract.project.fields).toEqual(developConfigCases.contract.projectFields)
    expect(developConfigContract.local.fields).toEqual(developConfigCases.contract.localFields)
  })

  it('matches the shared project config read contract cases', () => {
    for (const readCase of developConfigCases.projectReadCases) {
      expect(projectDevelopConfigFromDocument(readCase.document, developConfigContract, readCase.legacy), readCase.name)
        .toEqual(readCase.expected)
    }
  })

  it('matches the shared local config read contract cases', () => {
    for (const readCase of developConfigCases.localReadCases) {
      expect(localDevelopConfigFromDocument(readCase.document, developConfigContract, readCase.legacy), readCase.name)
        .toEqual(readCase.expected)
    }
  })

  it('matches the shared config document write contract cases', () => {
    for (const documentCase of developConfigCases.projectDocumentCases) {
      const document = projectDevelopConfigDocument(documentCase.existing, documentCase.config, developConfigContract)

      expect(Object.keys(document), documentCase.name).toEqual(documentCase.expectedKeys)
      expect(document, documentCase.name).toEqual(documentCase.expected)
    }

    for (const documentCase of developConfigCases.localDocumentCases) {
      const document = localDevelopConfigDocument(documentCase.existing, documentCase.config, developConfigContract)

      expect(Object.keys(document), documentCase.name).toEqual(documentCase.expectedKeys)
      expect(document, documentCase.name).toEqual(documentCase.expected)
    }
  })

  it('matches the shared config import document contract cases', () => {
    for (const importCase of developConfigCases.importDocumentCases) {
      expect(developConfigImportDocument(importCase.document), importCase.name)
        .toEqual(importCase.expected)
    }
  })

  it('matches the shared effective config contract cases', () => {
    for (const effectiveCase of developConfigCases.effectiveCases) {
      expect(effectiveDevelopConfig(effectiveCase.project, effectiveCase.local), effectiveCase.name)
        .toEqual(effectiveCase.expected)
    }
  })
})

describe('TideStack Develop capability manifest', () => {
  it('keeps shared capability availability explicit per IDE', () => {
    expect(tideStackDevelopCapabilities.map((capability) => capability.id)).toEqual([
      'metadata-visual-editor',
      'metadata-new-file-templates',
      'tidestack-develop-config',
      'deploy-and-debug',
      'project-templates',
    ])
    expect(capabilityStatus('deploy-and-debug', 'intellij')).toBe('available')
    expect(capabilityStatus('deploy-and-debug', 'vscode')).toBe('planned')
    expect(capabilitiesForIde('vscode').map((capability) => capability.id)).toContain('metadata-visual-editor')
  })
})

describe('amis editor drag fallback', () => {
  function element(attrs: Record<string, string>, closestTarget?: unknown) {
    return {
      getAttribute(name: string) {
        return attrs[name] ?? null
      },
      closest() {
        return closestTarget ?? this
      },
    }
  }

  it('starts only after a real drag distance', () => {
    const start = { clientX: 10, clientY: 10 }

    expect(hasExceededAmisEditorFallbackDragThreshold(start, { clientX: 12, clientY: 12 })).toBe(false)
    expect(hasExceededAmisEditorFallbackDragThreshold(start, { clientX: 18, clientY: 10 })).toBe(true)
  })

  it('resolves renderer and canvas region targets from AMIS editor DOM markers', () => {
    const renderer = element({ 'data-dnd-id': 'tpl' })
    const rendererChild = element({}, renderer)
    const region = element({ 'data-region-host': 'u:page', 'data-region': 'body' })
    const regionChild = element({}, region)

    expect(getAmisEditorFallbackRendererId(rendererChild)).toBe('tpl')
    expect(getAmisEditorFallbackDropTarget(regionChild)).toEqual({ id: 'u:page', region: 'body' })
  })

  it('enables the fallback for JCEF-like browser hosts', () => {
    expect(shouldUseAmisEditorDragFallback({ navigator: { userAgent: 'Mozilla/5.0 Chrome/120 Safari/537.36' } })).toBe(false)
    expect(shouldUseAmisEditorDragFallback({ navigator: { userAgent: 'Mozilla/5.0 JCEF Chrome/120' } })).toBe(true)
    expect(shouldUseAmisEditorDragFallback({ navigator: { userAgent: 'Mozilla/5.0 Chrome/120' }, cefQuery: () => undefined })).toBe(true)
  })
})

describe('metadata webview html', () => {
  it('allows extension-local AMIS chunks while keeping inline bootstrap nonce-bound', () => {
    const html = createMetadataEditorWebviewHtml({
      template: metadataEditorWebviewTemplate,
      fileName: 'main.menu.json',
      nonce: 'abc123',
      cspSource: 'vscode-resource:',
      amisSdkScriptUri: 'vscode-resource:/amis-sdk/sdk.js',
      amisSdkCssUri: 'vscode-resource:/amis-sdk/sdk.css',
      amisThemeCssUri: 'vscode-resource:/amis-sdk/cxd.css',
      amisDarkThemeCssUri: 'vscode-resource:/amis-sdk/dark.css',
      xlsxRuntimeScriptUri: 'vscode-resource:/xlsx/xlsx.full.min.js',
      runtimeScriptUri: 'vscode-resource:/runtime/metadata-editor-runtime.js',
      hostScript: 'window.OuroborosMetadataEditorHost = { initialState: {}, transport: {} };',
    })

    expect(html).toContain("script-src vscode-resource: 'nonce-abc123'")
    expect(html).toContain('font-src vscode-resource: data:')
    expect(html).toContain('nonce="abc123" src="vscode-resource:/amis-sdk/sdk.js"')
    expect(html).toContain('nonce="abc123" src="vscode-resource:/xlsx/xlsx.full.min.js"')
    expect(html).toContain('nonce="abc123" src="vscode-resource:/runtime/metadata-editor-runtime.js"')
    expect(html).toContain('<html lang="zh-CN">')
    expect(html).toContain('<body class="amis-scope">')
    expect(html).toContain('id="metadata-amis-root"')
    expect(html).toContain('id="metadata-import-toolbar"')
    expect(html).toContain('id="metadata-diagnostics-indicator"')
    expect(html).toContain('id="metadata-diagnostics-message"')
    expect(html).toContain('id="metadata-diagnostics-close"')
    expect(html).toContain('id="metadata-amis-theme-cxd"')
    expect(html).toContain('id="metadata-amis-theme-dark"')
    expect(html).toContain('id="__SVG_SPRITE_NODE__"')
    expect(html).toContain('body.metadata-theme-light { color-scheme: light; }')
    expect(html).toContain('metadata-embedded-logicflow-editor')
    expect(html).toContain('metadata-embedded-approval-workflow-editor')
    expect(html).toContain('body.metadata-amis-editor-light')
    expect(html).toContain('body.metadata-amis-editor-light .cxd-Modal { overflow-y: auto;')
    expect(html).toContain('body.metadata-amis-editor-light .cxd-Modal-content,')
    expect(html).toContain('body.metadata-amis-editor-light .cxd-Modal-body { max-height: none; overflow: visible; }')
    expect(html).toContain('body.metadata-amis-editor-light .cxd-ContextMenu-menu::-webkit-scrollbar-thumb')
    expect(html).toContain('#metadata-amis-root .amis-routes-wrapper { width: 100%; min-height: 100%; box-sizing: border-box; }')
    expect(html).toContain('#metadata-amis-root .dark-Panel-body { width: 100%; box-sizing: border-box; }')
    expect(html).toContain('#metadata-amis-root > .amis-scope > .cxd-Panel > .cxd-Panel-body')
    expect(html).toContain('#metadata-amis-root fieldset.cxd-Collapse > legend .cxd-TplField,')
    expect(html).toContain('#metadata-amis-root fieldset.dark-Collapse > legend .dark-TplField')
    expect(html).toContain('#metadata-amis-root .cxd-Form-item--horizontal:has(> .cxd-Form-value > .cxd-Form-group--hor) > .cxd-Form-label,')
    expect(html).toContain('#metadata-amis-root .dark-Form-group--hor > .dark-Form-groupColumn:first-child > .dark-Form-item--horizontal > .dark-Form-label.dark-Form-itemColumn--4 { flex: 0 0 25%; width: 25%; max-width: 25%; }')
    expect(html).toContain('#metadata-amis-root .metadata-ide-fallback-text-control { width: 100%; max-width: 100%; }')
    expect(html).toContain('#metadata-amis-root .dark-IconPickerControl.is-focused .dark-TextControl-input')
    expect(html).toContain('#metadata-amis-root .iconPickerControl-input .IconPickerControl-clear')
    expect(html).toContain('#metadata-amis-root .icon-picker-control { position: absolute; top: 100%;')
    expect(html).toContain('#metadata-amis-root .tab-header { display: flex;')
    expect(html).toContain('#metadata-amis-root .dark-Tabs--simple > .dark-Tabs-linksContainer-wrapper > .dark-Tabs-linksContainer > .dark-Tabs-linksContainer-main > .dark-Tabs-links > li.px-0 { padding: var(--Tabs--simple-split-size); }')
    expect(html).not.toContain('business-model-title-control')
    expect(html).not.toContain('business-model-tabs-links')
    expect(html).not.toContain('business-model-editor-shell')
    expect(html).not.toContain('business-model-tabs-content')
    expect(html).not.toContain('#metadata-amis-root .px-0 { padding-left: 0 !important;')
    expect(html).not.toContain('#metadata-amis-root .flex { display: flex !important; }')
    expect(html).not.toContain('top: calc(100% + 4px)')
    expect(html).not.toContain('gap: 24px')
    expect(html).toContain('#metadata-diagnostics:hover .metadata-diagnostics-popover')
    expect(html).toContain('#metadata-diagnostics.is-pinned .metadata-diagnostics-popover')
  })

  it('allows eval only when the AMIS visual editor runtime is loaded', () => {
    const html = createMetadataEditorWebviewHtml({
      template: metadataEditorWebviewTemplate,
      fileName: 'main.ui-schema.json',
      nonce: 'abc123',
      cspSource: 'vscode-resource:',
      amisSdkScriptUri: 'vscode-resource:/amis-sdk/sdk.js',
      amisSdkCssUri: 'vscode-resource:/amis-sdk/sdk.css',
      amisThemeCssUri: 'vscode-resource:/amis-sdk/cxd.css',
      amisDarkThemeCssUri: 'vscode-resource:/amis-sdk/dark.css',
      amisEditorRuntimeScriptUri: 'vscode-resource:/runtime/metadata-editor-amis-editor.js',
      amisEditorCssUri: 'vscode-resource:/runtime/metadata-editor-amis-editor.css',
      runtimeScriptUri: 'vscode-resource:/runtime/metadata-editor-runtime.js',
      hostScript: 'window.OuroborosMetadataEditorHost = { initialState: {}, transport: {} };',
    })

    expect(html).toContain("script-src vscode-resource: 'nonce-abc123' 'unsafe-eval'")
    expect(html).toContain('nonce="abc123" src="vscode-resource:/runtime/metadata-editor-amis-editor.js"')
    expect(html).toContain('href="vscode-resource:/runtime/metadata-editor-amis-editor.css"')
  })

  it('loads embedded editor bundles and static integration sources with the required CSP', () => {
    const html = createMetadataEditorWebviewHtml({
      template: metadataEditorWebviewTemplate,
      fileName: 'order.logicflow.json',
      nonce: 'abc123',
      cspSource: 'vscode-resource:',
      amisSdkScriptUri: 'vscode-resource:/amis-sdk/sdk.js',
      amisSdkCssUri: 'vscode-resource:/amis-sdk/sdk.css',
      amisThemeCssUri: 'vscode-resource:/amis-sdk/cxd.css',
      amisDarkThemeCssUri: 'vscode-resource:/amis-sdk/dark.css',
      editorIntegrationScriptUri: 'vscode-resource:/editor-integrations/metadata-editor-integrations.js',
      editorBundleScriptUris: [
        'vscode-resource:/editor-bundles/logicflow/static/js/app.js',
        'vscode-resource:/editor-bundles/approval-workflow/static/js/app.js',
      ],
      editorBundleStyleUris: [
        'vscode-resource:/editor-bundles/logicflow/static/css/app.css',
        'vscode-resource:/editor-bundles/approval-workflow/static/css/app.css',
      ],
      runtimeScriptUri: 'vscode-resource:/runtime/metadata-editor-runtime.js',
      hostScript: 'window.OuroborosMetadataEditorHost = { initialState: {}, transport: {} };',
    })

    expect(html).toContain("script-src vscode-resource: 'nonce-abc123' 'unsafe-eval'")
    expect(html).toContain('nonce="abc123" src="vscode-resource:/editor-integrations/metadata-editor-integrations.js"')
    expect(html).toContain('nonce="abc123" src="vscode-resource:/editor-bundles/logicflow/static/js/app.js"')
    expect(html).toContain('nonce="abc123" src="vscode-resource:/editor-bundles/approval-workflow/static/js/app.js"')
    expect(html).toContain('href="vscode-resource:/editor-bundles/logicflow/static/css/app.css"')
    expect(html).toContain('href="vscode-resource:/editor-bundles/approval-workflow/static/css/app.css"')
    expect(html).toContain('window.React = window.React || window.amisRequire')
  })

  it('creates the VS Code message transport script in the shared runtime', () => {
    const script = createVsCodeMetadataEditorHostScript({
      initialState: {
        document: {
          uri: 'file:///tmp/main.menu.json',
          text: '<script>alert(1)</script>',
          revision: '1',
          diagnostics: [],
        },
        editorSchema: {},
      },
      theme: 'cxd',
      locale: 'zh-CN',
    })

    expect(script).toContain('acquireVsCodeApi()')
    expect(script).toContain("requestHost('saveText'")
    expect(script).toContain('flushPendingChangesResult')
    expect(script).toContain('\\u003cscript')
    expect(script).not.toContain('<script>alert')
  })
})

describe('logical host and serializer', () => {
  it('runs the shared host contract for an embedded IDE browser', async () => {
    const uri = '/repo/src/main/metadata/menu-model/main.menu.json'
    let savedText = ''
    const browserHost = createBrowserMetadataEditorHost({
      uri,
      metadataType: 'menu',
      initialDocument: {
        text: '{"title":"Main","fullName":"main"}\n',
        revision: '7',
      },
      async saveText(text) {
        savedText = text
        return { revision: '8' }
      },
    })

    const loaded = await browserHost.loadState()
    const updated = await browserHost.saveText(
      '{"title":"Main menu","fullName":"main"}\n',
      loaded.document.revision,
    )

    expect(loaded.document).toMatchObject({ type: 'menu', revision: '7', diagnostics: [] })
    expect(loaded.editorSchema).toMatchObject({ type: 'form', title: '菜单模型' })
    expect(savedText).toBe('{"title":"Main menu","fullName":"main"}\n')
    expect(updated.document).toMatchObject({ revision: '8', diagnostics: [] })
  })

  it('keeps a newer external document when a native save finishes late', async () => {
    const uri = '/repo/src/main/metadata/menu-model/main.menu.json'
    let finishSave: ((value: { revision: string }) => void) | undefined
    const browserHost = createBrowserMetadataEditorHost({
      uri,
      metadataType: 'menu',
      initialDocument: {
        text: '{"title":"Main","fullName":"main"}\n',
        revision: '7',
      },
      saveText: () => new Promise((resolve) => {
        finishSave = resolve
      }),
    })

    const saving = browserHost.saveText('{"title":"Local","fullName":"main"}\n', '7')
    await browserHost.applyExternalDocument({
      text: '{"title":"External","fullName":"main"}\n',
      revision: '9',
    })
    finishSave?.({ revision: '8' })

    await expect(saving).rejects.toMatchObject({ code: 'revision-conflict' })
    await expect(browserHost.loadState()).resolves.toMatchObject({
      document: {
        revision: '9',
        value: { title: 'External', fullName: 'main' },
      },
    })
  })

  it('accepts the matching document event emitted by its own native save', async () => {
    const uri = '/repo/src/main/metadata/menu-model/main.menu.json'
    let browserHost: ReturnType<typeof createBrowserMetadataEditorHost>
    browserHost = createBrowserMetadataEditorHost({
      uri,
      metadataType: 'menu',
      initialDocument: {
        text: '{"title":"Main","fullName":"main"}\n',
        revision: '7',
      },
      async saveText(text) {
        await browserHost.applyExternalDocument({ text, revision: '8' })
        return { revision: '8' }
      },
    })

    await expect(
      browserHost.saveText('{"title":"Local","fullName":"main"}\n', '7'),
    ).resolves.toMatchObject({ document: { revision: '8', value: { title: 'Local' } } })
  })

  it('handles editor session requests through the shared session interface', async () => {
    const uri = '/repo/src/main/metadata/menu-model/main.menu.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: '{"title":"Main","fullName":"main"}\n',
    })
    const session = createMetadataEditorSession({
      host: createMetadataEditorHost(bridge),
      uri,
      metadataType: 'menu',
    })

    await expect(handleMetadataEditorSessionRequest(session, {
      type: 'loadState',
      requestId: 'load-1',
    })).resolves.toMatchObject({
      type: 'response',
      requestId: 'load-1',
      ok: true,
      result: { document: { revision: '1', type: 'menu' } },
    })

    await expect(handleMetadataEditorSessionRequest(session, {
      type: 'saveText',
      requestId: 'save-1',
      text: '{"title":"Changed","fullName":"main"}\n',
      revision: '1',
    })).resolves.toMatchObject({
      type: 'response',
      requestId: 'save-1',
      ok: true,
      result: { document: { revision: '2', value: { title: 'Changed' } } },
    })

    await expect(handleMetadataEditorSessionRequest(session, {
      type: 'saveText',
      requestId: 'save-stale',
      text: '{"title":"Stale","fullName":"main"}\n',
      revision: '1',
    })).resolves.toMatchObject({
      type: 'response',
      requestId: 'save-stale',
      ok: false,
      errorCode: 'revision-conflict',
    })
  })

  it('loads, validates, edits, and serializes through an IDE file bridge', async () => {
    const uri = '/repo/src/main/metadata/menu-model/main.menu.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: '{"title":"Main","fullName":"main"}\n',
    })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)
    expect(loaded.type).toBe('menu')
    expect(loaded.diagnostics).toEqual([])

    const nextText = serializeMetadataDocument({ title: 'Main', fullName: 'main' }, getContributionByType('menu'))
    const updated = await host.applyChange(uri, nextText, loaded.revision)
    expect(updated.text).toBe('{\n  "title": "Main",\n  "fullName": "main"\n}\n')
    expect(bridge.getDiagnostics(uri)).toEqual([])
  })

  it('preserves object property order during metadata serialization', () => {
    const text = serializeMetadataDocument({
      title: 'Main',
      fullName: 'main',
      children: [{ title: 'Child', fullName: 'child' }],
    }, getContributionByType('dev-menu'))

    expect(text).toBe(`{
  "title": "Main",
  "fullName": "main",
  "children": [
    {
      "title": "Child",
      "fullName": "child"
    }
  ]
}
`)
  })

  it('preserves invalid JSON text and publishes diagnostics without writing a fallback object', async () => {
    const uri = '/repo/src/main/metadata/authority/broken.authority.json'
    const bridge = new InMemoryMetadataFileBridge({ [uri]: '{' })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)

    expect(loaded.text).toBe('{')
    expect(loaded.value).toBeUndefined()
    expect(loaded.diagnostics).toEqual([expect.objectContaining({ code: 'invalid-json', severity: 'error' })])
    expect((await bridge.loadText(uri)).text).toBe('{')
  })

  it('validates required schema fields from the metadata type contribution', async () => {
    const uri = '/repo/src/main/metadata/menu-model/broken.menu.json'
    const bridge = new InMemoryMetadataFileBridge({ [uri]: '{"fullName":7,"placement":"side"}' })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)

    expect(loaded.diagnostics).toEqual([
      expect.objectContaining({ code: 'schema-required-property', path: 'title', severity: 'error' }),
      expect.objectContaining({ code: 'schema-property-type', path: 'fullName', severity: 'error' }),
      expect.objectContaining({ code: 'schema-enum-value', path: 'placement', severity: 'error' }),
    ])
  })

  it('does not reject empty optional metadata fields that are equivalent to unset values', async () => {
    const uri = '/repo/src/main/metadata/configurations.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: JSON.stringify([
        {
          key: 'feature.enabled',
          label: '启用功能',
          description: null,
          nullable: true,
          valueType: 'string',
          defaultValue: null,
          groupPath: '',
          order: null,
          scope: null,
          obsolete: null,
          obsoleteCause: '',
          editorSchema: null,
        },
      ]),
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(uri)).resolves.toMatchObject({ diagnostics: [] })
  })

  it('accepts non-string configuration default values', async () => {
    const uri = '/repo/src/main/metadata/configurations.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: JSON.stringify([
        { key: 'feature.enabled', label: '启用功能', valueType: 'boolean', defaultValue: false },
        { key: 'feature.limit', label: '限制数量', valueType: 'int', defaultValue: 10 },
        { key: 'feature.options', label: '功能选项', valueType: 'object', defaultValue: { mode: 'auto' } },
        { key: 'feature.list', label: '功能列表', valueType: 'list', defaultValue: ['alpha'] },
        { key: 'feature.empty', label: '空默认值', valueType: 'string', defaultValue: null },
      ]),
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(uri)).resolves.toMatchObject({ diagnostics: [] })
  })

  it('keeps required metadata fields strict when empty values are not optional', async () => {
    const uri = '/repo/src/main/metadata/configurations.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: JSON.stringify([{ key: 'feature.enabled', label: '启用功能', valueType: '' }]),
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(uri)).resolves.toMatchObject({
      diagnostics: [expect.objectContaining({ code: 'schema-enum-value', path: '[0].valueType' })],
    })
  })

  it('loads editor metadata by file suffix outside the packaged source path', async () => {
    const uri = '/tmp/resource-editor.menu.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: '{"title":"Resource Editor","fullName":"resource-editor"}\n',
    })
    const host = createMetadataEditorHost(bridge)

    const loaded = await host.loadDocument(uri)
    const contribution = await host.getTypeContribution(uri)

    expect(loaded.type).toBe('menu')
    expect(loaded.packagedPath).toBe('META-INF/ouroboros/metadata/resource-editor.menu.json')
    expect(loaded.diagnostics).toEqual([])
    expect(contribution.createEditorSchema({ uri, document: loaded.value })).toMatchObject({ type: 'form' })
  })

  it('validates runtime menu and authority structures', async () => {
    const menuUri = '/repo/src/main/metadata/menu-model/broken.menu.json'
    const authorityUri = '/repo/src/main/metadata/authority/broken.authority.json'
    const devMenuUri = '/repo/src/main/metadata/dev-menu-model/broken.dev-menu.json'
    const bridge = new InMemoryMetadataFileBridge({
      [menuUri]: '{"fullName":"main","title":"Main","order":"first"}',
      [authorityUri]: '{"fullName":"auth","title":"Auth","apis":[9]}',
      [devMenuUri]: '{"fullName":"dev","title":"Dev","stage":"prod","children":[{"title":"Child"}]}',
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(menuUri)).resolves.toMatchObject({
      diagnostics: [expect.objectContaining({ code: 'schema-property-type', path: 'order' })],
    })
    await expect(host.loadDocument(authorityUri)).resolves.toMatchObject({
      diagnostics: [expect.objectContaining({ code: 'schema-property-type', path: 'apis[0]' })],
    })
    await expect(host.loadDocument(devMenuUri)).resolves.toMatchObject({
      diagnostics: [
        expect.objectContaining({ code: 'schema-enum-value', path: 'stage' }),
        expect.objectContaining({ code: 'schema-required-property', path: 'children[0].fullName' }),
      ],
    })
  })

  it('validates JSON schema union types', async () => {
    const uri = '/repo/src/main/metadata/app.metadata.json'
    const bridge = new InMemoryMetadataFileBridge({
      [uri]: '{"name":"sample","logo":7}',
    })
    const host = createMetadataEditorHost(bridge)

    await expect(host.loadDocument(uri)).resolves.toMatchObject({
      diagnostics: [expect.objectContaining({ code: 'schema-property-type', path: 'logo' })],
    })
  })
})
