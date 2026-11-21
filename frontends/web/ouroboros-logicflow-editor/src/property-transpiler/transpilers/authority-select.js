import { registerTranspiler } from '../transpiler'

function transpiler(property) {
  return {
    ...property,
    type: 'input-text',
  }
}

registerTranspiler('authority-select', transpiler)
