const http = require('node:http')
const { URL } = require('node:url')
const {
  METADATA_MODULE_NAME,
  PLAYGROUND_APP_NAME,
  buildApiEnvelope,
  buildMetadataRecords,
} = require('./fixtures/scriptEditorIntelligence')
const {
  buildShellAppInfo,
  buildShellToken,
  buildShellUserInfo,
  buildShellUserSetting,
  buildSuccessEnvelope,
} = require('./fixtures/shellBootstrap')
const {
  buildMainAppHostAuthProviders,
  buildMainAppHostAuthorities,
  buildMainAppHostMenuTree,
  buildMainAppHostRoutes,
  buildMainAppHostUiSchema,
} = require('./fixtures/mainAppHostE2E')
const {
  buildRuntimeDashboard,
  buildRuntimeDashboardSchema,
  buildRuntimeChartExecution,
} = require('./fixtures/statisticsReportingRuntime')

const DEFAULT_PORT = 43174
const metadataRecords = buildMetadataRecords()
const { createPlaygroundLanguageMock } = require('../main-app/src/pages/debug/script-editor-intelligence.mock')

function createServer() {
  const languageMock = createPlaygroundLanguageMock(request => buildApiEnvelope(request).data.snapshot)
  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1')
    if (url.pathname.startsWith(`/api/dev/${PLAYGROUND_APP_NAME}/code-intelligence/sessions`)) {
      const body = req.method === 'POST' ? await readJsonBody(req) : undefined
      try {
        const result = languageMock(req.method.toLowerCase(), url.pathname, body)
        if (result !== undefined) return writeJson(res, 200, buildSuccessEnvelope(result))
      } catch (error) {
        return writeJson(res, 400, { status: -1, msg: error.message })
      }
    }
    if (req.method === 'GET' && url.pathname === '/health') {
      res.writeHead(200, {
        'Content-Type': 'text/plain; charset=UTF-8',
      })
      res.end('ok')
      return
    }
    if (req.method === 'GET' && /^\/api\/dev\/[^/]+\/composite-view\/execution-policy$/.test(url.pathname)) {
      return writeJson(res, 200, buildSuccessEnvelope({ queryTimeoutSeconds: 30, maxPageSize: 1000, maxExportRows: 100000, revision: 'test-defaults' }))
    }
    if (req.method === 'GET' && url.pathname === '/api/sys/web-app-info') {
      return writeJson(res, 200, buildSuccessEnvelope(buildMainAppHostAppInfo()))
    }
    if (req.method === 'GET' && url.pathname === '/api/sys/user/info') {
      return writeJson(res, 200, buildSuccessEnvelope(buildShellUserInfo()))
    }
    if (req.method === 'GET' && url.pathname === '/api/authored-menu-tree') {
      return writeJson(res, 200, buildSuccessEnvelope(buildMainAppHostMenuTree()))
    }
    if (req.method === 'GET' && url.pathname === '/api/auth/authorities') {
      return writeJson(res, 200, buildSuccessEnvelope(buildMainAppHostAuthorities()))
    }
    if (req.method === 'GET' && url.pathname === '/api/auth/providers') {
      return writeJson(res, 200, buildSuccessEnvelope(buildMainAppHostAuthProviders()))
    }
    if (req.method === 'GET' && url.pathname === '/api/user-setting') {
      return writeJson(res, 200, buildSuccessEnvelope(buildShellUserSetting()))
    }
    if (req.method === 'GET' && url.pathname === '/api/ui-schema') {
      return writeJson(res, 200, buildUiSchemaEnvelope(url))
    }
    if (req.method === 'POST' && url.pathname === '/api/auth/login') {
      return writeJson(res, 200, buildSuccessEnvelope(buildShellToken()))
    }
    if (req.method === 'POST' && url.pathname === '/api/auth/revoke-token') {
      return writeJson(res, 200, buildSuccessEnvelope(null))
    }
    if (
      req.method === 'POST' &&
      url.pathname.startsWith('/api/runtime/statistics-reporting/charts/') &&
      url.pathname.endsWith('/execute')
    ) {
      const chartName = decodeURIComponent(
        url.pathname
          .slice('/api/runtime/statistics-reporting/charts/'.length)
          .replace(/\/execute$/, '')
      )
      const result = buildRuntimeChartExecution(chartName)
      if (!result) {
        return writeJson(res, 404, {
          code: 404,
          status: 404,
          msg: `Runtime chart not found: ${chartName}`,
          data: null,
        })
      }
      return writeJson(res, 200, buildSuccessEnvelope(result))
    }
    if (req.method === 'GET' && url.pathname === '/api/demo/sales-dashboard') {
      const result = buildRuntimeDashboard(url.pathname.substring('/api'.length))
      if (!result) {
        return writeJson(res, 404, {
          code: 404,
          status: 404,
          msg: `Runtime dashboard not found: ${url.pathname}`,
          data: null,
        })
      }
      return writeJson(res, 200, buildSuccessEnvelope(result))
    }
    if (
      req.method === 'POST' &&
      url.pathname === `/api/dev/${PLAYGROUND_APP_NAME}/code-intelligence/context`
    ) {
      const body = await readJsonBody(req)
      return writeJson(res, 200, buildApiEnvelope(body))
    }
    if (
      req.method === 'GET' &&
      url.pathname === `/api/dev/${PLAYGROUND_APP_NAME}/metadata/${METADATA_MODULE_NAME}`
    ) {
      return writeJson(res, 200, buildMetadataListEnvelope(url))
    }
    if (
      req.method === 'GET' &&
      url.pathname.startsWith(
        `/api/dev/${PLAYGROUND_APP_NAME}/metadata/${METADATA_MODULE_NAME}/`
      )
    ) {
      const language = decodeURIComponent(
        url.pathname.substring(
          `/api/dev/${PLAYGROUND_APP_NAME}/metadata/${METADATA_MODULE_NAME}/`.length
        )
      )
      return writeJson(res, 200, buildMetadataDetailEnvelope(language, url))
    }

    return writeJson(res, 404, {
      code: 404,
      status: 404,
      msg: 'Not Found',
      data: null,
    })
  })
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = ''
    req.setEncoding('utf8')
    req.on('data', (chunk) => {
      raw += chunk
    })
    req.on('end', () => {
      if (!raw) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(raw))
      } catch (error) {
        reject(error)
      }
    })
    req.on('error', reject)
  })
}

function writeJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=UTF-8',
  })
  res.end(JSON.stringify(payload))
}

function buildMetadataListEnvelope(url) {
  const mode = url.searchParams.get('mode') || 'select'
  const keyword = url.searchParams.get('keyword') || url.searchParams.get('name') || ''
  const values = url.searchParams.getAll('value')
  const filtered = metadataRecords.filter((record) => {
    if (values.length > 0) {
      return values.includes(record.name)
    }
    if (!keyword) {
      return true
    }
    return record.name.includes(keyword) || String(record.label || '').includes(keyword)
  })

  return {
    code: 0,
    msg: null,
    data: {
      items: filtered.map((record) => projectMetadataRecord(record, mode)),
    },
  }
}

function buildMainAppHostAppInfo() {
  const appInfo = buildShellAppInfo()
  return {
    ...appInfo,
    routes: [
      ...buildMainAppHostRoutes(),
      {
        path: '/demo/sales-dashboard',
        title: '销售总览仪表板',
        type: 'amis',
        config: {
          schemaId: '/demo/sales-dashboard',
        },
      },
    ],
    config: {
      ...(appInfo.config || {}),
      'security.auth.login.localPassword.enabled': true,
    },
  }
}

function buildUiSchemaEnvelope(url) {
  const schemaId = url.searchParams.get('path') || ''
  if (schemaId === '/demo/sales-dashboard') {
    return buildSuccessEnvelope(buildRuntimeDashboardSchema())
  }
  const schema = buildMainAppHostUiSchema(schemaId)
  if (!schema) {
    return {
      code: -1,
      status: -1,
      msg: schemaId ? `未找到页面: ${schemaId}` : '缺少 path 参数',
      data: null,
    }
  }
  return buildSuccessEnvelope(schema)
}

function buildMetadataDetailEnvelope(language, url) {
  const mode = url.searchParams.get('mode') || 'select'
  const found = metadataRecords.find((record) => record.name === language)
  if (!found) {
    return {
      code: -1,
      msg: '未找到指定数据',
      data: null,
    }
  }
  return {
    code: 0,
    msg: null,
    data: projectMetadataRecord(found, mode),
  }
}

function projectMetadataRecord(record, mode) {
  if (mode === 'raw') {
    return record.details
  }
  if (mode === 'select') {
    return {
      label: record.label,
      value: record.name,
    }
  }
  return record
}

function resolvePort(argv) {
  const index = argv.indexOf('--port')
  if (index >= 0 && argv[index + 1]) {
    return Number(argv[index + 1])
  }
  return DEFAULT_PORT
}

function startServer(port = DEFAULT_PORT) {
  const server = createServer()
  server.listen(port, '127.0.0.1', () => {
    console.log(
      `[script-editor-intelligence-mock-server] listening on http://127.0.0.1:${port}`
    )
  })
  return server
}

if (require.main === module) {
  startServer(resolvePort(process.argv))
}

module.exports = {
  DEFAULT_PORT,
  createServer,
  startServer,
}
