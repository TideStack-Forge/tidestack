<#import "delete.subtable" as sub>
{
  "name": "${fullName!}",
  "description": "${description!}",
  "entryNode": "接受输入",
  "inputDataType": "any",
  "outputDataType": "any",
  "isEnabled": true,
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
      "name": "构建删除条件",
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
      "name": "执行前置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "where"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/delete'}/beforeScript",
      "language": "${beforeScriptLanguage!'javascript'}",
      "script": <#if beforeScript??>"${beforeScript?json_string}"<#else>null</#if>
    },
    {
      "name": "执行删除操作",
      "type": "deleteData",
      "inputPorts": [
        {}
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "returnDeletedData": true,
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
    <#list subTables!>
    <#items as subTable>
    <@sub.deleteSubTableNodes subTable=subTable/>
    </#items>
    <@sub.deleteSubTableMergeNode subTableCount=subTables?size/>
    </#list>
    {
      "name": "执行后置脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "deleted"
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
      "codeSourcePointer": "${sourcePointerPrefix!'/apis/delete'}/afterScript",
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
      "targetNode": "构建删除条件",
      "targetInputPort": 0
    },
    {
      "sourceNode": "构建删除条件",
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
      "targetNode": "执行删除操作",
      "targetInputPort": 0
    },
    <#list subTables!>
    {
      "sourceNode": "执行删除操作",
      "sourceOutputPort": 0,
      "targetNode": "合并结果",
      "targetInputPort": 0
    },
    <#items as subTable>
    <@sub.deleteSubTableConnections subTable=subTable index=subTable?counter softDelete=false/>
    </#items>
    {
      "sourceNode": "合并结果",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    <#else>
    {
      "sourceNode": "执行删除操作",
      "sourceOutputPort": 0,
      "targetNode": "执行后置脚本",
      "targetInputPort": 0
    },
    </#list>
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
  ]
}
