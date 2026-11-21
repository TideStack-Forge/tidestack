import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020.js'
import standaloneCode from 'ajv/dist/standalone/index.js'
import { buildSync } from 'esbuild'

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const repoRoot = join(packageRoot, '..', '..', '..')
const distRoot = join(packageRoot, 'dist')
const schemas = readdirSync(join(packageRoot, 'schemas'))
  .filter((name) => name.endsWith('.schema.json'))
  .sort()
  .map((name) => JSON.parse(readFileSync(join(packageRoot, 'schemas', name), 'utf8')))

rmSync(distRoot, { recursive: true, force: true })
mkdirSync(distRoot, { recursive: true })

writeManifest({
  sourceDir: 'metadata-types',
  indexFile: 'index.json',
  outputFile: 'metadataTypeContributions.json',
  manifestKey: 'types',
})
writeManifest({
  sourceDir: 'metadata-editors',
  indexFile: 'index.json',
  outputFile: 'metadataEditorRegistrations.json',
  manifestKey: 'editors',
})
copyResourceDirectory('metadata-types')
copyResourceDirectory('schemas')
copyResourceDirectory('metadata-editors')
copyResourceDirectory('i18n')
if (existsSync(join(packageRoot, 'editor-schemas'))) {
  copyResourceDirectory('editor-schemas')
}
writeEditorIntegrationResources()
cpSync(join(packageRoot, 'tidestackDevelopCapabilities.json'), join(distRoot, 'tidestackDevelopCapabilities.json'))
cpSync(join(packageRoot, 'metadataCreationContract.json'), join(distRoot, 'metadataCreationContract.json'))
cpSync(join(packageRoot, 'metadataCreationCases.json'), join(distRoot, 'metadataCreationCases.json'))
cpSync(join(packageRoot, 'metadataEditorAssets.json'), join(distRoot, 'metadataEditorAssets.json'))

function writeManifest({ sourceDir, indexFile, outputFile, manifestKey }) {
  const sourceRoot = join(packageRoot, sourceDir)
  const indexPath = join(sourceRoot, indexFile)
  const order = existsSync(indexPath)
    ? JSON.parse(readFileSync(indexPath, 'utf8')).order
    : readdirSync(sourceRoot).filter((fileName) => fileName.endsWith('.json') && fileName !== indexFile).sort()
  const entries = order.map((fileName) => loadDefinition(sourceRoot, fileName))
  const manifest = { [manifestKey]: entries }
  if (manifestKey === 'types') {
    manifest.schemas = schemas
    writeValidators()
  }
  writeFileSync(join(distRoot, outputFile), `${JSON.stringify(manifest, null, 2)}\n`)
}

function writeValidators() {
  // Compile during packaging so browser hosts need neither network access nor unsafe-eval.
  const ajv = new Ajv2020({ allErrors: true, strict: false, validateFormats: false, code: { source: true } })
  schemas.forEach((schema) => ajv.addSchema(schema))
  const exports = Object.fromEntries(schemas.map((schema, index) => [`validate${index}`, schema.$id]))
  const validators = schemas.map((schema, index) => `${JSON.stringify(schema.$id)}: exports.validate${index}`)
  const source = `${standaloneCode(ajv, exports)}\nexports.validators = {${validators.join(',')}};\n`
  const bundled = buildSync({
    stdin: { contents: source, resolveDir: packageRoot, sourcefile: 'metadataValidators.cjs' },
    bundle: true,
    platform: 'neutral',
    format: 'cjs',
    write: false,
  })
  writeFileSync(join(distRoot, 'metadataValidators.cjs'), bundled.outputFiles[0].contents)
  writeFileSync(join(distRoot, 'metadataValidators.d.cts'), `export interface ValidationError {
  instancePath: string
  keyword: string
  params: Record<string, unknown>
  message?: string
}
export interface MetadataValidator {
  (value: unknown): boolean
  errors?: ValidationError[] | null
}
export const validators: Record<string, MetadataValidator>
`)
}

