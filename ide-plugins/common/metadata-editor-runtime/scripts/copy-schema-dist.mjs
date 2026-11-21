import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const schemaSourceRoot = join(packageRoot, 'src', 'schema-resources')
const distRoot = join(packageRoot, 'dist')
const distSchemaRoot = join(distRoot, 'schema-resources')

const required = [
  'metadataTypeContributions.json',
  'metadataValidators.cjs',
  'metadataValidators.d.cts',
  'metadataEditorRegistrations.json',
  'metadataCreationContract.json',
  'metadataCreationCases.json',
  'metadataEditorAssets.json',
  'tidestackDevelopCapabilities.json',
  'editor-integrations/logicflow-node-defines.json',
  'editor-integrations/approval-workflow-candidate-source.json',
  'editor-integrations/approval-workflow-form-field-source.json',
  'editor-integrations/business-field-types.json',
  'editor-integrations/metadata-editor-integrations.js',
  'i18n/en-US.json',
  'i18n/zh-CN.json',
]
const missing = required.filter((relativePath) => !existsSync(join(schemaSourceRoot, relativePath)))
if (missing.length) {
  throw new Error(`Missing synced metadata editor schema resources:\n${missing.map((item) => `- ${join(schemaSourceRoot, item)}`).join('\n')}`)
}

rmSync(distSchemaRoot, { recursive: true, force: true })
rmSync(join(distRoot, 'i18n'), { recursive: true, force: true })

for (const relativePath of required) {
  const source = join(schemaSourceRoot, relativePath)
  const schemaTarget = join(distSchemaRoot, relativePath)
  mkdirSync(dirname(schemaTarget), { recursive: true })
  cpSync(source, schemaTarget)

  if (!relativePath.startsWith('i18n/')) {
    cpSync(source, join(distRoot, relativePath))
  }
}
