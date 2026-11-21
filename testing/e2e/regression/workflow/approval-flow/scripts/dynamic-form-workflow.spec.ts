import { expect, test, type Browser, type Page } from '@playwright/test'

import {
  ADMIN_CREDENTIAL,
  APPROVAL_WORKFLOW_IDENTITIES,
  approvalWorkflowDisplayName,
  appendApprovalFlowJsonEvidence,
  assertBrowserFetchOk,
  browserFetch,
  ensureApprovalWorkflowDynamicForm,
  extractApiData,
  login,
  requireEnv,
  uniqueSuffix,
} from './support'

type TodoTask = {
  id: string
  flowName?: string
  instanceId?: string
  fields?: Record<string, unknown>
}

type WorkflowRecord = {
  id?: string
  action?: string
  formKey?: string
  formId?: string
  instanceId?: string
  taskId?: string
  changedData?: Record<string, unknown>
}

const runtimeBaseUrl = requireEnv('OB_TEST_RUNTIME_FRONTEND_BASE_URL')
const approver1 = APPROVAL_WORKFLOW_IDENTITIES.approver1

function dynamicFormPermissions(writable: boolean) {
  return [
    permission('requestNo', 'Request Number', writable, true),
    permission('applicantName', 'Applicant Name', writable, true),
    permission('departmentCode', 'Department Code', writable, true),
    permission('amount', 'Amount', writable, true),
    permission('currency', 'Currency', writable, true),
    permission('urgent', 'Urgent', writable, true),
    permission('reason', 'Reason', writable, true),
    permission('requestDate', 'Request Date', writable, true),
  ]
}

function permission(name: string, label: string, writable: boolean, required: boolean) {
  return {
    name,
    label,
    visible: true,
    writable,
    required,
  }
}

function buildDynamicFormData(suffix: string) {
  const digits = suffix.replace(/\D/g, '').slice(-4).padStart(4, '0')
  return {
    requestNo: `AWF-REQ-${digits}`,
    applicantName: 'AWF Submitter',
    departmentCode: 'AWF-DEPT-ENGINEERING',
    amount: 2345.67,
    currency: 'CNY',
    urgent: 'false',
    reason: `Dynamic form workflow binding ${suffix}`,
    requestDate: '2026-04-25',
  }
}

function extractRecords<T>(result: unknown): T[] {
  if (Array.isArray(result)) {
    return result as T[]
  }
  if (!result || typeof result !== 'object') {
    return []
  }
  const record = result as Record<string, unknown>
  if (Array.isArray(record.items)) {
    return record.items as T[]
  }
  if ('data' in record) {
    return extractRecords<T>(record.data)
  }
  if ('result' in record) {
    return extractRecords<T>(record.result)
  }
  return []
}