function loadDefinition(sourceRoot, fileName) {
  const definition = JSON.parse(readFileSync(join(sourceRoot, fileName), 'utf8'))
  if (definition.jsonSchemaFile) {
    definition.jsonSchema = JSON.parse(readFileSync(join(packageRoot, definition.jsonSchemaFile), 'utf8'))
    delete definition.jsonSchemaFile
  }
  if (definition.legacyJsonSchemaFile) {
    definition.legacyJsonSchema = JSON.parse(readFileSync(join(packageRoot, definition.legacyJsonSchemaFile), 'utf8'))
    delete definition.legacyJsonSchemaFile
  }
  if (definition.editorSchemaFile) {
    const editorSchema = JSON.parse(readFileSync(join(packageRoot, definition.editorSchemaFile), 'utf8'))
    definition.editorSchema = adaptEditorSchemaForIde(definition.type, editorSchema)
    delete definition.editorSchemaFile
  }
  return definition
}

function adaptEditorSchemaForIde(type, schema) {
  if (type !== 'business') {
    return schema
  }
  rewriteBusinessFieldTypeSources(schema)
  removeStringValidateHandlers(schema)
  normalizeBusinessBaseInfoCollapse(schema)
  appendBusinessFeaturePluginHints(schema)
  return schema
}

function rewriteBusinessFieldTypeSources(schema) {
  visitJson(schema, (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return
    }
    if (typeof value.source === 'string') {
      value.source = rewriteBusinessFieldTypeUrl(value.source)
    }
    if (value.source && typeof value.source === 'object' && typeof value.source.url === 'string') {
      value.source.url = rewriteBusinessFieldTypeUrl(value.source.url)
    }
    if (value.schemaApi && typeof value.schemaApi === 'object' && typeof value.schemaApi.url === 'string') {
      value.schemaApi.url = rewriteBusinessFieldTypeUrl(value.schemaApi.url)
    }
  })
}

function rewriteBusinessFieldTypeUrl(url) {
  return url
    .replace('/api/app/${appName}/field-types/', 'ide://editor-integrations/business/field-types/')
    .replace('/api/app/{appName}/field-types/', 'ide://editor-integrations/business/field-types/')
}

function removeStringValidateHandlers(schema) {
  visitJson(schema, (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return
    }
    if (typeof value.validate === 'string') {
      delete value.validate
    }
  })
}

function normalizeBusinessBaseInfoCollapse(schema) {
  const shell = Array.isArray(schema.body) ? schema.body[0] : null
  const panel = shell && Array.isArray(shell.body) ? shell.body[0] : null
  if (!panel || !Array.isArray(panel.body) || !panel.body[0] || panel.body[0].type !== 'collapse') {
    return
  }
  const collapse = panel.body[0]
  panel.header = collapse.header
  panel.body[0] = {
    type: 'fieldSet',
    title: createBusinessBaseInfoToggle('更多', 'fas fa-angle-down'),
    collapseTitle: createBusinessBaseInfoToggle('收起', 'fas fa-angle-up'),
    titlePosition: 'bottom',
    showArrow: false,
    collapsed: true,
    collapsable: true,
    headingClassName: 'w-full flex justify-center p-0',
    body: Array.isArray(collapse.body) ? collapse.body : [],
  }
}

function createBusinessBaseInfoToggle(label, icon) {
  return {
    type: 'wrapper',
    className: 'p-0 text-secondary w-full flex justify-center hover:text-primary',
    body: [
      label,
      {
        type: 'icon',
        vendor: '',
        className: 'ml-1 mt-1',
        icon,
      },
    ],
  }
}

function appendBusinessFeaturePluginHints(schema) {
  const serialized = JSON.stringify(schema)
  const needsArchiveHint = !serialized.includes('自动生成并列归档模型')
  const needsHistoryHint = !serialized.includes('自动生成并列历史模型')
  if (!needsArchiveHint && !needsHistoryHint) {
    return
  }
  visitJson(schema, (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value) || value.id !== 'businessModelFeaturesPanel' || !Array.isArray(value.body)) {
      return
    }
    if (needsArchiveHint) {
      value.body.push(createBusinessFeatureHint('${enableArchiveDelete}', '归档删除会自动生成并列归档模型，删除时将记录复制到归档模型中，然后从源表物理删除。'))
    }
    if (needsHistoryHint) {
      value.body.push(createBusinessFeatureHint('${enableScd2History}', 'SCD2历史追踪会自动生成并列历史模型，在记录变更时创建新版本并记录操作人。'))
    }
  })
}

