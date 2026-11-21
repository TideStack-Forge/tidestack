#!/usr/bin/env node

const fs = require('node:fs')
const path = require('node:path')

const evidenceDir = process.env.OB_TEST_AWF_EVIDENCE_DIR
  ?? 'tmp/testing-evidence/workflow-approval-flow'
const identitiesPath = process.env.OB_TEST_AWF_IDENTITIES_FILE
  ?? path.join(evidenceDir, 'approval-workflow-identities.json')
const resetDefinitionName = process.env.OB_TEST_AWF_RESET_DEFINITION_NAME
  ?? 'AWF_FLOW_RESET_DESIGN'
const developBaseUrl = process.env.OB_TEST_BASE_URL ?? process.env.OB_TEST_APP_BASE_URL
if (!developBaseUrl) {
  throw new Error('Missing required environment variable: OB_TEST_BASE_URL or OB_TEST_APP_BASE_URL')
}
const devAppName = process.env.OB_TEST_DEV_APP_NAME ?? 'awf-e2e-app'
const adminPassword = process.env.OB_TEST_ADMIN_PASSWORD ?? '666666'
const adminAccessToken = process.env.OB_TEST_ADMIN_ACCESS_TOKEN

const fixturePath = path.join(process.cwd(), 'testing/common/fixtures/data/approval-workflow-flows-seed/flow-definitions.json')
const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'))
const source = fixture.flows.find((flow) => flow.flowKey === 'awf.single.l1')
if (!source) {
  throw new Error('Missing awf.single.l1 approval workflow seed')
}

function resolveProjectPath(targetPath, ...segments) {
  return path.isAbsolute(targetPath)
    ? path.join(targetPath, ...segments)
    : path.join(process.cwd(), targetPath, ...segments)
}

const defaultIdentities = {
  AWF_USER_SUBMITTER: 'awf.submitter',
  AWF_USER_APPROVER_1: 'awf.approver1',
  AWF_USER_APPROVER_2: 'awf.approver2',
  AWF_USER_APPROVER_3: 'awf.approver3',
  AWF_USER_AUDITOR: 'awf.auditor',
  AWF_USER_FLOW_ADMIN: 'awf.flowadmin',
}

function readIdentityAliases() {
  const absolutePath = resolveProjectPath(identitiesPath)
  if (!fs.existsSync(absolutePath)) {
    return defaultIdentities
  }
  const content = JSON.parse(fs.readFileSync(absolutePath, 'utf8'))
  const users = content.users ?? {}
  return {
    AWF_USER_SUBMITTER: users.submitter?.userIdentity ?? users.submitter?.account ?? defaultIdentities.AWF_USER_SUBMITTER,
    AWF_USER_APPROVER_1: users.approver1?.userIdentity ?? users.approver1?.account ?? defaultIdentities.AWF_USER_APPROVER_1,
    AWF_USER_APPROVER_2: users.approver2?.userIdentity ?? users.approver2?.account ?? defaultIdentities.AWF_USER_APPROVER_2,
    AWF_USER_APPROVER_3: users.approver3?.userIdentity ?? users.approver3?.account ?? defaultIdentities.AWF_USER_APPROVER_3,
    AWF_USER_AUDITOR: users.auditor?.userIdentity ?? users.auditor?.account ?? defaultIdentities.AWF_USER_AUDITOR,
    AWF_USER_FLOW_ADMIN: users.flowAdmin?.userIdentity ?? users.flowAdmin?.account ?? defaultIdentities.AWF_USER_FLOW_ADMIN,
  }
}

const aliases = readIdentityAliases()

function mapNode(node) {
  const mapped = { ...node }
  if (Array.isArray(mapped.candidates) && mapped.candidates.every((candidate) => typeof candidate === 'string')) {
    mapped.candidates = [{
      type: 'User',
      users: mapped.candidates.map((candidate) => aliases[candidate] ?? candidate),
    }]
  }
  return mapped
}

const metadata = {
  formatVersion: 1,
  name: resetDefinitionName,
  title: 'AWF Reset Design Original',
  description: 'Source metadata used by APF-RESET-DESIGN-001',
  version: 1,
  enabled: true,
  form: {
    formType: 'business-model',
    formId: 'awf-expense-request',
  },
  nodes: source.nodes.map(mapNode),
  edges: source.edges.map((edge) => ({ type: 'SEQUENCE', ...edge })),
}

