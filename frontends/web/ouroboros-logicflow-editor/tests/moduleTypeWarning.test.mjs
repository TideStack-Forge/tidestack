import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

test('editor source imports do not emit MODULE_TYPELESS_PACKAGE_JSON warnings under node', () => {
  const packageRoot = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    '..'
  )
  const entryFile = path.join(packageRoot, 'src', 'node', 'transpile.js')
  const result = spawnSync(
    process.execPath,
    [
      '--input-type=module',
      '-e',
      `await import(new URL(${JSON.stringify(entryFile)}, 'file://'))`,
    ],
    {
      cwd: packageRoot,
      encoding: 'utf8',
    }
  )

  assert.equal(result.status, 0, result.stderr || result.stdout)
  assert.equal(
    result.stderr.includes('MODULE_TYPELESS_PACKAGE_JSON'),
    false,
    result.stderr || 'unexpected module warning'
  )
})