async function createAndDeployDefaultDynamicFormFlow(page: Page, formKey: string) {
  const suffix = uniqueSuffix('dynamic')
  const id = `AWF_FLOW_DYNAMIC_FORM_${suffix}`.replace(/[^A-Za-z0-9_]/g, '_')
  const title = `AWF Dynamic Form Binding ${suffix}`
  const flow = {
    formatVersion: 1,
    id,
    flowKey: id,
    name: id,
    title,
    description: `Created through the runtime workflow entry for dynamic form ${formKey}`,
    version: 1,
    enabled: true,
    form: {
      formType: 'default',
      formId: formKey,
    },
    formType: 'default',
    formKey,
    nodes: [
      {
        id: 'AWF_NODE_DYNAMIC_START',
        type: 'StartNode',
        name: 'Start',
        permission: dynamicFormPermissions(true),
      },
      {
        id: 'AWF_NODE_DYNAMIC_APPROVAL',
        type: 'ApprovalNode',
        subType: '单签',
        name: 'Dynamic Form Approval',
        candidates: [{ type: 'User', users: [approver1.userIdentity] }],
        actions: ['approve', 'reject'],
        permission: [permission('reason', 'Reason', true, true)],
      },
      {
        id: 'AWF_NODE_DYNAMIC_END',
        type: 'EndNode',
        name: 'End',
      },
    ],
    edges: [
      {
        id: 'AWF_EDGE_DYNAMIC_1',
        type: 'SEQUENCE',
        sourceId: 'AWF_NODE_DYNAMIC_START',
        targetId: 'AWF_NODE_DYNAMIC_APPROVAL',
        condition: {
          left: { type: 'field', field: 'amount' },
          op: 'greater_or_equal',
          right: 1,
        },
      },
      {
        id: 'AWF_EDGE_DYNAMIC_2',
        type: 'SEQUENCE',
        sourceId: 'AWF_NODE_DYNAMIC_APPROVAL',
        targetId: 'AWF_NODE_DYNAMIC_END',
      },
    ],
  }

  await page.goto(
    new URL('/system-setting/approval-workflow-definition', runtimeBaseUrl).toString(),
    { waitUntil: 'networkidle' },
  )
  const createEntry = page.getByRole('button', { name: /新建审批流/ })
  await expect(createEntry).toBeVisible()
  await createEntry.click()
  await expect(page.locator('input[name="name"]')).toBeVisible()
  await page.locator('input[name="name"]').fill(id)
  await page.locator('input[name="title"]').fill(title)
  await page.locator('textarea[name="description"]').fill(flow.description)
  await page.getByRole('combobox').click()
  await page.getByRole('option').filter({ hasText: formKey }).click()

  const createResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/approval-workflow/definition'
  })
  await page.getByRole('button', { name: '创建并编辑', exact: true }).click()
  const createResponse = await createResponsePromise
  const createPayload = await createResponse.json() as Record<string, unknown>
  expect(createResponse.ok(), JSON.stringify(createPayload)).toBe(true)
  expect(createPayload.code, JSON.stringify(createPayload)).toBe(0)

  const fieldSourcePath = `/api/approval-workflow/base-info/form/default/${formKey}/workflow-field`
  const fieldSourceResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'GET'
      && new URL(response.url()).pathname === fieldSourcePath
  })
  await page.goto(
    new URL(`/system-setting/approval-workflow-definition/${id}`, runtimeBaseUrl).toString(),
    { waitUntil: 'domcontentloaded' },
  )
  const editorFieldSourceResponse = await fieldSourceResponsePromise
  expect(editorFieldSourceResponse.ok()).toBe(true)
  await expect(page.locator('.approval-workflow-editor-canvas')).toBeVisible({ timeout: 20_000 })

  const updateResult = await browserFetch(page, `/api/approval-workflow/definition/${id}`, {
    method: 'PUT',
    body: flow,
  })
  assertBrowserFetchOk(updateResult, `save runtime-created dynamic-form workflow ${flow.id}`)

  const deployResult = await browserFetch(page, `/api/approval-workflow/definition/${flow.id}/deploy`, {
    method: 'POST',
  })
  assertBrowserFetchOk(deployResult, `deploy default dynamic-form workflow ${flow.id}`)

  appendApprovalFlowJsonEvidence('dynamic-form-workflow.json', {
    caseId: 'APF-DYNAMIC-FORM-RUNTIME-ENTRY-001',
    flowId: flow.id,
    formKey,
    entryRoute: '/system-setting/approval-workflow-definition',
    createRoute: '/system-setting/approval-workflow-definition/add',
    editorRoute: `/system-setting/approval-workflow-definition/${id}`,
    fieldSourcePath,
    checked: ['runtime workflow create entry visible', 'create form submitted through browser UI', 'deployed dynamic form selected', 'runtime editor loaded shared workflow field source', 'definition saved through runtime model API', 'explicit deploy'],
  })

  return flow
}

async function listTodoTasks(page: Page) {
  const todoResult = await browserFetch(page, '/api/approval-workflow/task/todo?page=1&perPage=50')
  assertBrowserFetchOk(todoResult, 'list todo tasks')
  return extractRecords<TodoTask>(extractApiData<unknown>(todoResult))
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
  throw new Error(`Unable to find todo task for flowName=${flowName}; visible flows=${tasks.map((task) => task.flowName ?? task.id).join(', ')}`)
}

async function loginAs(browser: Browser) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, runtimeBaseUrl, approver1)
  return { context, page }
}

