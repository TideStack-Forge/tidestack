{
  "name": "${fullName!}",
  "description": "${description!}",
  "entryNode": "接受输入",
  "inputDataType": "any",
  "outputDataType": "any",
  "nodes": [
    {
      "name": "接受输入",
      "type": "mutex",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "name": "构建查询条件",
      "type": "mapping",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mapping": {
        "where.id": "params.id"
      }
    },
    {
      "name": "构建填充参数",
      "type": "const",
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
      "name": "构建查询对象",
      "type": "merge",
      "inputPorts": [
        {
          "name": "populate"
        },
        {
          "name": "where"
        }
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "name": "执行前置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "query"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/getOne'}/beforeScript",
      "language": "${beforeScriptLanguage!'javascript'}",
      "script": <#if beforeScript??>"${beforeScript?json_string}"<#else>null</#if>
    },
    {
      "name": "执行查询",
      "type": "getOneData",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
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
      "name": "执行后置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "record"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/getOne'}/afterScript",
      "language": "${afterScriptLanguage!'javascript'}",
      "script": <#if afterScript??>"${afterScript?json_string}"<#else>null</#if>
    },
    {
      "name": "判断是否存在",
      "type": "switch",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {},
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "conditions": [
        {
          "expression": "$0 != null"
        }
      ]
    },
    {
      "name": "返回404",
      "type": "ouroborosExceptions",
      "inputPorts": [
        {}
      ],
      "outputPorts": [],
      "isAsync": false,
      "isEnabled": true,
      "exceptionType": "BusinessException",
      "exceptionCode": 404,
      "exceptionMessage": "数据未找到"
    },
    {
      "name": "成功返回",
      "type": "return",
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
      "targetNode": "构建查询条件",
      "targetInputPort": 0
    },
    {
      "sourceNode": "构建填充参数",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 0
    },
    {
      "sourceNode": "构建查询条件",
      "sourceOutputPort": 0,
      "targetNode": "构建查询对象",
      "targetInputPort": 1
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
      "targetNode": "执行查询",
      "targetInputPort": 0
    },
    {
      "sourceNode": "执行查询",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    {
      "sourceNode": "接受输入",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 1
    },
    {
      "sourceNode": "执行后置脚本",
      "sourceOutputPort": 0,
      "targetNode": "判断是否存在",
      "targetInputPort": 0
    },
    {
      "sourceNode": "判断是否存在",
      "sourceOutputPort": 0,
      "targetNode": "成功返回",
      "targetInputPort": 0
    },
    {
      "sourceNode": "判断是否存在",
      "sourceOutputPort": 1,
      "targetNode": "返回404",
      "targetInputPort": 0
    }
  ],
  "isEnabled": true
}
