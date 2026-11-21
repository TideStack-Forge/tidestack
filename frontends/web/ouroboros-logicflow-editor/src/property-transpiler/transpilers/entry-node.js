import { registerTranspiler } from '../transpiler'
function transpiler(property) {
  return {
    ...property,
    type: 'select',
    source: '${__ENTRY_NODES}',
    searchable: true,
  }
}

registerTranspiler('entry-node', transpiler)
