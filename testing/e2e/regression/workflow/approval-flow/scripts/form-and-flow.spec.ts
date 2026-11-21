import { expect, test, type Page } from '@playwright/test'

import {
  ADMIN_CREDENTIAL,
  APPROVAL_WORKFLOW_CANDIDATE_ALIASES,
  APPROVAL_WORKFLOW_IDENTITIES,
  appendApprovalFlowJsonEvidence,
  assertBrowserFetchOk,
  buildDevelopApprovalWorkflowDefinition,
  buildSeedRequestNo,
  browserFetch,
  designRefreshAndDeployApprovalWorkflow,
  extractApiData,
  loadJsonFixture,
  login,
  mapApprovalWorkflowCandidates,
  requireEnv,
  resolveBusinessModelAppName,
  uniqueSuffix,
} from './support'

type WorkflowDefinition = {
  id: string
  name: string
  title?: string
  flowKey?: string
  formType?: string
  formKey?: string
  form?: {
    formType?: string
    formId?: string
    formKey?: string
  }
  formatVersion?: number
  nodes?: Array<Record<string, unknown>>
  edges?: Array<Record<string, unknown>>
  [key: string]: unknown
}

type BusinessModel = {
  name: string
  title: string
  fields?: Array<Record<string, unknown>>
  workflow?: Record<string, unknown>
  apis?: Record<string, unknown>
  views?: Record<string, unknown>
  accessControl?: Record<string, unknown>
  [key: string]: unknown
}

type WorkflowSeed = {
  id: string
  flowKey: string
  name: string
  description?: string
  nodes: Array<Record<string, unknown>>
  edges: Array<Record<string, unknown>>
}

type WorkflowSeedDocument = {
  flows: WorkflowSeed[]
}

type SearchResult = {
  items?: BusinessRecord[]
}

type BusinessRecord = Record<string, unknown> & {
  id?: string | number
  processInfo?: ProcessInfo
}

type ProcessInfo = {
  instanceId?: string
  tasks?: ProcessTask[]
  [key: string]: unknown
}

type ProcessTask = {
  taskId?: string
  taskName?: string
  name?: string
  nodeType?: string
  taskDefinitionKey?: string
  active?: boolean
  [key: string]: unknown
}

const frontBaseUrl = requireEnv('OB_TEST_FRONTEND_BASE_URL')
const runtimeBaseUrl = requireEnv('OB_TEST_RUNTIME_FRONTEND_BASE_URL')
const seededBusinessModelName = 'awf-expense-request'
const flowSeeds = loadJsonFixture<WorkflowSeedDocument>(
  'testing/common/fixtures/data/approval-workflow-flows-seed/flow-definitions.json',
)

function buildBusinessModelPath(modelFullName: string): string {
  return `/${modelFullName.split('.').map(toKebabCasePathPart).join('/')}`
}

function toKebabCasePathPart(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/[._\s]+/g, '-')
    .toLowerCase()
}

function workflowStatusMap() {
  return {
    draft: { code: 'DRAFT', label: '草稿' },
    submitted: { code: 'SUBMITTED', label: '已提交' },
    pending: { code: 'PENDING', label: '待开始' },
    processing: { code: 'PROCESSING', label: '审批中' },
    approved: { code: 'APPROVED', label: '通过' },
    veto: { code: 'VETO', label: '否决' },
    canceled: { code: 'CANCELED', label: '撤销' },
    terminated: { code: 'TERMINATED', label: '终止' },
  }
}

function buildWorkflowBinding(definitionId: string) {
  return {
    isEnabled: true,
    startMode: 'auto',
    bindings: [{
      key: 'default',
      processId: definitionId,
      statusField: 'workflowStatus',
      statusMap: workflowStatusMap(),
      trigger: 'manual',
    }],
  }
}

function getFlowSeed(flowKey: string): WorkflowSeed {
  const seed = flowSeeds.flows.find((item) => item.flowKey === flowKey)
  if (!seed) {
    throw new Error(`Unable to find approval workflow seed for flowKey=${flowKey}`)
  }
  return seed
}

function buildSeedWorkflowDefinition(flowKey: string) {
  const seed = getFlowSeed(flowKey)
  return mapApprovalWorkflowCandidates({
    ...seed,
    formType: 'business-model',
    formKey: seededBusinessModelName,
    nodes: seed.nodes.map((node) => ({ ...node })),
    edges: seed.edges.map((edge) => ({ ...edge })),
  })
}

