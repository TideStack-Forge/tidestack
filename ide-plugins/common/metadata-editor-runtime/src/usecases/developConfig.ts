export type JsonObject = Record<string, unknown>

export interface TideStackDevelopConfigSectionContract {
  path: string
  fields: string[]
  legacyAliases: Record<string, string[]>
  removedFields?: string[]
  optionalFields?: string[]
}

export interface TideStackDevelopConfigContract {
  schemaVersion: number
  project: TideStackDevelopConfigSectionContract
  local: TideStackDevelopConfigSectionContract
}

export interface TideStackDevelopProjectConfig {
  schemaVersion: number
  developmentServerUrl: string
  appName: string
}

export interface TideStackDevelopLocalConfig {
  schemaVersion: number
  devKey: string
}

export interface TideStackDevelopEffectiveConfig {
  developmentServerUrl: string
  appName: string
  devKey: string
}

export function projectDevelopConfigFromDocument(
  document: JsonObject,
  contract: TideStackDevelopConfigContract,
  legacy: Partial<TideStackDevelopProjectConfig> = {},
): TideStackDevelopProjectConfig {
  return {
    schemaVersion: readSchemaVersion(document, contract.schemaVersion),
    developmentServerUrl: readContractString(document, contract.project, 'developmentServerUrl') || legacy.developmentServerUrl || '',
    appName: readString(document, 'appName') || legacy.appName || '',
  }
}

export function localDevelopConfigFromDocument(
  document: JsonObject,
  contract: TideStackDevelopConfigContract,
  legacy: Partial<TideStackDevelopLocalConfig> = {},
): TideStackDevelopLocalConfig {
  return {
    schemaVersion: readSchemaVersion(document, contract.schemaVersion),
    devKey: readContractString(document, contract.local, 'devKey') || legacy.devKey || '',
  }
}

export function effectiveDevelopConfig(
  projectConfig: TideStackDevelopProjectConfig,
  localConfig: TideStackDevelopLocalConfig,
): TideStackDevelopEffectiveConfig {
  return {
    developmentServerUrl: projectConfig.developmentServerUrl,
    appName: projectConfig.appName,
    devKey: localConfig.devKey,
  }
}

export function projectDevelopConfigDocument(
  existing: JsonObject,
  config: TideStackDevelopProjectConfig,
  contract: TideStackDevelopConfigContract,
): JsonObject {
  return orderedContractDocument(existing, {
    schemaVersion: config.schemaVersion || contract.schemaVersion,
    developmentServerUrl: config.developmentServerUrl,
    appName: config.appName,
  }, contract.project)
}

export function localDevelopConfigDocument(
  existing: JsonObject,
  config: TideStackDevelopLocalConfig,
  contract: TideStackDevelopConfigContract,
): JsonObject {
  return orderedContractDocument(existing, {
    schemaVersion: config.schemaVersion || contract.schemaVersion,
    devKey: config.devKey,
  }, contract.local)
}

export function developConfigImportDocument(document: JsonObject): JsonObject {
  const data = document.data
  return isJsonObject(data) ? data : document
}

export function orderedContractDocument(
  existing: JsonObject,
  knownFields: JsonObject,
  section: TideStackDevelopConfigSectionContract,
): JsonObject {
  const knownNames = new Set([
    ...section.fields,
    ...Object.values(section.legacyAliases).flat(),
    ...(section.removedFields ?? []),
  ])
  const ordered: JsonObject = {}
  for (const field of section.fields) {
    if (field in knownFields) {
      ordered[field] = knownFields[field]
    }
  }
  for (const [key, value] of Object.entries(existing)) {
    if (!knownNames.has(key)) {
      ordered[key] = value
    }
  }
  return ordered
}

export function readContractString(
  document: JsonObject,
  section: TideStackDevelopConfigSectionContract,
  key: string,
): string {
  for (const field of [key, ...(section.legacyAliases[key] ?? [])]) {
    const result = readString(document, field)
    if (result) {
      return result
    }
  }
  return ''
}

function readString(value: JsonObject, key: string): string {
  const rawValue = value[key]
  return typeof rawValue === 'string' ? rawValue : ''
}

function isJsonObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readSchemaVersion(value: JsonObject, fallback: number): number {
  const rawValue = value.schemaVersion
  return typeof rawValue === 'number' ? rawValue : fallback
}
