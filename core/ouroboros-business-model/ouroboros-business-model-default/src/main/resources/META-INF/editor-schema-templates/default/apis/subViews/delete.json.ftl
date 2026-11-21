<#import "../lib" as lib>
<#noparse>
{
  "title": "删除",
  "mode": "horizontal",
  "icon": "far fa-trash-can",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "apis.delete.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${apis.delete.isEnabled}",
      "body": [
        {
          "type": "switch",
          "label": "自动逻辑",
          "name": "apis.delete.isAuto",
          "value": true
        },
        {
          "type": "switch",
          "name": "apis.delete.isForceCheckReference",
          "label": "强制检查引用",
          "value": false,
          "description": "开启以后会强制检查被删除的数据是否被引用过，否则只在未开启软删除的时候才会检查"
        },
        {
          "type": "container",
          "visibleOn": "${ apis.delete.isAuto }",
          "body": [
            {
              "type": "fieldSet",
              "title": "删除执行前脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.delete.beforeScript" /><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "删除执行后脚本",
              "collapsable": true,
              "collapsed": true,
              "body": [
                </#noparse><@lib.scriptSetting scriptName="apis.delete.afterScript" /><#noparse>
              ]
            }
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义脚本",
          "hiddenOn": "${ apis.delete.isAuto }",
          "body": [
            </#noparse><@lib.scriptSetting scriptName="apis.delete.script" /><#noparse>
          ]
        },</#noparse>
        <@lib.showLogicFlowButton logicFlowName="delete" logicFlowTitle="删除"/>
      ]
    }
  ]
}