<#noparse>
{
  "title": "流程绑定",
  "mode": "horizontal",
  "icon": "fas fa-link",
  "tab": [
    {
      "type": "alert",
      "title": "审批流程未启用",
      "body": "启用后可绑定默认流程，并继续配置流程发起与处理入口。",
      "level": "info",
      "showIcon": true,
      "visibleOn": "${ !workflow.isEnabled }",
      "className": "mb-5"
    },
    {
      "type": "input-group",
      "label": "绑定流程标识",
      "visibleOn": "${ workflow.isEnabled }",
      "body": [
            {
              "type": "input-text",
              "name": "workflow.bindings[0].processId",
              "requiredOn": "${ workflow.isEnabled }",
              "clearable": true,
              "placeholder": "输入或选择流程标识"
            },
            {
              "type": "button",
              "label": "创建流程",
              "level": "primary",
              "actionType": "dialog",
              "dialog": {
                "title": "新建默认审批流程",
                "closeOnEsc": true,
                "closeOnOutside": false,
                "body": {
                  "type": "form",
                  "name": "create-default-approval-workflow-form",
                  "title": false,
                  "wrapWithPanel": false,
                  "mode": "horizontal",
                  "body": [
                    {
                      "type": "input-text",
                      "name": "workflowDefinitionName",
                      "label": "流程标识",
                      "required": true,
                      "clearable": false
                    },
                    {
                      "type": "input-text",
                      "name": "workflowDefinitionTitle",
                      "label": "流程名称",
                      "required": true
                    },
                    {
                      "type": "textarea",
                      "name": "workflowDefinitionDescription",
                      "label": "流程描述",
                      "minRows": 3,
                      "maxRows": 6
                    },
                    {
                      "type": "hidden",
                      "name": "formType",
                      "value": "business-model"
                    },
                    {
                      "type": "hidden",
                      "name": "formId",
                      "value": "${fullName}"
                    },
                    {
                      "type": "button-toolbar",
                      "label": false,
                      "className": "mt-4 text-right",
                      "buttons": [
                        {
                          "type": "button",
                          "actionType": "closeDialog",
                          "label": "取消"
                        },
                        {
                          "type": "button",
                          "label": "创建并编辑",
                          "level": "primary",
                          "mergeData": true,
                          "onEvent": {
                            "click": {
                              "actions": [
                                {
                                  "actionType": "validate",
                                  "componentName": "create-default-approval-workflow-form",
                                  "outputVar": "validateResult"
                                },
                                {
                                  "actionType": "stopPropagation",
                                  "expression": "${validateResult.error}"
                                },
                                {
                                  "actionType": "ajax",
                                  "outputVar": "createResult",
                                  "api": {
                                    "url": "/api/dev/${appName}/approval-workflow-definition",
                                    "method": "post",
                                    "messages": {},
                                    "silent": true,
                                    "data": {
                                      "formatVersion": 1,
                                      "name": "${workflowDefinitionName}",
                                      "title": "${workflowDefinitionTitle}",
                                      "description": "${workflowDefinitionDescription || ''}",
                                      "version": 1,
                                      "enabled": true,
                                      "form": {
                                        "formType": "business-model",
                                        "formId": "${formId}"
                                      },
                                      "nodes": [
                                        {
                                          "type": "StartNode",
                                          "name": "发起节点",
                                          "id": "${workflowDefinitionName}-start",
                                          "permission": []
                                        },
                                        {
                                          "type": "ApprovalNode",
                                          "name": "审批节点",
                                          "id": "${workflowDefinitionName}-approve",
                                          "subType": "单签",
                                          "candidates": [],
                                          "permission": [],
                                          "actions": []
                                        },
                                        {
                                          "type": "EndNode",
                                          "name": "结束",
                                          "id": "${workflowDefinitionName}-end"
                                        }
                                      ],
                                      "edges": [
                                        {
                                          "id": "${workflowDefinitionName}-edge-start-approve",
                                          "type": "SEQUENCE",
                                          "sourceId": "${workflowDefinitionName}-start",
                                          "targetId": "${workflowDefinitionName}-approve"
                                        },
                                        {
                                          "id": "${workflowDefinitionName}-edge-approve-end",
                                          "type": "SEQUENCE",
                                          "sourceId": "${workflowDefinitionName}-approve",
                                          "targetId": "${workflowDefinitionName}-end"
                                        }
                                      ]
                                    }
                                  }
                                },
                                {
                                  "actionType": "toast",
                                  "expression": "${createResult.responseStatus !== 0}",
                                  "args": {
                                    "msgType": "error",
                                    "msg": "默认审批流程创建失败"
                                  }
                                },
                                {
                                  "actionType": "stopPropagation",
                                  "expression": "${createResult.responseStatus !== 0}"
                                },
                                {
                                  "actionType": "setValue",
                                  "componentName": "edit-business-model-form",
                                  "args": {
                                    "value": {
                                      "workflow.isEnabled": true,
                                      "workflow.bindings[0].key": "default",
                                      "workflow.bindings[0].processId": "${workflowDefinitionName}",
                                      "workflow.bindings[0].trigger": "manual"
                                    }
                                  }
                                },
                                {
                                  "actionType": "setValue",
                                  "componentName": "edit-business-model-form-status",
                                  "args": {
                                    "value": {
                                      "dirty": true
                                    }
                                  }
                                },
                                {
                                  "actionType": "broadcast",
                                  "args": {
                                    "eventName": "approvalWorkflowDefinitionSaved"
                                  },
                                  "data": {
                                    "definitionId": "${workflowDefinitionName}",
                                    "definitionName": "${workflowDefinitionTitle}",
                                    "bindingWritebackSuggested": true
                                  }
                                },
                                {
                                  "actionType": "openOrSwitchTab",
                                  "args": {
                                    "url": "/dev/${appName}/approval-workflow-definition/${workflowDefinitionName}",
                                    "title": "编辑审批流「${workflowDefinitionTitle}」",
                                    "receiveActions": [
                                      {
                                        "actionType": "setValue",
                                        "componentName": "edit-business-model-form",
                                        "args": {
                                          "value": {
                                            "workflow.isEnabled": true,
                                            "workflow.bindings[0].key": "default",
                                            "workflow.bindings[0].processId": "${definitionId}",
                                            "workflow.bindings[0].trigger": "manual"
                                          }
                                        }
                                      },
                                      {
                                        "actionType": "setValue",
                                        "componentName": "edit-business-model-form-status",
                                        "args": {
                                          "value": {
                                            "dirty": true
                                          }
                                        }
                                      },
                                      {
                                        "actionType": "broadcast",
                                        "args": {
                                          "eventName": "approvalWorkflowDefinitionSaved"
                                        },
                                        "data": {
                                          "definitionId": "${definitionId}",
                                          "definitionName": "${definitionName}",
                                          "bindingWritebackSuggested": "${bindingWritebackSuggested}"
                                        }
                                      }
                                    ],
                                    "params": {
                                      "formType": "business-model",
                                      "formId": "${formId}",
                                      "formName": "${workflowDefinitionTitle}",
                                      "sourceLayer": "default",
                                      "returnTarget": "/dev/${appName}/business-model/${fullName}"
                                    }
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
                    }
                  ],
                  "actions": []
                },
                "actions": []
              }
            },
            {
              "type": "button",
              "label": "选择现有流程",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "dialog",
                      "dialog": {
                        "type": "dialog",
                        "title": "选择默认流程",
                        "size": "lg",
                        "showCloseButton": true,
                        "closeOnEsc": true,
                        "closeOnOutside": false,
                        "body": {
                          "type": "crud",
                          "mode": "table",
                          "name": "defaultApprovalWorkflowPicker",
                          "rowClassNameExpr": "${(name || id) === workflow.bindings[0].processId ? 'bg-blue-50' : ''}",
                          "syncLocation": false,
                          "api": {
                            "url": "/api/dev/${appName}/approval-workflow-definition",
                            "method": "get",
                            "responseData": {
                              "items": {
                                "$items": {
                                  "&": "$model",
                                  "creationTime": "$creationTime",
                                  "lastModifiedTime": "$lastModifiedTime",
                                  "lastCommittedTime": "$lastCommittedTime",
                                  "isLocked": "$isLocked",
                                  "lockUser": "$lockUser",
                                  "lockUserName": "$lockUserName",
                                  "isChanged": "$isChanged",
                                  "lockState": "$lockState",
                                  "versionState": "$versionState",
                                  "size": "${ size | bytes }"
                                }
                              }
                            }
                          },
                          "perPageAvailable": [10],
                          "filter": {
                            "title": "",
                            "body": [
                              {
                                "type": "group",
                                "body": [
                                  {
                                    "type": "input-text",
                                    "name": "keyword",
                                    "label": "名称或标识",
                                    "clearable": true,
                                    "placeholder": "请输入流程名称或标识",
                                    "size": "md"
                                  }
                                ]
                              }
                            ],
                            "actions": [
                              {
                                "type": "reset",
                                "label": "重置"
                              },
                              {
                                "type": "submit",
                                "label": "查询",
                                "level": "primary"
                              }
                            ]
                          },
                          "headerToolbar": [
                            {
                              "type": "button",
                              "label": "刷新",
                              "icon": "fa fa-refresh",
                              "actionType": "reload"
                            }
                          ],
                          "footerToolbar": [
                            "switch-per-page",
                            "pagination"
                          ],
                          "columns": [
                            {
                              "name": "title",
                              "label": "流程名称"
                            },
                            {
                              "name": "name",
                              "label": "流程标识"
                            },
                            {
                              "type": "tpl",
                              "label": "绑定状态",
                              "tpl": "${(name || id) === workflow.bindings[0].processId ? '当前绑定' : ''}"
                            },
                            {
                              "name": "description",
                              "label": "流程描述"
                            },
                            {
                              "type": "operation",
                              "label": "操作",
                              "buttons": [
                                {
                                  "type": "button",
                                  "label": "选择",
                                  "level": "link",
                                  "onEvent": {
                                    "click": {
                                      "actions": [
                                        {
                                          "actionType": "setValue",
                                          "componentName": "edit-business-model-form",
                                          "args": {
                                            "value": {
                                              "workflow.isEnabled": true,
                                              "workflow.bindings[0].key": "default",
                                              "workflow.bindings[0].processId": "${name || id}",
                                              "workflow.bindings[0].trigger": "manual"
                                            }
                                          }
                                        },
                                        {
                                          "actionType": "setValue",
                                          "componentName": "edit-business-model-form-status",
                                          "args": {
                                            "value": {
                                              "dirty": true
                                            }
                                          }
                                        },
                                        {
                                          "actionType": "broadcast",
                                          "args": {
                                            "eventName": "approvalWorkflowDefinitionSaved"
                                          },
                                          "data": {
                                            "definitionId": "${name || id}",
                                            "definitionName": "${title || name || id}",
                                            "bindingWritebackSuggested": true
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
                            }
                          ]
                        },
                        "actions": [
                          {
                            "type": "button",
                            "actionType": "close",
                            "label": "取消"
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            },
            {
              "type": "button",
              "label": "打开流程编辑器",
              "level": "link",
              "visibleOn": "${ workflow.bindings[0].processId }",
              "onEvent": {
                "click": {
                  "actions": [
                    {
                      "actionType": "openOrSwitchTab",
                      "args": {
                        "url": "/dev/${appName}/approval-workflow-definition/${workflow.bindings[0].processId}",
                        "title": "编辑审批流「${workflow.bindings[0].processId}」",
                        "receiveActions": [
                          {
                            "actionType": "setValue",
                            "componentName": "edit-business-model-form",
                            "args": {
                              "value": {
                                "workflow.isEnabled": true,
                                "workflow.bindings[0].key": "default",
                                "workflow.bindings[0].processId": "${definitionId}",
                                "workflow.bindings[0].trigger": "manual"
                              }
                            }
                          },
                          {
                            "actionType": "setValue",
                            "componentName": "edit-business-model-form-status",
                            "args": {
                              "value": {
                                "dirty": true
                              }
                            }
                          },
                          {
                            "actionType": "broadcast",
                            "args": {
                              "eventName": "approvalWorkflowDefinitionSaved"
                            },
                            "data": {
                              "definitionId": "${definitionId}",
                              "definitionName": "${definitionName}",
                              "bindingWritebackSuggested": "${bindingWritebackSuggested}"
                            }
                          }
                        ],
                        "params": {
                          "formType": "business-model",
                          "formId": "${fullName}",
                          "formName": "${title || fullName}",
                          "boundProcessId": "${workflow.bindings[0].processId}",
                          "sourceLayer": "default",
                          "returnTarget": "/dev/${appName}/business-model/${fullName}"
                        }
                      }
                    }
                  ]
                }
              }
            }
          ]
        },
    {
      "type": "input-text",
      "label": "状态字段",
      "name": "workflow.bindings[0].statusField",
      "source": "${ fields | filter:name:notIn:['id'] | pick:name }",
      "requiredOn": "${ workflow.isEnabled }"
    },
    {
      "type": "fieldSet",
      "title": "流程状态映射",
      "body": [
        {
          "type": "combo",
          "label": "未提交",
          "name": "workflow.bindings[0].statusMap.draft",
          "strictMode": false,
          "labelRemark": {
            "shape": "circle",
            "content": "表单内容还未正式提交，可以自由编辑的状态"
          },
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-gray-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "已提交",
          "name": "workflow.bindings[0].statusMap.submitted",
          "labelRemark": {
            "shape": "circle",
            "content": "表单内容正式提交，可以发送至工作流的状态"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-cyan-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "待开始",
          "name": "workflow.bindings[0].statusMap.pending",
          "labelRemark": {
            "shape": "circle",
            "content": "已发起流程实例，流程实例尚未开始运行的状态（该状态通常只会持续几秒钟）"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-yellow-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "审批中",
          "name": "workflow.bindings[0].statusMap.processing",
          "labelRemark": {
            "shape": "circle",
            "content": "流程实例正在运行中的状态"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-blue-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "已暂停",
          "name": "workflow.bindings[0].statusMap.suspended",
          "labelRemark": {
            "shape": "circle",
            "content": "流程实例暂时停止推进，可以恢复或撤回"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-orange-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "已撤回",
          "name": "workflow.bindings[0].statusMap.withdrawn",
          "labelRemark": {
            "shape": "circle",
            "content": "发起人撤回本轮审批，业务单可修改并重新提交"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-orange-100 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "通过",
          "name": "workflow.bindings[0].statusMap.approved",
          "labelRemark": {
            "shape": "circle",
            "content": "流程引擎正常结束，表示审批通过"
          },
          "strictMode": false,
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-green-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "否决",
          "name": "workflow.bindings[0].statusMap.veto",
          "strictMode": false,
          "labelRemark": {
            "shape": "circle",
            "content": "流程被否决，流程实例强制结束"
          },
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-red-100 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "撤销",
          "name": "workflow.bindings[0].statusMap.canceled",
          "strictMode": false,
          "labelRemark": {
            "shape": "circle",
            "content": "流程被撤销，流程实例强制结束"
          },
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-red-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        },
        {
          "type": "combo",
          "label": "终止",
          "name": "workflow.bindings[0].statusMap.terminated",
          "strictMode": false,
          "labelRemark": {
            "shape": "circle",
            "content": "流程被管理员终止，流程强制结束"
          },
          "items": [
            {
              "type": "input-text",
              "label": "状态码",
              "name": "code",
              "requiredOn": "${ workflow.isEnabled }"
            },
            {
              "type": "input-text",
              "label": "显示文字",
              "name": "label",
              "requiredOn": "${ workflow.isEnabled }"
            }
          ],
          "className": "bg-purple-50 py-3 my-0 pl-2",
          "labelClassName": "font-bold",
          "itemClassName": "bg-none",
          "mode": "horizontal",
          "subFormMode": "normal"
        }
      ]
    }
  ]
}
</#noparse>
