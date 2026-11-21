{
  "name": "${fullName}",
  "description": "开始${modelTitle}工作流",
  "entryNode": "接受请求",
  "inputDataType": "any",
  "outputDataType": "any",
  "nodes": [
    {
      "name": "接受请求",
      "description": null,
      "type": "mutex",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "name": "过滤发起请求业务数据",
      "description": null,
      "type": "script",
      "inputPorts": [
        {
          "name": "payload",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "language": "groovy",
      "script": "def request = payload instanceof Map ? payload : [:]\ndef source = request.data instanceof Map ? request.data : [:]\nif (source.formData instanceof Map) {\n  return new java.util.LinkedHashMap(source.formData)\n}\ndef formData = new java.util.LinkedHashMap(source)\nformData.remove('taskAssignee')\nformData.remove('cc')\nformData.remove('approvers')\nformData.remove('idempotencyKey')\nreturn formData\n"
    },
    {
      "name": "准备更新参数",
      "description": null,
      "type": "mapping",
      "inputPorts": [
        {
          "name": "request",
          "description": "",
          "dataType": "any"
        },
        {
          "name": "formData",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "mappingEachItem": false,
      "mapping": {
        "where.id": "$request.params.id",
        "set": "$formData",
        "set.${statusField}": "'${pendingStatus}'"
      }
    },
    {
      "name": "更新业务数据",
      "description": null,
      "type": "partialUpdateData",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "dataModel": "${modelName!}",
      "plugins": [],
      "returnUpdatedData": true
    },
    {
      "name": "映射流程表单数据",
      "description": null,
      "type": "mapping",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "mappingEachItem": false,
      "codeSourcePointer": "/workflow/formDataMapping",
      "mapping" : <#if formDataMapping??>${Jsons.toJsonString(formDataMapping)}
      <#else>{
        "&": "$0"
      }
      </#if>
    },
    {
      "name": "映射流程参数",
      "description": null,
      "type": "mapping",
      "inputPorts": [
        {
          "name": "formData",
          "description": "",
          "dataType": "any"
        },
        {
          "name": "bizData",
          "description": "",
          "dataType": "any"
        },
        {
          "name": "startRequest",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "mappingEachItem": false,
      "mapping": {
        "initiator": "AuthContext.userId",
        "formData": "$formData",
        "taskAssignee": "$startRequest.data.taskAssignee",
        "cc": "$startRequest.data.cc",
        "approvers": "$startRequest.data.approvers",
        "idempotencyKey": "$startRequest.params.idempotencyKey != null ? $startRequest.params.idempotencyKey : $startRequest.data.idempotencyKey",
        "callbackFlow": "'${callbackFlow}'",
        "businessRecordType": "'${modelName}'",
        "businessRecordId": "$bizData.id",
        "approvalUrl": "'${modelPath}' + $bizData.id + '/approval'"<#if sendEditUrl>,
        "editUrl": "'${modelPath}edit?id=' + $bizData.id"</#if>
      }
    },
    {
      "name": "发起流程",
      "description": null,
      "type": "startProcess",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "moduleName": "${modelName}",
      "processId": "${processId}"
    },
    {
      "name": "执行后置脚本",
      "description": null,
      "type": "script",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "language": "groovy",
      "script": ""
    },
    {
      "name": "成功返回",
      "description": null,
      "type": "return",
      "inputPorts": [
        {
          "name": "",
          "description": "",
          "dataType": "any"
        }
      ],
      "outputPorts": [],
      "isAsync": false,
      "isEnabled": true
    }
  ],
  "connections": [
    {
      "label": null,
      "description": null,
      "sourceNode": "接受请求",
      "sourceOutputPort": 0,
      "targetNode": "过滤发起请求业务数据",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "接受请求",
      "sourceOutputPort": 0,
      "targetNode": "准备更新参数",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "过滤发起请求业务数据",
      "sourceOutputPort": 0,
      "targetNode": "准备更新参数",
      "targetInputPort": 1
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "更新业务数据",
      "sourceOutputPort": 0,
      "targetNode": "映射流程表单数据",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "映射流程表单数据",
      "sourceOutputPort": 0,
      "targetNode": "映射流程参数",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "更新业务数据",
      "sourceOutputPort": 0,
      "targetNode": "映射流程参数",
      "targetInputPort": 1
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "接受请求",
      "sourceOutputPort": 0,
      "targetNode": "映射流程参数",
      "targetInputPort": 2
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "映射流程参数",
      "sourceOutputPort": 0,
      "targetNode": "发起流程",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "发起流程",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "准备更新参数",
      "sourceOutputPort": 0,
      "targetNode": "更新业务数据",
      "targetInputPort": 0
    },
    {
      "label": null,
      "description": null,
      "sourceNode": "执行后置脚本",
      "sourceOutputPort": 0,
      "targetNode": "成功返回",
      "targetInputPort": 0
    }
  ],
  "isEnabled": true
}
