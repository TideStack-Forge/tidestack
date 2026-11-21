<#noparse>
{
  "type": "combo",
  "label": "默认过滤器",
  "name": "views.index.defaultFilters",
  "subFormMode": "normal",
  "multiLine": true,
  "multiple": true,
  "removable": true,
  "draggable": true,
  "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 }",
  "conditions": [
    {
      "label": "字段",
      "test": "${ type == 'field' || !type }",
      "scaffold": {
        "type": "field"
      },
      "items": [</#noparse><#include "index.filterConfig.field"><#noparse>]
    },
    {
      "label": "关系",
      "test": "${ type == 'relation' }",
      "scaffold": {
        "type": "relation"
      },
      "items": [</#noparse><#include "index.filterConfig.relation"><#noparse>]
    }
  ]
}
</#noparse>