function isApprovalNode(node: Record<string, unknown>) {
  return node.type === '审批节点' || node.type === 'ApprovalNode'
}

function mapCandidateAliases(nodes: Array<Record<string, unknown>> = []) {
  return nodes.map((node) => ({
    ...node,
    candidates: Array.isArray(node.candidates)
      ? node.candidates.every((candidate) => typeof candidate === 'string')
        ? [{
            type: 'User',
            users: node.candidates.map((candidate) => {
              const value = String(candidate)
              return APPROVAL_WORKFLOW_CANDIDATE_ALIASES[value] ?? value
            }),
          }]
        : node.candidates
      : node.candidates,
  }))
}

function buildExpenseRecord(suffix: string) {
  return {
    requestNo: buildSeedRequestNo(suffix),
    workflowStatus: 'SUBMITTED',
    applicantName: 'AWF Submitter',
    departmentCode: 'AWF-DEPT-ENGINEERING',
    amount: 1234.56,
    currency: 'CNY',
    urgent: 'false',
    reason: `Approval workflow business-model core lane ${suffix}`,
    requestDate: '2026-04-25',
  }
}

function buildSnowflakeIdPrimaryKeyField() {
  return {
    name: 'id',
    title: 'ID',
    fieldType: 'SnowflakePrimaryKey',
    type: '雪花主键',
    required: true,
    isRequired: true,
    isPrimaryKey: true,
  }
}

async function createBusinessModelViaUi() {
  const suffix = uniqueSuffix('awf-bm')
  return {
    name: suffix.replace(/-/g, ''),
    title: `AWF UI Model ${suffix}`,
    fields: [
      buildSnowflakeIdPrimaryKeyField(),
      {
        name: 'description',
        title: 'Description',
        fieldType: 'Text',
        type: '单行文本',
        required: false,
        isRequired: false,
      },
    ],
  }
}

async function getUiSchema(page: Page, schemaPath: string) {
  const result = await browserFetch(page, `/api/ui-schema?path=${encodeURIComponent(schemaPath)}`)
  assertBrowserFetchOk(result, `get UI schema ${schemaPath}`)
  return result
}

async function createBusinessModel(page: Page, appName: string, model: Pick<BusinessModel, 'name' | 'title' | 'fields'>) {
  const createResult = await browserFetch(page, `/api/dev/${appName}/business-model`, {
    method: 'POST',
    body: {
      modelType: 'default',
      name: model.name,
      title: model.title,
      fields: model.fields ?? [],
      description: 'Approval workflow browser E2E business model',
    },
  })
  const createJson = typeof createResult.json === 'object' && createResult.json !== null && !Array.isArray(createResult.json)
    ? createResult.json as Record<string, unknown>
    : null
  const modelAlreadyExists = createJson?.code === -1 && createResult.text.includes('业务模型文件已存在')
  if (!modelAlreadyExists) {
    assertBrowserFetchOk(createResult, `create business model ${model.name}`)
  }
  return extractApiData<BusinessModel>(createResult)
}

async function ensureSeededBusinessModel(page: Page, appName: string) {
  const fixture = loadJsonFixture<{ businessModel: BusinessModel }>(
    'testing/common/fixtures/data/approval-workflow-business-model-seed/business-model.json',
  ).businessModel
  const listResult = await browserFetch(page, `/api/dev/${appName}/business-model`)
  assertBrowserFetchOk(listResult, 'list business models')
  const existingModels = extractApiData<BusinessModel[]>(listResult)
  const exists = existingModels.some((item) => item.fullName === fixture.name || item.name === fixture.name)
  if (!exists) {
    await createBusinessModel(page, appName, fixture)
  }
  return updateBusinessModel(page, appName, fixture.name, (current) => ({
    ...current,
    title: fixture.title,
    description: fixture.description,
    isEnabled: true,
    apis: fixture.apis,
    views: fixture.views,
    accessControl: fixture.accessControl,
    fields: fixture.fields,
    workflow: fixture.workflow,
  }))
}

