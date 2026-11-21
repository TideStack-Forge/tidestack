<#macro editActions cloneEndpoint>
<#noparse>{
  "type": "button-toolbar",
  "className": "mt-3 text-right",
  "buttons": [
    {
      "type": "button",
      "label": "${isCloned ? '打开编辑器' : '克隆并编辑'}",
      "icon": "fa fa-edit",
      "level": "primary",
      "disabledOn": "${!editUrl}",
      "onEvent": {
        "click": {
          "actions": [
            {
              "actionType": "ajax",
              "expression": "${!isCloned}",
              "api": {
                "method": "post",
                "url": "/api/dev/${appName}/business-model/${fullName}/</#noparse>${cloneEndpoint}<#noparse>?name=${name}"
              }
            },
            {
              "actionType": "openTab",
              "args": {
                "title": "编辑「${editName}」",
                "url": "${editUrl}"
              }
            },
            {
              "actionType": "closeDialog"
            }
          ]
        }
      }
    }
  ]
}</#noparse>
</#macro>
