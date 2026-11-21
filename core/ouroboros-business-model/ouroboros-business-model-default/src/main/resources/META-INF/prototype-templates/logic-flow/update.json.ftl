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
      "name": "构建更新条件",
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
        "id": "params.id"
      }
    },
    {
      "name": "获取请求体",
      "type": "pick",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "key": "data"
    },
    {
      "name": "验证输入",
      "type": "validate",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "schema": ${Jsons.toJsonString(validationSchema!{})}
    },
    {
      "name": "构造更新查询",
      "type": "mapping",
      "inputPorts": [
        {},
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mapping": {
        "data": "$1",
        "where": "$0"
      }
    },
    {
      "name": "执行前置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "statement"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/update'}/beforeScript",
      "language": "${beforeScriptLanguage!'javascript'}",
      "script": <#if beforeScript??>"${beforeScript?json_string}"<#else>null</#if>
    },
    {
      "name": "执行更新操作",
      "type": "partialUpdateData",
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
      ],
      "returnUpdatedData": true
    },
    <#if childCollectionSyncPlans?has_content>
    {
      "name": "提取子表同步提交数据",
      "type": "pick",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "key": "data"
    },
    {
      "name": "同步子表",
      "type": "syncChildCollections",
      "inputPorts": [
        {
          "name": "persistedOwner"
        },
        {
          "name": "submittedOwner"
        }
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "mode": "replace",
      "plans": ${Jsons.toJsonString(childCollectionSyncPlans)}
    },
    {
      "name": "合并结果",
      "type": "merge",
      "inputPorts": [
        {},
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "deepMerge": false
    },
    </#if>
    {
      "name": "执行后置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "updated"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/update'}/afterScript",
      "language": "${afterScriptLanguage!'javascript'}",
      "script": <#if afterScript??>"${afterScript?json_string}"<#else>null</#if>
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
      "targetNode": "构建更新条件",
      "targetInputPort": 0
    },
    {
      "sourceNode": "接受输入",
      "sourceOutputPort": 0,
      "targetNode": "获取请求体",
      "targetInputPort": 0
    },
    {
      "sourceNode": "获取请求体",
      "sourceOutputPort": 0,
      "targetNode": "验证输入",
      "targetInputPort": 0
    },
    {
      "sourceNode": "构建更新条件",
      "sourceOutputPort": 0,
      "targetNode": "构造更新查询",
      "targetInputPort": 0
    },
    {
      "sourceNode": "验证输入",
      "sourceOutputPort": 0,
      "targetNode": "构造更新查询",
      "targetInputPort": 1
    },
    {
      "sourceNode": "构造更新查询",
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
      "targetNode": "执行更新操作",
      "targetInputPort": 0
    },
    <#if childCollectionSyncPlans?has_content>
    {
      "sourceNode": "执行更新操作",
      "sourceOutputPort": 0,
      "targetNode": "同步子表",
      "targetInputPort": 0
    },
    {
      "sourceNode": "执行前置脚本",
      "sourceOutputPort": 0,
      "targetNode": "提取子表同步提交数据",
      "targetInputPort": 0
    },
    {
      "sourceNode": "提取子表同步提交数据",
      "sourceOutputPort": 0,
      "targetNode": "同步子表",
      "targetInputPort": 1
    },
    {
      "sourceNode": "同步子表",
      "sourceOutputPort": 0,
      "targetNode": "合并结果",
      "targetInputPort": 1
    },
    {
      "sourceNode": "执行更新操作",
      "sourceOutputPort": 0,
      "targetNode": "合并结果",
      "targetInputPort": 0
    },
    {
      "sourceNode": "合并结果",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    <#else>
    {
      "sourceNode": "执行更新操作",
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
