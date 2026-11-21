const PLAYGROUND_APP_NAME = 'playground-app'
const METADATA_MODULE_NAME = 'script-intelligence'
const METADATA_SOURCE_CLASS_NAME =
  'com.ouroboros.devhelper.metadata.collector.ScriptIntelligenceMetadataCollector'
const METADATA_SOURCE_TYPE = 'pro-code'
const SUPPORTED_LANGUAGES = ['javascript', 'groovy']

const PLAYGROUND_FLOW_MODEL = {
  name: 'MockOrderFlow',
  nodes: [
    {
      id: 'script-node-1',
      name: 'script-node-1',
      type: 'script',
    },
  ],
}

const BASE_SNAPSHOT = {
  languageProjectionHints: {},
  javaImports: ['java.time.LocalDate'],
  javaTypes: [
    {
      qualifiedName: 'java.time.LocalDate',
      simpleName: 'LocalDate',
      packageName: 'java.time',
      constructors: [
        {
          name: 'LocalDate',
          memberKind: 'constructor',
          parameterTypeDisplayNames: ['int', 'int', 'int'],
        },
      ],
      staticMethods: [
        {
          name: 'now',
          memberKind: 'method',
          returnTypeDisplayName: 'LocalDate',
          parameterTypeDisplayNames: [],
        },
      ],
      staticFields: [
        {
          name: 'MIN',
          memberKind: 'field',
          returnTypeDisplayName: 'LocalDate',
        },
      ],
      instanceMethods: [
        {
          name: 'getYear',
          memberKind: 'method',
          returnTypeDisplayName: 'int',
          parameterTypeDisplayNames: [],
        },
      ],
    },
  ],
  globals: [
    {
      name: '$input',
      kind: 'object',
      typeDisplayName: 'OrderInput',
      documentation: 'Logicflow node input payload',
      members: [
        {
          name: 'orderId',
          memberKind: 'field',
          returnTypeDisplayName: 'String',
        },
        {
          name: 'submittedAt',
          memberKind: 'field',
          returnTypeDisplayName: 'LocalDate',
        },
        {
          name: 'toMap',
          memberKind: 'method',
          returnTypeDisplayName: 'Map',
          parameterTypeDisplayNames: [],
        },
      ],
    },
    {
      name: 'LocalDate',
      kind: 'class',
      typeDisplayName: 'java.time.LocalDate',
      qualifiedTypeName: 'java.time.LocalDate',
    },
  ],
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

function buildMockSnapshot(request) {
  const language = request && request.language ? request.language : 'unknown'
  return Object.assign(cloneJson(BASE_SNAPSHOT), {
    source: 'playground-mock',
    runtimeProfile: 'playground-mock',
    versionToken: `${language}-playground-v1`,
  })
}

function toRuntimeProfile(language) {
  if (language === 'javascript') {
    return 'logicflow-graaljs'
  }
  if (language === 'groovy') {
    return 'logicflow-groovy'
  }
  throw new Error(`不支持的脚本语言: ${language}`)
}

function buildSnapshot(language, source, versionToken) {
  return Object.assign(cloneJson(BASE_SNAPSHOT), {
    source,
    runtimeProfile: toRuntimeProfile(language),
    versionToken,
  })
}

function buildApiSnapshot(request) {
  const language = request && request.language ? request.language : 'javascript'
  return buildSnapshot(language, 'dev-helper-cache', `${language}-api-v1`)
}

function buildMetadataSnapshot(language) {
  return buildSnapshot(language, 'dev-helper-runtime', `${language}-metadata-v1`)
}

function buildMetadataRecord(language) {
  return {
    name: language,
    type: METADATA_MODULE_NAME,
    label: toRuntimeProfile(language),
    priority: 2147483647,
    sourceClassName: METADATA_SOURCE_CLASS_NAME,
    sourceType: METADATA_SOURCE_TYPE,
    details: buildMetadataSnapshot(language),
  }
}

function buildMetadataRecords() {
  return SUPPORTED_LANGUAGES.map((language) => buildMetadataRecord(language))
}

function buildApiEnvelope(request) {
  const language = request && request.language ? request.language : 'javascript'
  if (!SUPPORTED_LANGUAGES.includes(language)) {
    return {
      code: -1,
      status: -1,
      msg: `不支持的脚本语言: ${language}`,
      data: null,
    }
  }
  return {
    code: 0,
    status: 0,
    msg: '',
    data: {
      documentId: request.documentId, documentVersion: request.documentVersion, draftRevision: request.draftRevision,
      snapshot: buildApiSnapshot(request),
    },
  }
}

module.exports = {
  METADATA_MODULE_NAME,
  PLAYGROUND_APP_NAME,
  PLAYGROUND_FLOW_MODEL,
  buildApiEnvelope,
  buildMockSnapshot,
  buildMetadataRecords,
}
