<#macro columnConfig>
<#noparse>
{
  "type": "fieldSet",
  "title": "列属性",
  "collapsable": true,
  "collapsed": true,
  "body": [
    {
      "type": "input-text",
      "name": "columnProps.width",
      "label": "固定宽度",
      "clearable": true,
      "description": "留空则自动设置宽度"
    },
    {
      "type": "button-group-select",
      "name": "columnProps.fixed",
      "label": "固定列",
      "resetValue": null,
      "clearable": true,
      "options": [
        {
          "label": "左",
          "value": "left"
        },
        {
          "label": "右",
          "value": "right"
        }
      ]
    },
    {
      "type": "button-group-select",
      "name": "columnProps.align",
      "label": "对齐方式",
      "resetValue": null,
      "clearable": true,
      "options": [
        {
          "label": "两端对齐",
          "value": "justify"
        },
        {
          "label": "左对齐",
          "value": "left"
        },
        {
          "label": "居中对齐",
          "value": "center"
        },
        {
          "label": "右对齐",
          "value": "right"
        }
      ]
    }
  ]
}
</#noparse>
</#macro>

<#macro fieldFilterConfig>
<#noparse>
{
  "name": "filterOperator",
  "type": "select",
  "placeholder": "操作符",
  "searchable": true,
  "options": [
    {
      "label": "等于",
      "value": "EQ"
    },
    {
      "label": "不等于",
      "value": "NE"
    },
    {
      "label": "包含",
      "value": "CONTAINS"
    },
    {
      "label": "左包含",
      "value": "STARTS_WITH"
    },
    {
      "label": "右包含",
      "value": "ENDS_WITH"
    },
    {
      "label": "大于",
      "value": "GT"
    },
    {
      "label": "大于等于",
      "value": "GOE"
    },
    {
      "label": "小于",
      "value": "LT"
    },
    {
      "label": "小于等于",
      "value": "LOE"
    },
    {
      "label": "不包含",
      "value": "NOT_CONTAINS"
    },
    {
      "label": "在范围内",
      "value": "BETWEEN"
    }
  ],
  "required": true
},
{
  "type": "select",
  "name": "filterType",
  "placeholder": "过滤器类型",
  "searchable": true,
  "hiddenOn": "${ filterOperator == 'BETWEEN' }",
  "options": [
    {
      "label": "文本框",
      "value": "input-text"
    },
    {
      "label": "数字",
      "value": "input-number"
    },
    {
      "label": "日期",
      "value": "input-date"
    },
    {
      "label": "日期时间",
      "value": "input-datetime"
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
          "componentName": "schema",
          "args": {
            "value": null
          },
          "expression": "${ filterType !== 'custom' && schema != null }"
        }
      ]
    }
  },
  "required": true
},
{
  "type": "select",
  "name": "filterType",
  "placeholder": "过滤器类型",
  "searchable": true,
  "visibleOn": "${ filterOperator == 'BETWEEN' }",
  "options": [
    {
      "label": "日期范围",
      "value": "input-date-range"
    },
    {
      "label": "日期时间范围",
      "value": "input-datetime-range"
    },
    {
      "label": "数字范围",
      "value": "input-number-range",
      "visible": false
    }
  ],
  "required": true
}
</#noparse>
</#macro>

<#macro fieldFilterExtraConfig>
<#noparse>
{
  "type": "amis-schema-editor",
  "name": "schema",
  "switchLayout": false,
  "previewText": "预览过滤器",
  "visibleOn": "${ filterType == 'custom' && !!fieldName }",
  "initValueApi": {
    "method": "post",
    "url": "/api/dev/${appName}/business-model-helper/${fullName}/field-input-schema",
    "data": "${ ARRAYFIND(fields, item => item.name === fieldName) }",
    "responseData": {
      "&": "$$",
      "required": false
    }
  }
},
{
  "type": "amis-schema-editor",
  "name": "filterProps",
  "switchLayout": false,
  "onText": "定义过滤器",
  "offText": "定义过滤器",
  "strictMode": false,
  "visibleOn": "${ filterType && filterType != 'custom' }",
  "pipeIn": "return !value ? null : { ...value, type: data.filterType }",
  "pipeOut": "if (!value) return value; const { type, ...rest } = value; return rest;",
  "showSwitch": true,
  "value": null,
  "initValue": {
    "type": "${ filterType }"
  }
}
</#noparse>
</#macro>

<#macro relationFilterConfig>
<#noparse>
{
  "name": "filterOperator",
  "type": "select",
  "placeholder": "操作符",
  "searchable": true,
  "options": [
    {
      "label": "等于",
      "value": "EQ"
    },
    {
      "label": "不等于",
      "value": "NE"
    },
    {
      "label": "在列表中",
      "value": "IN"
    },
    {
      "label": "不在列表中",
      "value": "NOT_IN"
    }
  ],
  "required": true
},
{
  "type": "select",
  "name": "filterType",
  "placeholder": "过滤器类型",
  "searchable": true,
  "options": [
    {
      "label": "自动",
      "value": "auto"
    },
    {
      "label": "下拉框",
      "value": "select"
    },
    {
      "label": "拾取器",
      "value": "picker"
    }
  ],
  "required": true
}
</#noparse>
</#macro>

<#macro relationFilterExtraConfig>
<#noparse>
{
  "type": "fieldSet",
  "title": "更多",
  "collapseTitle": "收起",
  "titlePosition": "bottom",
  "collapsable": true,
  "collapsed": true,
  "headingClassName": "p-0",
  "mode": "horizontal",
  "horizontal": {
    "leftFixed": true
  },
  "body": [
    {
      "type": "input-text",
      "label": "过滤器标签",
      "clearable": true,
      "name": "filterProps.label",
      "placeholder": "保持为空则使用关系名",
      "size": "md",
      "labelWidth": "100px"
    },
    {
      "type": "input-kv",
      "name": "filterProps.defaultRequestParams",
      "label": "默认请求参数",
      "keyPlaceholder": "参数名",
      "valuePlaceholder": "参数值",
      "labelWidth": "100px",
      "description": "拉取关联模型数据时附带的请求参数"
    }
  ]
}
</#noparse>
</#macro>