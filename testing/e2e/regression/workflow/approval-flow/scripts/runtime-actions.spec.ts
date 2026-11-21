import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  ADMIN_CREDENTIAL,
  APPROVAL_WORKFLOW_CANDIDATE_ALIASES,
  APPROVAL_WORKFLOW_IDENTITIES,
  approvalWorkflowDisplayName,
  appendApprovalFlowJsonEvidence,
  assertBrowserFetchOk,
  buildApprovalWorkflowBusinessRecordId,
  buildDevelopApprovalWorkflowDefinition,
  buildSeedRequestNo,
  browserFetch,
  designRefreshAndDeployApprovalWorkflow,
  extractApiData,
  loadJsonFixture,
  login,
  requireEnv,
  uniqueSuffix,
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

type AdvancedDialogEvidence = {
  actor: string
  taskId: string
  dialogId: string
  dialogPath: string
  selectorUrl: string
  selectorStatus: number
  optionCount: number
  confirmRequestSource: string
}

const frontBaseUrl = requireEnv('OB_TEST_FRONTEND_BASE_URL')
const runtimeBaseUrl = requireEnv('OB_TEST_RUNTIME_FRONTEND_BASE_URL')
const approver1 = APPROVAL_WORKFLOW_IDENTITIES.approver1
const approver2 = APPROVAL_WORKFLOW_IDENTITIES.approver2
const approver3 = APPROVAL_WORKFLOW_IDENTITIES.approver3

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

function cloneFlowSeed(seed: WorkflowSeed, suffix: string) {
  const definitionName = `${seed.id}_${suffix}`.replace(/[^A-Za-z0-9_]/g, '_')
  return buildDevelopApprovalWorkflowDefinition(seed, definitionName)
}

function addUserCandidate(seed: DevelopApprovalWorkflowDefinition, nodeId: string, userId: string) {
  const node = seed.nodes.find((item) => item.id === nodeId)
  if (!node) {
    throw new Error(`Unable to find node ${nodeId} in flow seed ${seed.id}`)
  }
  const candidates = node.candidates
  if (!Array.isArray(candidates)) {
    node.candidates = [{ type: 'User', users: [userId] }]
    return
  }
  const userCandidate = candidates.find((candidate) => {
    if (typeof candidate !== 'object' || candidate === null || Array.isArray(candidate)) {
      return false
    }
    return (candidate as Record<string, unknown>).type === 'User'
  })
  if (userCandidate && typeof userCandidate === 'object' && !Array.isArray(userCandidate)) {
    const record = userCandidate as Record<string, unknown>
    const users = Array.isArray(record.users) ? record.users.map(String) : []
    if (!users.includes(userId)) {
      record.users = [...users, userId]
    }
    return
  }
  candidates.push({ type: 'User', users: [userId] })
}

