<#noparse>
{
  "type": "fieldSet",
  "title": "页面设计",
  "hiddenOn": "${ ui.mode == 'api' || ui.mode == 'custom' }",
  "body": [
    {
      "type": "button-toolbar",
      "buttons": [
        {
          "type": "button",
          "label": "设计页面",
          "level": "primary",
          "size": "lg",
          "disabledOn": "${ readOnly }",
          "onEvent": {
          "click": {
              "weight": 0,
              "actions": [
                {
                  "ignoreError": true,
                  "actionType": "ajax",
                  "outputVar": "result",
                  "options": {},
                  "api": {
                    "url": "/api/dev/${appName}/ui-schema/${fullName}.${name}",
                    "method": "get",
                    "messages": {
                      "success": "",
                      "failed": ""
                    }
                  }
                },
                {
                  "actionType": "openOrSwitchTab",
                  "expression": "${result.responseData}",
                  "args": {
                    "url": "/dev/${appName}/ui-schema/${responseData.name}",
                    "title": "编辑 UI Schema「${responseData.name}」"
                  }
                },
                {
                  "actionType": "dialog",
                  "expression": "${ !result.responseData }",
                  "dialog": {
                    "title": "请选择页面类型",
                    "body": {
                      "type": "form",
                      "api": "post:/api/dev/${appName}/ui-schema",
                      "wrappWithPanel": false,
                      "body": [
                        {
                          "type": "hidden",
                          "name": "name"
                        },
                        {
                          "type": "hidden",
                          "name": "title"
                        },
                        {
                          "type": "button-group-select",
                          "name": "schema",
                          "tiled": true,
                          "label": false,
                          "required": true,
                          "options": [
                            {
                              "label": "表单",
                              "value": {
                                "type": "form"
                              }
                            },
                            {
                              "label": "页面",
                              "value": {
                                "type": "page"
                              }
                            }
                          ]
                        }
                      ],
                      "onEvent": {
                        "submitSucc": {
                          "actions": [
                            {
                              "actionType": "openTab",
                              "args": {
                                "url": "/dev/${appName}/ui-schema/${name}",
                                "title": "编辑 UI Schema「${name}」"
                              }
                            }
                          ]
                        }
                      }
                    },
                    "data": {
                      "name": "${fullName}.${name}",
                      "title": "${title}",
                      "appName": "${appName}"
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