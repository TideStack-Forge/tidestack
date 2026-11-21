<#noparse>
{
  "title": "API配置",
  "icon": "fa fa-gears",
  "tab": [
    {
      "type": "switch",
      "name": "api.isEnabled",
      "label": "启用自定义API",
      "disabledOn": "${ !name || !title }",
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "expression": "${ value && !api.url }",
              "componentId": "hidden-api-url",
              "args": {
                "value": "${name | kebabCasePath:false}"
              }
            }
          ]
        }
      }
    },
    {
      "type": "hidden",
      "name": "api.url",
      "id": "hidden-api-url"
    },
    </#noparse><#include "detail"><#noparse>
  ]
}
</#noparse>