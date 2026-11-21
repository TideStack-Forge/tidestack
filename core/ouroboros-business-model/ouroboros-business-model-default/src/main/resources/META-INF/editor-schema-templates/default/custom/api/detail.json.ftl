<#import "../../apis/lib" as apisLib>

<#noparse>
{
  "visibleOn": "${api.isEnabled}",
  "type": "container",
  "body": [
    {
      "type": "divider"
    },
    {
      "type": "button-group-select",
      "name": "api.httpMethod",
      "label": "请求方法",
      "options": [
        "GET",
        "POST",
        "PUT",
        "PATCH",
        "DELETE"
      ]
    },
    {
      "type": "input-text",
      "name": "api.url",
      "label": "接口地址",
      "prefix": "${api.url[0] == '/' ? '/api' : '/api/'}${(api.url[0] == '/' ? '' : fullName) | kebabCasePath:false}${api.url[0] == '/' ? '' : '/'}",
      "placeholder": "",
      "required": true
    },
    {
      "type": "business-action-reference",
      "name": "api",
      "label": false,
      "appName": "${appName}",
      "visibleOn": "${formatVersion === '2.0'}"
    },
    {
      "type": "json-data-editor",
      "name": "api.actionInputMapping",
      "codeContext": { "codeSite": "business-model.http-mapping", "codeResourceType": "business-model",
        "codePointer": "/custom/*/api/actionInputMapping", "language": "spel",
        "intelligenceApi": "/api/dev/${appName}/code-intelligence/context" },
      "label": "HTTP 输入映射",
      "visibleOn": "${formatVersion === '2.0' && api.implementationMode === 'ACTION'}",
      "allowValueTypes": "object",
      "description": "映射值使用平台 EL；例如 params.record.id 取 request.params.id，data 取 request.body。完整同步 request 仍可由执行上下文访问；映射不会裁剪 request。"
    },
    {
      "type": "container",
      "visibleOn": "${formatVersion !== '2.0'}",
      "body": [
    {
      "type": "switch",
      "name": "api.isGenerateImplementation",
      "label": "生成接口实现",
      "description": "开启时由平台生成逻辑流实现，并默认通过同名业务 Action 执行；关闭时由高代码接口实现，是否进入业务 Action 由下方选项决定。"
    },
    {
      "type": "select",
      "name": "api.isBindAction",
      "label": "绑定同名业务 Action",
      "placeholder": "未显式设置：按生成接口实现推导",
      "joinValues": false,
      "extractValue": true,
      "options": [{"label": "绑定", "value": true}, {"label": "不绑定", "value": false}],
      "description": "开启：该功能的 HTTP URL 与手动 invoke 共用同名 Action，并经过 BusinessActionRuntime；高代码实现需使用 @BusinessActionBinding 或正式 Handler。关闭：由高代码 @RequestMapping/@WebRequestMapping 直接处理请求，不经过业务模型 Action；请确保对应 Controller 已提供实现。"
    },
    {
      "type": "wrapper",
      "className": "p-0",
      "visibleOn": "${api.isGenerateImplementation === true}",
      "body": [
        {
          "type": "divider"
        },
        {
          "type": "switch",
          "name": "api.isCustomLogicFlow",
          "label": "自定义逻辑流"
        },
        {
          "type": "wrapper",
          "className": "p-0",
          "hiddenOn": "${api.isCustomLogicFlow}",
          "body": [
            </#noparse><@apisLib.scriptSetting scriptName="api.script" initializeLanguage=false /><#noparse>
          ]
        },
        {
          "type": "fieldSet",
          "title": "自定义逻辑流",
          "visibleOn": "${api.isCustomLogicFlow}",
          "disabledOn": "${ readOnly }",
          "body": [
            {
              "type": "button-toolbar",
              "buttons": [
                {
                  "type": "button",
                  "label": "设计逻辑流",
                  "level": "primary",
                  "size": "lg",
                  "onEvent": {
                    "click": {
                      "actions": [
                        {
                          "actionType": "ajax",
                          "outputVar": "result",
                          "ignoreError": true,
                          "api": {
                            "url": "/api/dev/${appName}/logic-flow/${fullName}.${name}",
                            "method": "get",
                            "messages": {
                              "success": "",
                              "failed": ""
                            }
                          }
                        },
                        {
                          "actionType": "openOrSwitchTab",
                          "expression": "${result.responseData}",
                          "args": {
                            "url": "/dev/${appName}/logicflow-model/${responseData.name}",
                            "title": "编辑逻辑流「${responseData.name}」"
                          }
                        },
                        {
                          "actionType": "ajax",
                          "expression": "${ !result.responseData }",
                          "outputVar": "addResult",
                          "api": {
                            "method": "post",
                            "url": "/api/dev/${appName}/logic-flow",
                            "data": {
                              "name": "${fullName}.${name}",
                              "description": "${title}"
                            }
                          }
                        },
                        {
                          "actionType": "openTab",
                          "expression": "${ addResult && addResult.responseData }",
                          "args": {
                            "url": "/dev/${appName}/logicflow-model/${responseData.name}",
                            "title": "编辑逻辑流「${responseData.name}」"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
          ]
        }
      ]
    },
    </#noparse><#include "action-contract"><#noparse>
      ]
    }
  ]
}
</#noparse>