async function ensureBusinessModelWorkflowRoleAuthorities(page: Page) {
  const authorities = [
    'approvalCenter',
    'approvalCenter.start',
    `${seededBusinessModelName}.add`,
    `${seededBusinessModelName}.search`,
    `${seededBusinessModelName}.workflow`,
  ]
  const roleIds = [
    'AWF_ROLE_SUBMITTER',
    'AWF_ROLE_APPROVER_L1',
    'AWF_ROLE_APPROVER_L2',
    'AWF_ROLE_PARALLEL_APPROVER',
    'AWF_ROLE_AUDITOR',
    'AWF_ROLE_FLOW_ADMIN',
  ]
  for (const roleId of roleIds) {
    const result = await browserFetch(page, `/api/sys/role/${roleId}/authority`, {
      method: 'PUT',
      body: authorities.map((authority) => ({
        role: roleId,
        authority,
        accessControlParams: {},
      })),
    })
    assertBrowserFetchOk(result, `bind business-model workflow role authorities ${roleId}`)
  }
}

function buildDevelopWorkflowMetadata(definition: WorkflowDefinition, definitionId: string): Omit<WorkflowDefinition, 'id'> {
  return {
    formatVersion: 1,
    name: definitionId,
    title: definition.title ?? definition.name,
    description: String(definition.description ?? ''),
    version: 1,
    enabled: true,
    form: {
      formType: 'business-model',
      formId: seededBusinessModelName,
    },
    nodes: mapCandidateAliases(definition.nodes),
    edges: definition.edges?.map((edge) => ({
      type: 'SEQUENCE',
      ...edge,
    })),
  }
}

async function createDevelopWorkflowDefinition(page: Page, appName: string, definition: Omit<WorkflowDefinition, 'id'>) {
  const result = await browserFetch(page, `/api/dev/${appName}/approval-workflow-definition`, {
    method: 'POST',
    body: definition,
  })
  assertBrowserFetchOk(result, `create develop workflow definition ${definition.name}`)
  return extractApiData<Record<string, unknown>>(result)
}

async function updateDevelopWorkflowDefinition(page: Page, appName: string, definition: Omit<WorkflowDefinition, 'id'>) {
  const result = await browserFetch(page, `/api/dev/${appName}/approval-workflow-definition/${definition.name}`, {
    method: 'PUT',
    body: definition,
  })
  assertBrowserFetchOk(result, `update develop workflow definition ${definition.name}`)
  return extractApiData<Record<string, unknown>>(result)
}

async function commitDevelopWorkflowDefinition(page: Page, appName: string, definitionId: string) {
  const result = await browserFetch(page, `/api/dev/${appName}/approval-workflow-definition/${definitionId}/commit`, {
    method: 'POST',
    body: { commitMsg: `Approval workflow design-time E2E ${definitionId}` },
  })
  assertBrowserFetchOk(result, `commit develop workflow definition ${definitionId}`)
}

async function getDevelopWorkflowDefinition(page: Page, appName: string, definitionId: string) {
  const result = await browserFetch(page, `/api/dev/${appName}/approval-workflow-definition/${definitionId}`)
  assertBrowserFetchOk(result, `get develop workflow definition ${definitionId}`)
  return extractApiData<Record<string, unknown>>(result)
}

async function getDevelopWorkflowDefinitionStatus(page: Page, appName: string, definitionId: string) {
  const result = await browserFetch(page, `/api/dev/${appName}/approval-workflow-definition/${definitionId}/status`)
  assertBrowserFetchOk(result, `get develop workflow definition status ${definitionId}`)
  return extractApiData<Record<string, unknown>>(result)
}