function createBusinessFeatureHint(visibleOn, body) {
  return {
    type: 'container',
    body: [
      {
        type: 'container',
        visibleOn,
        body: [
          {
            type: 'alert',
            level: 'info',
            body,
          },
        ],
      },
    ],
  }
}

function visitJson(value, visitor) {
  visitor(value)
  if (Array.isArray(value)) {
    value.forEach((item) => visitJson(item, visitor))
    return
  }
  if (!value || typeof value !== 'object') {
    return
  }
  Object.values(value).forEach((item) => visitJson(item, visitor))
}

function copyResourceDirectory(relativePath) {
  cpSync(join(packageRoot, relativePath), join(distRoot, relativePath), { recursive: true })
}

function writeEditorIntegrationResources() {
  const integrationRoot = join(distRoot, 'editor-integrations')
  mkdirSync(integrationRoot, { recursive: true })

  const logicflowNodeDefines = buildLogicflowNodeDefines()
  const approvalWorkflowCandidateSource = buildApprovalWorkflowCandidateSource()
  const approvalWorkflowFormFieldSource = {
    sourceKind: 'ide-static-empty-form',
    revisionToken: 'ide-static-empty-form-fields:0',
    fields: [],
  }
  const businessFieldTypes = buildBusinessFieldTypes()
  const integrations = {
    schemaVersion: 1,
    endpoints: {
      logicflowNodeDefines: 'ide://editor-integrations/logicflow/node-defines',
      approvalWorkflowCandidateSource: 'ide://editor-integrations/approval-workflow/candidate',
      approvalWorkflowCandidateItems: 'ide://editor-integrations/approval-workflow/candidate/${candidateName}/items',
      approvalWorkflowCandidateCategories: 'ide://editor-integrations/approval-workflow/candidate/${candidateName}/category',
      approvalWorkflowFormFieldSource: 'ide://editor-integrations/approval-workflow/form-fields',
      businessFieldTypesPrimary: 'ide://editor-integrations/business/field-types/primary',
      businessFieldTypesNormal: 'ide://editor-integrations/business/field-types/normal',
      businessFieldTypeUiSchema: 'ide://editor-integrations/business/field-types/${fieldType}/ui-schema',
    },
    logicflowNodeDefines,
    approvalWorkflowCandidateSource,
    approvalWorkflowFormFieldSource,
    businessFieldTypes,
  }

  writeJson(join(integrationRoot, 'logicflow-node-defines.json'), logicflowNodeDefines)
  writeJson(join(integrationRoot, 'approval-workflow-candidate-source.json'), approvalWorkflowCandidateSource)
  writeJson(join(integrationRoot, 'approval-workflow-form-field-source.json'), approvalWorkflowFormFieldSource)
  writeJson(join(integrationRoot, 'business-field-types.json'), businessFieldTypes)
  writeFileSync(
    join(integrationRoot, 'metadata-editor-integrations.js'),
    `;(function (global) {\n  global.OuroborosMetadataEditorIntegrations = ${toScriptJson(integrations)};\n})(window);\n`,
  )
}

function buildLogicflowNodeDefines() {
  const sourceRoots = ['core', 'ability', 'security']
  const items = sourceRoots
    .flatMap((root) => collectFiles(join(repoRoot, root), (path) => path.endsWith('.flow-node.json') && path.includes('/src/main/metadata/')))
    .map((path) => JSON.parse(readFileSync(path, 'utf8')))
    .sort((left, right) => {
      const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER
      const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER
      return leftOrder - rightOrder || String(left.type || '').localeCompare(String(right.type || ''))
    })
  return {
    revisionToken: `logicflow-node-defines:${items.length}`,
    items,
  }
}

