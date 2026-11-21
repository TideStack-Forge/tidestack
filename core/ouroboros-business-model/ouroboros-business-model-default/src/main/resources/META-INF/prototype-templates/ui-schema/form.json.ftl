{
  "type": "form",
  "title": <#if title??>"${title}"<#else>false</#if>,
  <#if workflowEnabled!false>"id": "default-add-edit-form",</#if>
  <#-- 布局 -->
  "mode": "${formMode!"horizontal"}",
  <#if (formMode!"horizontal") == "horizontal">
  "labelWidth": ${labelWidth!120},
  </#if>
  <#if (layoutType!"horizontal") == "column">
  "columnCount": ${columnCount!1},
  </#if>
  <#-- 接口 -->
  <#if api??>
  "api": ${Jsons.toJsonString(api)},
  </#if>
  <#-- 初始化接口 -->
  <#if initApi??>
  "initApi": ${Jsons.toJsonString(initApi)},
  </#if>
  <#-- 表单项 -->
  "body": [
  <#list body as item>
    ${Jsons.toJsonString(item)}<#sep>,</#sep>
  </#list>
  ],
  <#-- 事件 -->
  "onEvent": {
    "submitSucc": {
      "actions": [
        <#--  如果是tab模式则提交后关闭当前tab页  -->
        <#if showMode! == "tab">
        {
          "actionType": "sendToOpenerTab",
          "args": {
            "msg": true
          }
        },
        {
          "actionType": "closeTab",
          "args": {
            "result": true
          }
        }
        </#if>
      ]
    }
  },
  <#if (actions![])?has_content>
  "actions": ${Jsons.toJsonString(actions!)},
  <#elseif static??>
  "actions": [],
  </#if>
  <#-- 其他 -->
  "messages": {
    "saveSuccess": "保存成功"
  },
  "static": ${(static!false)?c}
}
