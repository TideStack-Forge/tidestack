<#import "../../apis/lib" as lib>
<#noparse>
{
  "type": "panel",
  "title": "数据导入",
  "mode": "horizontal",
  "visibleOn": "${ features.isEnabledImport }",
  "body": [
    {
      "type": "button-group-select",
      "name": "features.import.dataMapMode",
      "label": "数据映射模式",
      "value": "auto",
      "options": [
        {
          "label": "自动",
          "value": "auto"
        },
        {
          "label": "自定义",
          "value": "custom"
        }
      ],
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentId": "importDataMapping",
              "expression": "${value === 'custom'}",
              "args": {
                "value": "${ ${ ARRAYMAP(CONCAT(ARRAYFILTER(fields, f => !f.isPrimaryKey), ARRAYFILTER(relations, r => r.type !== 'child')), f => ({ name: f.name, expr: '$item.get(\\'' + f.title + '\\')' })) } | arrayToObject:name:expr }"
              }
            },
            {
              "actionType": "setValue",
              "componentId": "importDataMapping",
              "expression": "${value === 'auto'}",
              "args": {
                "value": null
              }
            }
          ]
        }
      }
    },
    {
      "type": "alert",
      "title": "自动数据映射",
      "body": "根据字段名、关系名和显示名建立数据映射，即把Excel中的中文列名映射为数据模型的字段名或关系名。",
      "level": "info",
      "showIcon": true,
      "className": "mb-5",
      "visibleOn": "${ features.import.dataMapMode === 'auto' }"
    },
    {
      "type": "hidden",
      "name": "features.import.dataMap",
      "id": "importDataMapping"
    },
    {
      "type": "input-kv",
      "label": "映射字段",
      "name": "features.import.dataMap",
      "keyPlaceholder": "字段名",
      "id": "importDataMappingInputKV",
      "wrapperCustomStyle": {
        ".cxd-TextControl-inputSuffix,.cxd-TextControl-inputPrefix": {
          "color": "var(--primary)"
        },
        ".cxd-Form-col:first-child": {
          "flex-basis": "200px",
          "flex-grow": "0",
          "flex-shrink": "0"
        }
      },
      "keySchema": {
        "type": "select",
        "required": true,
        "searchable": true,
        "menuTpl": "${value} (<span class='text-gray-400'>${title}</span>)",
        "source": "${ ARRAYMAP(CONCAT(ARRAYFILTER(fields, f => !f.isPrimaryKey), ARRAYFILTER(relations, r => r.type !== 'child')), f => ({label: f.name, title: f.title, value: f.name})) }"
      },
      "valueSchema": {
        "type": "script-editor", "language": "spel",
        "codeSite": "business-model.import-mapping", "codeResourceType": "business-model",
        "codePointer": "/features/import/dataMap/**",
        "intelligenceApi": "/api/dev/${appName}/code-intelligence/context",
        "prefix": "\\${",
        "suffix": "}",
        "required": true,
        "placeholder": "映射表达式（SpEL），\\$item 为当前行数据，可以用 \\$item.get('字段名') 或 \\$item['字段名'] 取值"
      },
      "visibleOn": "${ features.import.dataMapMode === 'custom' }"
    },</#noparse>
    <@lib.showLogicFlowButton logicFlowName="import" logicFlowTitle="导入"/>
  ]
}