<#import "../projection-editor" as projection>
<#macro formFields viewName showBuiltInFields>
{
  "type": "tabs-transfer-editor",
  "label": "表单字段",
  "name": "views.${viewName}.fields",
  "sortable": "true",
  "valueField": "name",
  "joinValues": false,
  "creatable": true,
  "options": [
    <@fields />,
    <@relations />
    <#if showBuiltInFields>,
    <@builtInFields/>
    </#if>
  ],
  <#noparse>
  "showArrow": true,
  "selectedDataMapping": {
    "type": "$type",
    "name": "${name || null}",
    "title": "${title || null}",
    "schema": "${schema || null}"
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
          "label": "占位符",
          "value": "placeholder"
        },
        {
          "label": "分隔符",
          "value": "divider"
        },
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
      "resourceBrowserContext": {
        "mode": "dev-app-frontend-resource",
        "appName": "${appName}"
      },
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
    {
      "type": "amis-schema-editor",
      "name": "schema",
      "label": false,
      "mode": "normal",
      "resourceBrowserContext": {
        "mode": "dev-app-frontend-resource",
        "appName": "${appName}"
      },
      "showSwitch": true,
      "onText": "使用自定义组件",
      "offText": "使用默认组件",
      "switchLayout": false
    }
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
}
</#noparse>
</#macro>

<#macro builtInFields>
  <#noparse>
    {
      "label": "内置字段",
      "searchable": true,
      "visibleOn": "${features.isEnabledAuditFields}",
      "children": [
        {
          "type": "builtin",
          "label": "创建时间",
          "title": "创建时间",
          "name": "createdAt",
          "schema": {
            "type": "static-date",
            "format": "YYYY-MM-DD HH:mm:ss",
            "sortable": true
          },
          "visibleOn": "${features.isEnabledAuditFields}"
        },
        {
          "type": "builtin",
          "label": "更新时间",
          "title": "更新时间",
          "name": "updatedAt",
          "schema": {
            "type": "static-date",
            "format": "YYYY-MM-DD HH:mm:ss",
            "sortable": true
          },
          "visibleOn": "${features.isEnabledAuditFields}"
        },
        {
          "type": "builtin",
          "label": "创建人",
          "title": "创建人",
          "name": "createdBy",
          "schema": {
            "type": "user-ref"
          },
          "visibleOn": "${features.isEnabledAuditFields}"
        },
        {
          "type": "builtin",
          "label": "更新人",
          "title": "更新人",
          "name": "updatedBy",
          "schema": {
            "type": "user-ref"
          },
          "visibleOn": "${features.isEnabledAuditFields}"
        },
        {
          "type": "builtin",
          "label": "制单人",
          "title": "制单人",
          "name": "createdByEmployee",
          "schema": {
            "type": "employee-ref",
            "pristine": {
              "tpl": "\\${createdByEmployee.name}"
            }
          },
          "visibleOn": "${ !!accessControl.delete.isEnabled && !!accessControl.delete.filters.$byEmployee || !!accessControl.search.isEnabled && !!accessControl.search.filters.$byEmployee || !!accessControl.update.isEnabled && !!accessControl.update.filters.$byEmployee }"
        },
        {
          "type": "builtin",
          "label": "创建部门",
          "title": "创建部门",
          "name": "createdByDepartment",
          "schema": {
            "type": "department-ref",
            "pristine": {
              "tpl": "\\${createdByDepartment.name}"
            }
          },
          "visibleOn": "${ !!accessControl.delete.isEnabled && !!accessControl.delete.filters.$byDepartment || !!accessControl.search.isEnabled && !!accessControl.search.filters.$byDepartment || !!accessControl.update.isEnabled && !!accessControl.update.filters.$byDepartment }"
        },
        {
          "type": "builtin",
          "label": "创建公司",
          "title": "创建公司",
          "name": "createdByCompany",
          "schema": {
            "type": "company-ref",
            "pristine": {
              "tpl": "\\${createdByCompany.name}"
            }
          },
          "visibleOn": "${ !!accessControl.delete.isEnabled && !!accessControl.delete.filters.$byCompany || !!accessControl.search.isEnabled && !!accessControl.search.filters.$byCompany || !!accessControl.update.isEnabled && !!accessControl.update.filters.$byCompany }"
        }
      ]
    }
  </#noparse>
