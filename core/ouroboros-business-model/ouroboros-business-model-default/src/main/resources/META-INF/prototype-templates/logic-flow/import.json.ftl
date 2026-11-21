{
  "name": "${fullName!}",
  "description" : "${modelTitle}数据导入",
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
      "name" : "读取Excel文件",
      "description" : null,
      "type" : "readExcel",
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
      "excelSourcePath" : "data.file"
    },
    {
      "name" : "映射Excel数据",
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
      "mappingEachItem" : true,
      "codeSourcePointer": "/features/import/dataMap",
      "mapping" : ${Jsons.toJsonString(dataMapping)}
    },
    {
      "name" : "批量插入数据",
      "description" : null,
      "type" : "batchInsertData",
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
      ],
      "batchSize" : 200
    },
    {
      "name" : "成功返回",
      "type" : "return",
      "inputPorts" : [
        {}
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
      "targetNode" : "读取Excel文件",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "读取Excel文件",
      "sourceOutputPort" : 0,
      "targetNode" : "映射Excel数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "映射Excel数据",
      "sourceOutputPort" : 0,
      "targetNode" : "批量插入数据",
      "targetInputPort" : 0
    },
    {
      "label" : null,
      "description" : null,
      "sourceNode" : "批量插入数据",
      "sourceOutputPort" : 0,
      "targetNode" : "成功返回",
      "targetInputPort" : 0
    }
  ],
  "isEnabled" : true
}
