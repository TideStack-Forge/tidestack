import type {
  MetadataDiagnostic,
  MetadataPathMapping,
  MetadataTypeContribution,
} from './types'
import { metadataTypeContributionDefinitions } from './metadataTypeContributions'
import { metadataCreationContract } from './metadataCreationContract'

const SOURCE_ROOT = `${metadataCreationContract.sourceRoot}/`
const PACKAGED_RESOURCE_ROOT = `${metadataCreationContract.packagedResourceRoot}/`
const METADATA_RESOURCE_DIRECTORY = `${metadataCreationContract.metadataResourceDirectory}/`
const FIXED_METADATA_FILE_TYPES: Record<string, string> = Object.fromEntries(
  metadataTypeContributionDefinitions
    .filter((definition) => definition.fixedFileName)
    .flatMap((definition) => [
      definition.fixedFileName!,
      ...(definition.legacyFixedFileNames ?? []),
    ].map((fileName) => [fileName, definition.type])),
)
const STANDALONE_FIXED_METADATA_FILE_TYPES: Record<string, string> = Object.fromEntries(
  metadataTypeContributionDefinitions
    .filter((definition) => definition.fixedFileName)
    .map((definition) => [definition.fixedFileName!, definition.type]),
)
const FIXED_METADATA_TYPES = new Set(Object.values(FIXED_METADATA_FILE_TYPES))

export const metadataTypeContributions: MetadataTypeContribution[] = metadataTypeContributionDefinitions.map((definition) => ({
  type: definition.type,
  fileSuffix: definition.fileSuffix,
  fixedFileName: definition.fixedFileName,
  legacyFixedFileNames: [...(definition.legacyFixedFileNames ?? [])],
  displayName: definition.displayName,
  sourcePathPatterns: [...definition.sourcePathPatterns],
  packagedPathPrefix: definition.packagedPathPrefix,
  packagedPathPatterns: [...(definition.packagedPathPatterns ?? [])],
  editorKind: definition.editorKind ?? definition.editorKinds[0] ?? 'form',
  editorKinds: [...definition.editorKinds],
  documentShape: definition.documentShape ?? 'object',
  jsonSchema: cloneObject(definition.jsonSchema),
  legacyJsonSchema: definition.legacyJsonSchema ? cloneObject(definition.legacyJsonSchema) : undefined,
  fileTemplate: definition.fileTemplate ? cloneValue(definition.fileTemplate) : undefined,
  createEditorSchema: () => cloneObject(definition.editorSchema),
  normalize: normalizeObject,
}))

export function getContributionByType(type: string): MetadataTypeContribution | undefined {
  return metadataTypeContributions.find((contribution) => contribution.type === type)
}

export function getContributionBySuffix(uri: string): MetadataTypeContribution | undefined {
  const contribution = getRawContributionBySuffix(uri)
  return contribution && !FIXED_METADATA_TYPES.has(contribution.type) ? contribution : undefined
}

export function getContributionByPackagedPath(uri: string): MetadataTypeContribution | undefined {
  const relativePath = getPackagedResourceRelativePath(uri)
  if (!relativePath) {
    return undefined
  }
  if (relativePath.startsWith(METADATA_RESOURCE_DIRECTORY)) {
    return getContributionByMetadataRelativePath(relativePath.slice(METADATA_RESOURCE_DIRECTORY.length))
  }
  const suffixContribution = getRawContributionBySuffix(relativePath)
  if (suffixContribution && contributionOwnsPackagedRelativePath(suffixContribution, relativePath)) {
    return suffixContribution
  }
  return metadataTypeContributions.find((contribution) => {
    return contributionOwnsPackagedRelativePath(contribution, relativePath)
  })
}

export function mapMetadataSourcePath(uri: string): MetadataPathMapping | undefined {
  const normalizedPath = normalizePath(uri)
  const sourceRootIndex = normalizedPath.lastIndexOf(SOURCE_ROOT)
  const sourceRelativePath = sourceRootIndex >= 0 ? normalizedPath.slice(sourceRootIndex + SOURCE_ROOT.length) : undefined
  const contribution = sourceRelativePath
    ? getContributionByMetadataRelativePath(sourceRelativePath)
    : getContributionByStandaloneFixedFileName(normalizedPath) ?? getContributionBySuffix(normalizedPath)
  if (!contribution) {
    return undefined
  }

  const relativePath = sourceRelativePath ?? normalizedPath.slice(normalizedPath.lastIndexOf('/') + 1)
  const diagnostics: MetadataDiagnostic[] = []

  return {
    type: contribution.type,
    sourcePath: normalizedPath,
    packagedPath: `META-INF/ouroboros/metadata/${relativePath}`,
    contribution,
    diagnostics,
  }
}

