<#import "../lib" as lib>
<#noparse>
{
  "title": "新建",
  "mode": "horizontal",
  "icon": "fas fa-plus",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "apis.add.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${ apis.add.isEnabled }",
      "body": [
        {
          "type": "switch",
          "name": "apis.add.isAuto",
          "label": "自动逻辑",
          "value": true
        },
        {
          "type": "container",
          "visibleOn": "${ apis.add.isAuto }",
          "body": [
            {
              "type": "fieldSet",
              "title": "字段配置",
              "collapsable": true,
              "collapsed": true,
              "visible": false,
              "body": [
                {
                  "type": "input-tag",
                  "label": "允许的字段",
                  "name": "apis.add.allowedFields",
                  "source": "${fields|pick:label~title,value~name}",
                  "disabled": true,
                  "description": "功能完善中"
                },
                {
                  "type": "input-kvs",
                  "label": "字段覆写值",
                  "name": "apis.add.overwriteValues",
                  "keyItem": {
                    "type": "select",
                    "name": "field",
                    "placeholder": "请选择",
                    "mode": "inline",
                    "source": "${fields|pick:label~title,value~name}"
                  },
                  "valueItems": [
                    {
                      "type": "input-text",
                      "name": "_value",
                      "mode": "inline",
                      "defaultValue": "",
                      "placeholder": "默认数据"
                    }
                  ],
                  "disabled": true,
                  "description": "功能完善中"
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "插入执行前脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.add.beforeScript" /><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "插入执行后脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.add.afterScript" /><#noparse>
              ]
            }
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义脚本",
          "hiddenOn": "${ apis.add.isAuto }",
          "body": [
            </#noparse><@lib.scriptSetting scriptName="apis.add.script" /><#noparse>
          ]
        },</#noparse>
        <@lib.showLogicFlowButton logicFlowName="add" logicFlowTitle="新建"/>
      ]
    }
  ]
}