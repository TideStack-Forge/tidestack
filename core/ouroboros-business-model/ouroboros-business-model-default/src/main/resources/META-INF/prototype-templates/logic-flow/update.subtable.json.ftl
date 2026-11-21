<#macro updateSubTableNodes subTable>
<#assign
  relationTitle = subTable.title
  relationName = subTable.name
  referenceKey = subTable.referenceKey
  key = subTable.key
  relationModelName = subTable.relatedModelName
>
  {
    "name": "提取${relationTitle}数据",
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
    "name": "判断是否存在${relationTitle}",
    "description": null,
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
        "expression": "$0.containsKey('${relationName}')"
      }
    ]
  },
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
    "name": "构造${relationTitle}查询条件",
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
    "mappingEachItem": false,
    "mapping": {
      "where.${referenceKey}": "$0.${key}"
    }
  },
  {
    "name": "搜索${relationTitle}",
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
    "name": "新增的${relationTitle}",
    "type": "subtract",
    "inputPorts": [
      {},
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "isEqualExpr": "$0.id == $1.id"
  },
  {
    "name": "删除的${relationTitle}",
    "type": "subtract",
    "inputPorts": [
      {},
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "isEqualExpr": "$1.id == $0.id"
  },
  {
    "name": "更新的${relationTitle}",
    "type": "intersect",
    "inputPorts": [
      {},
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true,
    "isEqualExpr": "$0.${key} == $1.${key}"
  },
  {
    "name": "准备新增${relationTitle}数据",
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
      "data.${referenceKey}": "$1.${key}"
    }
  },
  {
    "name": "准备删除${relationTitle}参数",
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
    "name": "准备更新${relationTitle}参数",
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
      "params.id": "$0.id",
      "data": "$0"
    }
  },
  {
    "name": "调用新增${relationTitle}流程",
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
    "flowName": "${relationModelName}.add"
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
    "flowName": "${relationModelName}.delete"
  },
  {
    "name": "调用更新${relationTitle}流程",
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
    "flowName": "${relationModelName}.update"
  },
  {
    "name": "合并${relationTitle}更新结果",
    "type": "concat",
    "inputPorts": [
      {},
      {}
    ],
    "outputPorts": [
      {}
    ],
    "isAsync": false,
    "isEnabled": true
  },
  {
    "name": "映射${relationTitle}更新结果",
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
    "mappingEachItem": false,
    "mapping": {
      "${relationName}": "$0"
    }
  },
  {
    "name": "汇集${relationTitle}",
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
</#macro>

<#macro updateSubTableMergeNode subTableCount>
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

<#macro updateSubTableConnections subTable index>
<#assign relationTitle = subTable.title>
  {
    "sourceNode": "执行前置脚本",
    "sourceOutputPort": 0,
    "targetNode": "提取${relationTitle}数据",
    "targetInputPort": 0
  },
  {
    "sourceNode": "提取${relationTitle}数据",
    "sourceOutputPort": 0,
    "targetNode": "判断是否存在${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "判断是否存在${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "提取${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "判断是否存在${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "构造${relationTitle}查询条件",
    "targetInputPort": 0
  },
  {
    "sourceNode": "判断是否存在${relationTitle}",
    "sourceOutputPort": 1,
    "targetNode": "汇集${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "执行更新操作",
    "sourceOutputPort": 0,
    "targetNode": "构造${relationTitle}查询条件",
    "targetInputPort": 1
  },

  {
    "sourceNode": "构造${relationTitle}查询条件",
    "sourceOutputPort": 0,
    "targetNode": "搜索${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "提取${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "新增的${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "搜索${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "新增的${relationTitle}",
    "targetInputPort": 1
  },
  {
    "sourceNode": "搜索${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "删除的${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "提取${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "删除的${relationTitle}",
    "targetInputPort": 1
  },
  {
    "sourceNode": "提取${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "更新的${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "搜索${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "更新的${relationTitle}",
    "targetInputPort": 1
  },
  {
    "sourceNode": "新增的${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "准备新增${relationTitle}数据",
    "targetInputPort": 0
  },
  {
    "sourceNode": "判断是否存在${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "准备新增${relationTitle}数据",
    "targetInputPort": 1
  },
  {
    "sourceNode": "删除的${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "准备删除${relationTitle}参数",
    "targetInputPort": 0
  },
  {
    "sourceNode": "更新的${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "准备更新${relationTitle}参数",
    "targetInputPort": 0
  },
  {
    "sourceNode": "准备新增${relationTitle}数据",
    "sourceOutputPort": 0,
    "targetNode": "调用新增${relationTitle}流程",
    "targetInputPort": 0
  },
  {
    "sourceNode": "准备删除${relationTitle}参数",
    "sourceOutputPort": 0,
    "targetNode": "调用删除${relationTitle}流程",
    "targetInputPort": 0
  },
  {
    "sourceNode": "准备更新${relationTitle}参数",
    "sourceOutputPort": 0,
    "targetNode": "调用更新${relationTitle}流程",
    "targetInputPort": 0
  },
  {
    "sourceNode": "调用新增${relationTitle}流程",
    "sourceOutputPort": 0,
    "targetNode": "合并${relationTitle}更新结果",
    "targetInputPort": 0
  },
  {
    "sourceNode": "调用更新${relationTitle}流程",
    "sourceOutputPort": 0,
    "targetNode": "合并${relationTitle}更新结果",
    "targetInputPort": 1
  },
  {
    "sourceNode": "合并${relationTitle}更新结果",
    "sourceOutputPort": 0,
    "targetNode": "映射${relationTitle}更新结果",
    "targetInputPort": 0
  },
  {
    "sourceNode": "调用删除${relationTitle}流程",
    "sourceOutputPort": 0,
    "targetNode": "映射${relationTitle}更新结果",
    "targetInputPort": 1
  },
  {
    "sourceNode": "映射${relationTitle}更新结果",
    "sourceOutputPort": 0,
    "targetNode": "汇集${relationTitle}",
    "targetInputPort": 0
  },
  {
    "sourceNode": "汇集${relationTitle}",
    "sourceOutputPort": 0,
    "targetNode": "合并结果",
    "targetInputPort": ${index}
  },
</#macro>