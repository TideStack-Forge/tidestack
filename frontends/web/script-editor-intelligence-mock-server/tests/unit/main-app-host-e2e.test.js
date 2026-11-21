const assert = require('node:assert/strict')
const http = require('node:http')

const { createServer } = require('../../server')

function request(port, path, options = {}) {
  const { method = 'GET', body = null } = options

  return new Promise((resolve, reject) => {
    const payload = body == null ? null : JSON.stringify(body)
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path,
        method,
        headers:
          payload == null
            ? undefined
            : {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
              },
      },
      (res) => {
        let chunks = ''
        res.setEncoding('utf8')
        res.on('data', (chunk) => {
          chunks += chunk
        })
        res.on('end', () => {
          resolve({
            statusCode: res.statusCode,
            body: JSON.parse(chunks || '{}'),
          })
        })
      }
    )

    req.on('error', reject)
    if (payload != null) {
      req.write(payload)
    }
    req.end()
  })
}

async function withMockServer(run) {
  const server = createServer()
  await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', resolve)
  })

  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0

  try {
    await run(port)
  } finally {
    await new Promise((resolve, reject) => {
      server.close((error) => {
        if (error) {
          reject(error)
          return
        }
        resolve()
      })
    })
  }
}

test('mock server exposes deterministic main-app-host happy-path providers, routes, menus, and authorities', async () => {
  await withMockServer(async (port) => {
    const providers = await request(port, '/api/auth/providers')
    assert.equal(providers.statusCode, 200)
    assert.equal(providers.body.code, 0)
    assert.deepEqual(providers.body.data, [
      {
        key: 'mock-ldap',
        type: 'ldap',
        displayName: 'Mock LDAP',
        loginUrl: null,
        availability: 'healthy',
      },
    ])

    const appInfo = await request(port, '/api/sys/web-app-info')
    assert.equal(appInfo.statusCode, 200)
    assert.equal(appInfo.body.code, 0)
    assert.equal(appInfo.body.data.homePage, '/welcome')
    assert.deepEqual(appInfo.body.data.routes, [
      {
        path: '/mock/orders-dashboard',
        title: '订单看板',
        type: 'amis',
        config: {
          schemaId: '/mock/orders-dashboard',
          authority: 'main-app-host.orders.view',
        },
      },
      {
        path: '/mock/approval-center',
        title: '审批中心',
        type: 'amis',
        config: {
          schemaId: '/mock/approval-center',
          authority: 'main-app-host.approvals.view',
        },
      },
    ])

    const menus = await request(port, '/api/authored-menu-tree')
    assert.equal(menus.body.code, 0)
    assert.equal(menus.body.data.main[0].path, '/welcome')
    assert.equal(menus.body.data.main[1].title, '主应用 Host E2E')
    assert.deepEqual(
      menus.body.data.main[1].children.map((item) => item.path),
      ['/mock/orders-dashboard', '/mock/approval-center']
    )

    const authorities = await request(port, '/api/auth/authorities')
    assert.equal(authorities.body.code, 0)
    assert.equal(authorities.body.data['shell.view'], true)
    assert.equal(authorities.body.data['main-app-host.orders.view'], true)
    assert.equal(authorities.body.data['main-app-host.approvals.view'], true)
  })
})

test('mock server resolves deterministic ui-schema payloads for the menu-backed AMIS routes', async () => {
  await withMockServer(async (port) => {
    const orders = await request(port, '/api/ui-schema?path=%2Fmock%2Forders-dashboard')
    assert.equal(orders.statusCode, 200)
    assert.equal(orders.body.code, 0)
    assert.equal(orders.body.data.type, 'page')
    assert.equal(orders.body.data.title, '订单看板')
    assert.equal(orders.body.data.body[0].title, 'Main App Host E2E Happy Path')

    const approvals = await request(port, '/api/ui-schema?path=%2Fmock%2Fapproval-center')
    assert.equal(approvals.statusCode, 200)
    assert.equal(approvals.body.code, 0)
    assert.equal(approvals.body.data.type, 'page')
    assert.equal(approvals.body.data.title, '审批中心')
    assert.equal(approvals.body.data.body[1].items[1].content, 'main-app-host.approvals.view')
  })
})
