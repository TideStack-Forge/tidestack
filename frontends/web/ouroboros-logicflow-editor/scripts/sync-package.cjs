const fs = require('node:fs')
const path = require('node:path')

const sourceZipName = 'logicFlowEditor.zip'
const targetZipRelativePath =
  'core/ouroboros-logicflow/ouroboros-logicflow-editor-assets/src/main/metadata/micro-web/package/logicFlowEditor.zip'

function syncPackage(options = {}) {
  const packageRoot = options.packageRoot || path.resolve(__dirname, '..')
  const repoRoot = options.repoRoot || path.resolve(packageRoot, '..', '..', '..')
  const sourceZipPath = path.resolve(packageRoot, sourceZipName)
  const targetZipPath = path.resolve(repoRoot, targetZipRelativePath)

  if (!fs.existsSync(sourceZipPath)) {
    throw new Error(`Source zip not found: ${sourceZipPath}`)
  }

  const build = JSON.parse(fs.readFileSync(path.join(packageRoot, 'dist/editor-build.json'), 'utf8'))
  if (!build.buildId || !build.entry?.endsWith('/') || build.publicPath !== build.entry) {
    throw new Error('Editor build metadata is missing a consistent version directory')
  }
  const descriptorPath = path.resolve(repoRoot,
    'core/ouroboros-logicflow/ouroboros-logicflow-editor-assets/src/main/metadata/micro-web/logicFlowModelEditor.micro-web.json')
  fs.mkdirSync(path.dirname(descriptorPath), { recursive: true })
  fs.writeFileSync(descriptorPath, JSON.stringify({ name: 'microLogicflow', type: 'library',
    entry: build.entry, publicPath: build.publicPath, packageName: sourceZipName }, null, 2) + '\n')

  fs.mkdirSync(path.dirname(targetZipPath), { recursive: true })
  fs.copyFileSync(sourceZipPath, targetZipPath)
  console.log(`Copied ${sourceZipPath} -> ${targetZipPath}`)

  return { sourceZipPath, targetZipPath, descriptorPath }
}

if (require.main === module) {
  syncPackage()
}

module.exports = { syncPackage }
