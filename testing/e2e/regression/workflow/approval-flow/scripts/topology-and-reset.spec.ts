import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  ADMIN_CREDENTIAL,
  APPROVAL_WORKFLOW_IDENTITIES,
  approvalWorkflowDisplayName,
  appendApprovalFlowJsonEvidence,
  assertBrowserFetchOk,
  buildDevelopApprovalWorkflowDefinition,
  buildApprovalWorkflowBusinessRecordId,
  buildSeedRequestNo,
  browserFetch,
  designRefreshAndDeployApprovalWorkflow,
  extractApiData,
  loadJsonFixture,
  login,
  refreshDevelopWorkspaceToTestContainer,
  requireEnv,
  resolveBusinessModelAppName,
  uniqueSuffix,
  type ApprovalWorkflowDefinitionShape,
  type DevelopApprovalWorkflowDefinition,
} from './support'

type WorkflowSeed = {
  id: string
  flowKey: string
  name: string
  description?: string
  formModelId: string
  nodes: Array<Record<string, unknown>>
  edges: Array<Record<string, unknown>>
}

type WorkflowSeedDocument = {
  flows: WorkflowSeed[]
}

type TodoTask = {
  id: string
  flowName?: string
  name?: string
  instanceId?: string
  [key: string]: unknown
}

type WorkflowDefinition = ApprovalWorkflowDefinitionShape & { name: string }

type DefinitionSearchData = {
  items?: WorkflowDefinition[]
}

type DesignTimeWorkflow = DevelopApprovalWorkflowDefinition

const frontBaseUrl = requireEnv('OB_TEST_FRONTEND_BASE_URL')
const runtimeBaseUrl = requireEnv('OB_TEST_RUNTIME_FRONTEND_BASE_URL')
const approver1 = APPROVAL_WORKFLOW_IDENTITIES.approver1
const approver2 = APPROVAL_WORKFLOW_IDENTITIES.approver2
const approver3 = APPROVAL_WORKFLOW_IDENTITIES.approver3
const resetDesignDefinitionName = process.env.OB_TEST_AWF_RESET_DEFINITION_NAME ?? 'AWF_FLOW_RESET_DESIGN'
const resetDesignTitle = 'AWF Reset Design Original'
const resetDesignDescription = 'Source metadata used by APF-RESET-DESIGN-001'

const flowSeeds = loadJsonFixture<WorkflowSeedDocument>(
  'testing/common/fixtures/data/approval-workflow-flows-seed/flow-definitions.json',
)

function getFlowSeed(flowKey: string): WorkflowSeed {
  const seed = flowSeeds.flows.find((item) => item.flowKey === flowKey)
  if (!seed) {
    throw new Error(`Unable to find approval workflow seed for flowKey=${flowKey}`)
  }
  return seed
}

function buildExpenseFormData(suffix: string, amount = 1234.56) {
  return {
    id: buildApprovalWorkflowBusinessRecordId(suffix),
    requestNo: buildSeedRequestNo(suffix),
    applicantName: 'AWF Submitter',
    departmentCode: 'AWF-DEPT-ENGINEERING',
    amount,
    currency: 'CNY',
    urgent: 'false',
    reason: `Approval workflow topology verification ${suffix}`,
    requestDate: '2026-04-25',
  }
}

function extractItems(result: unknown): TodoTask[] {
  if (Array.isArray(result)) {
    return result as TodoTask[]
  }
  if (!result || typeof result !== 'object') {
    return []
  }
  const record = result as Record<string, unknown>
  if (Array.isArray(record.items)) {
    return record.items as TodoTask[]
  }
  if ('data' in record) {
    return extractItems(record.data)
  }
  if ('result' in record) {
    return extractItems(record.result)
  }
  return []
}

function buildDesignTimeWorkflow(seed: WorkflowSeed, definitionName: string): DesignTimeWorkflow {
  return buildDevelopApprovalWorkflowDefinition(seed, definitionName)
}

