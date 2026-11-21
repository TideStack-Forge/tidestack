import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const schemaDistRoot = join(packageRoot, '..', 'metadata-editor-schema', 'dist')
const targetRoot = join(packageRoot, 'src', 'schema-resources')

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
const missing = required.filter((relativePath) => !existsSync(join(schemaDistRoot, relativePath)))
if (missing.length) {
  throw new Error(`Missing metadata editor schema resources:\n${missing.map((item) => `- ${join(schemaDistRoot, item)}`).join('\n')}`)
}

rmSync(targetRoot, { recursive: true, force: true })
mkdirSync(targetRoot, { recursive: true })
for (const relativePath of required) {
  const target = join(targetRoot, relativePath)
  mkdirSync(dirname(target), { recursive: true })
  cpSync(join(schemaDistRoot, relativePath), target)
}
