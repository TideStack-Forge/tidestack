<#import "../../apis/lib" as lib>
<#noparse>
{
  "type": "panel",
  "title": "服务端数据导出",
  "mode": "horizontal",
  "visibleOn": "${ features.isEnabledServerSideExport }",
  "body": [
    {
      "type": "alert",
      "title": "看不到相关字段？",
      "body": ["如果找不到相关字段，请先保存当前模型，然后点击", {
        "type": "button",
        "level": "link",
        "label": "刷新字段",
        "onEvent": {
          "click": {
            "actions": [
              {
                "actionType": "reload",
                "componentId": "serverSideExportDataMappingFields"
              }
            ]
          }
        }
      }],
      "level": "info",
      "showIcon": true,
      "className": "mb-5"
    },
    {
      "type": "transfer",
      "label": "导出数据字段",
      "name": "features.serverSideExport.fields",
      "keyPlaceholder": "字段名",
      "selectMode": "tree",
      "sortable": "true",
      "id": "serverSideExportDataMappingFields",
      "joinValues": false,
      "extractValue": false,
      "source": {
        "method": "get",
        "url": "/api/dev/${appName}/business-model-helper/${fullName}/exportable-fields"
      },
      "visibleOn": "${ features.isEnabledServerSideExport }"
    }</#noparse>
  ]
}