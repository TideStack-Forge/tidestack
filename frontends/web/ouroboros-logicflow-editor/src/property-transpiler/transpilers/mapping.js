import { intelligenceApi } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  const { codeSite, codePointer, ...field } = property
  return {
    ...field,
    type: 'json-data-editor',
    allowValueTypes: 'object',
    codeContext: {
      language: 'spel',
      codeSite,
      codePointer,
      intelligenceApi: intelligenceApi(property, editorProps),
    },
  }
}

registerTranspiler('mapping', transpiler)
