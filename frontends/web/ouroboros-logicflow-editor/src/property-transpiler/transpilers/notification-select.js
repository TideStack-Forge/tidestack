import { referenceSource } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'select',
    source: referenceSource(property, editorProps, 'notification-template', '/api/dev/${appName}/metadata/notification-template'),
  }
}

registerTranspiler('notification-select', transpiler)