test.describe('Approval Flow - Dynamic Form Workflow Bridge', () => {
  test.setTimeout(120_000)

  test('APF-DYNAMIC-FORM-001: expose default dynamic form fields and record workflow binding/history', async ({ browser, page }) => {
    await login(page, runtimeBaseUrl, ADMIN_CREDENTIAL)
    const formKey = await ensureApprovalWorkflowDynamicForm(page)

    const fieldSourceResult = await browserFetch(page, `/api/approval-workflow/base-info/form/default/${formKey}/workflow-field`)
    assertBrowserFetchOk(fieldSourceResult, `get dynamic-form workflow fields ${formKey}`)
    const fieldSource = extractApiData<Record<string, unknown>>(fieldSourceResult)
    expect(fieldSource.sourceKind).toBe('form-fields')
    expect(String(fieldSource.revisionToken ?? '')).toContain(`default:${formKey}`)
    const fields = fieldSource.fields as Array<Record<string, unknown>>
    expect(fields.map((field) => field.name)).toEqual(expect.arrayContaining(['amount', 'reason', 'requestDate']))
    expect(fields.find((field) => field.name === 'amount')?.usages).toEqual(expect.arrayContaining(['permission', 'condition']))

    const flow = await createAndDeployDefaultDynamicFormFlow(page, formKey)
    const startResult = await browserFetch(page, `/api/approval-workflow/instance/${flow.id}/start`, {
      method: 'POST',
      body: {
        formData: buildDynamicFormData(uniqueSuffix('dynamic-start')),
      },
    })
    assertBrowserFetchOk(startResult, `start default dynamic-form workflow ${flow.id}`)
    const instanceId = String(extractApiData<unknown>(startResult))
    expect(instanceId).toBeTruthy()

    const bindingResult = await browserFetch(page, `/api/approval-workflow/dynamic-form/bindings?instanceId=${encodeURIComponent(instanceId)}`)
    assertBrowserFetchOk(bindingResult, `list dynamic-form bindings ${instanceId}`)
    const bindings = extractRecords<WorkflowRecord>(extractApiData<unknown>(bindingResult))
    expect(bindings).toHaveLength(1)
    expect(bindings[0].formKey).toBe(formKey)
    expect(bindings[0].formId).toBeTruthy()

    const approver = await loginAs(browser)
    const task = await findTodoTaskByFlowName(approver.page, approvalWorkflowDisplayName(flow))
    expect(task.fields?.reason).toMatchObject({ readable: true, writable: true, required: true })

    const updatedReason = `Dynamic form workflow update ${uniqueSuffix('history')}`
    const submitResult = await browserFetch(approver.page, `/api/approval-workflow/task/${task.id}`, {
      method: 'POST',
      body: {
        action: 'approve',
        comment: 'APF-DYNAMIC-FORM-001 approve with form update',
        formData: { reason: updatedReason },
      },
    })
    assertBrowserFetchOk(submitResult, `approve dynamic-form task ${task.id}`)

    const historyResult = await browserFetch(page, `/api/approval-workflow/dynamic-form/history?instanceId=${encodeURIComponent(instanceId)}`)
    assertBrowserFetchOk(historyResult, `list dynamic-form history ${instanceId}`)
    const history = extractRecords<WorkflowRecord>(extractApiData<unknown>(historyResult))
    expect(history.map((item) => item.action)).toEqual(expect.arrayContaining(['START', 'UPDATE']))
    const updateHistory = history.find((item) => item.action === 'UPDATE')
    expect(updateHistory?.taskId).toBe(task.id)
    expect(updateHistory?.changedData?.reason).toBe(updatedReason)

    appendApprovalFlowJsonEvidence('dynamic-form-workflow.json', {
      caseId: 'APF-DYNAMIC-FORM-001',
      formKey,
      flowId: flow.id,
      instanceId,
      formId: bindings[0].formId,
      taskId: task.id,
      fieldCount: fields.length,
      historyActions: history.map((item) => item.action),
      checked: [
        'default dynamic form field source feeds permission and condition context',
        'default dynamic form workflow starts through runtime approval workflow entry',
        'central binding row records instance to form data id',
        'task approval form update writes queryable history with changedData',
      ],
    })
    await approver.context.close()
  })
})
