import fs from 'node:fs'
import path from 'node:path'

import { expect, type Browser, type Page } from '@playwright/test'

type BrowserFetchInit = {
  method?: string
  headers?: Record<string, string>
  body?: unknown
}

export type BrowserFetchResult = {
  ok: boolean
  status: number
  text: string
  json: unknown
}

export type LoginCredential = {
  account: string
  password: string
}

type ApprovalWorkflowIdentityKey = 'submitter' | 'approver1' | 'approver2' | 'approver3' | 'auditor' | 'flowAdmin'

type ApprovalWorkflowIdentity = LoginCredential & {
  employeeId: string
  userIdentity: string
}

type ApprovalWorkflowIdentitiesFile = {
  password?: string
  users?: Partial<Record<ApprovalWorkflowIdentityKey, Partial<ApprovalWorkflowIdentity>>>
  aliases?: Record<string, string>
}

type DevelopAppListItem = {
  name?: string
  appInfo?: {
    name?: string
  }
}

type DevelopAppListData = {
  items?: DevelopAppListItem[]
}

type DevelopRuntimeContainerStatus = {
  containerId?: string
  containerName?: string
  status?: string
  capabilities?: {
    supportsWorkspaceHotReload?: boolean
  }
}

export type DevelopWorkspaceReloadEvidence = {
  appName: string
  containerId: string
  containerName?: string
  status?: string
  reloadPath: string
  readyWaitMs: number
  startupPhases: string[]
}

type ApprovalWorkflowBusinessModelSeedField = {
  name: string
  title: string
  fieldType: string
  required?: boolean
  isPrimaryKey?: boolean
  validations?: Record<string, unknown>
}

type ApprovalWorkflowBusinessModelSeed = {
  businessModel: {
    name: string
    title: string
    fields: ApprovalWorkflowBusinessModelSeedField[]
  }
}

type DynamicFormSummary = {
  id: number
  name: string
  title?: string
  status?: string
  [key: string]: unknown
}

export type ApprovalWorkflowDefinitionShape = {
  id?: string
  flowKey?: string
  name?: string
  title?: string
  description?: string
  formatVersion?: number
  version?: number
  enabled?: boolean
  form?: Record<string, unknown>
  formType?: string
  formKey?: string
  nodes?: Array<Record<string, unknown>>
  edges?: Array<Record<string, unknown>>
  status?: string
  source?: string
  adopted?: boolean
  processDefinitionId?: string
  [key: string]: unknown
}

export type DevelopApprovalWorkflowDefinition = ApprovalWorkflowDefinitionShape & {
  id: string
  flowKey: string
  name: string
  title: string
  formKey: string
  nodes: Array<Record<string, unknown>>
  edges: Array<Record<string, unknown>>
}

type ApprovalWorkflowDefinitionSearchData = {
  items?: ApprovalWorkflowDefinitionShape[]
}

export type DevelopApprovalWorkflowPublishEvidence = {
  appName: string
  definitionName: string
  source: string
  reloadEvidence: DevelopWorkspaceReloadEvidence
  runtimeDefinition: ApprovalWorkflowDefinitionShape
}

type FixtureIntegrityDefinitionEvidence = {
  flowKey: string
  conditionEdges: Array<{ id?: string; sourceId?: string; targetId?: string; condition: unknown }>
  endNodeIds: string[]
  rewritesApplied: boolean
  beforeDefinitionSummary: {
    nodeCount: number
    edgeCount: number
    conditionEdgeCount: number
    endNodeCount: number
  }
  afterDefinitionSummary: {
    nodeCount: number
    edgeCount: number
    conditionEdgeCount: number
    endNodeCount: number
  }
}

export const ADMIN_CREDENTIAL: LoginCredential = {
  account: 'admin',
  password: process.env.OB_TEST_ADMIN_PASSWORD ?? '666666',
}

export const DEFAULT_AWF_E2E_PASSWORD = process.env.OB_TEST_AWF_PASSWORD ?? 'AwfRegression@2026!'
export const APPROVAL_FLOW_EVIDENCE_DIR = process.env.OB_TEST_AWF_EVIDENCE_DIR
  ?? 'tmp/testing-evidence/workflow-approval-flow'

