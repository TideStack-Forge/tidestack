import { t } from '../i18n.js'
import { withEditorProps } from '@/property-transpiler'
// 节点固定属性
const nodeDefaultProperties = (editorProps) =>
  withEditorProps(editorProps).transpileAll([
    {
      type: 'fieldSet',
      title: '基本信息',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'select',
          name: 'type',
          label: '类型',
          source: '${__NODE_TYPES}',
          static: true,
        },
        {
          type: 'input-text',
          name: 'name',
          label: '名称',
          required: true,
          validateOnChange: true,
          validations: {
            isVariableName:
              /^[^\s!@#$%^&*()=|{}':;',[\].<>《》?~！@#￥……&*（）——|{}【】‘；：”“'。，、？ ]+$/,
          },
          validate(data, value) {
            const validator = data.__validate_unique_name
            if (!validator) {
              console?.error?.('未找到节点名唯一性校验器')
            }
            return validator(value) ? true : t('节点名已存在')
          },
          validationErrors: {
            isVariableName: '节点名不能包含空格和特殊字符',
          },
        },
        {
          type: 'textarea',
          name: 'description',
          label: '描述',
        },
        {
          type: 'switch',
          name: 'isAsync',
          label: '异步',
        },
        {
          type: 'switch',
          name: 'isEnabled',
          label: '启用',
        },
      ],
    },
  ])
// 连线固定属性
const edgeDefaultProperties = (editorProps) =>
  withEditorProps(editorProps).transpileAll([
    {
      type: 'fieldSet',
      title: '基本信息',
      className: 'mb-4',
      body: [
        {
          type: 'input-text',
          name: 'label',
          label: '标签',
        },
        {
          type: 'textarea',
          name: 'description',
          label: '描述',
        },
      ],
    },
    {
      type: 'fieldSet',
      title: '源',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'input-text',
          name: 'sourceNode',
          label: '节点',
          static: true,
        },
        {
          type: 'input-text',
          name: 'sourceOutputPort',
          label: '端口',
          static: true,
        },
      ],
    },
    {
      type: 'fieldSet',
      title: '目标',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'input-text',
          name: 'targetNode',
          label: '节点',
          static: true,
        },
        {
          type: 'input-text',
          name: 'targetInputPort',
          label: '端口',
          static: true,
        },
      ],
    },
  ])
// 逻辑流表单
const flowDefaultProperties = (editorProps) =>
  withEditorProps(editorProps).transpileAll([
    {
      type: 'fieldSet',
      title: '基本信息',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'input-text',
          name: 'name',
          label: '名称',
          static: true,
        },
        {
          type: 'textarea',
          name: 'description',
          label: '描述',
          minRows: 3,
          maxRows: 3,
        },
        {
          type: 'switch',
          name: 'isEnabled',
          disabled: !!editorProps?.logicFlowHost?.lockContract,
          label: '启用',
        },
      ],
    },
    {
      type: 'fieldSet',
      title: '输入',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'data-type',
          name: 'inputDataType',
          disabled: !!editorProps?.logicFlowHost?.lockContract,
          label: '数据类型',
        },
        { type: 'json-data-editor', name: 'inputSchema', readonly: !!editorProps?.logicFlowHost?.lockContract, label: '输入结构（JSON Schema）' },
        {
          type: 'entry-node',
          name: 'entryNode',
          label: '入口节点',
          required: true,
        },
      ],
    },
    {
      type: 'fieldSet',
      title: '输出',
      collapsable: true,
      collapsed: false,
      className: 'mb-4',
      body: [
        {
          type: 'data-type',
          name: 'outputDataType',
          disabled: !!editorProps?.logicFlowHost?.lockContract,
          label: '数据类型',
        },
        { type: 'json-data-editor', name: 'outputSchema', readonly: !!editorProps?.logicFlowHost?.lockContract, label: '输出结构（JSON Schema）' },
        {
          type: 'exit-node',
          name: 'exitNode',
          label: '出口节点',
          required: false,
        },
        {
          type: 'error-entry-node',
          name: 'errorEntryNode',
          label: '异常入口节点',
          required: false,
        },
      ],
    },
  ])
export { nodeDefaultProperties, edgeDefaultProperties, flowDefaultProperties }
