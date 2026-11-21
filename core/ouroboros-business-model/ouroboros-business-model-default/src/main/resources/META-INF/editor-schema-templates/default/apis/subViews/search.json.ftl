<#import "../lib" as lib>
<#noparse>
{
  "title": "搜索",
  "mode": "horizontal",
  "icon": "fas fa-magnifying-glass",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "apis.search.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${apis.search.isEnabled}",
      "body": [
        {
          "type": "switch",
          "label": "自动逻辑",
          "name": "apis.search.isAuto",
          "value": true
        },
        {
          "type": "container",
          "visibleOn": "${apis.search.isAuto}",
          "body": [
            {
              "type": "fieldSet",
              "title": "查询条件配置",
              "collapsable": true,
              "collapsed": true,
              "visible": false,
              "body": [
                {
                  "type": "input-kvs",
                  "label": "默认条件",
                  "name": "apis.search.defaultFilters",
                  "description": "即便前端没有传递，也会应用的查询条件（功能完善中）",
                  "keyItem": {
                    "type": "select",
                    "placeholder": "请选择",
                    "mode": "inline",
                    "source": "${fields|pick:label~title,value~name}"
                  },
                  "valueItems": [
                    {
                      "type": "input-text",
                      "name": "value",
                      "mode": "inline",
                      "placeholder": "条件值"
                    }
                  ],
                  "disabled": true
                },
                {
                  "type": "input-kvs",
                  "label": "覆写条件",
                  "name": "apis.search.overwriteFilters",
                  "description": "即便前端传递了，也会被覆盖的查询条件（功能完善中）",
                  "keyItem": {
                    "type": "select",
                    "placeholder": "请选择",
                    "mode": "inline",
                    "source": "${fields|pick:label~title,value~name}"
                  },
                  "valueItems": [
                    {
                      "type": "input-text",
                      "name": "value",
                      "mode": "inline",
                      "placeholder": "条件值"
                    }
                  ],
                  "disabled": true
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "默认排序配置",
              "collapsable": true,
              "collapsed": true,
              "body": [              
                {
                  "type": "list-select",
                  "name": "apis.search.order.fieldType",
                  "options": [
                    {
                      "label": "模型字段",
                      "value": "modelField"
                    },
                    {
                      "label": "内置字段",
                      "value": "builtIn"
                    }
                  ],
                  "value": "modelField"
                },
                {
                  "type": "input-group",
                  "label": "排序字段",
                  "body": [
                    {
                      "id": "orderFieldName",
                      "type": "select",
                      "name": "apis.search.order.field",
                      "source": "${fields|pick:label~title,value~name}",
                      "hiddenOn": "${apis.search.order.fieldType == 'builtIn'}",
                      "clearable": true,
                      "popOverContainerSelector": ".ouroboros-pop-over-container",
                      "size": "md"
                    },
                    {
                      "id": "orderFieldNameBuiltIn",
                      "type": "select",
                      "name": "apis.search.order.field",
                      "visibleOn": "${apis.search.order.fieldType == 'builtIn'}",
                      "clearable": true,
                      "size": "md",
                      "popOverContainerSelector": ".ouroboros-pop-over-container",
                      "options": [
                        {
                          "label": "创建时间",
                          "value": "createdAt",
                          "visibleOn": "${features.isEnabledAuditFields}"
                        },
                        {
                          "label": "更新时间",
                          "value": "updatedAt",
                          "visibleOn": "${features.isEnabledAuditFields}"
                        }
                      ]
                    },
                    {
                      "type": "button-group-select",
                      "name": "apis.search.order.direction",
                      "visibleOn": "apis.search.order.field",
                      "options": [
                        {
                          "label": "顺序",
                          "value": "ASC"
                        },
                        {
                          "label": "倒序",
                          "value": "DESC"
                        }
                      ],
                      "value": "ASC"
                    }
                  ]
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "关联模型",
              "collapsable": true,
              "collapsed": true,
              "multiple": true,
              "body": [
                {
                  "type": "populate-editor",
                  "label": "填充关联模型",
                  "name": "apis.search.populate",
                  "modelMetaProvider": </#noparse><@lib.defaultModelMetaProvider /><#noparse>
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "查询执行前脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.search.beforeScript" /><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "查询执行后脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.search.afterScript" /><#noparse>
              ]
            }
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义脚本",
          "hiddenOn": "${apis.search.isAuto}",
          "body": [
            </#noparse><@lib.scriptSetting scriptName="apis.search.script" /><#noparse>
          ]
        },</#noparse>
        <@lib.showLogicFlowButton logicFlowName="search" logicFlowTitle="搜索"/>
      ]
    }
  ]
}
