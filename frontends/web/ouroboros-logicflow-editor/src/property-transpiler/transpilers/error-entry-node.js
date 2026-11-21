import { registerTranspiler } from '../transpiler'

function transpiler(property) {
  return {
    ...property,
    type: 'select',
    source: '${__ERROR_ENTRY_NODES}',
    searchable: true,
  }
}

registerTranspiler('error-entry-node', transpiler)
