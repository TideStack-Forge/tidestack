import { registerTranspiler, transpileAll } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    items: transpileAll(property.items ?? [], editorProps),
  }
}

registerTranspiler('combo', transpiler)
