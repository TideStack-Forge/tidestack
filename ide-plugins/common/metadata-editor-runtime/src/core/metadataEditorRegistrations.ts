import manifest from '../schema-resources/metadataEditorRegistrations.json'

export type MetadataEditorPriority = 'default' | 'option'

export interface MetadataEditorRegistrationDefinition {
  editorId: string
  editorName: string
  editorNameKey: string
  metadataType: string
  schemaName: string
  viewType: string
  intellijEditorTypeId: string
  fileMatchPatterns: string[]
  priority: MetadataEditorPriority
}

export const metadataEditorRegistrationManifest = manifest as { editors: MetadataEditorRegistrationDefinition[] }
export const metadataEditorRegistrationDefinitions = metadataEditorRegistrationManifest.editors
