const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const { syncPackage } = require('../../../scripts/sync-package.cjs')

const packageRoot = path.resolve(__dirname, '..', '..', '..')
const targetRelativePath =
  'dev-module/ouroboros-dev-app-management/src/main/metadata/micro-web/package/debugConsoleHeader.zip'

function createWorkspace() {
  const repoRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'debug-console-sync-'))
  const packageRoot = path.join(repoRoot, 'frontends/web/ouroboros-debug-console-topbar')

  fs.mkdirSync(packageRoot, { recursive: true })

  return { repoRoot, packageRoot }
}

test('debug console topbar sync copies the zip and exposes sync scripts', () => {
  const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))
  const workspace = createWorkspace()

  try {
    assert.equal(packageJson.scripts.sync, 'node ./scripts/sync-package.cjs')
    assert.equal(packageJson.scripts['package:sync'], 'npm run package && npm run sync')

    const sourceZipPath = path.join(workspace.packageRoot, 'debugConsoleHeader.zip')
    const targetZipPath = path.join(workspace.repoRoot, targetRelativePath)

    fs.writeFileSync(sourceZipPath, 'debug-console-header-zip-bytes')

    const result = syncPackage({ packageRoot: workspace.packageRoot, repoRoot: workspace.repoRoot })

    assert.equal(fs.readFileSync(targetZipPath, 'utf8'), 'debug-console-header-zip-bytes')
    assert.equal(result.sourceZipPath, sourceZipPath)
    assert.equal(result.targetZipPath, targetZipPath)
  } finally {
    fs.rmSync(workspace.repoRoot, { recursive: true, force: true })
  }
})

test('debug console topbar sync fails when the source zip is missing', () => {
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