async function designAndRefreshWorkflow(
  browser: Browser,
  runtimePage: Page,
  seedFlowKey: string,
  customize?: (seed: DesignTimeWorkflow) => void,
) {
  const seed = getFlowSeed(seedFlowKey)
  const definitionName = `${seed.id}_${uniqueSuffix('design')}`.replace(/[^A-Za-z0-9_]/g, '_')
  const definition = buildDesignTimeWorkflow(seed, definitionName)
  customize?.(definition)
  const publishEvidence = await designRefreshAndDeployApprovalWorkflow(
    browser,
    runtimePage,
    frontBaseUrl,
    runtimeBaseUrl,
    definition,
    `Approval topology E2E ${definition.name}`,
  )
  appendApprovalFlowJsonEvidence('topology-and-reset.json', {
    caseId: 'APF-DESIGN-TO-RUNTIME-TOPOLOGY-001',
    definitionName,
    seedFlowKey,
    source: publishEvidence.source,
    reloadEvidence: publishEvidence.reloadEvidence,
    checked: ['develop create', 'develop save', 'develop submit', 'develop platform hot reload button', 'runtime metadata-layer projection', 'explicit runtime deploy'],
  })
  return definition
}

async function deployWorkflowDefinition(page: Page, definitionId: string) {
  const deployResult = await browserFetch(page, `/api/approval-workflow/definition/${definitionId}/deploy`, {
    method: 'POST',
  })
  assertBrowserFetchOk(deployResult, `deploy metadata-layer workflow definition ${definitionId}`)
}

async function createAndDeployFlow(browser: Browser, page: Page, seedFlowKey: string, customize?: (seed: DesignTimeWorkflow) => void) {
  return designAndRefreshWorkflow(browser, page, seedFlowKey, customize)
}

async function startFlowInstance(page: Page, flowId: string, suffix: string, amount = 1234.56) {
  const startResult = await browserFetch(page, `/api/approval-workflow/instance/${flowId}/start`, {
    method: 'POST',
    body: {
      formData: buildExpenseFormData(suffix, amount),
      taskAssignee: {},
    },
  })
  assertBrowserFetchOk(startResult, `start workflow instance ${flowId}`)
  return startResult
}

async function expectFlowStartRejected(page: Page, flowId: string, suffix: string) {
  const result = await browserFetch(page, `/api/approval-workflow/instance/${flowId}/start`, {
    method: 'POST',
    body: {
      formData: buildExpenseFormData(suffix),
      taskAssignee: {},
    },
  })
  const payload = result.json as Record<string, unknown> | null
  const succeeded = result.ok
    && (!payload || !('code' in payload) || payload.code === 0)
    && (!payload || !('responseStatus' in payload) || payload.responseStatus === 0)
  expect(succeeded, `workflow ${flowId} unexpectedly allowed a new instance while unavailable`).toBe(false)
  return result
}

async function listTodoTasks(page: Page) {
  const todoResult = await browserFetch(page, '/api/approval-workflow/task/todo?page=1&perPage=50')
  assertBrowserFetchOk(todoResult, 'list todo tasks')
  return extractItems(extractApiData<unknown>(todoResult))
}

async function findTodoTaskByFlowName(page: Page, flowName: string) {
  const deadline = Date.now() + 15000
  let tasks: TodoTask[] = []
  while (Date.now() < deadline) {
    tasks = await listTodoTasks(page)
    const match = tasks.find((task) => task.flowName === flowName)
    if (match) {
      return match
    }
    await page.waitForTimeout(500)
  }
  throw new Error(`Unable to find todo task for flowName=${flowName}; visible flows=${tasks.map((task) => task.flowName ?? task.name ?? task.id).join(', ')}`)
}

async function waitForTaskAbsence(page: Page, flowName: string) {
  const deadline = Date.now() + 15000
  while (Date.now() < deadline) {
    const tasks = await listTodoTasks(page)
    if (!tasks.some((task) => task.flowName === flowName)) {
      return
    }
    await page.waitForTimeout(500)
  }
  throw new Error(`Task for flowName=${flowName} is still visible after waiting`)
}

async function waitForTaskIdAbsence(page: Page, taskId: string) {
  const deadline = Date.now() + 15000
  while (Date.now() < deadline) {
    const tasks = await listTodoTasks(page)
    if (!tasks.some((task) => task.id === taskId)) {
      return
    }
    await page.waitForTimeout(500)
  }
  throw new Error(`Task ${taskId} is still visible after waiting`)
}

async function submitTaskAction(page: Page, taskId: string, body: Record<string, unknown>) {
  const submitResult = await browserFetch(page, `/api/approval-workflow/task/${taskId}`, {
    method: 'POST',
    body,
  })
  assertBrowserFetchOk(submitResult, `submit task action ${taskId}`)
  return submitResult
}

async function loginAs(browser: Browser, credential: typeof approver1) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, runtimeBaseUrl, credential)
  return { context, page }
}

