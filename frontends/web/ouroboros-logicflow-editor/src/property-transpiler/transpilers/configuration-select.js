import { referenceSource } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'select',
    source: referenceSource(property, editorProps, 'configuration', '/api/dev/${appName}/metadata/configuration'),
  }
}

registerTranspiler('configuration-select', transpiler)
