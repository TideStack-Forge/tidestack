{
  "name" : "${fullName}",
  "description" : "提交${modelTitle}流程任务",
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
          "name" : "request",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "formData",
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
      "name" : "准备更新参数",
      "description" : null,
      "type" : "mapping",
      "inputPorts" : [
        {
          "name" : "request",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "formData",
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
        "data" : "$formData",
        "where" : "$request.params.id"
      }
    },
    {
      "name" : "过滤业务更新表单数据",
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
      "script" : "${businessUpdateFormDataScript?json_string}"
    },
    {
      "name" : "更新业务表",
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
      "name" : "映射流程表单数据",
      "description" : null,
      "type" : "mapping",
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
      "mappingEachItem" : false,
      "codeSourcePointer": "/workflow/formDataMapping",
      "mapping" : <#if formDataMapping??>${Jsons.toJsonString(formDataMapping)}
      <#else>{
        "&": "$0"
      }
      </#if>
    },
    {
      "name" : "提交工作流任务",
      "description" : null,
      "type" : "submitTask",
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
      "moduleName" : "${modelName}"
    },
    {
      "name" : "等待任务提交后更新参数",
      "description" : null,
      "type" : "mapping",
      "inputPorts" : [
        {
          "name" : "statement",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "taskResult",
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
        "data" : "$statement.data",
        "where" : "$statement.where"
      }
    },
    {
      "name" : "映射任务参数",
      "description" : null,
      "type" : "mapping",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "any"
        },
        {
          "name" : "formData",
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
        "instanceId" : "$0.params.instanceId",
        "taskId" : "$0.params.taskId",
        "action" : "$0.params.ACTION",
        "formData" : "$formData"
      }
    },
    {
      "name" : "提取请求体",
      "description" : null,
      "type" : "pick",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "map"
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
      "pickEachItem" : false,
      "key" : "data"
    },
    {
      "name" : "合并参数",
      "description" : null,
      "type" : "merge",
      "inputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "map"
        },
        {
          "name" : "",
          "description" : "",
          "dataType" : "map"
        }
      ],
      "outputPorts" : [
        {
          "name" : "",
          "description" : "",
          "dataType" : "map"
        }
      ],
      "isAsync" : false,
      "isEnabled" : true,
      "deepMerge" : false
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
      "targetNode" : "准备更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "准备更新参数",
      "sourceOutputPort" : 0,
      "targetNode" : "等待任务提交后更新参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提交工作流任务",
      "sourceOutputPort" : 0,
      "targetNode" : "等待任务提交后更新参数",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "等待任务提交后更新参数",
      "sourceOutputPort" : 0,
      "targetNode" : "更新业务表",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "更新业务表",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "过滤业务更新表单数据",
      "sourceOutputPort" : 0,
      "targetNode" : "映射流程表单数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "映射流程表单数据",
      "sourceOutputPort" : 0,
      "targetNode" : "映射任务参数",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "接受请求",
      "sourceOutputPort" : 0,
      "targetNode" : "映射任务参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "接受请求",
      "sourceOutputPort" : 0,
      "targetNode" : "提取请求体",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取请求体",
      "sourceOutputPort" : 0,
      "targetNode" : "过滤业务更新表单数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "过滤业务更新表单数据",
      "sourceOutputPort" : 0,
      "targetNode" : "准备更新参数",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取请求体",
      "sourceOutputPort" : 0,
      "targetNode" : "合并参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "映射任务参数",
      "sourceOutputPort" : 0,
      "targetNode" : "合并参数",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "合并参数",
      "sourceOutputPort" : 0,
      "targetNode" : "提交工作流任务",
      "targetInputPort" : 0
    }
  ],
  "isEnabled" : true
}
