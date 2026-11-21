# Field Template

字段定义文件继续使用扁平结构，字段名保持稳定，不再使用 `paramsJsonSchema`。

## Canonical Shape

```json
{
  "name": "单行文本",
  "label": "单行文本",
  "description": "字段说明",
  "order": 10,
  "isPrimaryKeyType": false,
  "dataType": {
    "type": "String",
    "size": "${size}"
  },
  "ui": {
    "input": {
      "type": "input-text",
      "maxLength": "${size}"
    },
    "display": {
      "type": "static-text"
    }
  },
  "validations": {
    "matchRegexp": "${regexp}"
  },
  "validationErrors": {
    "matchRegexp": "${regexpMsg}"
  },
  "paramsUiSchema": [
    {
      "type": "input-number",
      "name": "size",
      "label": "长度",
      "value": 100
    }
  ],
  "primaryKeyGenerator": "coding:${template}"
}
```

## Field Semantics

- `name`: 字段类型唯一标识。
- `label`: 字段类型显示名称。
- `description`: 可选说明。
- `order`: 列表展示顺序。
- `isPrimaryKeyType`: 是否为主键字段类型。
- `dataType`: 数据字段模板，编译为 `DataModelFieldMeta`。
- `ui.input`: 输入态表单项模板。
- `ui.display`: 展示态表单项模板。
- `validations`: UI 校验模板。
- `validationErrors`: UI 校验提示模板。
- `paramsUiSchema`: 开发态参数编辑定义。
- `primaryKeyGenerator`: 主键生成器模板。

## Defaults

- 默认参数只从 `paramsUiSchema[].value` 提取。
- 默认值提取发生在元数据读取阶段。
- 编译阶段只做 `defaultParams + 调用参数` 合并，不推断额外默认值。

## Compile Rules

- 模板表达式统一由 `FieldTypeCompiler` 求值。
- `ui.display` 未配置时，由编译器基于 `ui.input` 生成静态展示态配置。
- 调用方应通过 `FieldTypeCatalog` 获取定义或编译结果，不再直接依赖历史模板对象行为。
