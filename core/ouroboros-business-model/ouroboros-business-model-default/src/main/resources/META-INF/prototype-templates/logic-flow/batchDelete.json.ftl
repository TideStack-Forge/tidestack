{
  "name": "${fullName!}",
  "description" : "${modelTitle}批量删除",
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
      "name" : "映射参数",
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
        "ids" : "data.ids"
      }
    },
    {
      "name" : "提取参数",
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
      "key" : "ids"
    },
    {
      "name" : "批量删除数据",
      "description" : null,
      "type" : "deleteData",
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
      "dataModel": "${modelName!}",
      "plugins": [
        <#list modelPluginDescriptors! as plugin>
          {
            "name": "${plugin.name}",
            "config": <#if plugin.config??>${Jsons.toJsonString(plugin.config)}<#else>null</#if>
          }<#sep>,</#sep>
        </#list>
      ]
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
      "targetNode" : "映射参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "映射参数",
      "sourceOutputPort" : 0,
      "targetNode" : "提取参数",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "提取参数",
      "sourceOutputPort" : 0,
      "targetNode" : "批量删除数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "批量删除数据",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    }
  ],
  "isEnabled" : true
}
