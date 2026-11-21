<#import "../../lib" as globalLib>
<#noparse>
{
  "type": "switch",
  "name": "subTable.isCustomInputFields",
  "label": "自定义子表输入",
  "visibleOn": "${ type == 'child' && model && submitTogether }"
},
{
  "type": "tabs-transfer-editor",
  "label": "子表输入项",
  "name": "subTable.customInputFields",
  "sortable": "true",
  "valueField": "name",
  "joinValues": false,
  "creatable": true,
  "source": {
    "method": "get",
    "url": "/api/dev/${appName}/business-model/${model}",
    "adaptor": "if(payload.status!==0)return payload;var model=payload.data;var fields={label:'字段',searchable:true,children:(model.fields||[]).map((f)=>({label:f.title,name:f.name,type:'field',})),};var relations={label:'关系',searchable:true,children:(model.relations||[]).map((r)=>({label:r.title,name:r.name,type:'relation',})),};return{status:0,code:0,data:[fields,relations]}"
  },
  "showArrow": true,
  "selectedDataMapping": {
    "type": "$type",
    "name": "${name || null}",
    "title": "${title || null}",
    "schema": "$schema",
    "columnProps": {
      "width": "$width",
      "fixed": null,
      "align": null
    }
  },
  "valueTpl": [
    {
      "type": "mapping",
      "name": "type",
      "map": {
        "field": "<span class='label label-primary'>字段</span>",
        "relation": "<span class='label label-success'>关联</span>",
        "placeholder": "<span class='label bg-gray-300'>占位符</span>",
        "divider": "<span class='label label-default'>分隔符</span>",
        "builtin": "<span class='label bg-purple-400'>内置</span>",
        "custom": "<span class='label label-warning'>自定义</span>"
      }
    },
    {
      "type": "text",
      "name": "label",
      "visibleOn": "${!selectedItems[index].title}",
      "hiddenOn": "${ type === 'placeholder' || type === 'divider' }"
    },
    {
      "type": "tpl",
      "tpl": "${selectedItems[index].title}",
      "className": "font-bold italic",
      "visibleOn": "${selectedItems[index].title}",
      "hiddenOn": "${ type === 'placeholder' || type === 'divider' }"
    },
    {
      "type": "tpl",
      "tpl": " (${name})",
      "hiddenOn": "${ type === 'placeholder' || type === 'divider' }",
      "className": "text-muted"
    }
  ],
  "editable": true,
  "editButton": {
    "level": "link",
    "label": "",
    "size": "xs",
    "className": "mr-2",
    "hiddenOn": "${ type === 'placeholder' || type === 'divider' }",
    "icon": "fa fa-pencil"
  },
  "addButton": {
    "level": "link",
    "label": "添加",
    "icon": "fa fa-plus",
    "size": "md"
  },
  "editDialog": {
    "size": "md",
    "title": "编辑 ${label}"
  },
  "addDialog": {
    "title": "添加自定义字段"
  },
  "addControls": [
    {
      "type": "list-select",
      "name": "type",
      "label": false,
      "options": [
        {
          "label": "自定义",
          "value": "custom"
        }
      ]
    },
    {
      "type": "input-text",
      "name": "name",
      "label": "字段名",
      "mode": "normal",
      "visibleOn": "${ type === 'custom' }",
      "required": true
    },
    {
      "type": "input-text",
      "name": "title",
      "label": "字段标题",
      "mode": "normal",
      "visibleOn": "${ type === 'custom' }",
      "required": true
    },
    </#noparse><@globalLib.columnConfig/><#noparse>,
    {
      "type": "formula",
      "name": "label",
      "formula": "${title}"
    },
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": "输入组件",
      "mode": "normal",
      "showSwitch": false,
      "switchLayout": false,
      "required": true,
      "visibleOn": "${ type === 'custom' }",
      "defaultSchema": {
        "type": "text"
      }
    }
  ],
  "editControls": [
    {
      "type": "input-text",
      "name": "title",
      "label": "字段标题",
      "mode": "normal",
      "placeholder": "请输入标题（若不填则默认使用字段标题）"
    },
    </#noparse><@globalLib.columnConfig/><#noparse>,
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": false,
      "mode": "normal",
      "showSwitch": true,
      "onText": "使用自定义组件",
      "offText": "使用默认组件",
      "switchLayout": false
    }
  ],
  "visibleOn": "${ type == 'child' && model && submitTogether && subTable.isCustomInputFields }"
}
</#noparse>