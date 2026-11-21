<#noparse>
{
  "type": "crud",
  "source": "${referenceRelations}",
  "affixHeader": false,
  "autoFillHeight": false,
  "visibleOn": "${fullName}",
  "loadDataOnce": true,
  "syncLocation": false,
  "api": {
    "method": "GET",
    "url": "/api/dev/${appName}/business-model/${fullName}/reference-relations"
  },
  "headerToolbar": [
    {
      "type": "tpl",
      "tpl": "外部关联"
    },
    {
      "type": "reload",
      "align": "left"
    }
  ],
  "columns": [
    {
      "label": "关联模型",
      "name": "relatedModelTitle",
      "type": "text"
    },
    {
      "label": "显示名",
      "name": "title",
      "type": "text"
    },
    {
      "type": "mapping",
      "label": "关联类型",
      "name": "type",
      "map": {
        "master": "主表",
        "child": "子表",
        "reference": "引用"
      }
    },
    {
      "type": "operation",
      "label": "操作",
      "hiddenOn": "${ readOnly == true }",
      "buttons": [
        {
          "type": "button",
          "label": "添加",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "actionType": "addItem",
                  "componentId": "relations",
                  "args": {
                    "item": {
                      "type": "${ type == 'child' ? 'master' : (type == 'master' || type == 'reference') ? 'child' : 'reference'}",
                      "referenceKey": "${ (type == 'reference' || type == 'master') ? name : null }",
                      "model": "${relatedModel}"
                    }
                  }
                }
              ]
            }
          }
        }
      ]
    }
  ]
}
</#noparse>
