{
  "name": "${fullName}",
  "description": "${description!'脚本逻辑模型'}",
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
    <#if modelName??>
    {
      "name": "获取数据模型",
      "type": "dataModel",
      "language": "groovy",
      "inputPorts": [],
      "outputPorts": [
        {}
      ],
      "dataModel": "${modelName!}"
    },
    </#if>
    {
      "name": "执行脚本",
      "type": "script",
      "inputPorts": [
        {
          "name": "${inputName!'request'}"
        }<#if modelName??>,
        {
          "name": "dataModel"
        }</#if>
      ],
      "outputPorts": [
        {}
      ],
      "isAsync": false,
      "isEnabled": true,
      "codeSourcePointer": "${sourcePointerPrefix!''}/script",
      "language": "${scriptLanguage!'javascript'}",
      "script": <#if script??>"${script?json_string}"<#else>null</#if>
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
      "targetNode": "执行脚本",
      "targetInputPort": 0
    }<#if modelName??>,
    {
      "sourceNode": "获取数据模型",
      "sourceOutputPort": 0,
      "targetNode": "执行脚本",
      "targetInputPort": 1
    }</#if>,
    {
      "sourceNode": "执行脚本",
      "sourceOutputPort": 0,
      "targetNode": "成功返回",
      "targetInputPort": 0
    }
  ],
  "isEnabled": true
}
