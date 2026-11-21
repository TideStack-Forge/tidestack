import { registerTranspiler, transpileAll } from '../transpiler'

function transpiler(property, editorProps) {
  const body = property.body ?? []
  const finalBodyArray = Array.isArray(body) ? body : [body]
  return {
    ...property,
    body: transpileAll(finalBodyArray, editorProps),
  }
}

registerTranspiler('fieldSet', transpiler)
