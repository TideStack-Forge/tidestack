<#import "../../lib" as globalLib>
<#noparse>{
  "type": "group",
  "body": [
    {
      "type": "select",
      "name": "fieldName",
      "placeholder": "字段",
      "searchable": true,
      "labelField": "title",
      "valueField": "name",
      "source": {
        "method": "get",
        "url": "/api/dev/${appName}/business-model/${model}",
        "responseData": {
          "items": "${ fields }"
        }
      },
      "required": true
    },</#noparse>
    <@globalLib.fieldFilterConfig/>
  ]
},
<@globalLib.fieldFilterExtraConfig/>