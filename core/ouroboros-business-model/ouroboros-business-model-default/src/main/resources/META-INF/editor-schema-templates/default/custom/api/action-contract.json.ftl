<#noparse>
{
  "type": "fieldSet",
  "title": "旧格式 Action 契约（保留原位置）",
  "collapsable": true,
  "collapsed": true,
  "visibleOn": "${api.isBindAction === true || (api.isBindAction == null && api.isGenerateImplementation === true)}",
  "body": [
    {
      "type": "tpl",
      "tpl": "这里编辑原内联契约，不复制到 actions。事件引用仅声明允许显式 emit；高级输入映射仍可访问完整同步 request。"
    },
    {
      "type": "json-data-editor",
      "name": "api.inputSchema",
      "label": "输入 Schema（API 原位置）",
      "visibleOn": "${api.inputSchema !== undefined || inputSchema === undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "inputSchema",
      "label": "输入 Schema（功能原位置）",
      "visibleOn": "${api.inputSchema === undefined && inputSchema !== undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "api.outputSchema",
      "label": "输出 Schema（API 原位置）",
      "visibleOn": "${api.outputSchema !== undefined || outputSchema === undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "outputSchema",
      "label": "输出 Schema（功能原位置）",
      "visibleOn": "${api.outputSchema === undefined && outputSchema !== undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "api.precondition",
      "label": "前置条件（API 原位置）",
      "visibleOn": "${api.precondition !== undefined || precondition === undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "precondition",
      "label": "前置条件（功能原位置）",
      "visibleOn": "${api.precondition === undefined && precondition !== undefined}",
      "allowValueTypes": "object"
    },
    {
      "type": "json-data-editor",
      "name": "api.emits",
      "label": "允许发出的事件引用（API 原位置）",
      "visibleOn": "${api.emits !== undefined || emits === undefined}",
      "allowValueTypes": "array"
    },
    {
      "type": "json-data-editor",
      "name": "emits",
      "label": "允许发出的事件引用（功能原位置）",
      "visibleOn": "${api.emits === undefined && emits !== undefined}",
      "allowValueTypes": "array"
    },
    {
      "type": "select",
      "name": "api.readOnly",
      "label": "是否只读（API 原位置）",
      "visibleOn": "${api.readOnly !== undefined || readOnly === undefined}",
      "joinValues": false,
      "extractValue": true,
      "placeholder": "未显式设置",
      "options": [
        {
          "label": "只读",
          "value": true
        },
        {
          "label": "允许写入",
          "value": false
        }
      ]
    },
    {
      "type": "select",
      "name": "readOnly",
      "label": "是否只读（功能原位置）",
      "visibleOn": "${api.readOnly === undefined && readOnly !== undefined}",
      "joinValues": false,
      "extractValue": true,
      "placeholder": "未显式设置",
      "options": [
        {
          "label": "只读",
          "value": true
        },
        {
          "label": "允许写入",
          "value": false
        }
      ]
    }
  ]
}
</#noparse>