</#macro>

<#macro relations>
  <#noparse>
    {
      "label": "关联模型",
      "searchable": true,
      "source": "${relations | pick:label~title,name | addProps:type:relation}"
    }
  </#noparse>
</#macro>

<#macro fields>
  <#noparse>
    {
      "label": "字段",
      "searchable": true,
      "source": "${fields | pick:label~title,name | addProps:type:field}"
    }
  </#noparse>
</#macro>

<#macro dialogLayoutSetting viewName>
{
  "type": "fieldSet",
  "visibleOn": "<#noparse>${</#noparse>views.${viewName}.isAuto && views.${viewName}.mode === 'dialog'}",
  "title": "弹窗配置",
  "collapsable": true,
  "collapsed": true,
  "body": [
    {
      "type": "button-group-select",
      "label": "弹窗尺寸",
      "name": "views.${viewName}.dialog.size",
      "value": "md",
      "options": [
        {
          "label": "超小",
          "value": "xs"
        },
        {
          "label": "小",
          "value": "sm"
        },
        {
          "label": "中",
          "value": "md"
        },
        {
          "label": "大",
          "value": "lg"
        },
        {
          "label": "超大",
          "value": "xl"
        },
        {
          "label": "全屏",
          "value": "full"
        }
      ]
    }
  ]
}
</#macro>


<#macro drawerLayoutSetting viewName>
{
  "type": "fieldSet",
  "visibleOn": "<#noparse>${</#noparse>views.${viewName}.isAuto && views.${viewName}.mode === 'drawer'}",
  "title": "抽屉配置",
  "collapsable": true,
  "collapsed": true,
  "body": [
    {
      "type": "button-group-select",
      "label": "抽屉尺寸",
      "name": "views.${viewName}.drawer.size",
      "value": "md",
      "options": [
        {
          "label": "超小",
          "value": "xs"
        },
        {
          "label": "小",
          "value": "sm"
        },
        {
          "label": "中",
          "value": "md"
        },
        {
          "label": "大",
          "value": "lg"
        },
        {
          "label": "超大",
          "value": "xl"
        }
      ]
    },
    {
      "type": "switch",
      "label": "可拖动改变大小",
      "name": "views.${viewName}.drawer.resizable",
      "className": "mb-5"
    }
  ]
}
</#macro>

<#macro formLayoutSetting viewName showBuiltInFields>
{
  "type": "fieldSet",
  "id": "layoutOptions",
  "visibleOn": "<#noparse>${</#noparse> views.${viewName}.isAuto }",
  "title": "表单布局",
  "collapsable": true,
  "collapsed": true,
  "body": [
    {
      "type": "button-group-select",
      "label": "布局类型",
      "name": "views.${viewName}.layoutType",
      "multiple": false,
      "options": [
        {
          "label": "水平",
          "value": "horizontal"
        },
        {
          "label": "垂直",
          "value": "normal"
        },
        {
          "label": "多列",
          "value": "column"
        }
      ],
      "value": "horizontal"
    },
    {
      "type": "input-number",
      "label": "列数",
      "name": "views.${viewName}.columnCount",
      "visibleOn": "<#noparse>${</#noparse> views.${viewName}.layoutType === 'column' }",
      "value": 2,
      "min": 1,
      "max": 12,
      "required": true
    },
    {
      "type": "input-number",
      "label": "Label 宽度",
      "name": "views.add.labelWidth",
      "hiddenOn": "<#noparse>${</#noparse> views.${viewName}.layoutType === 'normal' }",
      "value": 120,
      "required": true
    },
    <@formFields viewName=viewName showBuiltInFields=showBuiltInFields/>
  ],
  "bodyClassName": "p-t"
}
</#macro>

