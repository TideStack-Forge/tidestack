import { referenceSource } from '../editorHost.mjs'
import { registerTranspiler } from '../transpiler'

function transpiler(property, editorProps) {
  return {
    ...property,
    type: 'select',
    source: referenceSource(property, editorProps, 'mq-binding', {
      method: 'get',
      url: `/api/dev/\${appName}/mq-binding?bindingType=${property.bindingType}`,
      responseData: {
        items: {
          $items: {
            label: '$model.bindingName',
            value: '$model.bindingName',
            destination: '$model.destination',
            description: '$model.description',
          },
        },
      },
    }),
    menuTpl: {
      type: 'tooltip-wrapper',
      title: '绑定至: ${destination}',
      content: '${description}',
      body: '${value}',
      className: 'mb-1',
    },
    searchable: true,
  }
}

registerTranspiler('mq-binding-select', transpiler)
