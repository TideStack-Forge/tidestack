<#import "../../lib" as globalLib>
<#noparse>
{
  "type": "switch",
  "name": "subTable.isCustomDisplayColumns",
  "label": "自定义子表显示",
  "visibleOn": "${ type == 'child' && model }"
},
{
  "type": "tabs-transfer-editor",
  "label": "子表显示列",
  "name": "subTable.customDisplayColumns",
  "sortable": "true",
  "valueField": "name",
  "joinValues": false,
  "creatable": true,
  "source": {
    "method": "get",
    "url": "/api/dev/${appName}/business-model/${model}",
    "adaptor": "if(payload.status!==0)return payload;var model=payload.data;var fields={label:'字段',searchable:true,children:(model.fields||[]).map((f)=>({label:f.title,name:f.name,type:'field',})),};var relations={label:'关系',searchable:true,children:(model.relations||[]).map((r)=>({label:r.title,name:r.name,type:'relation',})),};var builtin=!(model.features&&model.features.isEnabledAuditFields)?[]:[{type:'builtin',label:'创建时间',title:'创建时间',name:'createdAt',schema:{type:'static-date',format:'YYYY-MM-DD HH:mm:ss',sortable:true,},},{type:'builtin',label:'更新时间',title:'更新时间',name:'updatedAt',schema:{type:'static-date',format:'YYYY-MM-DD HH:mm:ss',sortable:true,},},{type:'builtin',label:'创建人',title:'创建人',name:'createdBy',schema:{type:'user-ref'},},{type:'builtin',label:'更新人',title:'更新人',name:'updatedBy',schema:{type:'user-ref'},},];var accessControl=model.accessControl||{};var updateAC=accessControl.update||{};var deleteAC=accessControl.delete||{};var searchAC=accessControl.search||{};function isACFilterEnabled(filterName){return[updateAC,deleteAC,searchAC].every((ac)=>ac.isEnabled&&ac.filters&&ac.filters[filterName])}if(isACFilterEnabled('$byEmployee')){builtin.push({type:'builtin',label:'制单人',title:'制单人',name:'createdByEmployee',schema:{type:'employee-ref',pristine:{tpl:'${createdByEmployee.name}',},},})}if(isACFilterEnabled('$byDepartment')){builtin.push({type:'builtin',label:'创建部门',title:'创建部门',name:'createdByDepartment',schema:{type:'department-ref',pristine:{tpl:'${createdByDepartment.name}',},},})}if(!isACFilterEnabled('$byCompany')){builtin.push({type:'builtin',label:'创建公司',title:'创建公司',name:'createdByCompany',schema:{type:'company-ref',pristine:{tpl:'${createdByCompany.name}',},},})}var items=[fields,relations];if(builtin.length){items.push({label:'内建',searchable:true,children:builtin,})}return{status:0,code:0,data:items}"
  },
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
    </#noparse><@globalLib.columnConfig/><#noparse>,
    {
      "type": "formula",
      "name": "label",
      "formula": "${title}"
    },
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": "显示组件",
      "showSwitch": false,
      "switchLayout": false,
      "required": true,
      "defaultSchema": {
        "type": "text"
      }
    }
  ],
  "editControls": [
    {
      "type": "input-text",
      "name": "title",
      "label": "列标题",
      "placeholder": "请输入列标题（若不填则默认使用字段标题）"
    },
    </#noparse><@globalLib.columnConfig/><#noparse>,
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
    }
  ],
  "visibleOn": "${ type == 'child' && model && subTable.isCustomDisplayColumns }"
}
</#noparse>