const defaultApprovalWorkflowIdentities: Record<ApprovalWorkflowIdentityKey, ApprovalWorkflowIdentity> = {
  submitter: { account: 'awf.submitter', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_SUBMITTER', userIdentity: 'awf.submitter' },
  approver1: { account: 'awf.approver1', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_APPROVER_1', userIdentity: 'awf.approver1' },
  approver2: { account: 'awf.approver2', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_APPROVER_2', userIdentity: 'awf.approver2' },
  approver3: { account: 'awf.approver3', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_APPROVER_3', userIdentity: 'awf.approver3' },
  auditor: { account: 'awf.auditor', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_AUDITOR', userIdentity: 'awf.auditor' },
  flowAdmin: { account: 'awf.flowadmin', password: DEFAULT_AWF_E2E_PASSWORD, employeeId: 'AWF_USER_FLOW_ADMIN', userIdentity: 'awf.flowadmin' },
}

function resolveProjectPath(targetPath: string, ...segments: string[]): string {
  return path.isAbsolute(targetPath)
    ? path.join(targetPath, ...segments)
    : path.join(process.cwd(), targetPath, ...segments)
}

function loadApprovalWorkflowIdentitiesFile(): ApprovalWorkflowIdentitiesFile {
  const identitiesPath = process.env.OB_TEST_AWF_IDENTITIES_FILE
    ?? 'tmp/testing-evidence/workflow-approval-flow/approval-workflow-identities.json'
  const absolutePath = resolveProjectPath(identitiesPath)
  if (!fs.existsSync(absolutePath)) {
    return {}
  }
  return JSON.parse(fs.readFileSync(absolutePath, 'utf8')) as ApprovalWorkflowIdentitiesFile
}

function buildApprovalWorkflowIdentities(): Record<ApprovalWorkflowIdentityKey, ApprovalWorkflowIdentity> {
  const identitiesFile = loadApprovalWorkflowIdentitiesFile()
  const password = identitiesFile.password ?? DEFAULT_AWF_E2E_PASSWORD
  return Object.fromEntries(
    (Object.keys(defaultApprovalWorkflowIdentities) as ApprovalWorkflowIdentityKey[]).map((key) => {
      const defaultIdentity = defaultApprovalWorkflowIdentities[key]
      const override = identitiesFile.users?.[key] ?? {}
      const account = override.account ?? defaultIdentity.account
      return [
        key,
        {
          account,
          password: override.password ?? password,
          employeeId: override.employeeId ?? defaultIdentity.employeeId,
          userIdentity: override.userIdentity ?? account,
        },
      ]
    }),
  ) as Record<ApprovalWorkflowIdentityKey, ApprovalWorkflowIdentity>
}

export const APPROVAL_WORKFLOW_IDENTITIES = buildApprovalWorkflowIdentities()

export const APPROVAL_WORKFLOW_CANDIDATE_ALIASES: Record<string, string> = {
  AWF_USER_SUBMITTER: APPROVAL_WORKFLOW_IDENTITIES.submitter.userIdentity,
  AWF_USER_APPROVER_1: APPROVAL_WORKFLOW_IDENTITIES.approver1.userIdentity,
  AWF_USER_APPROVER_2: APPROVAL_WORKFLOW_IDENTITIES.approver2.userIdentity,
  AWF_USER_APPROVER_3: APPROVAL_WORKFLOW_IDENTITIES.approver3.userIdentity,
  AWF_USER_AUDITOR: APPROVAL_WORKFLOW_IDENTITIES.auditor.userIdentity,
  AWF_USER_FLOW_ADMIN: APPROVAL_WORKFLOW_IDENTITIES.flowAdmin.userIdentity,
}

export function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return value
}

export function optionalCredential(accountEnv: string, passwordEnv: string): LoginCredential | null {
  const account = process.env[accountEnv]
  const password = process.env[passwordEnv]
  if (!account || !password) {
    return null
  }
  return { account, password }
}

export function uniqueSuffix(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return null
  }
  return value as Record<string, unknown>
}

export function buildSeedRequestNo(seed: string): string {
  const digits = seed.replace(/\D/g, '').slice(-4).padStart(4, '0')
  return `AWF-REQ-${digits}`
}

export function buildApprovalWorkflowBusinessRecordId(seed: string): string {
  const suffixDigits = seed.replace(/\D/g, '').slice(-2).padStart(2, '0')
  const randomDigits = Math.floor(Math.random() * 1000).toString().padStart(3, '0')
  return `${Date.now()}${suffixDigits}${randomDigits}`
}

export function loadJsonFixture<T>(relativePath: string): T {
  const absolutePath = path.join(process.cwd(), relativePath)
  return JSON.parse(fs.readFileSync(absolutePath, 'utf8')) as T
}

export function writeApprovalFlowJsonEvidence(fileName: string, value: unknown): void {
  const evidenceDir = resolveProjectPath(APPROVAL_FLOW_EVIDENCE_DIR)
  fs.mkdirSync(evidenceDir, { recursive: true })
  fs.writeFileSync(path.join(evidenceDir, fileName), `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

function readApprovalFlowJsonEvidence<T>(fileName: string, fallback: T): T {
  const evidencePath = resolveProjectPath(APPROVAL_FLOW_EVIDENCE_DIR, fileName)
  if (!fs.existsSync(evidencePath)) {
    return fallback
  }
  return JSON.parse(fs.readFileSync(evidencePath, 'utf8')) as T
}

export function appendApprovalFlowJsonEvidence(fileName: string, entry: unknown): void {
  const current = readApprovalFlowJsonEvidence<{ generatedAt: string; entries: unknown[] }>(fileName, {
    generatedAt: new Date().toISOString(),
    entries: [],
  })
  writeApprovalFlowJsonEvidence(fileName, {
    generatedAt: current.generatedAt,
    updatedAt: new Date().toISOString(),
    entries: [...current.entries, entry],
  })
}

function summarizeDefinitionShape(definition: ApprovalWorkflowDefinitionShape) {
  const nodes = definition.nodes ?? []
  const edges = definition.edges ?? []
  return {
    nodeCount: nodes.length,
    edgeCount: edges.length,
    conditionEdgeCount: edges.filter((edge) => 'condition' in edge && edge.condition !== undefined && edge.condition !== null).length,
    endNodeCount: nodes.filter((node) => node.type === '结束节点' || node.type === 'EndNode').length,
  }
}

function collectConditionEdges(definition: ApprovalWorkflowDefinitionShape) {
  return (definition.edges ?? [])
    .filter((edge) => 'condition' in edge && edge.condition !== undefined && edge.condition !== null)
    .map((edge) => ({
      id: typeof edge.id === 'string' ? edge.id : undefined,
      sourceId: typeof edge.sourceId === 'string' ? edge.sourceId : undefined,
      targetId: typeof edge.targetId === 'string' ? edge.targetId : undefined,
      condition: edge.condition,
    }))
}

function collectEndNodeIds(definition: ApprovalWorkflowDefinitionShape) {
  return (definition.nodes ?? [])
    .filter((node) => node.type === '结束节点' || node.type === 'EndNode')
    .map((node) => String(node.id))
}

function recordFixtureIntegrityEvidence(
  before: ApprovalWorkflowDefinitionShape,
  after: ApprovalWorkflowDefinitionShape,
): void {
  const beforeConditionEdges = collectConditionEdges(before)
  const afterConditionEdges = collectConditionEdges(after)
  const beforeEndNodeIds = collectEndNodeIds(before)
  const afterEndNodeIds = collectEndNodeIds(after)
  const missingConditionEdges = beforeConditionEdges.filter((edge) => {
    return !afterConditionEdges.some((candidate) => candidate.id === edge.id && JSON.stringify(candidate.condition) === JSON.stringify(edge.condition))
  })
  const missingEndNodeIds = beforeEndNodeIds.filter((id) => !afterEndNodeIds.includes(id))
  const rewritesApplied = missingConditionEdges.length > 0 || missingEndNodeIds.length > 0
  const evidence: FixtureIntegrityDefinitionEvidence = {
    flowKey: String(after.flowKey ?? after.id ?? before.flowKey ?? before.id ?? 'unknown'),
    conditionEdges: afterConditionEdges,
    endNodeIds: afterEndNodeIds,
    rewritesApplied,
    beforeDefinitionSummary: summarizeDefinitionShape(before),
    afterDefinitionSummary: summarizeDefinitionShape(after),
  }
  appendApprovalFlowJsonEvidence('fixture-integrity.json', evidence)
  if (rewritesApplied) {
    throw new Error(
      `approval-flow fixture integrity failed for ${evidence.flowKey}: missing condition edges=${missingConditionEdges.length}, missing EndNode ids=${missingEndNodeIds.join(',')}`,
    )
  }
}

function buildApprovalWorkflowDynamicFormField(field: ApprovalWorkflowBusinessModelSeedField) {
  switch (field.fieldType) {
    case 'Text':
      return {
        name: field.name,
        label: field.title,
        type: '单行文本',
        required: Boolean(field.required),
        params: {
          size: Number(field.validations?.maxLength ?? 100),
          regexp: String(field.validations?.pattern ?? '.*'),
          regexpMsg: '',
        },
      }
    case 'Number':
      return {
        name: field.name,
        label: field.title,
        type: '浮点数字',
        required: Boolean(field.required),
        params: {
          min: field.validations?.minimum,
          max: field.validations?.maximum,
          precision: Number(field.validations?.precision ?? 2),
        },
      }
    case 'Date':
      return {
        name: field.name,
        label: field.title,
        type: '日期',
        required: Boolean(field.required),
        params: {
          format: 'YYYY-MM-DD',
        },
      }
    case 'LongText':
      return {
        name: field.name,
        label: field.title,
        type: '多行文本',
        required: Boolean(field.required),
        params: {
          size: Number(field.validations?.maxLength ?? 500),
          minRows: 3,
          maxRows: 8,
        },
      }
    case 'Boolean':
      return {
        name: field.name,
        label: field.title,
        type: '单选',
        required: Boolean(field.required),
        params: {
          dataType: 'String',
          size: 8,
          options: [
            { value: 'true', label: '是' },
            { value: 'false', label: '否' },
          ],
        },
      }
    default:
      throw new Error(`Unsupported approval workflow business model field type: ${field.fieldType}`)
  }
}

export async function ensureApprovalWorkflowDynamicForm(page: Page): Promise<string> {
  const fixture = loadJsonFixture<ApprovalWorkflowBusinessModelSeed>(
    'testing/common/fixtures/data/approval-workflow-business-model-seed/business-model.json',
  ).businessModel
  const formName = `${fixture.name.replace(/[^A-Za-z0-9_]/g, '_')}_${uniqueSuffix('df').replace(/[^A-Za-z0-9_]/g, '_')}`
  const payload = {
    name: formName,
    title: `${fixture.title} ${formName}`,
    fields: fixture.fields
      .filter((field) => !field.isPrimaryKey)
      .map(buildApprovalWorkflowDynamicFormField),
    options: {},
  }

  const createResult = await browserFetch(page, '/api/dynamic-form', {
    method: 'POST',
    body: {
      ...payload,
      fields: [],
    },
  })
  assertBrowserFetchOk(createResult, `create dynamic form ${formName}`)
  const existing = extractApiData<DynamicFormSummary>(createResult)

  const formId = existing.id
  const updateResult = await browserFetch(page, `/api/dynamic-form/${formId}`, {
    method: 'PUT',
    body: payload,
  })
  assertBrowserFetchOk(updateResult, `update dynamic form ${formName}`)

  const detailResult = await browserFetch(page, `/api/dynamic-form/${formId}`)
  assertBrowserFetchOk(detailResult, `get dynamic form ${formName}`)
  const detail = extractApiData<DynamicFormSummary>(detailResult)

  if (detail.status === '未部署') {
    const deployResult = await browserFetch(page, `/api/dynamic-form/${formId}/deploy`, {
      method: 'POST',
    })
    assertBrowserFetchDataTrue(deployResult, `deploy dynamic form ${formName}`)
  } else if (detail.status === '已下线') {
    const redeployResult = await browserFetch(page, `/api/dynamic-form/${formId}/re-deploy`, {
      method: 'POST',
    })
    assertBrowserFetchDataTrue(redeployResult, `redeploy dynamic form ${formName}`)
  }

  return formName
}

export function mapApprovalWorkflowCandidates<
  T extends { nodes?: Array<Record<string, unknown>>; edges?: Array<Record<string, unknown>> }
>(definition: T) {
  const mapped = {
    ...definition,
    formatVersion: 2,
    nodes: definition.nodes?.map((node) => {
      return {
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
      }
    }),
    edges: definition.edges?.map((edge) => {
      return {
        type: 'SEQUENCE',
        ...edge,
      }
    }),
  }
  recordFixtureIntegrityEvidence(definition, mapped)
  return mapped
}

export async function login(page: Page, baseUrl: string, credential: LoginCredential): Promise<void> {
  await page.goto(baseUrl)
  const injectedAdminToken = credential.account === 'admin'
    ? process.env.OB_TEST_ADMIN_ACCESS_TOKEN
    : undefined
  if (injectedAdminToken) {
    await page.evaluate(({ token, apiHost }: { token: string; apiHost: string }) => {
      window.sessionStorage.setItem('apiHost', apiHost)
      window.localStorage.setItem('Ouroboros_token', JSON.stringify({
        accessToken: {
          token,
          expiresAt: Date.now() + 60 * 60 * 1000,
        },
      }))
    }, { token: injectedAdminToken, apiHost: new URL(baseUrl).origin })
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' })
    await expect(page).not.toHaveURL(/\/login(?:$|[?#/])/)
    return
  }

  const loginResult = await page.evaluate(
    async ({ account, password }: LoginCredential) => {
      const apiHost = window.sessionStorage.getItem('apiHost') ?? window.location.origin
      const response = await fetch(new URL('/api/auth/login', apiHost).toString(), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ account, password }),
        credentials: 'include',
      })
      const text = await response.text()
      let json: unknown = null
      try {
        json = JSON.parse(text)
      } catch (error) {
        json = { parseError: String(error) }
      }
      if (!response.ok) {
        throw new Error(`login failed with HTTP ${response.status}: ${text}`)
      }
      const jsonRecord = typeof json === 'object' && json !== null && !Array.isArray(json)
        ? json as Record<string, unknown>
        : null
      if (jsonRecord && 'code' in jsonRecord && jsonRecord.code !== 0) {
        throw new Error(`login failed with code=${String(jsonRecord.code)}: ${text}`)
      }
      const data = jsonRecord?.data ?? jsonRecord?.result ?? json
      window.localStorage.setItem('Ouroboros_token', JSON.stringify(data))
      return { text }
    },
    credential,
  )
  expect(loginResult.text).toContain('accessToken')
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' })
  await expect(page).not.toHaveURL(/\/login(?:$|[?#/])/)
}

export async function browserFetch(page: Page, apiPath: string, init: BrowserFetchInit = {}): Promise<BrowserFetchResult> {
  return page.evaluate(
    async ({ apiPath: fetchPath, init: fetchInit }: { apiPath: string; init: BrowserFetchInit }) => {
      const parseStoredToken = (value: string | null): unknown => {
        if (!value) {
          return null
        }
        try {
          return JSON.parse(value)
        } catch (error) {
          throw new Error(`Failed to parse stored Ouroboros token: ${String(error)}`)
        }
      }
      const asObjectRecord = (value: unknown): Record<string, unknown> | null => {
        if (typeof value !== 'object' || value === null || Array.isArray(value)) {
          return null
        }
        return value as Record<string, unknown>
      }
      const extractTokenValue = (value: unknown): string | null => {
        if (typeof value === 'string') {
          return value
        }
        const record = asObjectRecord(value)
        if (!record) {
          return null
        }
        const candidate = record.token ?? record.access_token ?? record.value
        return typeof candidate === 'string' ? candidate : null
      }
      const tokenPayload = parseStoredToken(
        window.localStorage.getItem('Ouroboros_token') ?? window.sessionStorage.getItem('Ouroboros_token'),
      )
      const tokenRecord = asObjectRecord(tokenPayload)
      const accessToken = tokenRecord
        ? extractTokenValue(tokenRecord.accessToken ?? tokenRecord.access_token)
        : null
      const apiHost = window.sessionStorage.getItem('apiHost') ?? window.location.origin
      const requestUrl = fetchPath.startsWith('http') ? fetchPath : new URL(fetchPath, apiHost).toString()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(fetchInit.headers ?? {}),
      }
      if (accessToken && !headers.Authorization && !headers.authorization) {
        headers.Authorization = `Bearer ${accessToken}`
      }
      let response: Response
      try {
        response = await fetch(requestUrl, {
          method: fetchInit.method ?? 'GET',
          headers,
          body: fetchInit.body === undefined ? undefined : JSON.stringify(fetchInit.body),
          credentials: 'include',
        })
      } catch (error) {
        throw new Error(`fetch ${requestUrl} failed: ${String(error)}`)
      }
      const text = await response.text()
      let json: unknown = null
      try {
        json = JSON.parse(text)
      } catch {
        json = null
      }
      return {
        ok: response.ok,
        status: response.status,
        text,
        json,
      }
    },
    { apiPath, init },
  )
}

export function assertBrowserFetchOk(result: BrowserFetchResult, context: string): void {
  if (!result.ok) {
    throw new Error(`${context} failed with HTTP ${result.status}: ${result.text}`)
  }
  const json = result.json as Record<string, unknown> | null
  if (json && 'code' in json && json.code !== 0) {
    throw new Error(`${context} failed with code=${String(json.code)}: ${result.text}`)
  }
  if (json && 'responseStatus' in json && json.responseStatus !== 0) {
    throw new Error(`${context} failed with responseStatus=${String(json.responseStatus)}: ${result.text}`)
  }
}

export function assertBrowserFetchDataTrue(result: BrowserFetchResult, context: string): void {
  assertBrowserFetchOk(result, context)
  const data = extractApiData<unknown>(result)
  if (data !== true) {
    throw new Error(`${context} failed with data=${JSON.stringify(data)}: ${result.text}`)
  }
}

export function extractApiData<T>(result: BrowserFetchResult): T {
  const json = result.json as Record<string, unknown> | null
  if (!json) {
    throw new Error(`Expected JSON response but received: ${result.text}`)
  }
  if ('result' in json) {
    return json.result as T
  }
  if ('data' in json) {
    return json.data as T
  }
  return json as T
}

export async function resolveBusinessModelAppName(page: Page, frontBaseUrl: string): Promise<string> {
  const appName = process.env.OB_TEST_DEV_APP_NAME ?? 'awf-e2e-app'
  const appTitle = process.env.OB_TEST_DEV_APP_TITLE ?? 'AWF E2E App'
  await page.goto(frontBaseUrl, { waitUntil: 'domcontentloaded' })

  const listResult = await browserFetch(page, '/api/app')
  assertBrowserFetchOk(listResult, 'list develop apps')
  const listData = extractApiData<DevelopAppListData>(listResult)
  const appExists = (listData.items ?? []).some((item) => (item.appInfo?.name ?? item.name) === appName)
  if (!appExists) {
    const createResult = await browserFetch(page, '/api/app', {
      method: 'POST',
      body: {
        name: appName,
        title: appTitle,
        description: 'Approval workflow deterministic browser E2E app',
        icon: '',
        homePage: '',
        defaultTheme: 'dark',
      },
    })
    const createJson = asRecord(createResult.json)
    const appAlreadyExists = createJson?.code === -1 && createResult.text.includes('应用名已存在')
    if (!appAlreadyExists) {
      assertBrowserFetchOk(createResult, `create develop app ${appName}`)
    }
  }

  return appName
}

export async function refreshDevelopWorkspaceToTestContainer(
  page: Page,
  frontBaseUrl: string,
  runtimeBaseUrl: string,
  appName: string,
): Promise<DevelopWorkspaceReloadEvidence> {
  await page.goto(
    new URL(`/dev/${appName}/approval-workflow-definition`, frontBaseUrl).toString(),
    { waitUntil: 'domcontentloaded' },
  )

  const statusResult = await browserFetch(page, `/api/app/container/${appName}/status`)
  assertBrowserFetchOk(statusResult, `query test container status for ${appName}`)
  const status = extractApiData<DevelopRuntimeContainerStatus>(statusResult)
  if (!status.containerId) {
    throw new Error(`Test container for ${appName} did not expose a containerId`)
  }
  if (status.capabilities?.supportsWorkspaceHotReload !== true) {
    throw new Error(`Test container ${status.containerName ?? status.containerId} does not support workspace hot reload`)
  }

  const reloadPath = `/api/app/container/${status.containerId}/reload`
  const reloadButton = page.locator('button:has(.fa-arrows-rotate)')
  await expect(reloadButton).toBeVisible({ timeout: 15_000 })
  const reloadResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST'
      && new URL(response.url()).pathname === reloadPath
  })
  await reloadButton.click()
  const reloadResponse = await reloadResponsePromise
  const reloadText = await reloadResponse.text()
  let reloadJson: unknown = null
  try {
    reloadJson = JSON.parse(reloadText)
  } catch {
    reloadJson = null
  }
  assertBrowserFetchOk({
    ok: reloadResponse.ok(),
    status: reloadResponse.status(),
    text: reloadText,
    json: reloadJson,
  }, `refresh ${appName} development workspace to test container`)

  const readyStartedAt = Date.now()
  const startupPhases = new Set<string>()
  const readyDeadline = readyStartedAt + 120_000
  const probeUrl = new URL('/api/ui-schema?path=%2Fapproval-center', runtimeBaseUrl).toString()
  let bootstrapObserved = false
  while (Date.now() < readyDeadline) {
    const probeResponse = await page.request.get(probeUrl)
    const probeText = await probeResponse.text()
    let probeJson: Record<string, unknown> | null = null
    try {
      const parsed = JSON.parse(probeText) as unknown
      probeJson = typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
        ? parsed as Record<string, unknown>
        : null
    } catch {
      probeJson = null
    }

    const startupPhase = probeJson?.startupPhase
    if (typeof startupPhase === 'string' && startupPhase) {
      startupPhases.add(startupPhase)
    }
    if (probeJson?.startupState === 'BOOTSTRAPPING') {
      bootstrapObserved = true
    }
    if (bootstrapObserved && probeResponse.ok() && probeJson?.code === 0) {
      return {
        appName,
        containerId: status.containerId,
        containerName: status.containerName,
        status: status.status,
        reloadPath,
        readyWaitMs: Date.now() - readyStartedAt,
        startupPhases: [...startupPhases],
      }
    }
    if (probeResponse.status() === 500 || probeJson?.startupState === 'FAILED') {
      throw new Error(`Test container ${status.containerName ?? status.containerId} failed during workspace reload: ${probeText}`)
    }
    await page.waitForTimeout(100)
  }

  throw new Error(
    `Test container ${status.containerName ?? status.containerId} did not complete a BOOTSTRAPPING-to-ready workspace reload within 120 seconds`,
  )
}

export function buildDevelopApprovalWorkflowDefinition(
  seed: ApprovalWorkflowDefinitionShape,
  definitionName: string,
  formType = 'business-model',
  formKey = 'awf-expense-request',
): DevelopApprovalWorkflowDefinition {
  const mapped = mapApprovalWorkflowCandidates({
    ...seed,
    nodes: (seed.nodes ?? []).map((node) => ({ ...node })),
    edges: (seed.edges ?? []).map((edge) => ({ ...edge })),
  })
  return {
    formatVersion: 1,
    id: definitionName,
    flowKey: definitionName,
    name: definitionName,
    title: `${seed.name ?? definitionName} ${definitionName}`,
    description: seed.description ?? '',
    version: 1,
    enabled: true,
    form: {
      formType,
      formId: formKey,
    },
    formType,
    formKey,
    nodes: mapped.nodes ?? [],
    edges: (mapped.edges ?? []).map((edge) => ({
      type: 'SEQUENCE',
      ...edge,
    })),
  }
}

async function persistDevelopApprovalWorkflow(
  page: Page,
  appName: string,
  definition: DevelopApprovalWorkflowDefinition,
  commitMessage: string,
): Promise<void> {
  const basePath = `/api/dev/${appName}/approval-workflow-definition`
  const createResult = await browserFetch(page, basePath, {
    method: 'POST',
    body: definition,
  })
  assertBrowserFetchOk(createResult, `create develop workflow definition ${definition.name}`)

  const updateResult = await browserFetch(page, `${basePath}/${definition.name}`, {
    method: 'PUT',
    body: definition,
  })
  assertBrowserFetchOk(updateResult, `save develop workflow definition ${definition.name}`)

  const commitResult = await browserFetch(page, `${basePath}/${definition.name}/commit`, {
    method: 'POST',
    body: { commitMsg: commitMessage },
  })
  assertBrowserFetchOk(commitResult, `commit develop workflow definition ${definition.name}`)
}

async function waitForMetadataLayerDefinition(page: Page, definitionName: string) {
  const deadline = Date.now() + 15_000
  let definitions: ApprovalWorkflowDefinitionShape[] = []
  while (Date.now() < deadline) {
    const result = await browserFetch(page, '/api/approval-workflow/definition?status=all&page=1&perPage=200')
    assertBrowserFetchOk(result, 'search workflow definitions')
    definitions = extractApiData<ApprovalWorkflowDefinitionSearchData>(result).items ?? []
    const match = definitions.find((definition) => definition.name === definitionName)
    if (match?.source === 'metadata-layer') {
      return match
    }
    await page.waitForTimeout(500)
  }
  throw new Error(
    `Unable to find metadata-layer workflow definition ${definitionName}; visible definitions=${definitions
      .map((definition) => `${definition.name ?? '<unnamed>'}:${definition.source ?? '<unknown>'}`)
      .join(', ')}`,
  )
}

export async function designRefreshAndDeployApprovalWorkflow(
  browser: Browser,
  runtimePage: Page,
  frontBaseUrl: string,
  runtimeBaseUrl: string,
  definition: DevelopApprovalWorkflowDefinition,
  commitMessage: string,
): Promise<DevelopApprovalWorkflowPublishEvidence> {
  const developContext = await browser.newContext()
  const developPage = await developContext.newPage()
  let appName = ''
  let reloadEvidence: DevelopWorkspaceReloadEvidence
  try {
    await login(developPage, frontBaseUrl, ADMIN_CREDENTIAL)
    appName = await resolveBusinessModelAppName(developPage, frontBaseUrl)
    await persistDevelopApprovalWorkflow(developPage, appName, definition, commitMessage)
    reloadEvidence = await refreshDevelopWorkspaceToTestContainer(
      developPage,
      frontBaseUrl,
      runtimeBaseUrl,
      appName,
    )
  } finally {
    await developContext.close()
  }

  const runtimeDefinition = await waitForMetadataLayerDefinition(runtimePage, definition.name)
  if (runtimeDefinition.title !== definition.title) {
    throw new Error(
      `Runtime workflow ${definition.name} title mismatch: expected=${definition.title}, actual=${runtimeDefinition.title ?? ''}`,
    )
  }
  const deployResult = await browserFetch(
    runtimePage,
    `/api/approval-workflow/definition/${definition.name}/deploy`,
    { method: 'POST' },
  )
  assertBrowserFetchOk(deployResult, `deploy metadata-layer workflow definition ${definition.name}`)

  return {
    appName,
    definitionName: definition.name,
    source: 'metadata-layer',
    reloadEvidence,
    runtimeDefinition,
  }
}

export function approvalWorkflowDisplayName(definition: ApprovalWorkflowDefinitionShape): string {
  return String(definition.title ?? definition.name ?? definition.id ?? '')
}