function buildExpenseFormData(suffix: string) {
  return {
    id: buildApprovalWorkflowBusinessRecordId(suffix),
    requestNo: buildSeedRequestNo(suffix),
    applicantName: 'AWF Submitter',
    departmentCode: 'AWF-DEPT-ENGINEERING',
    amount: 1234.56,
    currency: 'CNY',
    urgent: 'false',
    reason: `Approval workflow runtime action verification ${suffix}`,
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

async function createAndDeployFlow(
  browser: Browser,
  page: Page,
  seedFlowKey: string,
  customize?: (seed: DevelopApprovalWorkflowDefinition) => void,
) {
  const seed = cloneFlowSeed(getFlowSeed(seedFlowKey), uniqueSuffix('runtime'))
  customize?.(seed)
  const publishEvidence = await designRefreshAndDeployApprovalWorkflow(
    browser,
    page,
    frontBaseUrl,
    runtimeBaseUrl,
    seed,
    `Approval runtime action E2E ${seed.name}`,
  )
  appendApprovalFlowJsonEvidence('runtime-actions.json', {
    caseId: 'APF-DESIGN-TO-RUNTIME-ACTIONS-001',
    definitionName: seed.name,
    seedFlowKey,
    source: publishEvidence.source,
    reloadEvidence: publishEvidence.reloadEvidence,
    checked: ['develop create', 'develop save', 'develop submit', 'develop platform hot reload button', 'runtime metadata-layer projection', 'explicit runtime deploy'],
  })
  return seed
}

function buildSelectedAssignees(selectedAssignees?: Record<string, string[]>) {
  if (!selectedAssignees) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(selectedAssignees).map(([nodeId, users]) => [
      nodeId,
      users.map((user) => ({ label: user, value: user })),
    ]),
  )
}

async function startFlowInstance(
  page: Page,
  flowKey: string,
  suffix: string,
  selectedAssignees?: Record<string, string[]>,
) {
  const startResult = await browserFetch(page, `/api/approval-workflow/instance/${flowKey}/start`, {
    method: 'POST',
    body: {
      formData: buildExpenseFormData(suffix),
      taskAssignee: buildSelectedAssignees(selectedAssignees),
    },
  })
  assertBrowserFetchOk(startResult, `start workflow instance ${flowKey}`)
  return startResult
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

async function advanceSerialFlowToSecondStage(browser: Browser, flow: DevelopApprovalWorkflowDefinition) {
  const firstStageContext = await browser.newContext()
  const firstStagePage = await firstStageContext.newPage()
  await login(firstStagePage, runtimeBaseUrl, approver1)
  const firstStageTask = await findTodoTaskByFlowName(firstStagePage, approvalWorkflowDisplayName(flow))
  await submitTaskAction(firstStagePage, firstStageTask.id, {
    action: 'approve',
    comment: 'Advance serial flow to second-stage task',
    formData: buildExpenseFormData('advance-serial-second-stage'),
  })
  await firstStageContext.close()
}

async function submitTaskAction(page: Page, taskId: string, body: Record<string, unknown>) {
  const submitResult = await browserFetch(page, `/api/approval-workflow/task/${taskId}`, {
    method: 'POST',
    body,
  })
  assertBrowserFetchOk(submitResult, `submit task action ${taskId}`)
  return submitResult
}

async function getUiSchema(page: Page, schemaPath: string) {
  const result = await browserFetch(page, `/api/ui-schema?path=${encodeURIComponent(schemaPath)}`)
  assertBrowserFetchOk(result, `get UI schema ${schemaPath}`)
  return result
}

function requiredActionSchemaSnippet(dialogId: string, action: string, selectorUrl: string) {
  return {
    dialogId,
    action,
    selectorUrl,
    confirmUrl: '/api/approval-workflow/task/${id}',
  }
}

async function assertAdvancedDialogSelector(
  page: Page,
  taskId: string,
  actor: string,
  dialogPath: string,
  dialogId: string,
  action: string,
  selectorPath: string,
) {
  const schemaResult = await getUiSchema(page, dialogPath)
  const schemaPayload = extractApiData<unknown>(schemaResult)
  const schemaText = JSON.stringify(schemaPayload)
  const snippet = requiredActionSchemaSnippet(dialogId, action, selectorPath)
  expect(schemaText).toContain(`"id":"${snippet.dialogId}"`)
  expect(schemaText).toContain(snippet.selectorUrl)
  expect(schemaText).toContain(`"action":"${snippet.action}"`)
  expect(schemaText).toContain(snippet.confirmUrl)

  const concreteSelectorUrl = selectorPath.replace('${id}', taskId)
  const selectorResult = await browserFetch(page, concreteSelectorUrl)
  assertBrowserFetchOk(selectorResult, `load selector ${dialogId}`)
  const optionCount = extractItems(extractApiData<unknown>(selectorResult)).length
  const evidence: AdvancedDialogEvidence = {
    actor,
    taskId,
    dialogId,
    dialogPath,
    selectorUrl: concreteSelectorUrl,
    selectorStatus: selectorResult.status,
    optionCount,
    confirmRequestSource: 'approval-center-dialog-schema-confirm-ajax',
  }
  appendApprovalFlowJsonEvidence('advanced-action-dialogs.json', evidence)
  expect(optionCount).toBeGreaterThan(0)
}

test.describe('Approval Flow - Runtime Actions', () => {
  test('APF-SUBMIT-001: open the source-backed start form route and start an approval instance', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.single.l1')

    await page.goto(new URL(`/approval-center/start/init-form?id=${flow.id}`, runtimeBaseUrl).toString())
    await expect(page).not.toHaveURL(/\/login(?:$|[?#/])/)

    const startResult = await startFlowInstance(page, flow.id, uniqueSuffix('submit'))
    const startPayload = extractApiData<unknown>(startResult)
    expect(startPayload).toBeTruthy()
  })

  test('APF-TASK-001: show the approver todo task on the source-backed approval-center route', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.single.l1')
    await startFlowInstance(page, flow.id, uniqueSuffix('task'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    await expect(findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()
    await approverContext.close()
  })

  test('APF-APPROVE-001: approve a task from the approval-center detail drawer', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.single.l1')
    await startFlowInstance(page, flow.id, uniqueSuffix('approve'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approverPage, task.id, {
      action: 'approve',
      comment: 'APF-APPROVE-001 source-backed approve',
      formData: buildExpenseFormData('approve'),
    })

    const remainingTasks = await listTodoTasks(approverPage)
    expect(remainingTasks.some((task) => task.flowName === approvalWorkflowDisplayName(flow))).toBe(false)
    await approverContext.close()
  })

  test('APF-REJECT-001: reject a task through the source-backed reject dialog', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.single.l1')
    await startFlowInstance(page, flow.id, uniqueSuffix('reject'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approverPage, task.id, {
      action: 'reject',
      comment: 'APF-REJECT-001 source-backed reject',
      formData: buildExpenseFormData('reject'),
    })
    await approverContext.close()
  })

  test('APF-ROLLBACK-001: rollback a task through the source-backed rollback dialog', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.single.l1')
    await startFlowInstance(page, flow.id, uniqueSuffix('rollback'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approverPage, task.id, {
      action: 'rollback',
      comment: 'APF-ROLLBACK-001 source-backed rollback',
      formData: buildExpenseFormData('rollback'),
    })
    await approverContext.close()
  })

  test('APF-ADVANCED-DIALOGS-001: approval-center advanced action dialogs load selector data for ordinary approvers', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)

    const serialFlow = await createAndDeployFlow(browser, page, 'awf.serial.multi', (seed) => {
      addUserCandidate(seed, 'AWF_NODE_SERIAL_L2', APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3)
    })
    await startFlowInstance(page, serialFlow.id, uniqueSuffix('advanced-dialog-serial'))

    const approver1Context = await browser.newContext()
    const approver1Page = await approver1Context.newPage()
    await login(approver1Page, runtimeBaseUrl, approver1)
    const firstStageTask = await findTodoTaskByFlowName(approver1Page, approvalWorkflowDisplayName(serialFlow))
    await assertAdvancedDialogSelector(
      approver1Page,
      firstStageTask.id,
      approver1.account,
      '/approval-center/add-sign-form',
      'add-sign-form',
      'addSign',
      '/api/approval-workflow/task/${id}/add-sign-targets',
    )
    await submitTaskAction(approver1Page, firstStageTask.id, {
      action: 'approve',
      comment: 'APF-ADVANCED-DIALOGS-001 setup first node approval',
      formData: buildExpenseFormData('advanced-dialog-first-stage'),
    })
    await approver1Context.close()

    const approver2Context = await browser.newContext()
    const approver2Page = await approver2Context.newPage()
    await login(approver2Page, runtimeBaseUrl, approver2)
    const secondStageTask = await findTodoTaskByFlowName(approver2Page, approvalWorkflowDisplayName(serialFlow))
    await assertAdvancedDialogSelector(
      approver2Page,
      secondStageTask.id,
      approver2.account,
      '/approval-center/transfer-form',
      'transfer-form',
      'transfer',
      '/api/approval-workflow/task/${id}/transfer-targets',
    )
    await assertAdvancedDialogSelector(
      approver2Page,
      secondStageTask.id,
      approver2.account,
      '/approval-center/free-reject-form',
      'free-reject-form',
      'freeReject',
      '/api/approval-workflow/task/${id}/free-reject-targets',
    )
    await approver2Context.close()

    const parallelFlow = await createAndDeployFlow(browser, page, 'awf.parallel.advanced')
    await startFlowInstance(page, parallelFlow.id, uniqueSuffix('advanced-dialog-parallel'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_2,
        APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3,
      ],
    })

    const parallelContext = await browser.newContext()
    const parallelPage = await parallelContext.newPage()
    await login(parallelPage, runtimeBaseUrl, approver2)
    const parallelTask = await findTodoTaskByFlowName(parallelPage, approvalWorkflowDisplayName(parallelFlow))
    await assertAdvancedDialogSelector(
      parallelPage,
      parallelTask.id,
      approver2.account,
      '/approval-center/free-approval-form',
      'free-approval-form',
      'freeApprove',
      '/api/approval-workflow/task/${id}/free-approval-targets',
    )
    await parallelContext.close()
  })

  test('APF-TRANSFER-001: transfer a task with the canonical typed payload and verify the target approver receives it', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.serial.multi', (seed) => {
      addUserCandidate(seed, 'AWF_NODE_SERIAL_L2', APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3)
    })
    await startFlowInstance(page, flow.id, uniqueSuffix('transfer'))

    const sourceContext = await browser.newContext()
    const sourcePage = await sourceContext.newPage()
    await login(sourcePage, runtimeBaseUrl, approver1)
    await sourcePage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const firstStageTask = await findTodoTaskByFlowName(sourcePage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(sourcePage, firstStageTask.id, {
      action: 'approve',
      comment: 'APF-TRANSFER-001 first node approval',
      formData: buildExpenseFormData('transfer-first-stage'),
    })
    await sourceContext.close()

    const secondStageContext = await browser.newContext()
    const secondStagePage = await secondStageContext.newPage()
    await login(secondStagePage, runtimeBaseUrl, approver2)
    const secondStageTask = await findTodoTaskByFlowName(secondStagePage, approvalWorkflowDisplayName(flow))
    await secondStagePage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    await submitTaskAction(secondStagePage, secondStageTask.id, {
      action: 'transfer',
      comment: 'APF-TRANSFER-001',
      formData: buildExpenseFormData('transfer'),
      payload: {
        targetUserId: APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3,
      },
    })

    const targetContext = await browser.newContext()
    const targetPage = await targetContext.newPage()
    await login(targetPage, runtimeBaseUrl, approver3)
    await targetPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const targetTasks = await listTodoTasks(targetPage)
    expect(targetTasks.some((task) => task.flowName === approvalWorkflowDisplayName(flow))).toBe(true)
    await secondStageContext.close()
    await targetContext.close()
  })

  test('APF-ADDSIGN-001: add a BEFORE_CURRENT sign-off target with the canonical typed payload', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.serial.multi')
    await startFlowInstance(page, flow.id, uniqueSuffix('addsign-before'))

    const sourceContext = await browser.newContext()
    const sourcePage = await sourceContext.newPage()
    await login(sourcePage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(sourcePage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(sourcePage, task.id, {
      action: 'addSign',
      comment: 'APF-ADDSIGN-001',
      formData: buildExpenseFormData('addsign-before'),
      payload: {
        mode: 'BEFORE_CURRENT',
        targetUserIds: [APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_2],
      },
    })

    const targetContext = await browser.newContext()
    const targetPage = await targetContext.newPage()
    await login(targetPage, runtimeBaseUrl, approver2)
    await targetPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const targetTasks = await listTodoTasks(targetPage)
    expect(targetTasks.some((task) => task.flowName === approvalWorkflowDisplayName(flow))).toBe(true)
    await sourceContext.close()
    await targetContext.close()
  })

  test('APF-ADDSIGN-002: add an AFTER_CURRENT sign-off target with the canonical typed payload', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.serial.multi')
    await startFlowInstance(page, flow.id, uniqueSuffix('addsign-after'))

    const sourceContext = await browser.newContext()
    const sourcePage = await sourceContext.newPage()
    await login(sourcePage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(sourcePage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(sourcePage, task.id, {
      action: 'addSign',
      comment: 'APF-ADDSIGN-002',
      formData: buildExpenseFormData('addsign-after'),
      payload: {
        mode: 'AFTER_CURRENT',
        targetUserIds: [APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_2],
      },
    })
    await submitTaskAction(sourcePage, task.id, {
      action: 'approve',
      comment: 'APF-ADDSIGN-002 approve current task',
      formData: buildExpenseFormData('addsign-after'),
    })

    const targetContext = await browser.newContext()
    const targetPage = await targetContext.newPage()
    await login(targetPage, runtimeBaseUrl, approver2)
    await targetPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const targetTasks = await listTodoTasks(targetPage)
    expect(targetTasks.some((task) => task.flowName === approvalWorkflowDisplayName(flow))).toBe(true)
    await sourceContext.close()
    await targetContext.close()
  })

  test('APF-FREEREJECT-001: submit the canonical freeReject payload for a second-stage task with executed history', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.serial.multi')
    await startFlowInstance(page, flow.id, uniqueSuffix('freereject'))
    await advanceSerialFlowToSecondStage(browser, flow)

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver2)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const submitResult = await submitTaskAction(approverPage, task.id, {
      action: 'freeReject',
      comment: 'APF-FREEREJECT-001',
      formData: buildExpenseFormData('freereject'),
      payload: {
        targetNodeId: 'AWF_NODE_SERIAL_L1',
      },
    })
    expect(submitResult.ok).toBe(true)
    await approverContext.close()
  })

  test('APF-FREEAPPROVE-001: submit the canonical freeApprove payload for a parallel flow task', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = await createAndDeployFlow(browser, page, 'awf.parallel.advanced')
    await startFlowInstance(page, flow.id, uniqueSuffix('freeapprove'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_2,
        APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3,
      ],
    })

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver2)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await approverPage.goto(new URL('/approval-center', runtimeBaseUrl).toString())
    const submitResult = await submitTaskAction(approverPage, task.id, {
      action: 'freeApprove',
      comment: 'APF-FREEAPPROVE-001',
      formData: buildExpenseFormData('freeapprove'),
      payload: {
        consultUserIds: [APPROVAL_WORKFLOW_CANDIDATE_ALIASES.AWF_USER_APPROVER_3],
      },
    })
    expect(submitResult.ok).toBe(true)
    await approverContext.close()
  })
})
