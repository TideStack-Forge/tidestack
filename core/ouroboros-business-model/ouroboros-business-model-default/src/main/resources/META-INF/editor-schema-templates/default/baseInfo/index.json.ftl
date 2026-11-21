<#noparse>
{
  "type": "panel",
  "className": "p-0 flex-grow-0",
  "bodyClassName": "p-0",
  "body": [
    {
      "type": "collapse",
      "className": "border-0 shadow-none",
      "headingClassName": "px-4 py-3 w-full",
      "bodyClassName": "px-4 pb-4 pt-0",
      "showArrow": false,
      "collapsed": true,
      "collapsable": true,
      "header": {
        "type": "wrapper",
        "className": "flex p-0 justify-between items-start w-full",
        "body": [
          {
            "type": "static-text",
            "name": "fullName",
            "mode": "horizontal",
            "label": "模型",
            "className": "mb-0",
            "labelWidth": 90
          },
          {
            "type": "wrapper",
            "className": "p-0 flex items-center gap-4",
            "body": [
              {
                "type": "switch",
                "label": false,
                "name": "isEnabled",
                "onText": "启用模型",
                "offText": "停用模型",
                "className": "mb-0"
              },
              {
                "type": "wrapper",
                "className": "p-0 text-secondary flex items-center hover:text-primary mt-2",
                "body": [
                  "展开",
                  {
                    "type": "icon",
                    "vendor": "",
                    "className": "ml-1",
                    "icon": "fas fa-angle-down"
                  }
                ]
              }
            ]
          }
        ]
      },
      "collapseHeader": {
        "type": "wrapper",
        "className": "flex p-0 justify-between items-start w-full",
        "body": [
          {
            "type": "static-text",
            "name": "fullName",
            "mode": "horizontal",
            "label": "模型",
            "className": "mb-0",
            "labelWidth": 90
          },
          {
            "type": "wrapper",
            "className": "p-0 flex items-center gap-4",
            "body": [
              {
                "type": "switch",
                "label": false,
                "name": "isEnabled",
                "onText": "启用模型",
                "offText": "停用模型",
                "className": "mb-0"
              },
              {
                "type": "wrapper",
                "className": "p-0 text-secondary flex items-center hover:text-primary mt-2",
                "body": [
                  "收起",
                  {
                    "type": "icon",
                    "vendor": "",
                    "className": "ml-1",
                    "icon": "fas fa-angle-up"
                  }
                ]
              }
            ]
          }
        ]
      },
      "body": [
        {
          "type": "group",
          "className": "mt-4",
          "body": [
            {
              "type": "input-text",
              "name": "title",
              "label": "模型标题",
              "placeholder": "请输入模型标题",
              "required": true
            },
            {
              "type": "ouroboros-icon-picker",
              "label": "图标",
              "name": "icon",
              "columnRatio": 4,
              "labelWidth": 40,
              "placeholder": "请输入图标名或地址"
            }
          ]
        },
        {
          "type": "switch",
          "name": "isAttachment",
          "label": "附属模型"
        },
        {
          "type": "input-number",
          "name": "order",
          "label": "排序",
          "placeholder": "请输入序号",
          "required": true,
          "value": 0
        },
        {
          "type": "textarea",
          "name": "description",
          "label": "模型描述",
          "placeholder": "请输入模型描述"
        },
        {
          "type": "hidden",
          "name": "dataStation"
        },
        {
          "type": "hidden",
          "name": "rawTableName"
        },
        {
          "type": "button-toolbar",
          "buttons": [
            {
              "type": "button",
              "actionType": "dialog",
              "icon": "fa fa-gear",
              "label": "高级设置",
              "dialog": {
                "type": "dialog",
                "title": "高级设置",
                "body": {
                  "type": "form",
                  "body": [
                    {
                      "type": "input-text",
                      "name": "dataStation",
                      "label": "数据源",
                      "clearable": true,
                      "autoComplete": {
                        "method": "get",
                        "url": "/api/dev/${appName}/datasource?perPage=1000",
                        "responseData": {
                          "items": "${ ARRAYMAP(items, item => item.dataStationName) }"
                        }
                      }
                    },
                    {
                      "type": "input-text",
                      "name": "rawTableName",
                      "label": "原始表名",
                      "visibleOn": "${ !dataStation }"
                    },
                    {
                      "type": "select",
                      "name": "rawTableName",
                      "label": "原始表名",
                      "addable": true,
                      "source": {
                        "method": "get",
                        "url": "/api/dev/${appName}/datasource/${dataStation}/table",
                        "responseData": {
                          "items": "${ ARRAYMAP(items, item => item.name) }"
                        }
                      },
                      "visibleOn": "${ dataStation }"
                    }
                  ]
                },
                "actions": [
                  {
                    "type": "button",
                    "actionType": "cancel",
                    "label": "取消"
                  },
                  {
                    "type": "button",
                    "actionType": "submit",
                    "label": "确定",
                    "level": "primary",
                    "mergeData": true
                  }
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "type": "hidden",
      "name": "namespace"
    },
    {
      "type": "hidden",
      "name": "modelType",
      "value": "default"
    },
    {
      "type": "hidden",
      "name": "formatVersion"
    },
    {
      "type": "hidden",
      "name": "name"
    }
  ]
}
</#noparse>
