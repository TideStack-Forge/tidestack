<#macro deleteSubTableNodes subTable>
<#assign
  relationTitle = subTable.title
  relationName = subTable.name
  referenceKey = subTable.referenceKey
  key = subTable.key
  relationModelName = subTable.relatedModelName
>
  {
    "name": "构造${relationTitle}查询条件",
    "type": "mapping",
    "inputPorts": [
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "mappingEachItem": false,
    "mapping": {
      "where.${referenceKey}": "${key}",
      "select": "'id'"
    }
  },
  {
    "name": "查询${relationTitle}",
    "type": "searchData",
    "inputPorts": [
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "dataModel": "${relationModelName}"
  },
  {
    "name": "构造删除${relationTitle}参数",
    "type": "mapping",
    "inputPorts": [
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "mappingEachItem": true,
    "mapping": {
      "params.id": "$0.id"
    }
  },
  {
    "name": "调用删除${relationTitle}流程",
    "type": "callFlow",
    "inputPorts": [
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "executeForEachItem": true,
    "flowName": "${relationModelName}.delete",
    "resultWrapperKey": "${relationName}"
  },
</#macro>

<#macro deleteSubTableMergeNode subTableCount>
  {
    "name": "合并结果",
    "type": "merge",
    "inputPorts": [
      <#list 0..subTableCount as i>
      {}<#sep>,</#sep>
      </#list>
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "deepMerge": false
  },
</#macro>

<#macro deleteSubTableConnections subTable index softDelete>
<#assign relationTitle = subTable.title>
  {
    "sourceNode": "<#if softDelete!>执行更新操作<#else>执行删除操作</#if>",
    "sourceOutputPort": 0,
    "targetNode": "构造${relationTitle}查询条件",
    "targetInputPort": 0
  },
  {
    "sourceNode": "构造${relationTitle}查询条件",
    "sourceOutputPort": 0,
    "targetNode": "查询${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "查询${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "构造删除${relationTitle}参数",
    "targetInputPort": 0
  },
  {
    "sourceNode": "构造删除${relationTitle}参数",
    "sourceOutputPort": 0,
    "targetNode": "调用删除${relationTitle}流程",
    "targetInputPort": 0
  },
  {
    "sourceNode": "调用删除${relationTitle}流程",
    "sourceOutputPort": 0,
    "targetNode": "合并结果",
    "targetInputPort": ${index}
  },
</#macro>