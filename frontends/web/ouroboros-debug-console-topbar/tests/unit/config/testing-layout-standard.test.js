const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const packageRoot = path.resolve(__dirname, '..', '..', '..')
const srcRoot = path.join(packageRoot, 'src')

function walk(dir, predicate, result = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(fullPath, predicate, result)
      continue
    }
    if (predicate(fullPath)) {
      result.push(path.relative(packageRoot, fullPath))
    }
  }
  return result
}

test('debug console topbar follows the unified unit-only testing standard', () => {
  const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))
  const srcTestFiles = walk(
    srcRoot,
    (filePath) =>
      /(^|\/)__tests__\//.test(filePath) || /\.(test|spec)\.(js|ts|tsx)$/.test(filePath)
  )

  assert.ok(packageJson.scripts.test, 'package.json should expose test')
  assert.ok(packageJson.scripts['test:unit'], 'package.json should expose test:unit')
  assert.match(
    packageJson.scripts['test:unit'],
    /vitest run/,
    'test:unit should be backed by Vitest'
  )
  assert.ok(
    !packageJson.devDependencies['babel-jest'],
    'legacy Jest-only dependency should be removed'
  )
  assert.equal(
    srcTestFiles.length,
    0,
    `test files should not remain under src/: ${srcTestFiles.join(', ')}`
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/README.md')),
    'tests/README.md should document the package testing standard'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'vitest.config.js')),
    'debug console topbar should define a vitest config'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/unit/src/topbar-entry-source.test.js')),
    'source-oriented tests should live under tests/unit/src'
  )
})