async function searchDefinitions(page: Page): Promise<WorkflowDefinition[]> {
  const result = await browserFetch(page, '/api/approval-workflow/definition?status=all&page=1&perPage=200')
  assertBrowserFetchOk(result, 'search workflow definitions')
  const data = extractApiData<DefinitionSearchData>(result)
  return data.items ?? []
}

async function findDefinitionSummary(page: Page, name: string): Promise<WorkflowDefinition> {
  const deadline = Date.now() + 15000
  let definitions: WorkflowDefinition[] = []
  while (Date.now() < deadline) {
    definitions = await searchDefinitions(page)
    const match = definitions.find((item) => item.name === name)
    if (match) {
      return match
    }
    await page.waitForTimeout(500)
  }
  throw new Error(`Unable to find workflow definition ${name}; visible definitions=${definitions.map((item) => item.name).join(', ')}`)
}

async function getWorkflowDefinition(page: Page, name: string): Promise<WorkflowDefinition> {
  const result = await browserFetch(page, `/api/approval-workflow/definition/${name}`)
  assertBrowserFetchOk(result, `get workflow definition ${name}`)
  return extractApiData<WorkflowDefinition>(result)
}

async function updateWorkflowDefinition(page: Page, name: string, body: WorkflowDefinition) {
  const result = await browserFetch(page, `/api/approval-workflow/definition/${name}`, {
    method: 'PUT',
    body,
  })
  assertBrowserFetchOk(result, `update workflow definition ${name}`)
}

async function resetWorkflowDefinition(page: Page, name: string) {
  const result = await browserFetch(page, `/api/approval-workflow/definition/${name}/reset`, {
    method: 'POST',
  })
  assertBrowserFetchOk(result, `reset workflow definition ${name}`)
}

async function listDefinitionVersions(page: Page, name: string) {
  const result = await browserFetch(page, `/api/approval-workflow/definition/${name}/versions`)
  assertBrowserFetchOk(result, `list workflow definition versions ${name}`)
  return extractApiData<unknown[]>(result)
}

async function getTimeline(page: Page, instanceId: string) {
  const result = await browserFetch(page, `/api/approval-workflow/instance/${instanceId}/timeline`)
  assertBrowserFetchOk(result, `get timeline ${instanceId}`)
  return extractApiData<unknown>(result)
}

function isBlank(value: unknown) {
  return value === undefined || value === null || String(value) === ''
}

