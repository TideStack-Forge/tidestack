import type { MetadataDocumentShape, MetadataEditorKind, MetadataFileTemplate } from './types'

export interface MetadataTypeContributionDefinition {
  type: string
  fileSuffix?: `.${string}.json`
  fixedFileName?: string
  legacyFixedFileNames?: string[]
  displayName: string
  sourcePathPatterns: string[]
  packagedPathPrefix: `META-INF/ouroboros/${string}`
  packagedPathPatterns?: string[]
  editorKind?: MetadataEditorKind
  editorKinds: MetadataEditorKind[]
  documentShape?: MetadataDocumentShape
  legacyJsonSchema?: Record<string, unknown>
  jsonSchema: Record<string, unknown>
  editorSchema: Record<string, unknown>
  fileTemplate?: MetadataFileTemplate
}
