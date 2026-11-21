<#noparse>
{
  "type": "button",
  "actionType": "drawer",
  "label": "",
  "icon": "fa fa-gear",
  "colSize": "max-content",
  "columnClassName": "flex-grow-0 min-w-max",
  "closeOnEsc": true,
  "closeOnOutside": true,
  "drawer": {
    "type": "drawer",
    "title": "关联属性",
    "resizable": true,
    "size": "lg",
    "body": [
      {
        "type": "form",
        "body": [
          {
            "type": "input-text",
            "name": "name",
            "label": "关系名",
            "required": true,
            "validate": "return data.fields && data.fields.some(item => item.name == value) ? '关系名与字段名重复' : ''",
            "validations": {
              "isVariableName": true
            }
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
            "hiddenOn": "${ type == 'child' }",
            "clearValueOnHidden": true,
            "description": "外键字段名，如果不填写，则默认根据关系名自动生成"
          },
          {
            "type": "textarea",
            "label": "描述",
            "name": "description",
            "description": "说明这条关系的业务角色、关联含义和使用限制，例如开票客户与收货客户的区别；可留空，帮助开发者及未来的 AI 智能体理解。"
          },
          {
            "type": "divider"
          },
          {
            "type": "select",
            "name": "type",
            "label": "类型",
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
            "type": "select",
            "name": "model",
            "label": "关联模型",
            "searchable": true,
            "required": true,
            "source": "get:/api/dev/${appName}/business-model/list",
            "labelField": "title",
            "valueField": "fullName"
          },
          {
            "type": "select",
            "name": "referenceKey",
            "label": "关联键",
            "searchable": true,
            "source": {
              "method": "get",
              "url": "/api/dev/${appName}/business-model/${model}",
              "adaptor": "return payload.status == 0 ? {status: 0, data: {items: payload.data.relations.filter(r => ['master', 'reference'].includes(r.type) && r.model == context.fullName)}} : payload"
            },
            "labelField": "title",
            "valueField": "name",
            "menuTpl": "${title} <span class=\"text-muted\">(${name})</span>",
            "required": true,
            "clearValueOnHidden": true,
            "visibleOn": "${ type == 'child' }",
            "description": "子表中的关联关系名"
          },
          {
            "type": "select",
            "name": "referenceKey",
            "label": "引用键",
            "searchable": true,
            "clearable": true,
            "source": {
              "method": "get",
              "url": "/api/dev/${appName}/business-model/${model}",
              "adaptor": "return payload.status == 0 ? {status: 0, data: {items: payload.data.fields}} : payload"
            },
            "menuTpl": "${title} <span class=\"text-muted\">(${name})</span>",
            "labelField": "title",
            "valueField": "name",
            "clearValueOnHidden": true,
            "visibleOn": "${ type !== 'child' }",
            "description": "引用键字段名，留空则默认使用主键（ID）"
          },
          {
            "type": "switch",
            "name": "submitTogether",
            "label": "同步提交",
            "visibleOn": "${ type == 'child' }",
            "value": false
          },
          {
            "type": "switch",
            "name": "isRequired",
            "label": "必填",
            "visibleOn": "${ type == 'reference' || type == 'master' }",
            "value": false
          },
          {
            "type": "button-group-select",
            "name": "selectorType",
            "label": "输入方式",
            "options": [
              {
                "label": "拾取器",
                "value": "picker"
              },
              {
                "label": "下拉框",
                "value": "select"
              }
            ],
            "value": "picker",
            "visibleOn": "${ type == 'reference' || type == 'master' }"
          },
          {
            "type": "button-group-select",
            "name": "displayType",
            "label": "显示方式",
            "options": [
              {
                "label": "详情（字段集）",
                "value": "fieldSet"
              },
              {
                "label": "表单字段",
                "value": "formItem"
              }
            ],
            "description": "在详情页时的显示方式",
            "value": "fieldSet",
            "visibleOn": "${ type == 'reference' || type == 'master' }"
          },</#noparse>
          {
            "type": "fieldSet",
            "title": "高级选项",
            "collapsable": true,
            "collapsed": true,
            "body": [
              <#include "relations.customSelector">,
              <#include "relations.customDisplayColumns">,
              <#include "relations.customInputFields">
            ]
          }<#noparse>
        ],
        "inheritData": false
      }
    ],
    "actions": [
      {
        "type": "button",
        "label": "确认",
        "level": "primary",
        "mergeData": true,
        "actionType": "confirm"
      }
    ]
  }
}</#noparse>
