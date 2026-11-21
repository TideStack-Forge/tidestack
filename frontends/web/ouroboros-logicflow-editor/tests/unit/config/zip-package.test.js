const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')
const { unzipSync } = require('cross-zip')

const script = path.resolve(__dirname, '../../../scripts/zip-package.cjs')

function createWorkspace() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'logicflow-package-'))
  const input = path.join(root, 'dist')
  const archive = path.join(root, 'editor.zip')
  fs.mkdirSync(input)
  return { root, input, archive }
}

function pack(workspace) {
  return spawnSync(process.execPath, [script, workspace.input, workspace.archive], { encoding: 'utf8' })
}

test('repeated packaging replaces removed build files with the current build', () => {
  const workspace = createWorkspace()
  try {
    fs.writeFileSync(path.join(workspace.input, 'canvas.old.js'), 'old build')
    assert.equal(pack(workspace).status, 0)
    fs.rmSync(path.join(workspace.input, 'canvas.old.js'))
    fs.writeFileSync(path.join(workspace.input, 'canvas.current.js'), 'current build')
    assert.equal(pack(workspace).status, 0)
    const extracted = path.join(workspace.root, 'extracted')
    unzipSync(workspace.archive, extracted)
    assert.deepEqual(fs.readdirSync(extracted), ['canvas.current.js'])
    assert.equal(fs.readFileSync(path.join(extracted, 'canvas.current.js'), 'utf8'), 'current build')
  } finally {
    fs.rmSync(workspace.root, { recursive: true, force: true })
  }
})

test('failed packaging preserves the previous usable archive and releases temporary files', () => {
  const workspace = createWorkspace()
  try {
    fs.writeFileSync(path.join(workspace.input, 'canvas.js'), 'usable build')
    assert.equal(pack(workspace).status, 0)
    const before = fs.readFileSync(workspace.archive)
    fs.rmSync(path.join(workspace.input, 'canvas.js'))
    assert.notEqual(pack(workspace).status, 0)
    assert.deepEqual(fs.readFileSync(workspace.archive), before)
    assert.deepEqual(fs.readdirSync(workspace.root).sort(), ['dist', 'editor.zip'])
  } finally {
    fs.rmSync(workspace.root, { recursive: true, force: true })
  }
})