async function createAndCommitDevelopWorkflowDefinitionViaUi(
  page: Page,
  appName: string,
  definition: { name: string; title: string; description: string },
) {
  await page.goto(
    new URL(`/dev/${appName}/approval-workflow-definition`, frontBaseUrl).toString(),
    { waitUntil: 'networkidle' },
  )
  await expect(page.getByRole('columnheader', { name: '流程名称' })).toBeVisible()
  const createButton = page.getByRole('button', { name: /新建/ }).last()
  await expect(createButton).toBeEnabled()
  await createButton.click()

  const createDialog = page.getByRole('dialog')
  await expect(createDialog).toBeVisible()
  await createDialog.locator('input[name="name"]').fill(definition.name)
  await createDialog.locator('input[name="title"]').fill(definition.title)
  await createDialog.locator('textarea[name="description"]').fill(definition.description)
  await createDialog.getByRole('combobox').click()
  await page.getByRole('option', { name: /AWF Expense Request/ }).click()

  const createPath = `/api/dev/${appName}/approval-workflow-definition`
  const createResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST'
      && new URL(response.url()).pathname === createPath
  })
  await createDialog.getByRole('button', { name: '创建并编辑', exact: true }).click()
  const createResponse = await createResponsePromise
  const createPayload = await createResponse.json() as Record<string, unknown>
  const createRequestBody = createResponse.request().postDataJSON()
  expect(createResponse.ok()).toBe(true)
  expect(
    createPayload.code,
    JSON.stringify({ response: createPayload, requestBody: createRequestBody }),
  ).toBe(0)

  const searchInput = page.locator('input[placeholder="请输入流程名称或标识"]:visible').last()
  await searchInput.fill(definition.name)
  await page.getByRole('button', { name: '查询', exact: true }).last().click()

  const startEditingButton = page.getByRole('button', { name: '开始编辑', exact: true })
  const definitionRow = page.getByRole('row').filter({ hasText: definition.name })
  await expect(definitionRow).toBeVisible({ timeout: 15_000 })
  await definitionRow.scrollIntoViewIfNeeded()
  const editButton = definitionRow.getByRole('button', { name: '编辑' })
  await expect(editButton).toBeVisible()
  await editButton.click()
  await expect(startEditingButton).toBeVisible()
  await startEditingButton.click()
  await page.getByTestId('workflow-editor-diagnostics-trigger').click()
  await page.locator('.workflow-editor-diagnostics-item').first().click()
  await expect(page.getByTestId('workflow-panel-right')).toContainText('人员配置')
  const starterCandidate = page.locator('.cxd-ListControl-itemLabel')
    .filter({ hasText: '流程发起人' })
  await expect(starterCandidate).toBeVisible()
  await starterCandidate.click()
  await expect(page.getByRole('button', { name: /当前未发现结构错误或警告/ })).toBeVisible()

  const definitionPath = `${createPath}/${definition.name}`
  const saveResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'PUT'
      && new URL(response.url()).pathname === definitionPath
  })
  await page.getByRole('button', { name: /保存/ }).click()
  expect((await saveResponsePromise).ok()).toBe(true)

  await page.getByRole('button', { name: /提交/ }).click()
  const commitDialog = page.getByRole('dialog')
  await commitDialog.getByPlaceholder('请输入修改说明').fill(`Approval workflow design-time E2E ${definition.name}`)
  const commitResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST'
      && new URL(response.url()).pathname === `${definitionPath}/commit`
  })
  await commitDialog.getByRole('button', { name: '提交', exact: true }).click()
  expect((await commitResponsePromise).ok()).toBe(true)
  await expect(page.getByRole('button', { name: '开始编辑', exact: true })).toBeVisible()
}

async function getBusinessModel(page: Page, appName: string, fullName: string) {
  const result = await browserFetch(page, `/api/dev/${appName}/business-model/${fullName}`)
  assertBrowserFetchOk(result, `get business model ${fullName}`)
  return extractApiData<BusinessModel>(result)
}

async function updateBusinessModel(page: Page, appName: string, fullName: string, updater: (model: BusinessModel) => BusinessModel) {
  const current = await getBusinessModel(page, appName, fullName)
  const next = updater(current)
  const updateResult = await browserFetch(page, `/api/dev/${appName}/business-model/${fullName}?commit=true`, {
    method: 'PUT',
    body: next,
  })
  assertBrowserFetchOk(updateResult, `update business model ${fullName}`)
  return next
}

async function createBusinessRecord(page: Page, modelPath: string, suffix: string) {
  const payload = buildExpenseRecord(suffix)
  const createResult = await browserFetch(page, `/api${modelPath}`, {
    method: 'POST',
    body: payload,
  })
  assertBrowserFetchOk(createResult, `create business record ${modelPath}`)
  const record = extractApiData<BusinessRecord>(createResult)
  const recordId = String(record.id ?? '')
  if (!recordId) {
    throw new Error(`create business record ${modelPath} did not return an id: ${createResult.text}`)
  }
  return { recordId, payload, record }
}

