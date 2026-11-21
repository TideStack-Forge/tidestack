<#noparse>
{
  "type": "combo",
  "className": "pr-2",
  "multiple": true,
  "draggable": true,
  "label": false,
  "name": "relations",
  "id": "relations",
  "canAccessSuperData": false,
  "disabledOn": "${ !(fields && fields.length > 0) }",
  "scaffold": {
    "name": "",
    "title": "",
    "description": "",
    "type": "",
    "isPrimaryKey": false,
    "submitTogether": false
  },
  "itemClassName": "items-start",
  "items": [
    {
      "type": "input-text",
      "name": "name",
      "label": "关系名",
      "columnClassName": "flex-1",
      "required": true,
      "unique": true,
      "validate": "return data.fields && data.fields.some(item => item.name == value) ? '关系名与字段名重复' : ''",
      "validations": {
        "isVariableName": true
      }
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
      "labelRemark": {
        "shape": "circle",
        "content": "<ui><li><b>主表：</b>指定外部模型为本模型的主表</li><li><b>子表：</b>指定外部模型为本表的子表（该外部模型需要先建立指向本表的主表关系）</li><li><b>引用：</b>与主表类似，但更多用于表示一个非主子表的关联外键</li></ui>"
      },
      "options": [
        {
          "label": "主表",
          "value": "master"
        },
        {
          "label": "子表",
          "value": "child"
        },
        {
          "label": "引用",
          "value": "reference"
        }
      ]
    },
    {
      "type": "group",
      "columnClassName": "flex-1",
      "body": [
        {
          "type": "select",
          "name": "model",
          "label": "模型",
          "searchable": true,
          "required": true,
          "size": "full",
          "labelWidth": 60,
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/business-model/list",
            "cache": 2000
          },
          "labelField": "title",
          "valueField": "fullName"
        },
        {
          "type": "select",
          "name": "referenceKey",
          "label": "引用键",
          "searchable": true,
          "labelWidth": 60,
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/business-model/${model}",
            "adaptor": "return payload.status == 0 ? {status: 0, data: {items: payload.data.relations.filter(r => ['master', 'reference'].includes(r.type) && r.model == context.fullName)}} : payload",
            "cache": 2000
          },
          "labelField": "title",
          "valueField": "name",
          "menuTpl": "${title} <span class=\"text-muted\">(${name})</span>",
          "required": true,
          "clearValueOnHidden": true,
          "visibleOn": "${ type == 'child' }"
        }
      ]
    },</#noparse>
    <#include "relations.configButton"><#noparse>
  ]
},
{
  "type": "hidden",
  "name": "relations"
}
</#noparse>
