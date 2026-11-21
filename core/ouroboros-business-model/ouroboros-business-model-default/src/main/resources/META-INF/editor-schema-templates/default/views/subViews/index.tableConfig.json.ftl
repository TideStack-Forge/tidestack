<#import "../lib" as lib>
<#import "../../lib" as globalLib>
<#noparse>
{
  "type": "tabs-transfer-editor",
  "label": "显示列",
  "name": "views.index.displayColumns",
  "id": "views_index_display_columns",
  "sortable": "true",
  "valueField": "name",
  "joinValues": false,
  "creatable": true,
  "options": [
    </#noparse>
    <@lib.fields />,
    <@lib.relations />,
    <@lib.builtInFields />
    <#noparse>
  ],
  "showArrow": true,
  "selectedDataMapping": {
    "type": "$type",
    "name": "$name",
    "title": "$title",
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
        "builtin": "<span class='label bg-purple-400'>内置</span>",
        "custom": "<span class='label label-warning'>自定义</span>"
      }
    },
    {
      "type": "text",
      "name": "label",
      "visibleOn": "${!selectedItems[index].title}"
    },
    {
      "type": "tpl",
      "tpl": "${selectedItems[index].title}",
      "className": "font-bold italic",
      "visibleOn": "${selectedItems[index].title}"
    },
    {
      "type": "tpl",
      "tpl": " (${name})",
      "className": "text-muted"
    }
  ],
  "editable": true,
  "editButton": {
    "level": "link",
    "label": "",
    "size": "xs",
    "className": "mr-2",
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
    "size": "md",
    "title": "添加自定义列"
  },
  "addControls": [
    {
      "type": "hidden",
      "name": "type",
      "value": "custom"
    },
    {
      "type": "input-text",
      "name": "name",
      "label": "列名",
      "required": true
    },
    {
      "type": "input-text",
      "name": "title",
      "label": "列标题",
      "required": true
    },
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": false,
      "showSwitch": true,
      "onText": "使用自定义组件",
      "offText": "使用默认组件",
      "switchLayout": false,
      "defaultSchema": {
        "type": "text"
      }
    },
    {
      "type": "formula",
      "name": "label",
      "formula": "${title}"
    },
    </#noparse><@globalLib.columnConfig/><#noparse>
  ],
  "editControls": [
    {
      "type": "input-text",
      "name": "title",
      "label": "列标题",
      "placeholder": "请输入列标题（若不填则默认使用字段标题）"
    },
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": false,
      "showSwitch": true,
      "onText": "使用自定义组件",
      "offText": "使用默认组件",
      "switchLayout": false,
      "defaultSchema": {
        "type": "text"
      }
    },
    </#noparse><@globalLib.columnConfig/><#noparse>
  ],
  "onEvent": {
    "businessModelChanged": {
      "actions": [
        {
          "actionType": "reloadOptions",
          "data": "$changedData"
        }
      ]
    }
  }
}</#noparse>