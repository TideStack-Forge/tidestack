import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const TEST_DIR = path.dirname(fileURLToPath(import.meta.url))
const ICON_INDEX_PATH = path.resolve(TEST_DIR, '../src/icons/index.js')

test('icon registration uses explicit svg imports instead of require.context', async () => {
  const source = await fs.readFile(ICON_INDEX_PATH, 'utf8')

  assert.doesNotMatch(source, /require\.context/)
  assert.match(source, /import '\.\/svg\/mark\.svg'/)
  assert.match(source, /import '\.\/svg\/unknown\.svg'/)
})
