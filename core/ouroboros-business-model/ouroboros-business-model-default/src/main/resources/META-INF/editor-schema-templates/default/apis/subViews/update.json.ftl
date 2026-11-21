<#import "../lib" as lib>
<#noparse>
{
  "title": "更新",
  "mode": "horizontal",
  "icon": "fas fa-arrow-up",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "apis.update.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${ apis.update.isEnabled }",
      "body": [
        {
          "type": "switch",
          "label": "自动逻辑",
          "name": "apis.update.isAuto",
          "value": true
        },
        {
          "type": "container",
          "visibleOn": "${ apis.update.isAuto }",
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
                  "label": "允许传递字段",
                  "name": "apis.update.allowedFields",
                  "source": "${fields|pick:label~title,value~name}",
                  "placeholder": "请选择",
                  "disabled": true,
                  "description": "功能完善中"
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "更新执行前脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.update.beforeScript" /><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "更新执行后脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.update.afterScript" /><#noparse>
              ]
            }
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义脚本",
          "hiddenOn": "${ apis.update.isAuto }",
          "body": [
            </#noparse><@lib.scriptSetting scriptName="apis.update.script" /><#noparse>
          ]
        },</#noparse>
        <@lib.showLogicFlowButton logicFlowName="update" logicFlowTitle="更新"/>
      ]
    }
  ]
}