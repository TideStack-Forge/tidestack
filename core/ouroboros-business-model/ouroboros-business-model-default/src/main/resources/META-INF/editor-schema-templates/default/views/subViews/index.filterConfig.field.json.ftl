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
      "source": "${ fields }",
      "required": true,
      "popOverContainerSelector": ".ouroboros-pop-over-container"
    },</#noparse>
    <@globalLib.fieldFilterConfig/>
  ]
},
<@globalLib.fieldFilterExtraConfig/>