import { registerTranspiler } from '../transpiler'

function transpiler(property) {
  return {
    ...property,
    type: 'list-select',
    options: [
      {
        label: 'Javascript',
        value: 'javascript',
      },
      {
        label: 'Groovy',
        value: 'groovy',
      },
    ],
  }
}

registerTranspiler('script-language', transpiler)
