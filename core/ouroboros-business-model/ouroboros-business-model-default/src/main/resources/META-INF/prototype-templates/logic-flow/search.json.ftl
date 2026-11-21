{
  "name": "${fullName!}",
  "description": "${description!}",
  "entryNode": "接受输入",
  "inputDataType": "any",
  "outputDataType": "any",
  "nodes": [
    {
      "type": "mutex",
      "name": "接受输入",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {
          "name": "request"
        }
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "type": "pick",
      "name": "提取请求参数",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {
          "name": "queryParams"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "key": "params"
    },
    {
      "type": "const",
      "name": "构建填充参数",
      "inputPorts": [],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "value": {
        "populate": <#if populate??>${Jsons.toJsonString(populate)}<#else>null</#if>
      }
    },
    {
      "type": "mapping",
      "name": "映射分页参数",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mapping": {
        "limit": "perPage != null ? Converter.toInteger(perPage) : null",
        "skip": "perPage != null && page != null ? ((Converter.toInteger(page) - 1) * Converter.toInteger(perPage)) : null"
      }
    },
    {
      "type": "const",
      "name": "默认排序",
      "inputPorts": [],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "value": <#if defaultOrder??>${Jsons.toJsonString(defaultOrder)}<#else>null</#if>
    },
    {
      "type": "mapping",
      "name": "映射排序参数",
      "inputPorts": [
        {},
        {
          "name": "default"
        }
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mapping": {
        "order": "($0.orderBy ?: '').isEmpty() ? $default : $0.orderBy + ' ' + (($0.orderDir ?: '').isEmpty() ? 'ASC' : $0.orderDir)"
      }
    },
    {
      "type": "mapping",
      "name": "映射查询条件",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mapping": {
        "where": "$0"
      }
    },
    {
      "type": "merge",
      "name": "构建查询对象",
      "inputPorts": [
        {
          "name": "pagination"
        },
        {
          "name": "order"
        },
        {
          "name": "where"
        },
        {
          "name": "populate"
        }
      ],
      "outputPorts": [
        {
          "name": "query"
        }
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "type": "script",
      "name": "执行前置脚本",
      "inputPorts": [
        {
          "name": "query"
        },
        {
          "name": "request"
        }
      ],
      "outputPorts": [
        {
          "name": "query"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/search'}/beforeScript",
      "language": "${beforeScriptLanguage!'javascript'}",
      "script": <#if beforeScript??>"${beforeScript?json_string}"<#else>null</#if>
    },
    {
      "type": "pagedSearchData",
      "name": "查询数据",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {
          "name": "recordList"
        }
      ],
      "isAsync": false,
      "isEnabled": true,
      "resultWrapperKey": "items",
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
    <#if workflowEnabled>
    {
      "name" : "附加工作流信息",
      "description" : "",
      "type" : "attachProcessInfo",
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
      "moduleName" : "${modelName!}",
      "businessRecordType": "${modelName!}",
      "modelPath": "${modelPath!}",
      "processId": "${processId}",
      "attachCurrentNodesInfo": true
    },</#if>
    {
      "type": "script",
      "name": "执行后置脚本",
      "inputPorts": [
        {
          "name": "result"
        },
        {
          "name": "request"
        }
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/search'}/afterScript",
      "language": "${afterScriptLanguage!'javascript'}",
      "script": <#if afterScript??>"${afterScript?json_string}"<#else>null</#if>
    },
    {
      "type": "return",
      "name": "成功返回",
      "inputPorts": [
        {}
      ],
      "outputPorts": [],
      "isAsync": false,
      "isEnabled": true
    }
  ],
  "connections": [
    {
      "sourceNode": "接受输入",
      "sourceOutputPort": 0,
      "targetNode": "提取请求参数",
      "targetInputPort": 0
    },
    {
      "sourceNode": "提取请求参数",
      "sourceOutputPort": 0,
      "targetNode": "映射分页参数",
      "targetInputPort": 0
    },
    {
      "sourceNode": "提取请求参数",
      "sourceOutputPort": 0,
      "targetNode": "映射排序参数",
      "targetInputPort": 0
    },
    {
      "sourceNode": "默认排序",
      "sourceOutputPort": 0,
      "targetNode": "映射排序参数",
      "targetInputPort": 1
    },
    {
      "sourceNode": "映射分页参数",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 0
    },
    {
      "sourceNode": "映射排序参数",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 1
    },
    {
      "sourceNode": "提取请求参数",
      "sourceOutputPort": 0,
      "targetNode": "映射查询条件",
      "targetInputPort": 0
    },
    {
      "sourceNode": "映射查询条件",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 2
    },
    {
      "sourceNode": "构建填充参数",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 3
    },
    {
      "sourceNode": "构建查询对象",
      "sourceOutputPort": 0,
      "targetNode": "执行前置脚本",
      "targetInputPort": 0
    },
    {
      "sourceNode": "接受输入",
      "sourceOutputPort": 0,
      "targetNode": "执行前置脚本",
      "targetInputPort": 1
    },
    {
      "sourceNode": "执行前置脚本",
      "sourceOutputPort": 0,
      "targetNode": "查询数据",
      "targetInputPort": 0
    },
    <#if workflowEnabled>
    {
      "sourceNode": "查询数据",
      "sourceOutputPort": 0,
      "targetNode": "附加工作流信息",
      "targetInputPort": 0
    },
    {
      "sourceNode": "附加工作流信息",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    <#else>
    {
      "sourceNode": "查询数据",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    </#if>
    {
      "sourceNode": "接受输入",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 1
    },
    {
      "sourceNode": "执行后置脚本",
      "sourceOutputPort": 0,
      "targetNode": "成功返回",
      "targetInputPort": 0
    }
  ],
  "isEnabled": true
}