async function startBusinessProcess(page: Page, modelPath: string, recordId: string) {
  const startProcessUrl = `/api${modelPath}/${recordId}/start-process`
  const startResult = await browserFetch(page, startProcessUrl, {
    method: 'POST',
    body: {},
  })
  assertBrowserFetchOk(startResult, `start business process ${recordId}`)
  return { startProcessUrl, startPayload: extractApiData<unknown>(startResult) }
}

async function listBusinessRecords(page: Page, modelPath: string) {
  const listResult = await browserFetch(page, `/api${modelPath}?page=1&perPage=50`)
  assertBrowserFetchOk(listResult, `list business records ${modelPath}`)
  const data = extractApiData<SearchResult | BusinessRecord[]>(listResult)
  return Array.isArray(data) ? data : (data.items ?? [])
}

async function waitForBusinessProcessInfo(page: Page, modelPath: string, recordId: string) {
  const deadline = Date.now() + 15000
  let records: BusinessRecord[] = []
  while (Date.now() < deadline) {
    records = await listBusinessRecords(page, modelPath)
    const match = records.find((record) => String(record.id) === recordId)
    if (match?.processInfo?.instanceId && selectBusinessApprovalTask(match.processInfo)) {
      return match
    }
    await page.waitForTimeout(500)
  }
  const latest = records.find((record) => String(record.id) === recordId)
  throw new Error(`Unable to find approval processInfo for business record ${recordId}; latest=${JSON.stringify(latest?.processInfo)}; visible ids=${records.map((record) => String(record.id)).join(', ')}`)
}

async function waitForBusinessRecordStatus(
  page: Page,
  modelPath: string,
  recordId: string,
  expectedStatuses: string[],
): Promise<BusinessRecord> {
  const deadline = Date.now() + 15000
  let latest: BusinessRecord | undefined
  while (Date.now() < deadline) {
    const records = await listBusinessRecords(page, modelPath)
    latest = records.find((record) => String(record.id) === recordId)
    const status = String(latest?.workflowStatus ?? '')
    if (latest && expectedStatuses.includes(status)) {
      return latest
    }
    await page.waitForTimeout(500)
  }
  throw new Error(`Business record ${recordId} did not reach ${expectedStatuses.join('/')} status; latest=${JSON.stringify(latest)}`)
}

async function submitBusinessTaskWrapper(
  page: Page,
  modelPath: string,
  recordId: string,
  processInfo: ProcessInfo,
  formData: Record<string, unknown>,
) {
  const instanceId = processInfo.instanceId
  const taskId = selectBusinessApprovalTask(processInfo)?.taskId
  if (!instanceId || !taskId) {
    throw new Error(`processInfo is missing instanceId/approval taskId: ${JSON.stringify(processInfo)}`)
  }
  const submitTaskUrl = `/api${modelPath}/${recordId}/process/instance/${instanceId}/task/${taskId}/PASS`
  const submitResult = await browserFetch(page, submitTaskUrl, {
    method: 'POST',
    body: {
      comment: 'APF-BUSINESS-CORE-001 business model wrapper approval',
      formData,
    },
  })
  assertBrowserFetchOk(submitResult, `submit business task wrapper ${taskId}`)
  return { submitTaskUrl, submitPayload: extractApiData<unknown>(submitResult) }
}

function selectBusinessApprovalTask(processInfo?: ProcessInfo): ProcessTask | undefined {
  return processInfo?.tasks?.find((task) => {
    if (!task.taskId || task.active === false) {
      return false
    }

    const nodeType = String(task.nodeType ?? '')
    const taskName = String(task.taskName ?? task.name ?? '')
    const taskDefinitionKey = String(task.taskDefinitionKey ?? '')
    return nodeType !== '发起节点'
      && nodeType !== 'StartNode'
      && taskName !== '发起'
      && !taskDefinitionKey.includes('START')
  })
}

async function getTimeline(page: Page, instanceId: string) {
  const timelineResult = await browserFetch(page, `/api/approval-workflow/instance/${instanceId}/timeline`)
  assertBrowserFetchOk(timelineResult, `get timeline ${instanceId}`)
  return extractApiData<unknown>(timelineResult)
}

