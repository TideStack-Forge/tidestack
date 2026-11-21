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
      "source": "${ relations | filter:type:isIn:master,reference }",
      "required": true,
      "popOverContainerSelector": ".ouroboros-pop-over-container"
    },</#noparse>
    <@globalLib.relationFilterConfig/>
  ]
},
<@globalLib.relationFilterExtraConfig/>