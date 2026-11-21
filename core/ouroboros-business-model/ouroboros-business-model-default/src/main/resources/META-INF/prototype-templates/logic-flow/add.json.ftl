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
      "inputPorts": [],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true
    },
    {
      "name": "提取输入内容",
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
      "name": "执行前置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "data"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/add'}/beforeScript",
      "language": "${beforeScriptLanguage!'javascript'}",
      "script": <#if beforeScript??>"${beforeScript?json_string}"<#else>null</#if>
    },
    {
      "name": "执行插入操作",
      "type": "insertData",
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
    <#if childCollectionSyncPlans?has_content>
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
      "mode": "create",
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
          "name": "data"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/add'}/afterScript",
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
      "targetNode": "提取输入内容",
      "targetInputPort": 0
    },
    {
      "sourceNode": "提取输入内容",
      "sourceOutputPort": 0,
      "targetNode": "验证输入",
      "targetInputPort": 0
    },
    {
      "sourceNode": "验证输入",
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
      "targetNode": "执行插入操作",
      "targetInputPort": 0
    },
    <#if childCollectionSyncPlans?has_content>
    {
      "sourceNode": "执行插入操作",
      "sourceOutputPort": 0,
      "targetNode": "同步子表",
      "targetInputPort": 0
    },
    {
      "sourceNode": "执行前置脚本",
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
      "sourceNode": "执行插入操作",
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
      "sourceNode": "执行插入操作",
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
