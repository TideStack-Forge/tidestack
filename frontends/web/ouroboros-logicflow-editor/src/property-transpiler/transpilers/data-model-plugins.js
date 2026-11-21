import { registerTranspiler, transpileAll } from '../transpiler'

const plugins = [
  {
    name: 'AccessControl',
    label: '访问控制',
    params: [
      {
        name: 'createAuthority',
        label: '创建权限',
        type: 'authority-select',
        placeholder: '留空则不控制',
      },
      {
        name: 'readAuthority',
        label: '读取权限',
        type: 'authority-select',
        placeholder: '留空则不控制',
      },
      {
        name: 'updateAuthority',
        label: '更新权限',
        type: 'authority-select',
        placeholder: '留空则不控制',
      },
      {
        name: 'deleteAuthority',
        label: '删除权限',
        type: 'authority-select',
        placeholder: '留空则不控制',
      },
    ],
  },
  {
    name: 'SoftDelete',
    label: '软删除',
    params: [
      {
        name: 'isDeletedField',
        label: '删除标志位',
        type: 'input-text',
        placeholder: 'isDeleted',
      },
      {
        name: 'deletedAtField',
        label: '删除时间字段',
        type: 'input-text',
        placeholder: '留空则不记录更新时间',
      },
      {
        name: 'deletedByField',
        label: '删除人字段',
        type: 'input-text',
        placeholder: '留空则不记录删除人',
      },
    ],
  },
  {
    name: 'BasicAudit',
    label: '审计字段',
    params: [
      {
        name: 'createdAtField',
        label: '创建时间字段',
        type: 'input-text',
        placeholder: 'createdAt',
      },
      {
        name: 'createdByField',
        label: '创建人字段',
        type: 'input-text',
        placeholder: 'createdBy',
      },
      {
        name: 'updatedAtField',
        label: '更新时间字段',
        type: 'input-text',
        placeholder: 'updatedAt',
      },
      {
        name: 'updatedByField',
        label: '更新人字段',
        type: 'input-text',
        placeholder: 'updatedBy',
      },
    ],
  },
  {
    name: 'DuplicateDataChecker',
    label: '重复数据',
    params: [
      {
        name: 'notifyDuplicateFields',
        label: '提示重复字段',
        type: 'switch',
        value: true,
      },
    ],
  },
  {
    name: 'UpdateWhenDataChange',
    label: '数据变化则更新',
    params: [
      {
        type: 'alert',
        title: '无可配置参数',
      },
    ],
  },
  {
    name: 'EmployeeSetter',
    label: '追加制单人',
    params: [
      {
        type: 'alert',
        title: '无可配置参数',
      },
    ],
  },
  {
    name: 'DepartmentSetter',
    label: '追加部门',
    params: [
      {
        type: 'alert',
        title: '无可配置参数',
      },
    ],
  },
  {
    name: 'CompanySetter',
    label: '追加公司',
    params: [
      {
        type: 'alert',
        title: '无可配置参数',
      },
    ],
  },
  {
    name: 'NormalizeWhere',
    label: '规范化查询条件',
    params: [
      {
        type: 'switch',
        name: 'removeEmptyValue',
        label: '清除空字符串条件',
        value: true,
      },
      {
        type: 'switch',
        name: 'removeNullValue',
        label: '清除null值条件',
        value: false,
      },
    ],
  },
  {
    name: 'ReferenceChecker',
    label: '删除前检查引用数据',
    params: [
      {
        type: 'alert',
        title: '暂无可配置参数',
      },
    ],
  },
]

function transpiler(property) {
  return {
    ...property,
    type: 'combo',
    scaffold: {
      name: '',
      config: {},
    },
    validate(_data, value) {
      if (!value || !value.length) return true
      // 检查是否有重复的插件
      const names = value.map((v) => v.name)
      const set = new Set(names)
      if (set.size !== names.length) {
        return '插件重复'
      }
    },
    items: [
      {
        type: 'select',
        name: 'name',
        labelField: 'label',
        valueField: 'name',
        searchable: true,
        options: plugins,
        required: true,
        onEvent: {
          change: {
            actions: [
              {
                actionType: 'setValue',
                componentName: 'config',
                args: {
                  value: {},
                },
              },
            ],
          },
        },
      },
      {
        type: 'hidden',
        name: 'config',
      },
      {
        type: 'action',
        label: '参数',
        icon: 'fa fa-cog',
        colSize: 'max-content',
        columnClassName: 'flex-grow-0 min-w-max',
        actionType: 'dialog',
        disabledOn: '${!name}',
        dialog: {
          title: '插件参数',
          body: {
            type: 'form',
            body: plugins.map((plugin) => ({
              type: 'combo',
              name: 'config',
              noBorder: true,
              label: false,
              multiLine: true,
              staticOn: '${__IS_READONLY}',
              items: transpileAll(plugin.params),
              visibleOn: '${name === "' + plugin.name + '"}',
            })),
          },
          actions: [
            {
              type: 'submit',
              level: 'primary',
              label: '确定',
              mergeData: true,
            },
          ],
          data: {
            name: '$name',
            config: '$config',
            __IS_READONLY: '${__IS_READONLY}',
          },
        },
      },
    ],
    multiple: true,
  }
}

registerTranspiler('data-model-plugins', transpiler)
