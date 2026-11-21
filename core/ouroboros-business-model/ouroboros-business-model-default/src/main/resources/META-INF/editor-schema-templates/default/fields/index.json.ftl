{
  "type": "panel",
  "header": <#noparse>{
    "type": "container",
    "bodyClassName": "flex flex-row justify-between",
    "body": [
      {
        "type": "tpl",
        "tpl": "业务字段"
      },
      {
        "type": "button",
        "label": "导入字段",
        "size": "xs",
        "level": "enhance",
        "onEvent": {
          "click": {
            "actions": [
              {
                "actionType": "dialog",
                "dialog": {
                  "type": "dialog",
                  "size": "lg",
                  "url": "/business-model-editor/import-fields",
                  "data": {
                    "appName": "${appName}",
                    "fullName": "${fullName}"
                  },
                  "actions": []
                },
                "waitForAction": true,
                "outputVar": "importedFields"
              },
              {
                "actionType": "stopPropagation",
                "expression": "${!importedFields.confirmed}"
              },
              {
                "actionType": "setValue",
                "componentName": "fields",
                "args": {
                  "value": "${importedFields.value[0].fields}"
                }
              }
            ]
          }
        }
      }
    ]
  }</#noparse>,
  "body": [<#include "editor">]
}