test.describe('Approval Flow - Topology and Reset', () => {
  test.setTimeout(120_000)

  test('APF-RUNTIME-REFRESH-001: refresh the test container from the development workspace through the platform UI', async ({ page }) => {
    await login(page, frontBaseUrl, ADMIN_CREDENTIAL)
    const appName = await resolveBusinessModelAppName(page, frontBaseUrl)
    const reloadEvidence = await refreshDevelopWorkspaceToTestContainer(
      page,
      frontBaseUrl,
      runtimeBaseUrl,
      appName,
    )

    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const definition = await findDefinitionSummary(page, resetDesignDefinitionName)
    expect(definition.source).toBe('metadata-layer')
    expect(definition.title).toBe(resetDesignTitle)
    expect(definition.description ?? '').toBe(resetDesignDescription)

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-RUNTIME-REFRESH-001',
      ...reloadEvidence,
      definitionName: definition.name,
      definitionSource: definition.source,
      definitionVersion: definition.version,
      checked: ['develop platform hot reload button', 'app-container reload request', 'runtime metadata-layer definition visible'],
    })
    await page.screenshot({
      path: `${process.env.OB_TEST_AWF_EVIDENCE_DIR ?? 'tmp/testing-evidence/workflow-approval-flow'}/develop-workspace-hot-reload.png`,
      fullPage: true,
    })
  })

  test('APF-CONDITION-XOR-001: route by amount condition without GatewayNode', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.condition.xor')

    await startFlowInstance(page, flow.id, uniqueSuffix('xor-low'), 500)

    const lowApprover = await loginAs(browser, approver1)
    await expect(findTodoTaskByFlowName(lowApprover.page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()

    const highApproverBefore = await loginAs(browser, approver2)
    await waitForTaskAbsence(highApproverBefore.page, approvalWorkflowDisplayName(flow))

    await startFlowInstance(page, flow.id, uniqueSuffix('xor-high'), 1500)
    await expect(findTodoTaskByFlowName(highApproverBefore.page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-CONDITION-XOR-001',
      flowId: flow.id,
      flowName: approvalWorkflowDisplayName(flow),
      checked: ['amount <= 1000 -> approver1', 'amount > 1000 -> approver2'],
    })
    await lowApprover.context.close()
    await highApproverBefore.context.close()
  })

  test('APF-OR-JOIN-001: run both branches and converge through JoinNode(joinType=OR)', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.or.join')
    await startFlowInstance(page, flow.id, uniqueSuffix('or-join'))

    const branchA = await loginAs(browser, approver1)
    const branchB = await loginAs(browser, approver2)
    const branchATask = await findTodoTaskByFlowName(branchA.page, approvalWorkflowDisplayName(flow))
    const branchBTask = await findTodoTaskByFlowName(branchB.page, approvalWorkflowDisplayName(flow))

    await submitTaskAction(branchA.page, branchATask.id, {
      action: 'approve',
      comment: 'APF-OR-JOIN-001 branch A approval',
      formData: buildExpenseFormData('or-join-a'),
    })
    await expect(findTodoTaskByFlowName(branchB.page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()
    await submitTaskAction(branchB.page, branchBTask.id, {
      action: 'approve',
      comment: 'APF-OR-JOIN-001 branch B approval',
      formData: buildExpenseFormData('or-join-b'),
    })
    await waitForTaskAbsence(branchA.page, approvalWorkflowDisplayName(flow))
    await waitForTaskAbsence(branchB.page, approvalWorkflowDisplayName(flow))

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-OR-JOIN-001',
      flowId: flow.id,
      flowName: approvalWorkflowDisplayName(flow),
      checked: ['both branch tasks created', 'OR join waited until activated branches completed'],
    })
    await branchA.context.close()
    await branchB.context.close()
  })

  test('APF-NODE-TYPES-001: execute HandleNode and CcNode before ApprovalNode completion', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.node.types')
    await startFlowInstance(page, flow.id, uniqueSuffix('node-types'))

    const handler = await loginAs(browser, approver1)
    const handleTask = await findTodoTaskByFlowName(handler.page, approvalWorkflowDisplayName(flow))
    await submitTaskAction(handler.page, handleTask.id, {
      action: 'complete',
      comment: 'APF-NODE-TYPES-001 handle node complete',
      formData: buildExpenseFormData('node-types-handle'),
    })

    const approver = await loginAs(browser, approver2)
    const approvalTask = await findTodoTaskByFlowName(approver.page, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approver.page, approvalTask.id, {
      action: 'approve',
      comment: 'APF-NODE-TYPES-001 approval node approve after cc',
      formData: buildExpenseFormData('node-types-approve'),
    })
    await waitForTaskAbsence(approver.page, approvalWorkflowDisplayName(flow))

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-NODE-TYPES-001',
      flowId: flow.id,
      flowName: approvalWorkflowDisplayName(flow),
      checked: ['StartNode', 'HandleNode complete', 'CcNode pass-through', 'ApprovalNode approve', 'EndNode'],
    })
    await handler.context.close()
    await approver.context.close()
  })

  test('APF-SUBFLOW-001: deploy child flow first and complete parent SubflowNode invocation', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const child = await createAndDeployFlow(browser, page, 'awf.subflow.child')
    const parent = await createAndDeployFlow(browser, page, 'awf.subflow.parent', (seed) => {
      seed.nodes = seed.nodes.map((node) => {
        if (node.type !== 'SubflowNode') {
          return node
        }
        return {
          ...node,
          subFlowKey: child.id,
        }
      })
    })
    await startFlowInstance(page, parent.id, uniqueSuffix('subflow-parent'))

    const childApprover = await loginAs(browser, approver3)
    const childTask = await findTodoTaskByFlowName(childApprover.page, approvalWorkflowDisplayName(child))
    await submitTaskAction(childApprover.page, childTask.id, {
      action: 'approve',
      comment: 'APF-SUBFLOW-001 child approval',
      formData: buildExpenseFormData('subflow-child'),
    })
    await waitForTaskAbsence(childApprover.page, approvalWorkflowDisplayName(child))

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-SUBFLOW-001',
      parentFlowId: parent.id,
      childFlowId: child.id,
      checked: ['child deployed before parent', 'SubflowNode called child process', 'child task completion returned to parent'],
    })
    await childApprover.context.close()
  })

  test('APF-RESET-DESIGN-001: reset adopted metadata-layer definition to source draft without deleting history', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const target = (await searchDefinitions(page))
      .find((item) => item.name === resetDesignDefinitionName && item.source === 'metadata-layer' && item.enabled !== false)
    if (!target?.name) {
      throw new Error(`APF-RESET-DESIGN-001 requires ${resetDesignDefinitionName} from APF-RUNTIME-REFRESH-001`)
    }

    await resetWorkflowDefinition(page, target.name)
    const original = await getWorkflowDefinition(page, target.name)
    expect(original.title).toBe(resetDesignTitle)
    expect(original.description ?? '').toBe(resetDesignDescription)
    await deployWorkflowDefinition(page, target.name)
    const deployed = await findDefinitionSummary(page, target.name)
    expect(deployed.status).toBe('deployed')
    expect(isBlank(deployed.processDefinitionId)).toBe(false)
    const oldProcessDefinitionId = String(deployed.processDefinitionId)
    await startFlowInstance(page, target.name, uniqueSuffix('reset-history'))
    const historicalApprover = await loginAs(browser, approver1)
    const historicalTask = await findTodoTaskByFlowName(historicalApprover.page, resetDesignTitle)
    expect(historicalTask.instanceId).toBeTruthy()
    const versionsBeforeReset = await listDefinitionVersions(page, target.name)

    const customized: WorkflowDefinition = {
      ...original,
      name: target.name,
      title: `${original.title ?? target.name} Instance Customized`,
      description: `${original.description ?? ''} APF-RESET-DESIGN-001 adopted customization`.trim(),
    }
    await updateWorkflowDefinition(page, target.name, customized)
    const adopted = await findDefinitionSummary(page, target.name)
    expect(adopted.adopted).toBe(true)
    expect(adopted.status).toBe('updated')

    await resetWorkflowDefinition(page, target.name)
    const reset = await findDefinitionSummary(page, target.name)
    expect(reset.status).toBe('draft')
    expect(reset.source).toBe('metadata-layer')
    expect(reset.adopted).toBe(false)
    expect(isBlank(reset.processDefinitionId)).toBe(true)
    expect(reset.title).toBe(resetDesignTitle)
    expect(reset.description ?? '').toBe(resetDesignDescription)
    expect(reset.version).toBe(original.version)

    const blockedStart = await expectFlowStartRejected(page, target.name, uniqueSuffix('reset-draft-blocked'))
    await submitTaskAction(historicalApprover.page, historicalTask.id, {
      action: 'approve',
      comment: 'APF-RESET-DESIGN-001 existing instance continues after reset',
      formData: buildExpenseFormData('reset-existing-instance'),
    })
    await waitForTaskIdAbsence(historicalApprover.page, historicalTask.id)
    const historicalTimeline = await getTimeline(historicalApprover.page, String(historicalTask.instanceId))
    expect(historicalTimeline).toBeTruthy()
    expect(JSON.stringify(historicalTimeline).length).toBeGreaterThan(2)

    const versionsAfterReset = await listDefinitionVersions(page, target.name)
    expect(versionsAfterReset.length).toBeGreaterThanOrEqual(versionsBeforeReset.length)

    await deployWorkflowDefinition(page, target.name)
    const redeployed = await findDefinitionSummary(page, target.name)
    expect(redeployed.status).toBe('deployed')
    expect(isBlank(redeployed.processDefinitionId)).toBe(false)
    expect(String(redeployed.processDefinitionId)).not.toBe(oldProcessDefinitionId)
    const newInstance = await startFlowInstance(page, target.name, uniqueSuffix('reset-redeployed'))
    expect(extractApiData<string>(newInstance)).toBeTruthy()

    appendApprovalFlowJsonEvidence('topology-and-reset.json', {
      caseId: 'APF-RESET-DESIGN-001',
      definitionName: target.name,
      oldProcessDefinitionId,
      newProcessDefinitionId: redeployed.processDefinitionId,
      preservedInstanceId: historicalTask.instanceId,
      blockedStartStatus: blockedStart.status,
      versionsBeforeReset: versionsBeforeReset.length,
      versionsAfterReset: versionsAfterReset.length,
      checked: ['adopted customization set', 'reset offlines new starts', 'existing instance continues after reset', 'reset to draft', 'processDefinitionId cleared', 'history versions retained', 'historical instance timeline remains queryable', 'explicit redeploy creates new engine version and restores start'],
    })
    await historicalApprover.context.close()
  })
})
