<#import "../../lib" as globalLib>
<#noparse>
{
  "type": "input-kv",
  "name": "selector.defaultRequestParams",
  "label": "默认请求参数",
  "keyPlaceholder": "参数名",
  "valuePlaceholder": "参数值",
  "description": "拉取关联模型数据时附带的请求参数",
  "visibleOn": "${ (type == 'reference' || type == 'master') }"
},
{
  "type": "switch",
  "name": "selector.isCustomPicker",
  "label": "自定义拾取器",
  "visibleOn": "${ (type == 'reference' || type == 'master') && model && selectorType == 'picker' }"
},</#noparse>
<#include "relations.customPickerDisplayColumns">
<#noparse>,
{
  "type": "switch",
  "name": "selector.isCustomPickerFilters",
  "label": "自定义过滤器",
  "visibleOn": "${ (type == 'reference' || type == 'master') && model && selectorType == 'picker' && selector.isCustomPicker }"
},
{
  "type": "combo",
  "label": "自定义过滤器",
  "name": "selector.customPickerFilters",
  "subFormMode": "normal",
  "multiLine": true,
  "multiple": true,
  "removable": true,
  "draggable": true,
  "conditions": [
    {
      "label": "字段",
      "test": "${ type == 'field' || !type }",
      "scaffold": {
        "type": "field"
      },
      "items": [</#noparse><#include "relations.customPickerFilters.field"><#noparse>]
    },
    {
      "label": "关系",
      "test": "${ type == 'relation' }",
      "scaffold": {
        "type": "relation"
      },
      "items": [</#noparse><#include "relations.customPickerFilters.relation"><#noparse>]
    }
  ],
  "visibleOn": "${ (type == 'reference' || type == 'master') && model && selectorType == 'picker' && selector.isCustomPicker && selector.isCustomPickerFilters }"
}
</#noparse>