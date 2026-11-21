import { intelligenceApi } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'script-editor',
    language: 'spel',
    codeSite: property.codeSite,
    codePointer: property.codePointer,
    intelligenceApi: intelligenceApi(property, editorProps),
    size: 'sm',
    options: { lineNumbers: 'off', minimap: { enabled: false } },
  }
}

registerTranspiler('expression', transpiler)
