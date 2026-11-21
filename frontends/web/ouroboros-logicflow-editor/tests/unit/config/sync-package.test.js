const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const { syncPackage } = require('../../../scripts/sync-package.cjs')

const packageRoot = path.resolve(__dirname, '..', '..', '..')
const targetRelativePath =
  'core/ouroboros-logicflow/ouroboros-logicflow-editor-assets/src/main/metadata/micro-web/package/logicFlowEditor.zip'

function createWorkspace() {
  const repoRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'logicflow-sync-'))
  const packageRoot = path.join(repoRoot, 'frontends/web/ouroboros-logicflow-editor')

  fs.mkdirSync(packageRoot, { recursive: true })

  return { repoRoot, packageRoot }
}

test('logicflow editor sync copies the zip and exposes sync scripts', () => {
  const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))
  const workspace = createWorkspace()

  try {
    assert.equal(packageJson.scripts.sync, 'node ./scripts/sync-package.cjs')
    assert.equal(packageJson.scripts['package:sync'], 'npm run package && npm run sync')

    const sourceZipPath = path.join(workspace.packageRoot, 'logicFlowEditor.zip')
    const targetZipPath = path.join(workspace.repoRoot, targetRelativePath)

    fs.mkdirSync(path.join(workspace.packageRoot, 'dist'), { recursive: true })
    fs.writeFileSync(path.join(workspace.packageRoot, 'dist/editor-build.json'), JSON.stringify({
      buildId: 'test-v1', entry: '/micro-logicflow-editor/test-v1/', publicPath: '/micro-logicflow-editor/test-v1/',
    }))
    fs.writeFileSync(sourceZipPath, 'logicflow-editor-zip-bytes')

    const result = syncPackage({ packageRoot: workspace.packageRoot, repoRoot: workspace.repoRoot })

    assert.equal(fs.readFileSync(targetZipPath, 'utf8'), 'logicflow-editor-zip-bytes')
    assert.equal(result.sourceZipPath, sourceZipPath)
    assert.equal(result.targetZipPath, targetZipPath)
    const descriptor = JSON.parse(fs.readFileSync(result.descriptorPath, 'utf8'))
    assert.equal(descriptor.entry, '/micro-logicflow-editor/test-v1/')
    assert.equal(descriptor.publicPath, descriptor.entry)
  } finally {
    fs.rmSync(workspace.repoRoot, { recursive: true, force: true })
  }
})

test('logicflow editor sync fails when the source zip is missing', () => {
  const workspace = createWorkspace()

  try {
    assert.throws(
      () => syncPackage({ packageRoot: workspace.packageRoot, repoRoot: workspace.repoRoot }),
      /Source zip not found:/
    )
  } finally {
    fs.rmSync(workspace.repoRoot, { recursive: true, force: true })
  }
})
