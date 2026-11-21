import { registerTranspiler } from '../transpiler'
import schema from './json-schema.json'

function transpiler(property) {
  const description =
    '基于 <a href="https://json-schema.org/" target="_blank">JsonSchema</a>'
  return {
    type: 'container',
    body: [
      {
        type: 'form',
        wrapWithPanel: false,
        className: 'mb-2',
        body: [
          {
            type: 'button-group-select',
            name: 'select-edit-mode',
            value: 'json-schema',
            label: 'JsonSchema 编辑模式',
            options: [
              {
                label: '编辑器',
                value: 'json-schema',
              },
              {
                label: '源代码',
                value: 'editor',
              },
            ],
            onEvent: {
              change: {
                actions: [
                  {
                    actionType: 'parallel',
                    children: [
                      {
                        actionType: 'show',
                        componentId: 'jsonSchemaEditor',
                      },
                      {
                        actionType: 'hidden',
                        componentId: 'jsonEditor',
                      },
                    ],
                    expression: "value === 'json-schema'",
                  },
                  {
                    actionType: 'parallel',
                    children: [
                      {
                        actionType: 'hidden',
                        componentId: 'jsonSchemaEditor',
                      },
                      {
                        actionType: 'show',
                        componentId: 'jsonEditor',
                      },
                    ],
                    expression: "value === 'editor'",
                  },
                ],
              },
            },
          },
        ],
      },
      {
        ...property,
        type: 'editor',
        language: 'json',
        name: 'schema',
        visible: false,
        id: 'jsonEditor',
        label: false,
        editorDidMount: (editor, monaco) => {
          const newModelUri = monaco.Uri.parse(
            `ouroboros://${Date.now()}.jsonschema.json`
          ).toString()

          // 添加 json schema，并把当前编辑器新 model 的 uri 添加到 fileMatch 中
          const oldSchemas = [
            ...monaco.languages.json?.jsonDefaults.diagnosticsOptions.schemas,
          ]
          const defaultSchema = {
            uri: monaco.Uri.parse('http://json-schema.org/draft-07/schema#'),
            fileMatch: [],
            schema,
          }
          const foundSchema =
            oldSchemas.find(
              (item) => item.uri.toString() === defaultSchema.uri.toString()
            ) || defaultSchema
          foundSchema.fileMatch.push(newModelUri)

          const schemas = [
            ...oldSchemas.filter((item) => item !== foundSchema),
            foundSchema,
          ]
          monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
            schemas,
            validate: true,
            enableSchemaRequest: true,
            allowComments: false,
          })

          // 重新创建当前编辑器的 model
          const oldModel = editor.getModel()
          const newModel = monaco.editor.createModel(
            oldModel.getValue(),
            'json',
            monaco.Uri.parse(newModelUri)
          )
          editor.setModel(newModel)
        },
        validate(_data, value) {
          if (typeof value === 'string') return 'Schema 格式错误'
        },
        pipeOut: (value) => {
          if (typeof value === 'string') {
            try {
              return JSON.parse(value)
            } catch {
              return value
            }
          }
          return value
        },
        description,
      },
      {
        ...property,
        type: 'json-schema-editor',
        rootTypeMutable: true,
        showRootInfo: true,
        value: {},
        id: 'jsonSchemaEditor',
        label: false,
        description,
      },
    ],
  }
}

registerTranspiler('schema', transpiler)
