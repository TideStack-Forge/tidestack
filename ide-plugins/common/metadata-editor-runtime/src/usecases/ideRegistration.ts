import { metadataTypeContributions } from '../core/contributions'
import { metadataEditorRegistrationDefinitions } from '../core/metadataEditorRegistrations'
import type { MetadataEditorRegistrationDefinition } from '../core/metadataEditorRegistrations'

const metadataTypeNameSegments: Record<string, string[]> = {
  authority: ['Authority'],
  menu: ['Menu'],
  'dev-menu': ['Dev', 'Menu'],
  'ui-model': ['Ui', 'Model'],
  'ui-schema': ['Ui', 'Schema'],
  business: ['Business'],
  logicflow: ['Logicflow'],
  'approval-workflow': ['Approval', 'Workflow'],
  'field-type': ['Field', 'Type'],
  'flow-node': ['Flow', 'Node'],
  widget: ['Widget'],
  'micro-web': ['Micro', 'Web'],
  rpc: ['Rpc'],
  'rpc-auth': ['Rpc', 'Auth'],
  webapi: ['Webapi'],
  notification: ['Notification'],
  'business-log': ['Business', 'Log'],
  'data-init': ['Data', 'Init'],
  mq: ['Mq'],
  schedule: ['Schedule'],
  i18n: ['I18n'],
  'data-dict': ['Data', 'Dict'],
  position: ['Position'],
  'error-code': ['Error', 'Code'],
  'app-metadata': ['App', 'Metadata'],
  'modules-diff': ['Modules', 'Diff'],
  'app-module': ['App', 'Module'],
  configuration: ['Configuration'],
  'configuration-group': ['Configuration', 'Group'],
  'configuration-item': ['Configuration', 'Item'],
  'configuration-group-item': ['Configuration', 'Group', 'Item'],
}

export interface NewMetadataFileCommandDefinition {
  command: string
  metadataType: string
}

export { metadataEditorRegistrationDefinitions }
export type { MetadataEditorRegistrationDefinition }

export const newMetadataFileCommandDefinitions: NewMetadataFileCommandDefinition[] = metadataTypeContributions
  .filter((contribution) => contribution.fileTemplate)
  .map((contribution) => ({
    command: newMetadataFileCommandId(contribution.type),
    metadataType: contribution.type,
  }))

export function metadataEditorViewType(metadataType: string): string {
  return `tidestack.metadataEditor.${lowerCamel(metadataTypeNameSegments[metadataType] ?? fallbackSegments(metadataType))}`
}

export function newMetadataFileCommandId(metadataType: string): string {
  return `tidestack.metadata.new${upperCamel(metadataTypeNameSegments[metadataType] ?? fallbackSegments(metadataType))}File`
}

function fallbackSegments(value: string): string[] {
  return value.split(/[-_\s]+/).filter(Boolean).map((segment) => `${segment.charAt(0).toUpperCase()}${segment.slice(1)}`)
}

function upperCamel(segments: string[]): string {
  return segments.join('')
}

function lowerCamel(segments: string[]): string {
  const joined = upperCamel(segments)
  return joined ? `${joined.charAt(0).toLowerCase()}${joined.slice(1)}` : joined
}
