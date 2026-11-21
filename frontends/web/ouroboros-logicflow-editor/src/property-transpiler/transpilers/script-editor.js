import { intelligenceApi } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'script-editor',
    codeSite: property.codeSite,
    codePointer: property.codePointer,
    intelligenceApi: intelligenceApi(property, editorProps),
  }
}

registerTranspiler('script-editor', transpiler)
