const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const packageRoot = path.resolve(__dirname, '..', '..', '..')

test('mock server tests live under tests/unit', () => {
  const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))

  assert.ok(packageJson.scripts.test, 'package.json should expose test')
  assert.ok(packageJson.scripts['test:unit'], 'package.json should expose test:unit')
  assert.match(
    packageJson.scripts['test:unit'],
    /vitest run/,
    'test:unit should be backed by Vitest'
  )
  assert.ok(!packageJson.scripts['test:node'], 'legacy test:node alias should be removed')
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/README.md')),
    'tests/README.md should document the package testing standard'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/unit/server.test.js')),
    'server test should live under tests/unit'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'vitest.config.js')),
    'mock server should define a vitest config'
  )
  assert.ok(
    !fs.existsSync(path.join(packageRoot, 'server.test.js')),
    'root-level server.test.js should be moved under tests/unit'
  )
})
