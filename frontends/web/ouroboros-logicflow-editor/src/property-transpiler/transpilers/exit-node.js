import { registerTranspiler } from '../transpiler'
function transpiler(property) {
  return {
    ...property,
    type: 'select',
    source: '${__EXIT_NODES}',
    searchable: true,
  }
}

registerTranspiler('exit-node', transpiler)
