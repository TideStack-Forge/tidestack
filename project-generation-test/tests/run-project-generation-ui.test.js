const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { spawn, spawnSync } = require('node:child_process')
const http = require('node:http')
const { once } = require('node:events')
const { after, test } = require('node:test')

const repoRoot = path.resolve(__dirname, '..', '..')
const scratch = fs.mkdtempSync(path.join(repoRoot, 'tmp', 'project-generation-runner-'))
const checkout = path.join(scratch, 'checkout with spaces')
const mainApp = path.join(checkout, 'frontends', 'web', 'main-app')
fs.mkdirSync(path.join(mainApp, 'node_modules', 'playwright'), { recursive: true })
fs.writeFileSync(path.join(mainApp, 'package.json'), '{}')
fs.writeFileSync(path.join(mainApp, 'node_modules', 'playwright', 'index.js'), 'module.exports = {}')
after(() => fs.rmSync(scratch, { recursive: true, force: true }))

function check(...args) {
  return spawnSync(process.execPath, [
    '--permission', '--allow-fs-read=*', `--allow-fs-write=${scratch}`,
    path.join(repoRoot, 'project-generation-test', 'run-project-generation-ui.js'),
    '--check', ...args,
  ], { cwd: scratch, encoding: 'utf8' })
}

test('preflight binds dependencies and output to the requested checkout without writing', () => {
  const output = path.join(scratch, 'evidence')
  const result = check('--repo', checkout, '--output', output)
  assert.equal(result.status, 0, result.stderr)
  const plan = JSON.parse(result.stdout)
  assert.equal(plan.repoRoot, fs.realpathSync(checkout))
  assert.equal(plan.outputRoot, output)
  assert.equal(plan.playwrightModule, path.join(mainApp, 'node_modules', 'playwright', 'index.js'))
  assert.equal(fs.existsSync(output), false)
})

test('an occupied output is rejected and its evidence is preserved', () => {
  const output = path.join(scratch, 'existing-evidence')
  fs.mkdirSync(output)
  fs.writeFileSync(path.join(output, 'manifest.json'), 'previous run')
  const result = check('--repo', checkout, '--output', output)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /already exists/)
  assert.equal(fs.readFileSync(path.join(output, 'manifest.json'), 'utf8'), 'previous run')
})

test('default evidence paths are unique and remain inside the requested checkout', () => {
  const first = check('--repo', checkout)
  const second = check('--repo', checkout)
  assert.equal(first.status, 0, first.stderr)
  assert.equal(second.status, 0, second.stderr)
  const firstPath = JSON.parse(first.stdout).outputRoot
  const secondPath = JSON.parse(second.stdout).outputRoot
  assert.notEqual(firstPath, secondPath)
  assert.equal(path.relative(path.join(checkout, 'tmp', 'testing-evidence', 'project-generation'), firstPath).startsWith('..'), false)
  assert.equal(fs.existsSync(firstPath), false)
  assert.equal(fs.existsSync(secondPath), false)
})

test('a missing checkout is reported before any output is created', () => {
  const result = check('--output', path.join(scratch, 'missing-repo-output'))
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /--repo/)
  assert.equal(fs.existsSync(path.join(scratch, 'missing-repo-output')), false)
})

test('preflight rejects a checkout without installed Playwright', () => {
  const emptyCheckout = path.join(scratch, 'without-dependencies')
  fs.mkdirSync(path.join(emptyCheckout, 'frontends', 'web', 'main-app'), { recursive: true })
  fs.writeFileSync(path.join(emptyCheckout, 'frontends', 'web', 'main-app', 'package.json'), '{}')
  const result = check('--repo', emptyCheckout)
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /playwright/)
  assert.equal(fs.existsSync(path.join(emptyCheckout, 'tmp')), false)
})

test('a backend failure retains a failed manifest in the new evidence directory', async () => {
  const server = http.createServer((request, response) => {
    response.writeHead(503)
    response.end('Unavailable')
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const output = path.join(scratch, 'failed-run')
  try {
    const child = spawn(process.execPath, [
      path.join(repoRoot, 'project-generation-test', 'run-project-generation-ui.js'),
      '--repo', checkout, '--output', output,
    ], { cwd: scratch, env: { ...process.env, BACKEND_BASE_URL: `http://127.0.0.1:${server.address().port}` }, stdio: 'ignore' })
    const [code] = await once(child, 'close')
    assert.notEqual(code, 0)
    const manifest = JSON.parse(fs.readFileSync(path.join(output, 'manifest.json'), 'utf8'))
    assert.equal(manifest.status, 'failed')
    assert.match(manifest.error, /HTTP 503/)
    assert.equal(manifest.outputRoot, output)
    assert.deepEqual(manifest.downloads, [])
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('a catalog business error retains its message instead of treating HTTP 200 as success', async () => {
  const server = http.createServer((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify(request.url === '/api/auth/login'
      ? { code: 0, data: { accessToken: { token: 'test-token' } } }
      : { code: 50000, message: 'Catalog is expired', data: null }))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const output = path.join(scratch, 'business-error-run')
  try {
    const child = spawn(process.execPath, [
      path.join(repoRoot, 'project-generation-test', 'run-project-generation-ui.js'),
      '--repo', checkout, '--output', output,
    ], { cwd: scratch, env: { ...process.env, BACKEND_BASE_URL: `http://127.0.0.1:${server.address().port}` }, stdio: 'ignore' })
    const [code] = await once(child, 'close')
    assert.notEqual(code, 0)
    const manifest = JSON.parse(fs.readFileSync(path.join(output, 'manifest.json'), 'utf8'))
    assert.equal(manifest.status, 'failed')
    assert.match(manifest.error, /50000.*Catalog is expired/)
    assert.deepEqual(manifest.downloads, [])
  } finally {
    server.close()
    await once(server, 'close')
  }
})
