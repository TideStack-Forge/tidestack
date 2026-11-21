const assert = require('node:assert/strict')
const http = require('node:http')
const { createServer } = require('../../server')

function requestJson(port, language) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      flowModel: {
        name: 'MockOrderFlow',
        nodes: [],
      },
      nodeName: 'script-node-1',
      language,
    })

    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path: '/api/dev/playground-app/code-intelligence/context',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
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
    req.write(body)
    req.end()
  })
}

function request(port, path) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port,
        path,
        method: 'GET',
      },
      (res) => {
        let chunks = ''
        res.setEncoding('utf8')
        res.on('data', (chunk) => {
          chunks += chunk
        })
        res.on('end', () => {
          const contentType = String(res.headers['content-type'] || '')
          resolve({
            statusCode: res.statusCode,
            body: contentType.includes('application/json')
              ? JSON.parse(chunks || '{}')
              : chunks,
          })
        })
      }
    )

    req.on('error', reject)
    req.end()
  })
}

test('mock server returns dev-helper-style snapshot envelopes per language', async () => {
  const server = createServer()

  await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', resolve)
  })

  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0

  try {
    const health = await request(port, '/health')
    assert.equal(health.statusCode, 200)
    assert.equal(health.body, 'ok')

    const javascript = await requestJson(port, 'javascript')
    assert.equal(javascript.statusCode, 200)
    assert.equal(javascript.body.code, 0)
    assert.equal(javascript.body.status, 0)
    assert.equal(javascript.body.data.snapshot.runtimeProfile, 'logicflow-graaljs')
    assert.equal(javascript.body.data.snapshot.source, 'dev-helper-cache')

    const groovy = await requestJson(port, 'groovy')
    assert.equal(groovy.statusCode, 200)
    assert.equal(groovy.body.data.snapshot.runtimeProfile, 'logicflow-groovy')
    assert.equal(groovy.body.data.snapshot.source, 'dev-helper-cache')
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
})

test('mock server exposes raw metadata list and detail endpoints', async () => {
  const server = createServer()

  await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', resolve)
  })

  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0

  try {
    const list = await request(
      port,
      '/api/dev/playground-app/metadata/script-intelligence?mode=raw'
    )
    assert.equal(list.statusCode, 200)
    assert.equal(list.body.code, 0)
    assert.deepEqual(
      list.body.data.items.map((item) => item.runtimeProfile).sort(),
      ['logicflow-graaljs', 'logicflow-groovy']
    )
    assert.deepEqual(
      list.body.data.items.map((item) => item.source),
      ['dev-helper-runtime', 'dev-helper-runtime']
    )

    const javascript = await request(
      port,
      '/api/dev/playground-app/metadata/script-intelligence/javascript?mode=raw'
    )
    assert.equal(javascript.statusCode, 200)
    assert.equal(javascript.body.code, 0)
    assert.equal(javascript.body.data.runtimeProfile, 'logicflow-graaljs')
    assert.equal(javascript.body.data.source, 'dev-helper-runtime')
    assert.deepEqual(javascript.body.data.javaImports, ['java.time.LocalDate'])

    const missing = await request(
      port,
      '/api/dev/playground-app/metadata/script-intelligence/python?mode=raw'
    )
    assert.equal(missing.statusCode, 200)
    assert.equal(missing.body.code, -1)
    assert.equal(missing.body.msg, '未找到指定数据')
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
})

test('mock server rejects unsupported script languages instead of silently returning graaljs', async () => {
  const server = createServer()

  await new Promise((resolve) => {
    server.listen(0, '127.0.0.1', resolve)
  })

  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : 0

  try {
    const unsupported = await requestJson(port, 'python')
    assert.equal(unsupported.statusCode, 200)
    assert.notEqual(unsupported.body.code, 0)
    assert.equal(unsupported.body.data, null)
    assert.match(String(unsupported.body.msg || ''), /不支持的脚本语言: python/)
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
})
