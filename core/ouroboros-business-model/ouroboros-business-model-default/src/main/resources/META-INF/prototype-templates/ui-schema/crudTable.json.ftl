{
  "type": "crud",
  "mode": "table",
  <#if workflowEnabled!false>"id": "default-crud-list",</#if>
  <#-- 表格显示配置 -->
  "affixHeader": ${(affixHeader!false)?c},
  <#-- API 配置 -->
  <#if autoFillHeight!false>
  "autoFillHeight": true,
  </#if>
  <#if api??>
    "api": ${Jsons.toJsonString(api)},
    <#-- 过滤器配置 -->
    <#list filters!>
      "filterColumnCount": ${filterColumnCount!4},
      "filterTogglable": true,
      "filter": {
        "title": <#if filterTitle??>"${filterTitle}"<#else>false</#if>,
        "body": [
          <#items as filter>
          ${Jsons.toJsonString(filter)}<#sep>,</#sep>
          </#items>
        ],
        "actions": [
          {
            "type": "action",
            "actionType": "clear",
            "label": "重置",
            "level": "link"
          },
          {
            "type": "submit",
            "label": "搜索",
            "close": false,
            "level": "primary"
          }
        ]
      },
    </#list>
  </#if>

  <#-- 选择配置 -->
  <#if selection??>
    <#if selection.isForceEnabled!false>
    "pickerMode": true,
    </#if>
    <#if selection.isMultiple!false>
    "multiple": true,
    </#if>
    <#if selection.isCheckOnItemClick!false>
    "checkOnItemClick": true,
    </#if>
  </#if>

  <#-- 批量操作配置 -->
  <#if bulkActions?? || batchDeleteEnabled!false>
  "bulkActions": [
    <#if batchDeleteEnabled!false>
    {
      "type": "button",
      "label": "批量删除",
      "level": "danger",
      "actionType": "ajax",
      "hasAuth": "${businessModel.fullName}.batchDelete",
      "api": {
        "method": "POST",
        "url": "${api.url}/batch-delete",
        "data": {
          "ids": "<#noparse>${selectedItems | pick:id}</#noparse>"
        }
      }
    }<#if bulkActions?? && bulkActions?has_content>,</#if>
    </#if>
    <#list bulkActions as action>
    ${Jsons.toJsonString(action)}<#sep>,</#sep>
    </#list>
  ],
  </#if>

  <#-- 单条操作配置 -->
  <#if itemActions??>
  "itemActions": ${Jsons.toJsonString(itemActions)},
  </#if>

  <#-- 列配置 -->
  "columns": [
    <#-- 字段列 -->
    <#list columns! as column>
      ${Jsons.toJsonString(column)}<#sep>,</#sep>
    </#list>
    <#-- 操作栏 -->
    <#if columns?has_content && operations?has_content>,</#if>
    <#list operations!>
    {
      "type": "operation",
      <#if operationsColumnWidth??>"width": ${Jsons.toJsonString(operationsColumnWidth)},</#if>
      <#if operationsColumnFixed!true>"fixed": "right",</#if>
      "label": "操作",
      "buttons": [
        <#items as operation>
        ${Jsons.toJsonString(operation)}<#sep>,</#sep>
        </#items>
      ]
    }
    </#list>
  ],

  <#-- 表头工具栏 -->
  "headerToolbar": [
    <#if api??>
      <#-- 过滤器开关 -->
      <#if filters??>
        {
          "type": "filter-toggler",
          "align": "left",
          "label": "筛选"
        },
      </#if>
      {
        "actionType": "reload",
        "icon": "fa fa-refresh",
        "type": "button"
      }
      <#-- 导出当前页 -->
      <#if isEnabledServerSideExport!false>
        <#if api??>,</#if>
        {
          "type": "button",
          "label": "导出Excel",
          "downloadFileName": "导出-${title}",
          "icon": "vxe-download",
          "actionType": "download",
          "hasAuth": "${businessModel.fullName}.serverSideExport",
          "api": {
            "method": "GET",
            "url": "/api/default-business-model-data-export/${businessModel.fullName}/export-excel",
            "data": {
              "&": "$$"
            }
          }
        },
      <#elseif (isExportCurrentPageEnabled!true || isExportAllEnabled!true)>
        <#if isExportCurrentPageEnabled!true>
          ,{
            "type": "export-excel",
            "label": "导出当前页",
            "filename": "${title}-P<#noparse>${page}</#noparse>"
          },
        </#if>
        <#-- 导出全部 -->
        <#if isExportAllEnabled!true>
          <#if !(isExportCurrentPageEnabled!true)>,</#if>
          {
            "type": "export-excel",
            "label": "导出全部",
            "filename": "${title}-全部",
            "api": {
              <#if api.method??>
              "method": "${api.method}",
              </#if>
              "url": "${api.url!}",
              "data": {
                  "&": "$$",
                "perPage": <#noparse>"${total}"</#noparse>,
                "page": 1
              }
            }
          },
        </#if>
      <#else>,
      </#if>
      <#-- 导入 -->
      <#if importEnabled!false>
        {
          "type": "button",
          "label": "导入数据",
          "icon": "fa fa-upload",
          "actionType": "dialog",
          "hasAuth": "${businessModel.fullName}.import",
          "dialog": {
            "title": "导入数据",
            "body": {
              "type": "form",
              "api": "POST:${api.url!}/import",
              "mode": "horizontal",
              "body": [
                {
                  "type": "input-file",
                  "name": "file",
                  "label": false,
                  "accept": ".xls,.xlsx",
                  "drag": true,
                  "required": true,
                  "asBlob": true
                }
              ]
            }
          }
        },
      </#if>
    <#else>
    </#if>
    <#-- bulkActions 总是存在 -->
    {
      "type": "bulkActions"
    }<#if headerToolbarActions?? && headerToolbarActions?has_content>,</#if>
    <#-- 自定义工具栏 -->
    <#list headerToolbarActions!>
      <#items as action>
      ${Jsons.toJsonString(action)}<#sep>,</#sep>
      </#items>
    </#list>
  ],
  <#-- 表尾工具栏 -->
  "footerToolbar": [
    "statistics",
    "switch-per-page",
    "pagination"
  ],
  <#-- 分页配置 -->
  "alwaysShowPagination": ${(alwaysShowPagination!true)?c},
  "perPage": ${defaultPageSize!20}
}