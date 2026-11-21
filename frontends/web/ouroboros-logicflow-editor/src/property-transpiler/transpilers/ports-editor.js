import { registerTranspiler, transpile } from '../transpiler'

function transpiler(property) {
  const {
    title,
    name,
    ruleType, // 端口规则
    min, // 最少端口数
    max, // 最多端口数
  } = property && property.props
  return {
    type: 'fieldSet',
    title:
      title +
      (ruleType === 'limit' ? `（${min || 0} - ${max == 0 ? 0 : max || '无限'}）` : ''),
    collapsable: true,
    collapsed: false,
    className: 'mb-4',
    body: [
      {
        type: 'combo',
        name: name,
        label: false,
        multiLine: true,
        multiple: true,
        minLength: min,
        maxLength: max,
        subFormMode: 'horizontal',
        addable: max != 0,
        subFormHorizontal: {
          leftFixed: '18px',
        },
        items: [
          {
            type: 'group',
            label: '$index',
            body: [
              {
                type: 'input-text',
                name: 'name',
                label: false,
                placeholder: '端口名',
                value: '',
                className: 'mb-0',
                columnRatio: 3,
              },
              transpile({
                type: 'data-type',
                name: 'dataType',
                label: false,
                value: 'any',
                className: 'mb-0',
                columnRatio: 3,
              }),
              {
                type: 'input-text',
                name: 'description',
                label: false,
                placeholder: '描述',
                value: '',
                className: 'mb-0',
                columnRatio: 6,
              },
            ],
          },
        ],
        disabled: ruleType !== 'limit',
      },
    ],
  }
}

registerTranspiler('ports-editor', transpiler)