function buildApprovalWorkflowCandidateSource() {
  return {
    revisionToken: 'approval-workflow-design-time-candidates:static-v1',
    items: [
      {
        name: 'Role',
        label: '角色',
        configMode: 'MultiSelect',
        valueKey: 'roles',
        disabledIn: [],
        max: null,
        min: 0,
        items: [],
      },
      {
        name: 'Position',
        label: '岗位',
        configMode: 'MultiSelect',
        valueKey: 'positions',
        disabledIn: [],
        max: null,
        min: 0,
        items: [],
      },
      {
        name: 'Starter',
        label: '流程发起人',
        configMode: 'Check',
        valueKey: 'isChecked',
        disabledIn: ['会签'],
        max: 1,
        min: 1,
        items: [],
      },
      {
        name: 'FormField',
        label: '表单字段',
        configMode: 'SingleSelect',
        valueKey: 'field',
        disabledIn: [],
        max: 1,
        min: 1,
        schema: {
          type: 'select',
          name: 'field',
          label: '请选择处理人字段',
          source: '$candidateFieldSource.fields',
          labelField: 'label',
          valueField: 'value',
          joinValues: false,
          extractValue: true,
          searchable: true,
          clearable: true,
          required: true,
          mode: 'normal',
          validateOnChange: true,
        },
        items: [],
      },
      {
        name: 'UserSelect',
        label: '用户自选',
        configMode: 'Enum',
        valueKey: 'mode',
        disabledIn: [],
        max: 1,
        min: 1,
        items: [
          { label: '单人', value: 'single' },
          { label: '多人', value: 'multiple' },
        ],
      },
    ],
  }
}

function buildBusinessFieldTypes() {
  const sourceRoots = ['core', 'ability', 'security']
  const definitions = deduplicateFieldTypeDefinitions(sourceRoots
    .flatMap((root) => collectFiles(join(repoRoot, root), (path) => path.endsWith('.field-type.json') && path.includes('/src/main/metadata/')))
    .map((path) => ({ path, definition: JSON.parse(readFileSync(path, 'utf8')) })))
    .sort(compareFieldTypeDefinitionEntries)
  const normal = definitions
    .filter(({ definition }) => !Boolean(definition.isPrimaryKeyType))
    .map(({ definition }) => toFieldTypeOption(definition))
  const primary = definitions
    .filter(({ definition }) => Boolean(definition.isPrimaryKeyType))
    .map(({ definition }) => toFieldTypeOption(definition))
  const uiSchemas = definitions.reduce((result, { definition }) => {
    if (typeof definition.name === 'string' && definition.name) {
      result[definition.name] = Array.isArray(definition.paramsUiSchema) ? definition.paramsUiSchema : []
    }
    return result
  }, {})

  return {
    sourceKind: 'ide-static-business-field-types',
    revisionToken: `business-field-types:${definitions.length}`,
    items: definitions.map(({ definition }) => definition),
    normal,
    primary,
    uiSchemas,
  }
}

function deduplicateFieldTypeDefinitions(entries) {
  const byName = new Map()
  entries
    .filter(({ definition }) => definition && typeof definition.name === 'string' && definition.name)
    .sort(compareFieldTypeDefinitionEntries)
    .forEach((entry) => {
      const existing = byName.get(entry.definition.name)
      if (!existing || shouldReplaceFieldTypeDefinition(existing, entry)) {
        byName.set(entry.definition.name, entry)
      }
    })
  return Array.from(byName.values())
}

function shouldReplaceFieldTypeDefinition(existing, candidate) {
  const existingIsDevMetadata = existing.path.includes('/src/main/metadata/dev/')
  const candidateIsDevMetadata = candidate.path.includes('/src/main/metadata/dev/')
  return existingIsDevMetadata && !candidateIsDevMetadata
}

function compareFieldTypeDefinitionEntries(left, right) {
  const leftOrder = typeof left.definition.order === 'number' ? left.definition.order : Number.MAX_SAFE_INTEGER
  const rightOrder = typeof right.definition.order === 'number' ? right.definition.order : Number.MAX_SAFE_INTEGER
  return leftOrder - rightOrder || String(left.definition.name || '').localeCompare(String(right.definition.name || ''))
}

function toFieldTypeOption(definition) {
  return {
    label: definition.label || definition.name,
    value: definition.name,
  }
}

function collectFiles(root, predicate) {
  if (!existsSync(root)) {
    return []
  }
  return readdirSync(root).flatMap((name) => {
    const path = join(root, name)
    const normalizedPath = path.replace(/\\/g, '/')
    if (['.git', '.worktrees', '.ai-workflows', 'node_modules', 'target', 'dist'].includes(name)) {
      return []
    }
    const stat = statSync(path)
    if (stat.isDirectory()) {
      return collectFiles(path, predicate)
    }
    return predicate(normalizedPath) ? [normalizedPath] : []
  })
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`)
}

function toScriptJson(value) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
}
