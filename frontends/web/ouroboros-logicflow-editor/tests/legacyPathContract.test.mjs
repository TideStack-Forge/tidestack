import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')

function readProjectFile(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

function listSourceFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      return listSourceFiles(entryPath)
    }
    return entry.isFile() ? [entryPath] : []
  })
}

test('frontend built-in node define merge path is not exposed as a formal source', () => {
  const source = [
    readProjectFile('src/node-define/index.js'),
    readProjectFile('src/logicFlowEditor/state/useLogicFlowEditorState.js'),
    readProjectFile('src/logicFlowEditor/state/editorStateHelpers.mjs'),
  ].join('\n')

  assert.doesNotMatch(source, /getBuiltInNodeDefines/)
  assert.doesNotMatch(source, /mergeNodeDefineSources/)
  assert.doesNotMatch(source, /disableBuiltIn/)
  assert.doesNotMatch(source, /overwriteBuiltIn/)
  assert.equal(fs.existsSync(path.join(projectRoot, 'src/node-define/switch.js')), false)
})

test('frontend capability normalization does not contain type-based legacy overrides', () => {
  const source = [
    readProjectFile('src/node-define/editorCapabilityDefaults.mjs'),
    readProjectFile('src/node-define/editorCapabilities.js'),
  ].join('\n')

  assert.doesNotMatch(source, /legacyTypeOverrides/)
})

test('frontend output port normalization does not contain switch type fallback', () => {
  const source = readProjectFile('src/node/outputPortNormalization.mjs')

  assert.doesNotMatch(source, /legacySelectiveOutputConfigs/)
  assert.doesNotMatch(source, /SWITCH_EXCLUSIVE_GROUP_ID/)
})

test('unknown node fallback remains render-only', () => {
  const productionFiles = listSourceFiles(path.join(projectRoot, 'src'))
    .map((absolutePath) => path.relative(projectRoot, absolutePath))
    .filter((relativePath) => relativePath !== 'src/node-define/unknow.js')

  const consumers = productionFiles.filter((relativePath) =>
    readProjectFile(relativePath).includes('node-define/unknow')
  )

  assert.deepEqual(consumers, ['src/node/transpile.js'])
})
