<#noparse>
{
  "type": "panel",
  "title": "行为契约",
  "body": [
    {
      "type": "business-behavior-catalog",
      "name": "behaviorCatalog",
      "label": false,
      "appName": "${appName}",
      "definitionSchemas": {
        "action": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "name"
            ],
            "properties": {
              "name": {
                "type": "string",
                "minLength": 1
              },
              "title": {
                "type": "string"
              },
              "description": {
                "type": "string"
              },
              "isEnabled": {
                "type": "boolean"
              },
              "readOnly": {
                "type": "boolean"
              },
              "inputSchema": {
                "type": "object"
              },
              "outputSchema": {
                "type": "object"
              },
              "precondition": {
                "type": "object",
                "required": [
                  "expression"
                ],
                "additionalProperties": false,
                "properties": {
                  "expression": {
                    "type": "string"
                  },
                  "message": {
                    "type": "string",
                    "minLength": 1
                  }
                }
              },
              "binding": {
                "type": "object",
                "required": [
                  "kind"
                ],
                "properties": {
                  "kind": {
                    "enum": [
                      "handler",
                      "logicflow"
                    ]
                  },
                  "handlerName": {
                    "type": "string"
                  },
                  "flowName": {
                    "type": "string"
                  },
                  "inputMapping": {
                    "type": "object"
                  },
                  "outputMapping": {},
                  "requiresHttpRequest": {
                    "type": "boolean"
                  }
                }
              },
              "emits": {
                "type": "array",
                "items": {
                  "type": "object",
                  "required": [
                    "modelFullName",
                    "eventName"
                  ],
                  "properties": {
                    "modelFullName": {
                      "type": "string"
                    },
                    "eventName": {
                      "type": "string"
                    }
                  }
                }
              }
            }
          }
        },
        "event": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "name"
            ],
            "properties": {
              "name": {
                "type": "string",
                "minLength": 1
              },
              "title": {
                "type": "string"
              },
              "description": {
                "type": "string"
              },
              "occurrenceCondition": {
                "type": "string"
              },
              "subject": {
                "enum": [
                  "model",
                  "instance"
                ]
              },
              "payloadSchema": {
                "type": "object"
              },
              "allowedProducerKinds": {
                "type": "array",
                "items": {
                  "type": "string"
                }
              },
              "requireUnfilteredModelRead": {
                "type": "boolean"
              }
            }
          }
        },
        "subscription": {
          "type": "array",
          "items": {
            "type": "object",
            "required": [
              "name"
            ],
            "properties": {
              "name": {
                "type": "string",
                "pattern": "\\S",
                "description": "订阅在消费模型内的标识。"
              },
              "title": {
                "type": "string"
              },
              "description": {
                "type": "string"
              },
              "isEnabled": {
                "type": [
                  "boolean",
                  "null"
                ],
                "description": "缺省启用；停用后已有任务阻塞，须显式重驱。"
              },
              "sourceEvent": {
                "type": "object",
                "properties": {
                  "modelFullName": {
                    "type": "string"
                  },
                  "eventName": {
                    "type": "string"
                  }
                }
              },
              "filter": {
                "type": [
                  "boolean",
                  "string",
                  "null"
                ]
              },
              "executionMode": {
                "type": "string",
                "enum": [
                  "JOIN_SOURCE_TRANSACTION",
                  "AFTER_COMMIT_TRANSACTIONAL",
                  "AFTER_COMMIT_INDEPENDENT"
                ],
                "description": "同步加入源事务、提交后独立事务、提交后由监听器保障外部副作用。责任创建时固定。"
              },
              "target": {
                "oneOf": [
                  {
                    "type": "object",
                    "additionalProperties": false,
                    "required": [
                      "kind",
                      "modelFullName",
                      "actionName"
                    ],
                    "properties": {
                      "kind": {
                        "const": "action"
                      },
                      "modelFullName": {
                        "type": "string",
                        "pattern": "\\S"
                      },
                      "actionName": {
                        "type": "string",
                        "pattern": "\\S"
                      },
                      "inputMapping": {
                        "type": "object"
                      }
                    }
                  },
                  {
                    "type": "object",
                    "additionalProperties": false,
                    "required": [
                      "kind",
                      "listenerName"
                    ],
                    "properties": {
                      "kind": {
                        "const": "listener"
                      },
                      "listenerName": {
                        "type": "string",
                        "pattern": "\\S"
                      },
                      "config": {
                        "type": "object",
                        "description": "监听器的静态 JSON 配置。"
                      },
                      "inputMapping": {
                        "type": "object",
                        "description": "依据监听器输入 Schema，将事件映射为动态输入。"
                      }
                    }
                  }
                ]
              }
            }
          }
        }
      },
      "diagnostics": [
        {
          "type": "button",
          "label": "检查或调用已发布 动作",
          "actionType": "dialog",
          "dialog": {
            "title": "已发布 动作",
            "size": "lg",
            "closeOnEsc": true,
            "body": {
              "type": "form",
              "closeDialogOnSubmit": false,
              "data": {
                "behaviorModel": "${fullName || (namespace ? namespace + '.' + name : name)}",
                "behaviorApiBase": "${behaviorApiBase}"
              },
              "body": [
                {
                  "type": "input-text",
                  "name": "behaviorApiBase",
                  "label": "目标应用 API 地址",
                  "required": true,
                  "description": "如 /api/tunnel/现有会话ID/api，或同源应用的 /api。末尾不带斜杠；请先通过现有运行环境入口建立连接并登录目标应用。"
                },
                {
                  "type": "input-password",
                  "name": "behaviorAccessToken",
                  "label": "目标应用访问令牌",
                  "description": "可选。跨应用时填写目标应用登录返回的 accessToken；仅用于当前弹窗，不写入业务模型。"
                },
                {
                  "type": "tpl",
                  "tpl": "此处使用已成功发布的契约。检查可用性不执行动作；执行会产生真实业务写入。"
                },
                {
                  "type": "input-text",
                  "name": "behaviorModel",
                  "label": "模型",
                  "required": true,
                  "readOnly": true
                },
                {
                  "type": "select",
                  "name": "actionName",
                  "label": "动作",
                  "required": true,
                  "labelField": "title",
                  "valueField": "name",
                  "source": {
                    "method": "get",
                    "url": "${behaviorApiBase}/business-model-behaviors/${behaviorModel}/actions",
                    "responseData": {
                      "options": "${items}"
                    },
                    "sendOn": "${!!behaviorApiBase}",
                    "headers": {
                      "Authorization": "${behaviorAccessToken ? 'Bearer ' + behaviorAccessToken : 'Bearer ' + getAccessToken()}"
                    }
                  }
                },
                {
                  "type": "json-data-editor",
                  "name": "input",
                  "label": "输入",
                  "allowValueTypes": "object",
                  "value": {},
                  "jsonSchema": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "params": {},
                      "data": {}
                    }
                  }
                }
              ],
              "feedback": {
                "title": "调用结果",
                "body": {
                  "type": "json",
                  "source": "${behaviorResult}"
                }
              },
              "actions": [
                {
                  "type": "button",
                  "label": "关闭",
                  "actionType": "cancel"
                },
                {
                  "type": "submit",
                  "label": "检查可用性",
                  "api": {
                    "method": "post",
                    "url": "${behaviorApiBase}/business-model-behaviors/${behaviorModel}/actions/${actionName}/availability",
                    "data": {
                      "input": "${input}"
                    },
                    "responseData": {
                      "behaviorResult": "$$"
                    },
                    "headers": {
                      "Authorization": "${behaviorAccessToken ? 'Bearer ' + behaviorAccessToken : 'Bearer ' + getAccessToken()}"
                    }
                  }
                },
                {
                  "type": "submit",
                  "label": "执行 动作",
                  "level": "primary",
                  "confirmText": "确认使用当前输入执行该 动作？",
                  "visibleOn": "${!!actionName}",
                  "api": {
                    "method": "post",
                    "url": "${behaviorApiBase}/business-model-behaviors/${behaviorModel}/actions/${actionName}/invoke",
                    "data": {
                      "input": "${input}"
                    },
                    "responseData": {
                      "behaviorResult": "$$"
                    },
                    "headers": {
                      "Authorization": "${behaviorAccessToken ? 'Bearer ' + behaviorAccessToken : 'Bearer ' + getAccessToken()}"
                    }
                  }
                }
              ]
            }
          }
        },
        {
          "type": "button",
          "label": "查询事件交付",
          "actionType": "dialog",
          "dialog": {
            "title": "事件交付诊断",
            "size": "lg",
            "closeOnEsc": true,
            "body": {
              "type": "form",
              "closeDialogOnSubmit": false,
              "body": [
                {
                  "type": "input-text",
                  "name": "behaviorApiBase",
                  "label": "目标应用 API 地址",
                  "required": true,
                  "description": "如 /api/tunnel/现有会话ID/api，或同源应用的 /api。末尾不带斜杠；请先通过现有运行环境入口建立连接并登录目标应用。"
                },
                {
                  "type": "input-password",
                  "name": "behaviorAccessToken",
                  "label": "目标应用访问令牌",
                  "description": "可选。跨应用时填写目标应用登录返回的 accessToken；仅用于当前弹窗，不写入业务模型。"
                },
                {
                  "type": "input-text",
                  "name": "eventId",
                  "label": "事件 ID",
                  "required": true
                }
              ],
              "api": {
                "method": "get",
                "url": "${behaviorApiBase}/business-events/${eventId}/deliveries",
                "responseData": {
                  "deliveryResult": "$$"
                },
                "headers": {
                  "Authorization": "${behaviorAccessToken ? 'Bearer ' + behaviorAccessToken : 'Bearer ' + getAccessToken()}"
                }
              },
              "actions": [
                {
                  "type": "button",
                  "label": "关闭",
                  "actionType": "cancel"
                },
                {
                  "type": "submit",
                  "label": "查询"
                }
              ],
              "feedback": {
                "title": "交付结果",
                "size": "lg",
                "body": [
                  {
                    "type": "json",
                    "source": "${deliveryResult.fact}"
                  },
                  {
                    "type": "table",
                    "source": "${deliveryResult.deliveries}",
                    "columns": [
                      {
                        "name": "deliveryId",
                        "label": "交付 ID"
                      },
                      {
                        "name": "subscriptionName",
                        "label": "订阅"
                      },
                      {
                        "name": "status",
                        "label": "状态"
                      },
                      {
                        "name": "attempt",
                        "label": "尝试次数"
                      },
                      {
                        "name": "diagnosticCode",
                        "label": "阻塞或失败原因"
                      },
                      {
                        "type": "operation",
                        "label": "操作",
                        "buttons": [
                          {
                            "type": "button",
                            "label": "重驱",
                            "visibleOn": "${deliveryResult.canRedrive && (status === 'AUTHORIZATION_BLOCKED' || status === 'CONTRACT_BLOCKED' || status === 'PRECONDITION_BLOCKED' || status === 'DEAD')}",
                            "actionType": "ajax",
                            "confirmText": "确认已修复阻塞原因并恢复此交付责任？",
                            "api": {
                              "method": "post",
                              "url": "${behaviorApiBase}/business-event-deliveries/${deliveryId}/redrive",
                              "headers": {
                                "Authorization": "${behaviorAccessToken ? 'Bearer ' + behaviorAccessToken : 'Bearer ' + getAccessToken()}"
                              }
                            },
                            "messages": {
                              "success": "重驱请求已完成，请重新查询确认状态"
                            }
                          }
                        ]
                      }
                    ]
                  }
                ]
              },
              "data": {
                "behaviorApiBase": "${behaviorApiBase}"
              }
            }
          }
        }
      ]
    }
  ]
}
</#noparse>
