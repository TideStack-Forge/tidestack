{
  "name" : "${fullName}",
  "description" : "初始化${modelTitle}流程任务表单",
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
      "name" : "提取查询参数",
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
      "mapping" : {
        "params.id" : "params.id"
      }
    },
    {
      "name" : "获取业务数据",
      "description" : null,
      "type" : "callFlow",
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
      "authorityName" : "",
      "resultWrapperKey" : "formData",
      "executeForEachItem" : false,
      "flowName" : "${modelName}.getOne"
    },
    {
      "name" : "提取任务参数",
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
      "mapping" : {
        "instanceId" : "params.instanceId",
        "taskId" : "params.taskId"
      }
    },
    {
      "name" : "获取任务信息",
      "description" : null,
      "type" : "getTaskInfo",
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
      "name" : "合并结果",
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
      "targetNode" : "提取查询参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取查询参数",
      "sourceOutputPort" : 0,
      "targetNode" : "获取业务数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "合并结果",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "接受请求",
      "sourceOutputPort" : 0,
      "targetNode" : "提取任务参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取任务参数",
      "sourceOutputPort" : 0,
      "targetNode" : "获取任务信息",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "获取业务数据",
      "sourceOutputPort" : 0,
      "targetNode" : "合并结果",
      "targetInputPort" : 1
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "获取任务信息",
      "sourceOutputPort" : 0,
      "targetNode" : "合并结果",
      "targetInputPort" : 0
    }
  ],
  "isEnabled" : true
}
