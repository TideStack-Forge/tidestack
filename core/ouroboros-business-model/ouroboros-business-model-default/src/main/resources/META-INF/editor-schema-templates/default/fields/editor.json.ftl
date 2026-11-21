<#noparse>
{
  "type": "hidden",
  "name": "fields"
},
{
  "type": "combo",
  "name": "fields",
  "label": false,
  "className": "pr-2",
  "scaffold": {
    "name": "",
    "title": "",
    "description": "",
    "isPrimaryKey": false
  },
  "items": [
    {
      "type": "input-text",
      "name": "name",
      "label": "字段名",
      "columnClassName": "flex-1",
      "required": true,
      "unique": true,
      "validate": "return data.relations && data.relations.some(item => item.name == value) ? '字段名与关系名重复' : ''",
      "validations": {
        "isVariableName": true
      },
      "disabledOn": "isPrimaryKey"
    },
    {
      "type": "input-text",
      "name": "title",
      "label": "显示名",
      "columnClassName": "flex-1",
      "required": true
    },
    {
      "type": "select",
      "name": "type",
      "label": "类型",
      "columnClassName": "flex-1",
      "required": true,
      "source": {
        "method": "get",
        "url": "/api/app/${appName}/field-types/${isPrimaryKey ? 'primary' : 'normal'}",
        "cache": 1000
      },
      "searchable": true,
      "popOverContainerSelector": ".ouroboros-pop-over-container"
    },
    {
      "type": "checkbox",
      "name": "isPrimaryKey",
      "label": "主键",
      "colSize": "max-content",
      "className": "w-16",
      "inputClassName": "pt-0",
      "columnClassName": "flex-grow-0 min-w-max",
      "disabledOn": "${ AND(ARRAYFINDINDEX(fields, item => !!item.isPrimaryKey) !== -1, !isPrimaryKey) }",
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentName": "isRequired",
              "expression": "isPrimaryKey",
              "args": {
                "value": true
              }
            },
            {
              "actionType": "setValue",
              "componentName": "isUnique",
              "expression": "isPrimaryKey",
              "args": {
                "value": true
              }
            },
            {
              "actionType": "setValue",
              "componentName": "name",
              "expression": "isPrimaryKey",
              "args": {
                "value": "id"
              }
            },
            {
              "actionType": "reload",
              "componentName": "type"
            }
          ]
        }
      }
    },
    {
      "type": "checkbox",
      "name": "isRequired",
      "label": "必填",
      "disabledOn": "${isPrimaryKey || isUnique}",
      "colSize": "max-content",
      "className": "w-16",
      "inputClassName": "pt-0",
      "columnClassName": "flex-grow-0 min-w-max"
    },
    {
      "type": "checkbox",
      "name": "isUnique",
      "label": "值唯一",
      "colSize": "max-content",
      "className": "w-20",
      "inputClassName": "pt-0",
      "columnClassName": "flex-grow-0 min-w-max",
      "disabledOn": "${isPrimaryKey}",
      "value": false,
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentName": "isRequired",
              "args": {
                "value": true
              },
              "expression": "value"
            }
          ]
        }
      }
    },
    {
      "type": "button",
      "actionType": "drawer",
      "label": "",
      "icon": "fa fa-gear",
      "colSize": "max-content",
      "columnClassName": "flex-grow-0 min-w-max",
      "drawer": {
        "type": "drawer",
        "title": "字段属性",
        "body": {
          "type": "form",
          "body": [
            {
              "type": "input-text",
              "name": "name",
              "label": "字段名",
              "validate": "return data.relations && data.relations.some(item => item.name == value) ? '字段名与关系名重复' : ''",
              "validations": {
                "isVariableName": true
              },
              "required": true,
              "disabledOn": "isPrimaryKey"
            },
            {
              "type": "input-text",
              "name": "title",
              "label": "显示名",
              "required": true
            },
            {
              "type": "input-text",
              "name": "rawFieldName",
              "label": "原始字段名",
              "validations": {
                "isVariableName": true
              },
              "description": "如果字段名和数据库字段名不一致，可以在这里填写数据库字段名，留空则根据字段名自动生成数据库字段名"
            },
            {
              "type": "textarea",
              "label": "描述",
              "name": "description",
              "description": "说明字段的业务含义、单位、取值口径和使用限制，帮助开发者及未来的 AI 智能体理解；可留空，仅作说明，不执行表达式。"
            },
            {
              "type": "select",
              "name": "type",
              "label": "字段类型",
              "searchable": true,
              "required": true,
              "source": "get:/api/app/{appName}/field-types/${isPrimaryKey ? 'primary' : 'normal'}"
            },
            {
              "type": "switch",
              "name": "isDisplayField",
              "label": "作为显示字段",
              "hiddenOn": "${ isPrimaryKey }"
            },
            {
              "type": "switch",
              "name": "isPrimaryKey",
              "label": "主键",
              "value": false,
              "onEvent": {
                "change": {
                  "actions": [
                    {
                      "actionType": "setValue",
                      "componentName": "isRequired",
                      "expression": "isPrimaryKey",
                      "args": {
                        "value": true
                      }
                    },
                    {
                      "actionType": "setValue",
                      "componentName": "isUnique",
                      "expression": "isPrimaryKey",
                      "args": {
                        "value": true
                      }
                    },
                    {
                      "actionType": "setValue",
                      "componentName": "name",
                      "expression": "isPrimaryKey",
                      "args": {
                        "value": "id"
                      }
                    }
                  ]
                }
              }
            },
            {
              "type": "switch",
              "name": "isRequired",
              "label": "必填",
              "disabledOn": "${ isPrimaryKey || isUnique }",
              "value": false
            },
            {
              "type": "switch",
              "name": "isUnique",
              "label": "值唯一",
              "disabledOn": "${ isPrimaryKey }",
              "value": false,
              "onEvent": {
                "change": {
                  "actions": [
                    {
                      "actionType": "setValue",
                      "componentName": "isRequired",
                      "args": {
                        "value": true
                      },
                      "expression": "value"
                    }
                  ]
                }
              }
            },
            {
              "type": "script-editor", "language": "spel",
              "codeSite": "business-model.field-default", "codeResourceType": "business-model",
              "codePointer": "/fields/*/defaultValue", "intelligenceApi": "/api/dev/${appName}/code-intelligence/context",
              "label": "默认值",
              "name": "defaultValue",
              "hiddenOn": "${ isPrimaryKey }",
              "description": "默认值是一个表达式，若要设置字符串常量，请加单引号。表达式中可以引用其他字段，也可以使用平台提供的工具类，详情请查阅文档。"
            },
            {
              "type": "amis-schema-editor",
              "name": "customInput",
              "label": "输入组件",
              "onText": "使用自定义组件",
              "offText": "使用默认组件",
              "initValueApi": {
                "method": "post",
                "url": "/api/dev/${appName}/business-model-helper/${fullName}/field-input-schema",
                "data": "${ fields[__index] }"
              },
              "showSwitch": true
            },
            {
              "type": "amis-schema-editor",
              "name": "customDisplay",
              "label": "显示组件",
              "onText": "使用自定义组件",
              "offText": "使用默认组件",
              "initValueApi": {
                "method": "post",
                "url": "/api/dev/${appName}/business-model-helper/${fullName}/field-display-schema",
                "data": "${ fields[__index] }"
              },
              "showSwitch": true
            },
            {
              "type": "service",
              "schemaApi": {
                "method": "get",
                "url": "/api/app/${appName}/field-types/${type}/ui-schema",
                "sendOn": "${ type }",
                "responseData": [
                  {
                    "type": "divider",
                    "visible": "${!!body && !!body.length}"
                  },
                  {
                    "type": "fieldSet",
                    "title": "展开更多设置",
                    "collapseTitle": "收起设置",
                    "titlePosition": "bottom",
                    "collapsable": true,
                    "collapsed": false,
                    "visible": "${!!body && !!body.length}",
                    "body": "$body"
                  }
                ]
              }
            }
          ],
          "inheritData": false
        },
        "actions": [
          {
            "type": "button",
            "label": "确认",
            "level": "primary",
            "actionType": "confirm",
            "mergeData": true
          }
        ],
        "closeOnEsc": true,
        "resizable": true
      }
    }
  ],
  "strictMode": false,
  "multiple": true,
  "draggable": true,
  "canAccessSuperData": false
}
</#noparse>
