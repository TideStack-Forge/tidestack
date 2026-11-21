<#noparse>{
  "type": "service",
  "api": {
    "url": "/api/dev/${appName}/</#noparse>${apiPath}<#noparse>/status",
    "method": "get"
  },
  "body": [
    {
      "type": "tooltip-wrapper",
      "visibleOn": "${versionState && (versionState != 'NOTCHANGED')}",
      "body": [
        {
          "type": "icon",
          "icon": "fas fa-asterisk",
          "vendor": "",
          "className": {
            "text-danger": "${versionState == 'CHANGED'}",
            "text-yellow-600": "${versionState != 'CHANGED'}"
          },
          "visibleOn": "${versionState != 'NOTCHANGED'}"
        }
      ],
      "content": "已修改，未提交",
      "trigger": [
        "hover"
      ]
    },
    {
      "type": "tooltip-wrapper",
      "body": [
        {
          "type": "icon",
          "icon": "fa fa-lock",
          "vendor": "",
          "className": {
            "text-danger": "${lockState == 'LOCKED'}",
            "text-success": "${lockState == 'OWNED'}",
            "mx-2": true
          }
        }
      ],
      "visibleOn": "${lockState && (lockState != 'UNLOCKED')}",
      "content": "${lockState == 'OWNED' ? '已被您锁定' : '已被 ' + lockOwnerName + ' 锁定'}",
      "trigger": [
        "hover"
      ]
    },
    {
      "type": "button-toolbar",
      "label": "",
      "buttons": [
        {
          "type": "button",
          "label": "</#noparse>${startButton!'开始编辑'}<#noparse>",
          "size": "lg",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "ignoreError": false,
                  "actionType": "ajax",
                  "outputVar": "responseResult",
                  "api": {
                    "url": "/api/dev/${appName}/</#noparse>${apiPath}<#noparse>/lock",
                    "method": "post"
                  }
                },
                {
                  "componentId": "</#noparse>${statusId!statusName!'statusService'}<#noparse>",
                  "ignoreError": false,
                  "actionType": "reload"
                }
              ]
            }
          },
          "level": "primary",
          "disabledOnAction": true,
          "visibleOn": "${currentUserCanEdit !== false && lockState && (lockState != 'OWNED')}",
          "disabledOn": "${lockState == 'LOCKED'</#noparse><#if editDisabledOn?? && editDisabledOn?has_content> || (${editDisabledOn})</#if><#noparse>}"
        },
        {
          "type": "button",
          "label": "回滚",
          "size": "lg",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "ignoreError": false,
                  "actionType": "confirmDialog",
                  "dialog": {
                    "title": "确认",
                    "msg": "回滚操作将会丢失未保存的修改，确定要回滚吗？",
                    "actionType": "confirmDialog"
                  }
                },
                {
                  "ignoreError": false,
                  "actionType": "ajax",
                  "outputVar": "responseResult",
                  "api": {
                    "url": "/api/dev/${appName}/</#noparse>${apiPath}<#noparse>/rollback",
                    "method": "post"
                  }
                },
                {
                  "ignoreError": false,
                  "actionType": "reload",
                  "componentId": "</#noparse>${statusId!statusName!'statusService'}<#noparse>"
                },
                {
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "ignoreError": false,
                  "actionType": "reload"
                }
              ]
            }
          },
          "icon": "fa fa-undo",
          "level": "warning",
          "disabledOnAction": true,
          "visibleOn": "${currentUserCanEdit !== false && lockState == 'OWNED'}",
          "disabledOn": "${</#noparse><#if editDisabledOn?? && editDisabledOn?has_content>${editDisabledOn}<#else>false</#if><#noparse>}"
        },
        {
          "type": "button",
          "label": "保存",
          "size": "lg",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "actionType": "validate",
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "ignoreError": false,
                  "outputVar": "validateResult"
                },
                {
                  "actionType": "submit",
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "ignoreError": false,
                  "outputVar": "submitResult"
                },
                {
                  "actionType": "reload",
                  "componentId": "</#noparse>${statusId!statusName!'statusService'}<#noparse>"
                }
              ]
            }
          },
          "level": "light",
          "icon": "fa fa-save",
          "disabledOnAction": true,
          "visibleOn": "${currentUserCanEdit !== false && lockState == 'OWNED'}",
          "disabledOn": "${</#noparse><#if editDisabledOn?? && editDisabledOn?has_content>${editDisabledOn}<#else>false</#if><#noparse>}"
        },
        {
          "type": "button",
          "label": "提交",
          "size": "lg",
          "onEvent": {
            "click": {
              "actions": [
                {
                  "ignoreError": false,
                  "actionType": "dialog",
                  "waitForAction": true,
                  "dialog": {
                    "type": "dialog",
                    "title": "提交信息",
                    "body": [
                      {
                        "type": "form",
                        "title": "表单",
                        "mode": "flex",
                        "labelAlign": "top",
                        "body": [
                          {
                            "type": "textarea",
                            "label": "",
                            "name": "commitMsg",
                            "row": 0,
                            "minRows": 3,
                            "maxRows": 20,
                            "placeholder": "请输入修改说明"
                          }
                        ],
                        "resetAfterSubmit": true,
                        "actions": []
                      }
                    ],
                    "actions": [
                      {
                        "type": "button",
                        "actionType": "cancel",
                        "label": "取消"
                      },
                      {
                        "type": "button",
                        "actionType": "confirm",
                        "level": "primary",
                        "label": "提交"
                      }
                    ],
                    "showCloseButton": true,
                    "closeOnOutside": false,
                    "closeOnEsc": false,
                    "showErrorMsg": true,
                    "showLoading": true,
                    "draggable": false,
                    "actionType": "dialog"
                  },
                  "outputVar": "commitResult",
                  "stopPropagation": "${!event.data.commitResult.confirmed}"
                },
                {
                  "actionType": "validate",
                  "ignoreError": false,
                  "outputVar": "validateResult",
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "expression": "${event.data.commitResult.confirmed}"
                },
                {
                  "actionType": "submit",
                  "ignoreError": false,
                  "outputVar": "submitResult",
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "expression": "${!event.data.validateResult.error}"
                },
                {
                  "actionType": "ajax",
                  "ignoreError": false,
                  "outputVar": "responseResult",
                  "api": {
                    "url": "/api/dev/${appName}/</#noparse>${apiPath}<#noparse>/commit",
                    "method": "post",
                    "data": {
                      "commitMsg": "${event.data.commitResult.value[0].commitMsg}"
                    }
                  },
                  "expression": "${!event.data.submitResult.error}"
                },
                {
                  "componentId": "</#noparse>${statusId!statusName!'statusService'}<#noparse>",
                  "actionType": "reload"
                },
                {
                  "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
                  "actionType": "reload"
                }
              ]
            }
          },
          "level": "primary",
          "icon": "fa fa-check",
          "disabledOnAction": true,
          "visibleOn": "${currentUserCanEdit !== false && lockState == 'OWNED'}",
          "disabledOn": "${</#noparse><#if editDisabledOn?? && editDisabledOn?has_content>${editDisabledOn}<#else>false</#if><#noparse>}"
        }
      ]
    }
  ],
  "onEvent": {
    "fetchInited": {
      "weight": 0,
      "actions": [
        {
          "actionType": "ajax",
          "ignoreError": true,
          "api": {
            "url": "/api/app/${appName}/edit-access-entry",
            "method": "get"
          }
        },
        {
          "actionType": "setValue",
          "componentId": "</#noparse>${statusId!statusName!'statusService'}<#noparse>",
          "expression": "responseResult.responseStatus == 0",
          "args": {
            "value": {
              "currentUserCanEdit": "${responseResult.responseData.currentUserCanEdit}"
            }
          }
        },
        {
          "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
          "actionType": "</#noparse>${ ((actionType!'static') == 'static')?then('nonstatic','enabled') }<#noparse>",
          "expression": "${event.data.lockState == 'OWNED'}"
        },
        {
          "componentId": "</#noparse>${formId!formName!'form'}<#noparse>",
          "actionType": "</#noparse>${ ((actionType!'static') == 'static')?then('static','disabled') }<#noparse>",
          "expression": "${event.data.lockState != 'OWNED'}"
        }
      ]
    }
  },
  "className": "flex items-center",
  "id": "</#noparse>${statusId!statusName!'statusService'}<#noparse>"
}</#noparse>