<#macro customViewButton viewName viewTitle>
{
  "label": "",
  "type": "button-toolbar",
  "hiddenOn": "<#noparse>${</#noparse> views.${viewName}.isAuto }",
  "buttons": [
    {
      "type": "action",
      "actionType": "dialog",
      "label": "表单设计器",
      "level": "primary",
      "dialog": {
        "title": "<#noparse>${</#noparse> title | default:'未命名' } ${viewTitle}设计器",
        "size": "full",
        "bodyClassName": "p-0 m-0",
        "body": {
          "type": "form",
          "mode": "normal",
          "className": "h-full",
          "title": "",
          "body": {
            "label": "",
            "type": "amis-editor",
            "resourceBrowserContext": {
              "mode": "dev-app-frontend-resource",
              "appName": "${appName}"
            },
            "lockRoot": true,
            "inputClassName": "h-full",
            "className": "h-full",
            "name": "views.${viewName}.schema",
            "value": {
              "type": "form",
              "body": []
            }
          }
        },
        "actions": [
          {
            "type": "action",
            "actionType": "cancel",
            "label": "取消"
          },
          {
            "type": "action",
            "actionType": "confirm",
            "level": "primary",
            "label": "确认",
            "mergeData": true
          }
        ]
      }
    }
  ]
}
</#macro>

<#macro previewViewButton viewName viewTitle viewSubPath>
{
  "type": "fieldSet", "title": "预览页面", "collapsable": true, "collapsed": false,
  "body": [
    {
      "type": "button", "label": "预览页面", "name": "preview-${viewName}-button", "level": "primary", "disabledTip": "请先保存模型",
      "actionType": "dialog",
      "dialog": {
        "title": "预览${viewTitle}", "size": "lg",
        "body": {
          "type": "service",
          "api": { "url": "<#noparse>/api/dev/${appName}/business-model/${fullName}/projection-target?kind=ui-schema&name=${fullName | kebabCasePath}</#noparse>${viewSubPath}" },
          "body": [
            { "name": "modelDetailPreview", "type": "amis-schema-preview", "source": "<#noparse>${model}</#noparse>",
              "frontendResourceRenderContext": { "mode": "dev-app-workspace", "appName": "<#noparse>${appName}</#noparse>" } },
            <@projection.editActions cloneEndpoint="clone-ui-schema"/>
          ]
        },
        "actions": [{ "type": "button", "label": "关闭", "actionType": "close" }],
        "closeOnEsc": true, "closeOnOutside": true
      }
    },
    {
      "type": "service",
      "onEvent": {
        "businessModelChanged": {
          "actions": [
            {
              "actionType": "disabled",
              "componentName": "preview-${viewName}-button",
              "expression": <#noparse>"${changedData.fullName === fullName}"</#noparse>
            }
          ]
        },
        "businessModelSaved": {
          "actions": [
            {
              "actionType": "enabled",
              "componentName": "preview-${viewName}-button",
              "expression": <#noparse>"${savedData.fullName === fullName}"</#noparse>
            }
          ]
        }
      }
    }
  ]
}
</#macro>

<#macro formViewSetting viewName viewTitle titlePlaceholder viewSubPath showBuiltInFields>
{
  "title": "${viewTitle}",
  "mode": "horizontal",
  "icon": "far fa-pen-to-square",
  "visibleOn": <#noparse>"${ fields && fields.length > 0 || relations && relations.length > 0 }",</#noparse>
  "body": [
    {
      "label": "显示",
      "type": "switch",
      "name": "views.${viewName}.isEnabled",
      "value": true,
      "description": "是否在索引页显示${viewTitle}按钮"
    },
    {
      "type": "container",
      "body": [
        {
          "type": "input-text",
          "name": "views.${viewName}.title",
          "label": "标题",
          "placeholder": "${titlePlaceholder}"
        },
        {
          "type": "button-group-select",
          "label": "打开方式",
          "name": "views.${viewName}.mode",
          "multiple": false,
          "options": [
            {
              "label": "弹窗",
              "value": "dialog"
            },
            {
              "label": "抽屉",
              "value": "drawer"
            },
            {
              "label": "新标签",
              "value": "tab"
            }
          ],
          "value": "dialog"
        },
        {
          "type": "switch",
          "label": "自动表单",
          "name": "views.${viewName}.isAuto",
          "value": true,
          "visibleOn": <#noparse>"${</#noparse> views.${viewName}.schema }"
        },
        <@dialogLayoutSetting viewName=viewName/>,
        <@drawerLayoutSetting viewName=viewName/>,
        <@formLayoutSetting viewName=viewName showBuiltInFields=showBuiltInFields/>,
        <@customViewButton viewName=viewName viewTitle=viewTitle/>,
        <@previewViewButton viewName=viewName viewTitle=viewTitle viewSubPath=viewSubPath/>
      ]
    }
  ]
}
</#macro>
