import { cpSync, existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const pluginRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const idePluginsRoot = dirname(pluginRoot)
const repoRoot = dirname(idePluginsRoot)
const outputRoot = join(pluginRoot, 'dist', 'media')
const amisSdkSource = join(repoRoot, 'frontends', 'web', 'node_modules', 'amis', 'sdk')
const xlsxRuntimeSource = join(repoRoot, 'frontends', 'web', 'node_modules', 'xlsx', 'dist')
const runtimeRoot = join(idePluginsRoot, 'common', 'metadata-editor-runtime')
const schemaDistRoot = join(idePluginsRoot, 'common', 'metadata-editor-schema', 'dist')
const contractRoot = join(idePluginsRoot, 'common', 'contracts')
const assetManifest = JSON.parse(readFileSync(join(schemaDistRoot, 'metadataEditorAssets.json'), 'utf8'))
const editorBundles = Array.isArray(assetManifest.editorBundles) ? assetManifest.editorBundles : []

const assets = [
  ...assetManifest.runtimeFiles.map((relativePath) => ({
    source: join(runtimeRoot, relativePath),
    target: join(outputRoot, relativePath),
  })),
  ...assetManifest.schemaFiles.map((relativePath) => ({
    source: join(schemaDistRoot, relativePath),
    target: join(outputRoot, relativePath),
  })),
  ...(assetManifest.editorIntegrationFiles || []).map((relativePath) => ({
    source: join(schemaDistRoot, relativePath),
    target: join(outputRoot, relativePath),
  })),
  ...assetManifest.xlsxRuntimeFiles.map((relativePath) => ({
    source: join(xlsxRuntimeSource, relativePath),
    target: join(outputRoot, 'xlsx', relativePath),
  })),
  ...assetManifest.contractFiles.map((fileName) => ({
    source: join(contractRoot, fileName),
    target: join(pluginRoot, 'dist', 'contracts', fileName),
  })),
  ...editorBundles.flatMap((bundle) => (bundle.requiredFiles || []).map((relativePath) => ({
    source: join(repoRoot, bundle.sourceRoot, relativePath),
    target: join(outputRoot, bundle.targetRoot, relativePath),
  }))),
]

const missing = assets.filter((asset) => !existsSync(asset.source))
if (!existsSync(amisSdkSource)) {
  missing.push({ source: amisSdkSource })
}
if (missing.length) {
  throw new Error(`Missing VS Code metadata webview assets:\n${missing.map((asset) => `- ${asset.source}`).join('\n')}`)
}

rmSync(outputRoot, { recursive: true, force: true })
for (const asset of assets) {
  mkdirSync(dirname(asset.target), { recursive: true })
  cpSync(asset.source, asset.target)
}
cpSync(amisSdkSource, join(outputRoot, 'amis-sdk'), { recursive: true })
for (const bundle of editorBundles) {
  cpSync(join(repoRoot, bundle.sourceRoot), join(outputRoot, bundle.targetRoot), { recursive: true })
}
