<#noparse>
{
  "type": "button",
  "actionType": "dialog",
  "label": "新增",
  "icon": "fa fa-plus",
  "level": "primary",
  "className": "mb-5",
  "dialog": {
    "title": "新增自定义功能",
    "body": {
      "type": "form",
      "wrapWithPanel": false,
      "body": [
        {
          "type": "input-text",
          "name": "name",
          "label": "名字",
          "required": true,
          "placeholder": "请输入功能名（输入后不能修改）",
          "validateOnChange": true,
          "validations": {
            "isVariableName": "^[A-Za-z][A-Za-z0-9]*$",
            "notIn": [
              "add,insert,create,update,edit,delete,remove,detail,batchInsert,batchUpdate,batchDelete",
              ","
            ]
          },
          "validationErrors": {
            "isVariableName": "名称必须符合变量名规则",
            "notIn": "不能使用保留字"
          }
        },
        {
          "type": "input-text",
          "name": "title",
          "label": "标题",
          "placeholder": "请输入功能标题",
          "required": true
        },
        {
          "type": "ouroboros-icon-picker",
          "label": "图标",
          "name": "icon",
          "placeholder": "请输入图标类名"
        },
        {
          "type": "hidden",
          "name": "ui",
          "value": {
            "isEnabled": false,
            "entryPoint": "operation",
            "mode": "dialog",
            "buttonOrder": 0,
            "buttonIcon": "",
            "buttonLabel": "",
            "buttonLabelVisible": true,
            "buttonLevel": "default",
            "buttonSize": "md",
            "buttonBackgroundColor": "",
            "buttonForegroundColor": "",
            "apiConfirm": false,
            "apiFeedback": false,
            "apiReload": true,
            "apiRedirect": false
          }
        },
        {
          "type": "hidden",
          "name": "api",
          "value": {
            "isEnabled": false,
            "httpMethod": "POST",
            "url": "",
            "scriptLanguage": "javascript",
            "script": "",
            "isGenerateImplementation": true,
            "isBindAction": true,
            "customLogicFlow": false
          }
        }
      ],
      "onEvent": {
        "submit": {
          "actions": [
            {
              "actionType": "validate"
            },
            {
              "actionType": "custom",
              "script": "if (event.data.validateResult.error){ event.stopPropagation();return; } let payload = event.data.validateResult.payload; let item = {name: payload.name, title: payload.title, ui: payload.ui, api: payload.api}; if (payload.icon !== undefined) { item.icon = payload.icon; } let items = context.props.data.allItems || []; if (items.find(i => i.name == item.name)) { context.props.env.notify('error', '已存在同名功能'); event.stopPropagation(); return; } if (context.props.data.modelFormatVersion === '2.0') { item.api = {isEnabled: false, httpMethod: 'POST', url: '', implementationMode: 'WEB_API'}; } event.setData({newCustomFunction: item});"
            },
            {
              "actionType": "addItem",
              "componentName": "custom",
              "expression": "${ !validateResult.error }",
              "args": {
                "item": "${ newCustomFunction }"
              }
            },
            {
              "expression": "${ !validateResult.error }",
              "actionType": "close"
            }
          ]
        }
      }
    },
    "data": {
      "allItems": "${custom || []}",
      "modelFormatVersion": "${formatVersion}"
    },
    "actions": [
      {
        "type": "action",
        "actionType": "close",
        "label": "取消"
      },
      {
        "type": "action",
        "actionType": "submit",
        "level": "primary",
        "label": "确定",
        "close": false
      }
    ]
  },
  "block": false,
  "hiddenOn": "${ readOnly }",
  "size": "sm"
},
{
  "type": "combo",
  "name": "custom",
  "label": false,
  "multiple": true,
  "tabsMode": true,
  "tabsStyle": "line",
  "tabsLabelTpl": "${ title && name ? (title + '(' + name  + ')') : '未命名' }",
  "multipleLine": true,
  "subFormMode": "horizontal",
  "addable": false,
  "removable": false,
  "strictMode": false,
  "subFormHorizontal": {
    "leftFixed": true
  },
  "scaffold": {
    "name": "",
    "title": "",
    "ui": {
      "isEnabled": false,
      "entryPoint": "menu",
      "icon": "",
      "mode": "tab",
      "schema": null
    },
    "api": {
      "isEnabled": false,
      "httpMethod": "POST",
      "url": "",
      "scriptLanguage": "javascript",
      "script": ""
    }
  },
  "items": [
    {
      "type": "wrapper",
      "body": [
        {
          "type": "input-text",
          "name": "name",
          "label": "名字",
          "required": true,
          "disabled": true,
          "className": "flex-grow mr-3"
        },
        {
          "type": "button",
          "label": "删除",
          "level": "danger",
          "className": "flex-grow-0",
          "hiddenOn": "${ readOnly }",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "actionType": "confirmDialog",
                  "dialog": {
                    "title": "请确认",
                    "msg": "确认要删除${title}「${name}」吗？"
                  }
                },
                {
                  "actionType": "custom",
                  "script": "var name = context.props.data.name; var items = context.props.data.custom; var finalItems = items.filter(item => item.name !== name); event.setData({items: finalItems});"
                },
                {
                  "actionType": "setValue",
                  "componentName": "custom",
                  "args": {
                    "value": "$items"
                  }
                }
              ]
            }
          }
        }
      ],
      "className": "p-0 flex"
    },
    {
      "type": "fieldSet",
      "title": "展开",
      "collapsable": true,
      "collapsed": true,
      "body": [
        {
          "type": "input-text",
          "name": "title",
          "label": "标题",
          "required": true
        },
        {
          "type": "ouroboros-icon-picker",
          "label": "图标",
          "name": "icon",
          "placeholder": "请输入图标类名"
        },
        {
          "type": "textarea",
          "label": "功能描述",
          "name": "description",
          "placeholder": "请输入功能描述"
        }
      ],
      "titlePosition": "bottom",
      "collapseTitle": "收起"
    },
    {
      "type": "divider"
    },
    {
      "type": "tabs",
      "tabsMode": "sidebar",
      "tabs": [</#noparse>
        <#include "ui/index">,
        <#include "api/index">
      ]
    }
  ]
}