export function mapMetadataPackagedResourcePath(uri: string): MetadataPathMapping | undefined {
  const normalizedPath = normalizePath(uri)
  const relativePath = getPackagedResourceRelativePath(normalizedPath)
  if (!relativePath || !relativePath.endsWith('.json')) {
    return undefined
  }

  const contribution = getContributionByPackagedPath(normalizedPath)
  if (!contribution) {
    return undefined
  }

  const suffixContribution = getRawContributionBySuffix(normalizedPath)
  const diagnostics: MetadataDiagnostic[] = []
  if (suffixContribution && suffixContribution.type !== contribution.type) {
    diagnostics.push({
      code: 'metadata-path-suffix-conflict',
      message: `Packaged path ${contribution.packagedPathPrefix} resolves this file as ${contribution.type}, but suffix ${suffixContribution.fileSuffix} resolves it as ${suffixContribution.type}.`,
      severity: 'error',
      path: normalizedPath,
    })
  }

  return {
    type: contribution.type,
    sourcePath: normalizedPath,
    packagedPath: `META-INF/ouroboros/${relativePath}`,
    contribution,
    diagnostics,
  }
}

export function normalizePath(path: string): string {
  return path.replace(/\\/g, '/')
}

function getPackagedResourceRelativePath(path: string): string | undefined {
  const normalizedPath = normalizePath(path)
  const resourceRootIndex = normalizedPath.lastIndexOf(PACKAGED_RESOURCE_ROOT)
  if (resourceRootIndex < 0) {
    return undefined
  }
  return normalizedPath.slice(resourceRootIndex + PACKAGED_RESOURCE_ROOT.length)
}

function getContributionByMetadataRelativePath(relativePath: string): MetadataTypeContribution | undefined {
  const normalizedPath = normalizePath(relativePath)
  const fixedType = FIXED_METADATA_FILE_TYPES[normalizedPath]
  if (fixedType) {
    return getContributionByType(fixedType)
  }
  const standaloneFixedType = STANDALONE_FIXED_METADATA_FILE_TYPES[normalizedPath.slice(normalizedPath.lastIndexOf('/') + 1)]
  if (standaloneFixedType) {
    return getContributionByType(standaloneFixedType)
  }
  const contribution = getContributionBySuffix(normalizedPath)
  return contribution && !FIXED_METADATA_TYPES.has(contribution.type) ? contribution : undefined
}

function getContributionByStandaloneFixedFileName(path: string): MetadataTypeContribution | undefined {
  const fileName = normalizePath(path).slice(normalizePath(path).lastIndexOf('/') + 1)
  const fixedType = STANDALONE_FIXED_METADATA_FILE_TYPES[fileName]
  return fixedType ? getContributionByType(fixedType) : undefined
}

function getRawContributionBySuffix(uri: string): MetadataTypeContribution | undefined {
  return metadataTypeContributions.find((contribution) => contribution.fileSuffix && uri.endsWith(contribution.fileSuffix))
}

function contributionOwnsPackagedRelativePath(contribution: MetadataTypeContribution, relativePath: string): boolean {
  const runtimeDirectory = contribution.packagedPathPrefix.replace('META-INF/ouroboros/', '')
  return contribution.packagedPathPatterns?.includes(relativePath) === true
    || relativePath.startsWith(`${runtimeDirectory}/`)
}

function normalizeObject<TDocument>(document: TDocument): TDocument {
  if (Array.isArray(document)) {
    return document.map((item) => normalizeObject(item)) as TDocument
  }
  if (!document || typeof document !== 'object') {
    return document
  }
  return Object.fromEntries(
    Object.entries(document).map(([key, value]) => [key, normalizeObject(value)]),
  ) as TDocument
}

function cloneObject(value: Record<string, unknown>): Record<string, unknown> {
  return cloneValue(value) as Record<string, unknown>
}

function cloneValue<TValue>(value: TValue): TValue {
  return JSON.parse(JSON.stringify(value)) as TValue
}
