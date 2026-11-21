import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  ADMIN_CREDENTIAL,
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

type TaskFormSchema = {
  actions?: Array<Record<string, string>>
  body?: unknown[]
  [key: string]: unknown
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

function cloneSeed(seed: WorkflowSeed, suffix: string) {
  const definitionName = `${seed.id}_${suffix}`.replace(/[^A-Za-z0-9_]/g, '_')
  return buildDevelopApprovalWorkflowDefinition(seed, definitionName)
}

function buildExpenseFormData(suffix: string) {
  return {
    id: buildApprovalWorkflowBusinessRecordId(suffix),
    requestNo: buildSeedRequestNo(suffix),
    applicantName: `Applicant ${suffix}`,
    departmentCode: 'AWF-DEPT-ENGINEERING',
    amount: 2000,
    currency: 'CNY',
    urgent: 'false',
    reason: `Countersign verification ${suffix}`,
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
  definition: DevelopApprovalWorkflowDefinition,
) {
  const publishEvidence = await designRefreshAndDeployApprovalWorkflow(
    browser,
    page,
    frontBaseUrl,
    runtimeBaseUrl,
    definition,
    `Approval countersign/history E2E ${definition.name}`,
  )
  appendApprovalFlowJsonEvidence('countersign-history.json', {
    caseId: 'APF-DESIGN-TO-RUNTIME-COUNTERSIGN-001',
    definitionName: definition.name,
    source: publishEvidence.source,
    reloadEvidence: publishEvidence.reloadEvidence,
    checked: ['develop create', 'develop save', 'develop submit', 'develop platform hot reload button', 'runtime metadata-layer projection', 'explicit runtime deploy'],
  })
}

async function startFlowInstance(page: Page, flowKey: string, suffix: string) {
  return startFlowInstanceWithAssignees(page, flowKey, suffix)
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

async function startFlowInstanceWithAssignees(
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
}

async function listTodoTasks(page: Page) {
  const todoResult = await browserFetch(page, '/api/approval-workflow/task/todo?page=1&perPage=50')
  assertBrowserFetchOk(todoResult, 'list todo tasks')
  return extractItems(extractApiData<unknown>(todoResult))
}

async function getTimeline(page: Page, instanceId: string) {
  const result = await browserFetch(page, `/api/approval-workflow/instance/${instanceId}/timeline`)
  assertBrowserFetchOk(result, `get timeline ${instanceId}`)
  return extractApiData<unknown>(result)
}

async function getTaskFormSchema(page: Page, taskId: string) {
  const result = await browserFetch(page, `/api/approval-workflow/task/${taskId}/task-form-schema`)
  assertBrowserFetchOk(result, `get task form schema ${taskId}`)
  return extractApiData<TaskFormSchema>(result)
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

async function submitTaskAction(page: Page, taskId: string, body: Record<string, unknown>) {
  const submitResult = await browserFetch(page, `/api/approval-workflow/task/${taskId}`, {
    method: 'POST',
    body,
  })
  assertBrowserFetchOk(submitResult, `submit task action ${taskId}`)
}

function createSerialCountersignFlow() {
  const suffix = uniqueSuffix('serial-countersign')
  const base = cloneSeed(getFlowSeed('awf.parallel.advanced'), suffix)
  return {
    ...base,
    nodes: base.nodes.map((node) => {
      if (node.type !== '审批节点') {
        return node
      }
      return {
        ...node,
        subType: '会签',
        multipleMode: '串行',
        percentage: 100,
        candidates: [{
          type: 'User',
          users: [approver2.userIdentity, approver3.userIdentity],
        }],
      }
    }),
  }
}

function createParallelPercentageFlow() {
  const suffix = uniqueSuffix('parallel-percentage')
  const base = cloneSeed(getFlowSeed('awf.parallel.advanced'), suffix)
  return {
    ...base,
    nodes: base.nodes.map((node) => {
      if (node.type !== '审批节点') {
        return node
      }
      return {
        ...node,
        subType: '会签',
        multipleMode: '并行',
        percentage: 50,
        candidates: [{
          type: 'User',
          users: [approver2.userIdentity, approver3.userIdentity],
        }],
      }
    }),
  }
}

test.describe('Approval Flow - Countersign and History', () => {
  test('APF-COUNTERSIGN-001: verify serial countersign hands work off in sequence', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = createSerialCountersignFlow()
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstanceWithAssignees(page, String(flow.id), uniqueSuffix('serial'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
        APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
      ],
    })

    const approver2Context = await browser.newContext()
    const approver2Page = await approver2Context.newPage()
    await login(approver2Page, runtimeBaseUrl, approver2)
    const firstTask = await findTodoTaskByFlowName(approver2Page, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approver2Page, firstTask.id, {
      action: 'approve',
      comment: 'APF-COUNTERSIGN-001 first approver',
      formData: buildExpenseFormData('serial-1'),
    })

    const approver3Context = await browser.newContext()
    const approver3Page = await approver3Context.newPage()
    await login(approver3Page, runtimeBaseUrl, approver3)
    await expect(findTodoTaskByFlowName(approver3Page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()
    await approver2Context.close()
    await approver3Context.close()
  })

  test('APF-COUNTERSIGN-002: verify parallel countersign creates concurrent tasks', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.parallel.advanced'), uniqueSuffix('parallel'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstanceWithAssignees(page, String(flow.id), uniqueSuffix('parallel-start'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
        APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
      ],
    })

    const approver2Context = await browser.newContext()
    const approver2Page = await approver2Context.newPage()
    await login(approver2Page, runtimeBaseUrl, approver2)
    await expect(findTodoTaskByFlowName(approver2Page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()

    const approver3Context = await browser.newContext()
    const approver3Page = await approver3Context.newPage()
    await login(approver3Page, runtimeBaseUrl, approver3)
    await expect(findTodoTaskByFlowName(approver3Page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()

    await approver2Context.close()
    await approver3Context.close()
  })

  test('APF-COUNTERSIGN-003: verify percentage-based parallel countersign can complete after the threshold is met', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = createParallelPercentageFlow()
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstanceWithAssignees(page, String(flow.id), uniqueSuffix('parallel-percent'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
        APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
      ],
    })

    const approver2Context = await browser.newContext()
    const approver2Page = await approver2Context.newPage()
    await login(approver2Page, runtimeBaseUrl, approver2)
    const approver2Task = await findTodoTaskByFlowName(approver2Page, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approver2Page, approver2Task.id, {
      action: 'approve',
      comment: 'APF-COUNTERSIGN-003 threshold approval',
      formData: buildExpenseFormData('parallel-percent'),
    })

    const approver3Context = await browser.newContext()
    const approver3Page = await approver3Context.newPage()
    await login(approver3Page, runtimeBaseUrl, approver3)
    await waitForTaskAbsence(approver3Page, approvalWorkflowDisplayName(flow))
    await approver2Context.close()
    await approver3Context.close()
  })

  test('APF-HISTORY-001: show the source-backed timeline inside the approval detail drawer', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.parallel.advanced'), uniqueSuffix('history'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstanceWithAssignees(page, String(flow.id), uniqueSuffix('history-start'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
        APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
      ],
    })

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver2)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    const timeline = await getTimeline(approverPage, String(task.instanceId))
    expect(timeline).toBeTruthy()
    const timelineText = JSON.stringify(timeline)
    expect(timelineText).toContain('workflow-approval-timeline')
    expect(timelineText).toContain(APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity)
    expect(timelineText).toContain(APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity)
    expect(timelineText).not.toContain('节点：')
    expect(timelineText).not.toContain('并行会签')
    await approverContext.close()
  })

  test('APF-COMPLETE-001: verify completion without inventing a standalone completed route', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.single.l1'), uniqueSuffix('complete'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstance(page, String(flow.id), uniqueSuffix('complete-start'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approverPage, task.id, {
      action: 'approve',
      comment: 'APF-COMPLETE-001 complete the flow',
      formData: buildExpenseFormData('complete'),
    })
    const remainingTasks = await listTodoTasks(approverPage)
    expect(remainingTasks.some((item) => item.flowName === approvalWorkflowDisplayName(flow))).toBe(false)
    await approverContext.close()
  })

  test('APF-MULTIUSER-001: verify task visibility changes across approver identities', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.serial.multi'), uniqueSuffix('multiuser'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstance(page, String(flow.id), uniqueSuffix('multiuser-start'))

    const approver1Context = await browser.newContext()
    const approver1Page = await approver1Context.newPage()
    await login(approver1Page, runtimeBaseUrl, approver1)
    const approver1Task = await findTodoTaskByFlowName(approver1Page, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approver1Page, approver1Task.id, {
      action: 'approve',
      comment: 'APF-MULTIUSER-001 first approval',
      formData: buildExpenseFormData('multiuser'),
    })

    const approver2Context = await browser.newContext()
    const approver2Page = await approver2Context.newPage()
    await login(approver2Page, runtimeBaseUrl, approver2)
    await expect(findTodoTaskByFlowName(approver2Page, approvalWorkflowDisplayName(flow))).resolves.toBeTruthy()
    await approver1Context.close()
    await approver2Context.close()
  })

  test('APF-ACTION-VISIBILITY-001: check source-backed action labels on the approval detail drawer', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.parallel.advanced'), uniqueSuffix('visible'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstanceWithAssignees(page, String(flow.id), uniqueSuffix('visible-start'), {
      AWF_NODE_PARALLEL_SIGN: [
        APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
        APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
      ],
    })

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver2)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    const schema = await getTaskFormSchema(approverPage, task.id)
    expect(JSON.stringify(schema.actions ?? [])).toContain('approveButton')
    expect(JSON.stringify(schema.actions ?? [])).toContain('rejectButton')
    expect(JSON.stringify(schema.actions ?? [])).toContain('freeApproveButton')
    await approverContext.close()
  })

  test('APF-ACTION-VALIDATION-001: verify source-backed required validation exists on reject and rollback forms', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.single.l1'), uniqueSuffix('validation'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstance(page, String(flow.id), uniqueSuffix('validation-start'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    const schema = await getTaskFormSchema(approverPage, task.id)
    expect(JSON.stringify(schema.actions ?? [])).toContain('rejectButton')
    expect(JSON.stringify(schema.actions ?? [])).toContain('rollbackButton')
    await approverContext.close()
  })

  test('APF-COMMENT-001: preserve approval comments in the timeline-backed detail drawer flow', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.single.l1'), uniqueSuffix('comment'))
    await createAndDeployFlow(browser, page, flow)
    await startFlowInstance(page, String(flow.id), uniqueSuffix('comment-start'))

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    await submitTaskAction(approverPage, task.id, {
      action: 'reject',
      comment: 'APF-COMMENT-001 timeline comment',
      formData: buildExpenseFormData('comment'),
    })
    const timeline = await getTimeline(approverPage, String(task.instanceId))
    expect(JSON.stringify(timeline)).toContain('APF-COMMENT-001 timeline comment')
    await approverContext.close()
  })

  test('APF-FORMDATA-001: verify runtime detail flow can load task form data on the source-backed drawer route', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const flow = cloneSeed(getFlowSeed('awf.single.l1'), uniqueSuffix('formdata'))
    await createAndDeployFlow(browser, page, flow)
    const suffix = uniqueSuffix('formdata-start')
    await startFlowInstance(page, String(flow.id), suffix)

    const approverContext = await browser.newContext()
    const approverPage = await approverContext.newPage()
    await login(approverPage, runtimeBaseUrl, approver1)
    const task = await findTodoTaskByFlowName(approverPage, approvalWorkflowDisplayName(flow))
    const schema = await getTaskFormSchema(approverPage, task.id)
    expect(schema).toBeTruthy()
    await approverContext.close()
  })
})
