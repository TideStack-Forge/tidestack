{
  "type": "panel",
  "title": "审批流程",
  "bodyClassName": "p-0",
  "body": [
    {
      "label": "启用工作流集成",
      "type": "switch",
      "name": "workflow.isEnabled",
      "value": false,
      "labelWidth": 120,
      "className": "mt-3"
    },
    {
      "type": "tabs",
      "tabsMode": "sidebar",
      "className": "m-5",
      "linksClassName": "w-20",
      "disabledOn": <#noparse>"${ ! workflow.isEnabled }",</#noparse>
      "tabs": [
        <#include "subViews/binding">,
        <#include "subViews/start">,
        <#include "subViews/flow">
      ]
    }
  ]
}
