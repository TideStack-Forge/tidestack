<#macro addSubTableNodes subTable>
<#assign
  relationTitle = subTable.title
  relationName = subTable.name
  referenceKey = subTable.referenceKey
  key = subTable.key
  relationModelName = subTable.relatedModelName
>
  {
    "name": "提取${relationTitle}",
    "type": "pick",
    "inputPorts": [
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "key": "${relationName}"
  },
  {
    "name": "映射${relationTitle}",
    "description": "追加主表Key",
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
    "mappingEachItem": true,
    "mapping": {
      "data": "$0",
      "data.${referenceKey}": "$parentContext.$1.${key}"
    }
  },
  {
    "name": "调用添加${relationTitle}流程",
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
    "flowName": "${relationModelName}.add",
    "resultWrapperKey": "${relationName}"
  },
</#macro>

<#macro addSubTableMergeNode subTableCount>  
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

<#macro addSubTableConnections subTable index>
<#assign relationTitle = subTable.title>
  {
    "sourceNode": "执行前置脚本",
    "sourceOutputPort": 0,
    "targetNode": "提取${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "提取${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "映射${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "执行插入操作",
    "sourceOutputPort": 0,
    "targetNode": "映射${relationTitle}",
    "targetInputPort": 1
  },
  {
    "sourceNode": "映射${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "调用添加${relationTitle}流程",
    "targetInputPort": 0
  },
  {
    "sourceNode": "调用添加${relationTitle}流程",
    "sourceOutputPort": 0,
    "targetNode": "合并结果",
    "targetInputPort": ${index}
  },
</#macro>