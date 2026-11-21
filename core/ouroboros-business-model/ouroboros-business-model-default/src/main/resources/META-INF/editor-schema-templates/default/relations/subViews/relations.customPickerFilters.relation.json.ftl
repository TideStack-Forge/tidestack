<#import "../../lib" as globalLib>
<#noparse>{
  "type": "group",
  "body": [
    {
      "type": "select",
      "name": "fieldName",
      "placeholder": "关系",
      "searchable": true,
      "labelField": "title",
      "valueField": "name",
      "source": {
        "method": "get",
        "url": "/api/dev/${appName}/business-model/${model}",
        "responseData": {
          "items": "${ relations | filter:type:isIn:master,reference }"
        }
      },
      "required": true
    },</#noparse>
    <@globalLib.relationFilterConfig/>
  ]
},
<@globalLib.relationFilterExtraConfig/>