const evidencePath = resolveProjectPath(evidenceDir, 'design-time-metadata.json')
fs.mkdirSync(path.dirname(evidencePath), { recursive: true })

function joinUrl(baseUrl, apiPath) {
  return new URL(apiPath, `${baseUrl.replace(/\/$/, '')}/`).toString()
}

function extractToken(payload) {
  const data = payload?.data ?? payload?.result ?? payload
  const accessToken = data?.accessToken ?? data?.access_token
  if (typeof accessToken === 'string') {
    return accessToken
  }
  return accessToken?.token ?? data?.token ?? data?.access_token
}

function extractData(payload) {
  return payload?.data ?? payload?.result ?? payload
}

async function requestJson(apiPath, options = {}) {
  const response = await fetch(joinUrl(developBaseUrl, apiPath), {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const text = await response.text()
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch (error) {
    throw new Error(`${options.context ?? apiPath} returned non-JSON response: ${text}`)
  }
  if (!response.ok) {
    throw new Error(`${options.context ?? apiPath} failed with HTTP ${response.status}: ${text}`)
  }
  if (payload && typeof payload === 'object' && payload.code !== undefined && payload.code !== 0) {
    throw new Error(`${options.context ?? apiPath} failed with code=${payload.code}: ${text}`)
  }
  return payload
}

async function login() {
  if (adminAccessToken) {
    return adminAccessToken
  }

  const payload = await requestJson('/api/auth/login', {
    method: 'POST',
    body: { account: 'admin', password: adminPassword },
    context: `login ${developBaseUrl}`,
  })
  const token = extractToken(payload)
  if (!token) {
    throw new Error(`login ${developBaseUrl} did not return an access token`)
  }
  return token
}

async function persistThroughDevelopPlatform(token) {
  const basePath = `/api/dev/${devAppName}/approval-workflow-definition`
  const existingPayload = await requestJson(`${basePath}/${resetDefinitionName}`, {
    token,
    context: `read design-time workflow definition ${resetDefinitionName}`,
  })
  const existing = extractData(existingPayload)
  const writeMethod = existing ? 'PUT' : 'POST'
  const writePath = existing ? `${basePath}/${resetDefinitionName}` : basePath
  await requestJson(writePath, {
    method: writeMethod,
    token,
    body: metadata,
    context: `${writeMethod} design-time workflow definition ${resetDefinitionName}`,
  })
  if (writeMethod === 'PUT') {
    await requestJson(`${basePath}/${resetDefinitionName}/commit`, {
      method: 'POST',
      token,
      body: { commitMsg: 'APF-RESET-DESIGN-001 design-time source' },
      context: `commit design-time workflow definition ${resetDefinitionName}`,
    })
  }
  const persistedPayload = await requestJson(`${basePath}/${resetDefinitionName}`, {
    token,
    context: `verify design-time workflow definition ${resetDefinitionName}`,
  })
  const persisted = extractData(persistedPayload)
  if (!persisted || persisted.name !== resetDefinitionName) {
    throw new Error(`develop platform did not persist workflow definition ${resetDefinitionName}`)
  }
  return {
    writeMethod,
    committed: true,
    persistedVersion: persisted.version,
  }
}

async function main() {
  const token = await login()
  const platformEvidence = await persistThroughDevelopPlatform(token)
  fs.writeFileSync(evidencePath, `${JSON.stringify({
    generatedAt: new Date().toISOString(),
    assetPath: `workflow/${resetDefinitionName}.workflow.json`,
    definitionName: resetDefinitionName,
    title: metadata.title,
    description: metadata.description,
    version: metadata.version,
    form: metadata.form,
    nodeCount: metadata.nodes.length,
    edgeCount: metadata.edges.length,
    devAppName,
    platformWrite: platformEvidence,
  }, null, 2)}\n`, 'utf8')
  console.log(`Prepared design-time workflow metadata through develop platform: workflow/${resetDefinitionName}.workflow.json`)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
