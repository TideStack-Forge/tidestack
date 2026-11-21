<#import "../lib" as lib>
<#noparse>
{
  "title": "获取单条",
  "mode": "horizontal",
  "icon": "fas fa-arrow-down ",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "apis.getOne.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${ apis.getOne.isEnabled }",
      "body": [
        {
          "type": "switch",
          "label": "自动逻辑",
          "name": "apis.getOne.isAuto",
          "value": true
        },
        {
          "type": "container",
          "visibleOn": "${ apis.getOne.isAuto }",
          "body": [
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
                  "name": "apis.getOne.populate",
                  "modelMetaProvider": </#noparse><@lib.defaultModelMetaProvider /><#noparse>
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "查询前执行脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.getOne.beforeScript" /><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "查询后执行脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.getOne.afterScript" /><#noparse>
              ]
            }
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义脚本",
          "hiddenOn": "${ apis.getOne.isAuto }",
          "body": [
            </#noparse><@lib.scriptSetting scriptName="apis.getOne.script" /><#noparse>
          ]
        },</#noparse>
        <@lib.showLogicFlowButton logicFlowName="getOne" logicFlowTitle="获取单条"/>
      ]
    }
  ]
}
