<#assign processingStatusCode=((statusMap.processing.code)!'')?replace("'", "\\'")>
<#assign suspendedStatusCode=((statusMap.suspended.code)!'')?replace("'", "\\'")>
<#assign withdrawnStatusCode=((statusMap.withdrawn.code)!'')?replace("'", "\\'")>
<#assign approvedStatusCode=((statusMap.approved.code)!'')?replace("'", "\\'")>
<#assign vetoStatusCode=((statusMap.veto.code)!'')?replace("'", "\\'")>
<#assign canceledStatusCode=((statusMap.canceled.code)!'')?replace("'", "\\'")>
<#assign terminatedStatusCode=((statusMap.terminated.code)!'')?replace("'", "\\'")>
<#assign statusCodeScript>
def payload = $payload
def outcome = null
def eventType = null
def status = null

if (payload instanceof Map) {
  outcome = payload.get('outcome')
  eventType = payload.get('eventType')
  status = payload.get('${statusField}')
} else if (payload != null && payload.metaClass?.hasProperty(payload, '${statusField}')) {
  if (payload.metaClass?.hasProperty(payload, 'outcome')) {
    outcome = payload.outcome
  }
  if (payload.metaClass?.hasProperty(payload, 'eventType')) {
    eventType = payload.eventType
  }
  status = payload."${statusField}"
}

if (eventType == 'STARTED') {
  return '${processingStatusCode}'
}
if (eventType == 'SUSPENDED' || outcome == 'suspended') {
  return '${suspendedStatusCode}'
}
if (eventType == 'RESUMED') {
  return '${processingStatusCode}'
}
if (outcome == 'approved' || eventType == 'APPROVED') {
  return '${approvedStatusCode}'
}
if (outcome == 'rejected' || eventType == 'REJECTED') {
  return '${vetoStatusCode}'
}
if (outcome == 'withdrawn' || outcome == 'withdraw' || eventType == 'WITHDRAWN') {
  return '${withdrawnStatusCode}'
}
if (outcome == 'canceled' || outcome == 'cancelled' || eventType == 'CANCELLED') {
  return '${canceledStatusCode}'
}
if (outcome == 'terminated' || eventType == 'TERMINATED') {
  return '${terminatedStatusCode}'
}
if (eventType == 'FAILED') {
  return '${canceledStatusCode}'
}

if (status instanceof Map) {
  return status.get('code')
}
if (status != null && status.metaClass?.hasProperty(status, 'code')) {
  return status.code
}
return status
</#assign>
{
  "name" : "${fullName}",
  "description" : "${modelTitle}流程回调",
  "entryNode" : "接受请求",
  "inputDataType" : "any",
  "outputDataType" : "any",
  "nodes" : [
    {
      "name" : "接受请求",
      "description" : null,
      "type" : "mutex",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true
    },
    {
      "name" : "提取流程状态代码",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "payload",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : "${statusCodeScript?json_string}"
    },
    {
      "name" : "流程状态路由",
      "description" : null,
      "type" : "switch",
      "inputPorts" : [
        {
          "name" : "payload",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "statusCode",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "conditions" : [
        {
          "name" : "审批中",
          "expression" : "$statusCode == '${processingStatusCode}'"
        },
        {
          "name" : "已暂停",
          "expression" : "$statusCode == '${suspendedStatusCode}'"
        },
        {
          "name" : "已撤回",
          "expression" : "$statusCode == '${withdrawnStatusCode}'"
        },
        {
          "name" : "通过",
          "expression" : "$statusCode == '${approvedStatusCode}'"
        },
        {
          "name" : "否决",
          "expression" : "$statusCode == '${vetoStatusCode}'"
        },
        {
          "name" : "撤销",
          "expression" : "$statusCode == '${canceledStatusCode}'"
        },
        {
          "name" : "终止",
          "expression" : "$statusCode == '${terminatedStatusCode}'"
        }
      ]
    },
    {
      "name" : "审批中",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "已暂停",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "已撤回",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "通过",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "否决",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "撤销",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "终止",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "其他",
      "description" : null,
      "type" : "script",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "language" : "groovy",
      "script" : ""
    },
    {
      "name" : "准备状态更新参数",
      "description" : null,
      "type" : "mapping",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "statusCode",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "mappingEachItem" : false,
      "mapping" : {
        "where" : "$businessContext.recordId",
        "data.${statusField}" : "$statusCode"
      }
    },
    {
      "name" : "更新业务状态",
      "description" : null,
      "type" : "partialUpdateData",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "dataModel": "${modelName}",
      "plugins": [],
      "returnUpdatedData": true
    },
    {
      "name" : "成功返回",
      "description" : null,
      "type" : "return",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        }
      ],
      "outputPorts" : [],
      "isAsync" : false,
      "isEnabled" : true
    }
  ],
  "connections" : [
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "接受请求",
      "sourceOutputPort" : 0,
      "targetNode" : "提取流程状态代码",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "接受请求",
      "sourceOutputPort" : 0,
      "targetNode" : "流程状态路由",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取流程状态代码",
      "sourceOutputPort" : 0,
      "targetNode" : "流程状态路由",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 0,
      "targetNode" : "审批中",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 1,
      "targetNode" : "已暂停",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 2,
      "targetNode" : "已撤回",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 3,
      "targetNode" : "通过",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 4,
      "targetNode" : "否决",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 5,
      "targetNode" : "撤销",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 6,
      "targetNode" : "终止",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "流程状态路由",
      "sourceOutputPort" : 7,
      "targetNode" : "其他",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "审批中",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "已暂停",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "已撤回",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "通过",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "否决",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "撤销",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "终止",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取流程状态代码",
      "sourceOutputPort" : 0,
      "targetNode" : "准备状态更新参数",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "准备状态更新参数",
      "sourceOutputPort" : 0,
      "targetNode" : "更新业务状态",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "更新业务状态",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "其他",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    }
  ],
  "isEnabled" : true
}
