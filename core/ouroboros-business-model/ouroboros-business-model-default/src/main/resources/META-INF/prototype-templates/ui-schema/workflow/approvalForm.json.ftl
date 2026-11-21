<#noparse>
{
  "type": "page",
  "initApi": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/init-form",
  "body": {
    "type": "form",
    "id": "default-approval-form",
    "mode": "horizontal",
    "labelAlign": "left",
    "wrapWithPanel": false,
    "body": [
      {
        "type": "fieldset",
        "title": "</#noparse>${title}信息<#noparse>",
        "collapsable": true,
        "className": "my-4",
        "body": [
          {
            "type": "combo",
            "label": false,
            "name": "formData",
            "multiLine": true,
            "subFormMode": "horizontal",
            "noBorder": true,
            "multiple": false,
            "static": true,
            "strictMode": false,
            "items": </#noparse>${Jsons.toJsonString(formItems)}<#noparse>
          }
        ]
      },
      {
        "type": "fieldset",
        "title": "审批意见",
        "collapsable": false,
        "className": "my-4",
        "body": [
          {
            "type": "textarea",
            "label": "审批意见",
            "name": "comment",
            "value": "",
            "minRows": 3,
            "maxRows": 20
          },
          {
            "type": "group",
            "body": [
              {
                "type": "user-picker",
                "label": "审批人",
                "name": "userId",
                "columnRatio": 6,
                "defaultCurrentUser": true,
                "disabled": true
              },
              {
                "type": "input-date",
                "label": "审批时间",
                "name": "datetime",
                "value": "${TODAY()}",
                "valueFormat": "YYYY-MM-DD",
                "clearable": false,
                "disabled": true
              }
            ]
          }
        ]
      },
      {
        "type": "fieldset",
        "title": "审批记录",
        "collapsable": true,
        "collapsed": true,
        "body": [
          {
            "type": "workflow-timeline",
            "instanceId": "${instanceId}"
          }
        ]
      }
    ]
  },
  "actions": [</#noparse><#if (workflow.actions.pass.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "同意",
              "level": "primary",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'PASS')}",
              "className": "approval-button approval-button-pass",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "ignoreError": false,
                      "actionType": "ajax",
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/PASS",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "success",
                        "msg": "提交成功"
                      }
                    },
                    {
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true,
                      "actionType": "reload"
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.advanceActions.freeRecover.isEnabled)!false><#noparse>
            {
              "type": "button",
              "label": "选择下个节点",
              "level": "primary",
              "className": "approval-button approval-button-free-recover",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'F_RECOVER')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "outputVar": "SelectResult",
                      "actionType": "dialog",
                      "dialog": {
                        "type": "dialog",
                        "url": "/workflow/common/select-recoverable-nodes",
                        "data": {
                          "instanceId": "$instanceId",
                          "taskId": "$taskId"
                        }
                      },
                      "waitForAction": true
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${!SelectResult.confirmed}"
                    },
                    {
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "actionType": "ajax",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/F_RECOVER",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}",
                          "targetTaskKey": "${SelectResult.value[0].targetTaskKey}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "success",
                        "msg": "提交成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.advanceActions.freePass.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "自由审批",
              "level": "primary",
              "className": "approval-button approval-button-free-pass",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'F_PASS')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "dialog",
                      "outputVar": "SelectResult",
                      "dialog": {
                        "type": "dialog",
                        "url": "/workflow/common/free-approval",
                        "data": {}
                      },
                      "waitForAction": true
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${!SelectResult.confirmed}"
                    },
                    {
                      "ignoreError": false,
                      "actionType": "ajax",
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/F_PASS",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}",
                          "newTask": "${SelectResult.value[0]}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "success",
                        "msg": "提交成功"
                      }
                    },
                    {
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true,
                      "actionType": "reload"
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.reject.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "驳回",
              "level": "enhance",
              "className": "text-danger border-danger approval-button approval-button-reject",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'REJECT')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "confirmDialog",
                      "dialog": {
                        "title": "确认",
                        "msg": "确认要驳回吗？此操作将会把任务退回到最近可退回的流程节点"
                      }
                    },
                    {
                      "actionType": "validate",
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "ajax",
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/REJECT",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "warning",
                        "msg": "驳回成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.advanceActions.freeReject.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "自由驳回",
              "level": "enhance",
              "className": "text-danger border-danger approval-button approval-button-free-reject",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'F_REJECT')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "outputVar": "SelectResult",
                      "actionType": "dialog",
                      "dialog": {
                        "type": "dialog",
                        "url": "/workflow/common/select-rejectable-nodes",
                        "data": {
                          "instanceId": "$instanceId",
                          "taskId": "$taskId"
                        }
                      },
                      "waitForAction": true
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${!SelectResult.confirmed}"
                    },
                    {
                      "ignoreError": false,
                      "actionType": "ajax",
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/F_REJECT",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}",
                          "targetTaskKey": "${SelectResult.value[0].targetTaskKey}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "warning",
                        "msg": "驳回成功"
                      }
                    },
                    {
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true,
                      "actionType": "reload"
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.sign.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "加签",
              "level": "enhance",
              "className": "approval-button approval-button-sign",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'SIGN')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "outputVar": "SelectResult",
                      "actionType": "dialog",
                      "dialog": {
                        "type": "dialog",
                        "url": "/workflow/common/add-signer",
                        "data": {}
                      },
                      "waitForAction": true
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${!SelectResult.confirmed}"
                    },
                    {
                      "ignoreError": false,
                      "actionType": "ajax",
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/SIGN",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}",
                          "assignUserId": "${SelectResult.value[0].assignUserId}",
                          "signType": "${SelectResult.value[0].signType}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "success",
                        "msg": "加签成功"
                      }
                    },
                    {
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true,
                      "actionType": "reload"
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.transfer.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "转办",
              "level": "enhance",
              "className": "approval-button approval-button-transfer",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'TRANSFER')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult",
                      "actionType": "validate"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "dialog",
                      "dialog": {
                        "type": "dialog",
                        "url": "/workflow/common/transfer-task",
                        "data": {}
                      },
                      "outputVar": "SelectResult",
                      "waitForAction": true
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${!SelectResult.confirmed}"
                    },
                    {
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "actionType": "ajax",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/TRANSFER",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}",
                          "assignUserId": "${SelectResult.value[0].assignUserId}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "success",
                        "msg": "转办成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.veto.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "否决",
              "level": "danger",
              "className": "approval-button approval-button-veto",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'VETO')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "confirmDialog",
                      "dialog": {
                        "title": "确认",
                        "msg": "确认要否决该流程吗？"
                      }
                    },
                    {
                      "actionType": "validate",
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "ajax",
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/VETO",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "warning",
                        "msg": "否决成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.cancel.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "撤销",
              "level": "warning",
              "className": "approval-button approval-button-cancel",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'CANCEL')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "confirmDialog",
                      "dialog": {
                        "title": "确认",
                        "msg": "确认要撤销该流程吗？此操作将取消整个流程。"
                      }
                    },
                    {
                      "actionType": "validate",
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "ajax",
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/CANCEL",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "warning",
                        "msg": "撤销成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if><#if (workflow.actions.revoke.isEnabled)!true><#noparse>
            {
              "type": "button",
              "label": "撤回",
              "level": "warning",
              "className": "approval-button approval-button-revoke",
              "visibleOn": "${ARRAYINCLUDES(taskInfo.actions, 'REVOKE')}",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "confirmDialog",
                      "dialog": {
                        "title": "确认",
                        "msg": "确认要撤回该流程吗？此操作将重新回到流程起点。"
                      }
                    },
                    {
                      "actionType": "validate",
                      "componentId": "default-approval-form",
                      "ignoreError": false,
                      "outputVar": "validateResult"
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${validateResult.error}"
                    },
                    {
                      "actionType": "ajax",
                      "ignoreError": false,
                      "outputVar": "responseResult",
                      "options": {},
                      "api": {
                        "url": "/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/REVOKE",
                        "method": "post",
                        "requestAdaptor": "",
                        "adaptor": "",
                        "messages": {},
                        "data": {
                          "&": "${OMIT(validateResult.payload, 'taskInfo')}"
                        },
                        "silent": true
                      }
                    },
                    {
                      "actionType": "toast",
                      "expression": "${responseResult.responseStatus !== 0}",
                      "args": {
                        "msgType": "error",
                        "msg": "提交失败"
                      }
                    },
                    {
                      "actionType": "stopPropagation",
                      "expression": "${responseResult.responseStatus !== 0}"
                    },
                    {
                      "actionType": "toast",
                      "args": {
                        "msgType": "warning",
                        "msg": "撤回成功"
                      }
                    },
                    {
                      "actionType": "reload",
                      "componentId": "default-crud-list",
                      "groupType": "component",
                      "ignoreError": true
                    },
                    {
                      "actionType": "closeDialog"
                    }
                  ]
                }
              }
            },</#noparse></#if>
            <#if showMode == 'dialog' || showMode == 'drawer'>
            {
              "type": "button",
              "label": "返回",
              "actionType": "cancel",
              "className": "approval-button approval-button-back"
            }
            <#elseif showMode == 'tab'>
            {
              "type": "button",
              "label": "返回",
              "className": "approval-button approval-button-back",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "closeTab"
                    }
                  ]
                }
              }
            }
            <#else>
            {
              "type": "button",
              "label": "返回",
              "actionType": "cancel",
              "className": "approval-button approval-button-back"
            }
            </#if>
            <#noparse>
  ]
}
</#noparse>
