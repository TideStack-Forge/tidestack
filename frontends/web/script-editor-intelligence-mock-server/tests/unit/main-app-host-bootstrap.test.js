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

test('mock server exposes main-app-host bootstrap endpoints for the frontend host bootstrap flow', async () => {
  await withMockServer(async (port) => {
    const appInfo = await request(port, '/api/sys/web-app-info')
    assert.equal(appInfo.statusCode, 200)
    assert.equal(appInfo.body.code, 0)
    assert.equal(appInfo.body.data.appName, 'Ouroboros Mock Shell')
    assert.equal(appInfo.body.data.layoutType, 'header-sidebar')
    assert.equal(appInfo.body.data.homePage, '/welcome')
    assert.deepEqual(appInfo.body.data.componentLibraries, [])

    const userInfo = await request(port, '/api/sys/user/info')
    assert.equal(userInfo.body.code, 0)
    assert.equal(userInfo.body.data.nickname, 'Mock Admin')

    const menus = await request(port, '/api/authored-menu-tree')
    assert.equal(menus.body.code, 0)
    assert.equal(menus.body.data.main[0].path, '/welcome')

    const authorities = await request(port, '/api/auth/authorities')
    assert.equal(authorities.body.code, 0)
    assert.equal(authorities.body.data['shell.view'], true)

    const settings = await request(port, '/api/user-setting')
    assert.equal(settings.body.code, 0)
    assert.deepEqual(settings.body.data, {})
  })
})

test('mock server can also mint and revoke a main-app-host token for manual debug flows', async () => {
  await withMockServer(async (port) => {
    const login = await request(port, '/api/auth/login', {
      method: 'POST',
      body: {
        username: 'mock-admin',
        password: 'mock-password',
      },
    })
    assert.equal(login.body.code, 0)
    assert.equal(login.body.data.accessToken, 'mock-access-token')
    assert.equal(login.body.data.refreshToken, 'mock-refresh-token')

    const revoke = await request(port, '/api/auth/revoke-token', {
      method: 'POST',
      body: {},
    })
    assert.equal(revoke.body.code, 0)
    assert.equal(revoke.body.data, null)
  })
})
