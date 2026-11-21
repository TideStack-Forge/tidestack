import { registerTranspiler } from '../transpiler.js'

function transpiler(property) {
  const valueField = property.valueField ?? (property.source ? 'value' : 'name')
  const labelField = property.labelField ?? (property.source ? 'label' : 'title')
  return {
    ...property,
    type: 'picker',
    source: property.source ?? '/api/dev/${appName}/metadata/rpc-model?mode=raw',
    valueField,
    labelField,
    modalTitle: '选择 RPC 模型',
    modalSize: 'xl',
    pickerSchema: {
      mode: 'table',
      className: 'rpc-model-browser',
      bodyClassName: 'rpc-model-browser__list',
      placeholder: '暂无匹配的 RPC 模型',
      loadDataOnce: true,
      loadDataOnceFetchOnFilter: false,
      filter: {
        title: '',
        mode: 'normal',
        className: 'rpc-model-browser__aside',
        wrapWithPanel: false,
        canAccessSuperData: true,
        submitOnChange: true,
        actions: [],
        body: [
          {
            type: 'input-text',
            name: '__rpcKeyword',
            label: '搜索模型',
            placeholder: '名称或标题',
            clearable: true,
            size: 'full',
          },
          {
            type: 'input-tree',
            name: '__rpcGroup',
            label: '分组',
            value: 'all',
            source: '${itemsRaw | rpcModelGroups}',
            multiple: false,
            onlyLeaf: false,
            showRadio: false,
            showIcon: false,
            showOutline: true,
            initiallyOpen: true,
            size: 'full',
          },
        ],
      },
      matchFunc: `
        const selected = options.query.__rpcGroup || 'all';
        const keyword = String(options.query.__rpcKeyword || '').trim().toLocaleLowerCase();
        return items.filter(item => {
          const group = item.group || '';
          const path = selected.slice(6);
          const inGroup = selected === 'all' || (path === '' ? !group : group === path || group.startsWith(path + '/'));
          return inGroup && [item[${JSON.stringify(valueField)}], item[${JSON.stringify(labelField)}]]
            .some(value => value != null && String(value).toLocaleLowerCase().includes(keyword));
        });
      `,
      columns: [
        { name: labelField, label: '标题', type: 'text' },
        { name: valueField, label: '名称', type: 'text' },
        { name: 'group', label: '分组', type: 'text', placeholder: '未分组' },
      ],
      ...property.pickerSchema,
    },
  }
}

registerTranspiler('rpc-model', transpiler)
