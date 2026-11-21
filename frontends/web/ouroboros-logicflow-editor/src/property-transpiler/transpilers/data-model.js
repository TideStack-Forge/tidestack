import { referenceSource } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'select',
    source: referenceSource(property, editorProps, 'data-model', '/api/dev/${appName}/metadata/data-model'),
    menuTpl: {
      type: 'tpl',
      tpl: '${label || value} <span class="text-secondary">(${value})</span>',
    },
    searchable: true,
  }
}

registerTranspiler('data-model', transpiler)