test.describe('Approval Flow - Form and Flow Design', () => {
  test('APF-FORM-001: create and commit a business model through source-backed authoring routes', async ({ page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)
    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    const model = await createBusinessModelViaUi()

    const createSchema = await getUiSchema(page, '/dev/{appName}/business-model/create')
    expect(createSchema.text).toContain('create-business-model-form')
    expect(createSchema.text).toContain('/api/amis-schema?path=/dev/{appName}/business-model/editor')

    const createdModel = await createBusinessModel(page, appName, model)
    expect(createdModel.name).toBe(model.name)
    expect(createdModel.title).toBe(model.title)

    const updatedTitle = `${model.title} Updated`
    const editSchema = await getUiSchema(page, '/dev/{appName}/business-model/{fullName}')
    expect(editSchema.text).toContain('edit-business-model-form')
    await updateBusinessModel(page, appName, model.name, (current) => ({
      ...current,
      title: updatedTitle,
    }))

    const savedModel = await getBusinessModel(page, appName, model.name)
    expect(savedModel.name).toBe(model.name)
    expect(savedModel.title).toBe(updatedTitle)
    expect(savedModel.fields?.[0]).toMatchObject({
      name: 'id',
      fieldType: 'SnowflakePrimaryKey',
      isPrimaryKey: true,
    })
  })

  test('APF-FLOW-001: design and commit a single-sign definition through the develop platform', async ({ page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)

    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    const listSchema = await getUiSchema(page, '/dev/{appName}/approval-workflow-definition')
    expect(listSchema.text).toContain('/api/dev/${appName}/approval-workflow-definition')

    const suffix = uniqueSuffix('awf-single')
    const definition = {
      name: suffix,
      title: `AWF Single ${suffix}`,
      description: `Created and committed through the develop workflow designer ${suffix}`,
    }
    await createAndCommitDevelopWorkflowDefinitionViaUi(page, appName, definition)
    const savedDefinition = await getDevelopWorkflowDefinition(page, appName, definition.name)
    expect(savedDefinition.name).toBe(definition.name)
    expect(savedDefinition.title).toBe(definition.title)
    expect(savedDefinition).not.toHaveProperty('processDefinitionId')
    expect(savedDefinition).not.toHaveProperty('status')
    const flowDefinition = savedDefinition.flowDefinition as WorkflowDefinition
    const approvalNode = (flowDefinition.nodes ?? []).find(isApprovalNode)
    expect(approvalNode?.candidates).toContainEqual({ type: 'Starter', isChecked: true })
    const status = await getDevelopWorkflowDefinitionStatus(page, appName, definition.name)
    expect(status.versionState).toBe('NOTCHANGED')
    const editorSchema = await getUiSchema(page, '/dev/{appName}/approval-workflow-definition/{id}')
    expect(editorSchema.text).toContain('workflow-editor')
    expect(editorSchema.text).toContain('/api/dev/${appName}/approval-workflow-definition/${id}')
    appendApprovalFlowJsonEvidence('design-time.json', {
      caseId: 'APF-FLOW-001',
      appName,
      definitionName: definition.name,
      assetPath: `workflow/${definition.name}.workflow.json`,
      versionState: status.versionState,
      browserActions: ['new definition', 'bind business model', 'start editing', 'configure candidate', 'save', 'commit'],
    })
    await page.screenshot({
      path: `${process.env.OB_TEST_AWF_EVIDENCE_DIR ?? 'tmp/testing-evidence/workflow-approval-flow'}/develop-workflow-design-commit.png`,
      fullPage: true,
    })
  })

  test('APF-FLOW-002: edit and commit advanced actions through the develop workflow editor contract', async ({ page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)

    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    const suffix = uniqueSuffix('awf-advanced')
    const fixtureDefinition = loadJsonFixture<WorkflowDefinition>(
      'testing/fixtures/approval-workflow/v2-explicit-graph/golden-scenario-1-single-branch.json',
    )
    const advancedDefinition = buildDevelopWorkflowMetadata({
      ...fixtureDefinition,
      name: suffix,
      title: `AWF Advanced ${suffix}`,
    }, suffix)
    await createDevelopWorkflowDefinition(page, appName, advancedDefinition)
    const updatedDefinition: Omit<WorkflowDefinition, 'id'> = {
      ...advancedDefinition,
      nodes: (advancedDefinition.nodes ?? []).map((node) => {
        if (!isApprovalNode(node)) {
          return node
        }
        return {
          ...node,
          subType: '单签',
          candidates: [{ type: 'User', users: [APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_1] }],
          actions: ['rollback', 'reject', 'transfer', 'addSign', 'freeReject', 'freeApprove'],
          advancedOptions: { freeReject: true, freeApproval: true },
        }
      }),
    }
    await updateDevelopWorkflowDefinition(page, appName, updatedDefinition)
    await commitDevelopWorkflowDefinition(page, appName, advancedDefinition.name)
    const savedUpdatedDefinition = await getDevelopWorkflowDefinition(page, appName, advancedDefinition.name)
    expect(savedUpdatedDefinition.version).toBe(2)
    const status = await getDevelopWorkflowDefinitionStatus(page, appName, advancedDefinition.name)
    expect(status.versionState).toBe('NOTCHANGED')
    const editorSchema = await getUiSchema(page, '/dev/{appName}/approval-workflow-definition/{id}')
    expect(editorSchema.text).toContain('workflow-editor')
    expect(editorSchema.text).toContain('/api/dev/${appName}/approval-workflow-definition/${id}')
    expect(editorSchema.text).toContain('formFieldSourceApi')
    expect(editorSchema.text).toContain('candidateSourceApi')

    const savedFlowDefinition = savedUpdatedDefinition.flowDefinition as WorkflowDefinition
    const approvalNodes = (savedFlowDefinition.nodes ?? []).filter(isApprovalNode)
    expect(approvalNodes).not.toHaveLength(0)
    expect(approvalNodes[0].actions).toEqual(
      expect.arrayContaining(['transfer', 'addSign', 'freeReject', 'freeApprove']),
    )
  })

  test('APF-BIND-001: persist definition form binding and business-model workflow configuration with source-backed fields', async ({ page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)
    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    await ensureSeededBusinessModel(page, appName)
    const suffix = uniqueSuffix('awf-bind')

    const fixtureDefinition = loadJsonFixture<WorkflowDefinition>(
      'testing/fixtures/approval-workflow/v2-explicit-graph/golden-scenario-1-single-branch.json',
    )
    const definition = buildDevelopWorkflowMetadata({
      ...fixtureDefinition,
      name: suffix,
      title: `AWF Binding ${suffix}`,
    }, suffix)
    await createDevelopWorkflowDefinition(page, appName, definition)
    await updateDevelopWorkflowDefinition(page, appName, definition)
    await commitDevelopWorkflowDefinition(page, appName, suffix)

    const definitionAfterBinding = await getDevelopWorkflowDefinition(page, appName, suffix)
    expect((definitionAfterBinding.form as Record<string, unknown>)?.formType).toBe('business-model')
    expect((definitionAfterBinding.form as Record<string, unknown>)?.formId).toBe(seededBusinessModelName)

    const updatedBusinessModel = await updateBusinessModel(page, appName, seededBusinessModelName, (current) => ({
      ...current,
      workflow: buildWorkflowBinding(suffix),
    }))

    const editorSchema = await getUiSchema(page, '/dev/{appName}/approval-workflow-definition/{id}')
    expect(editorSchema.text).toContain('workflow-editor')
    expect(editorSchema.text).toContain('/api/dev/${appName}/approval-workflow-definition/${id}')
    expect(editorSchema.text).toContain('formFieldSourceApi')
    const businessModelEditSchema = await getUiSchema(page, '/dev/{appName}/business-model/{fullName}')
    expect(businessModelEditSchema.text).toContain('edit-business-model-form')

    const binding = ((updatedBusinessModel.workflow as Record<string, unknown>).bindings as Array<Record<string, unknown>>)[0]
    expect(binding.processId).toBe(suffix)
    expect(binding.statusField).toBe('workflowStatus')
    expect(binding.trigger).toBe('manual')
  })

  test('APF-BUSINESS-CORE-001: submit a business model record and complete the workflow through generated wrappers', async ({ browser, page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)
    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    const seededModel = await ensureSeededBusinessModel(page, appName)
    const definitionName = uniqueSuffix('AWF_FLOW_BUSINESS_CORE')
    const definition = buildDevelopApprovalWorkflowDefinition(
      buildSeedWorkflowDefinition('awf.single.l1'),
      definitionName,
      'business-model',
      seededBusinessModelName,
    )
    await updateBusinessModel(page, appName, seededBusinessModelName, (current) => ({
      ...current,
      workflow: buildWorkflowBinding(definitionName),
    }))

    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const publishEvidence = await designRefreshAndDeployApprovalWorkflow(
      browser,
      page,
      frontBaseUrl,
      runtimeBaseUrl,
      definition,
      `Approval business-model core E2E ${definitionName}`,
    )
    await ensureBusinessModelWorkflowRoleAuthorities(page)

    const suffix = uniqueSuffix('awf-business-core')

    const modelPath = buildBusinessModelPath(String(seededModel.fullName ?? seededModel.name ?? seededBusinessModelName))
    const addSchema = await getUiSchema(page, `${modelPath}/add`)
    expect(addSchema.text).toContain('default-add-edit-form')
    expect(addSchema.text).toContain(`/api${modelPath}/\${saveResult.id}/start-process`)
    expect(addSchema.text).toContain('startProcessResult')
    const listSchema = await getUiSchema(page, modelPath)
    expect(listSchema.text).toContain('start-process-operation')
    expect(listSchema.text).toContain('process-operation')

    const { recordId, payload } = await createBusinessRecord(page, modelPath, suffix)
    const { startProcessUrl, startPayload } = await startBusinessProcess(page, modelPath, recordId)
    const recordWithProcess = await waitForBusinessProcessInfo(page, modelPath, recordId)
    const processInfo = recordWithProcess.processInfo
    const approvalTask = selectBusinessApprovalTask(processInfo)
    if (!processInfo?.instanceId || !approvalTask?.taskId) {
      throw new Error(`Missing processInfo after start-process: ${JSON.stringify(recordWithProcess)}`)
    }

    const instanceId = processInfo.instanceId
    const taskId = approvalTask.taskId
    const approvalUrl = `${modelPath}/${recordId}/approval?instanceId=${instanceId}&taskId=${taskId}`
    const processDetailUrl = `${modelPath}/${recordId}/process-detail?instanceId=${instanceId}`
    const approvalSchema = await getUiSchema(page, `${modelPath}/{id}/approval`)
    expect(approvalSchema.text).toContain(`/api${modelPath}/\${id}/process/instance/\${instanceId}/task/\${taskId}/PASS`)
    expect(approvalSchema.text).toContain('workflow-timeline')
    const processDetailSchema = await getUiSchema(page, `${modelPath}/{id}/process-detail`)
    expect(processDetailSchema.text).toContain('workflow-timeline')

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, APPROVAL_WORKFLOW_IDENTITIES.approver1)
    await approverPage.goto(new URL(approvalUrl, runtimeBaseUrl).toString())
    await expect(approverPage).not.toHaveURL(/\/login(?:$|[?#/])/)
    const { submitTaskUrl, submitPayload } = await submitBusinessTaskWrapper(
      approverPage,
      modelPath,
      recordId,
      processInfo,
      {
        ...payload,
        amount: 1234.56,
        reason: `${payload.reason} approved through business wrapper`,
      },
    )
    await approverContext.close()

    const completedRecord = await waitForBusinessRecordStatus(page, modelPath, recordId, ['APPROVED'])
    const timeline = await getTimeline(page, instanceId)
    const timelineVisible = processDetailSchema.text.includes('workflow-timeline')
      && JSON.stringify(timeline).length > 2

    appendApprovalFlowJsonEvidence('business-model-journey.json', {
      caseId: 'APF-BUSINESS-CORE-001',
      modelPath,
      recordId,
      submitSource: 'business-model-add-form-submit-action-chain',
      formSubmitSchemaPath: `${modelPath}/add`,
      startProcessUrl,
      startPayload,
      instanceId,
      processInfo,
      approvalUrl,
      processDetailUrl,
      submitTaskUrl,
      submitPayload,
      definitionName,
      definitionSource: publishEvidence.source,
      workspaceReload: publishEvidence.reloadEvidence,
      businessStatus: completedRecord.workflowStatus,
      timelineVisible,
    })

    expect(recordWithProcess.processInfo).toBeTruthy()
    expect(submitTaskUrl).toContain('/process/instance/')
    expect(completedRecord.workflowStatus).toBe('APPROVED')
    expect(timelineVisible).toBe(true)
  })
})
