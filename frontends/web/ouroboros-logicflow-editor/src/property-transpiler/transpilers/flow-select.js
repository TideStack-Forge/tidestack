import { referenceSource } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'
function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'select',
    source: referenceSource(property, editorProps, 'flow', '/api/dev/${appName}/metadata/logic-flow'),
  }
}

registerTranspiler('flow-select', transpiler)
