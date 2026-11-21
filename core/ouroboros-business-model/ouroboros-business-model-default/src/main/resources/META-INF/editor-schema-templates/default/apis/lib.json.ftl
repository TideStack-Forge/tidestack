<#import "../projection-editor" as projection>
<#macro showLogicFlowButton logicFlowName logicFlowTitle>
{
  "type": "fieldSet", "title": "查看逻辑流", "collapsable": true, "collapsed": false,
  "body": [
    {
      "type": "button", "label": "查看逻辑流", "name": "preview-${logicFlowName}-logic-button",
      "id": "preview-${logicFlowName}-logic-button", "level": "primary", "disabledTip": "请先保存模型",
      "actionType": "dialog",
      "dialog": {
        "title": "预览${logicFlowTitle}接口逻辑流", "size": "full",
        "body": {
          "type": "service", "className": "h-full",
          "api": { "url": "<#noparse>/api/dev/${appName}/business-model/${fullName}/projection-target?kind=logicflow&name=${fullName}.</#noparse>${logicFlowName}" },
          "body": [
            { "type": "logic-flow-editor", "name": "model", "label": false, "static": true, "className": "h-full",
              "nodeDefineApi": "<#noparse>/api/dev/${appName}/logicflow/node-define</#noparse>" },
            <@projection.editActions cloneEndpoint="clone-logicflow-model"/>
          ]
        },
        "actions": [{ "type": "button", "label": "关闭", "actionType": "close" }],
        "closeOnEsc": true, "closeOnOutside": true
      }
    },
    {
      "type": "service",
      "onEvent": {
        "businessModelChanged": { "actions": [{ "actionType": "disabled", "componentId": "preview-${logicFlowName}-logic-button" }] },
        "businessModelSaved": { "actions": [{ "actionType": "enabled", "componentId": "preview-${logicFlowName}-logic-button" }] }
      }
    }
  ]
}
</#macro>

<#macro scriptSetting scriptName initializeLanguage=true>
{
  "type": "list-select",
  "name": "${scriptName}Language",
  "label": false,
  "options": [
    {
      "label": "JavaScript",
      "value": "javascript"
    },
    {
      "label": "Groovy",
      "value": "groovy"
    }
  ]<#if initializeLanguage>,
  "value": "javascript"</#if>
},
{
  "type": "script-editor",
  "name": "${scriptName}",
  "label": false,
  "language": <#noparse>"${</#noparse> ${scriptName}Language }",
  "codeResourceType": "business-model",
  "codeSite": "business-model.api-script",
  "codePointer": "<#if scriptName == 'api.script'>/custom/*/api/script<#else>/${scriptName?replace('.', '/')}</#if>",
  "intelligenceApi": <#noparse>"/api/dev/${appName}/code-intelligence/context"</#noparse>
}
</#macro>

<#macro defaultModelMetaProvider>
{
  "source": {
    "method": "get",
    "url": "<#noparse>/api/dev/${appName}/business-model-helper/${modelRef}/model-meta</#noparse>",
    "data": {
      "appName": "<#noparse>${appName}</#noparse>",
      "modelRef": "<#noparse>${modelRef}</#noparse>"
    }
  },
  "modelRefKey": "fullName",
  "requestData": {
    "appName": "<#noparse>${appName}</#noparse>"
  },
  "paths": {
    "model": "data.model",
    "fields": "data.fields",
    "relations": "data.relations"
  },
  "fieldMapping": {
    "name": "name",
    "label": "label",
    "type": "type",
    "options": "options",
    "optionsSource": "optionsSource"
  },
  "relationMapping": {
    "name": "name",
    "label": "label",
    "targetModel": "targetModel",
    "targetModelLabel": "targetModelLabel",
    "type": "type"
  }
}
</#macro>
