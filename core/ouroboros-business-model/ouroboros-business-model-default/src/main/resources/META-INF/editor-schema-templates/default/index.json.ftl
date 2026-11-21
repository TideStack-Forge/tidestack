{
  "type": "wrapper",
  "className": "h-full max-h-full p-0 flex flex-col",
  "body": [
    <#include "baseInfo/index">,
    {
      "type": "tabs",
      "id": "business-model-config-tabs",
      "tabsMode": "simple",
      "className": "flex flex-col flex-grow h-full",
      "style": {
        "height": "200px"
      },
      "contentClassName": "flex-grow h-full overflow-y-auto",
      "linksClassName": "flex justify-center",
      "tabs": [
        {
          "title": "字段与关联",
          "hash": "fields-n-relations",
          "icon": "fas fa-list",
          "className": "px-0",
          "body": [<#include "fields/index">, <#include "relations/index">]
        },
        {
          "title": "视图配置",
          "hash": "crud-ui",
          "icon": "fas fa-window-restore",
          "className": "px-0",
          "body": [<#include "views/index">]
        },
        {
          "title": "接口配置",
          "hash": "crud-api",
          "icon": "fas fa-plug",
          "className": "px-0",
          "disabledOn": <#noparse>"${ !(fields && fields.length > 0 || relations && relations.length > 0) }"</#noparse>,
          "body": [<#include "apis/index">]
        },
        {
          "title": "行为契约",
          "hash": "behaviors",
          "icon": "fas fa-bolt",
          "className": "px-0",
          "body": [<#include "behavior/index">]
        },
        {
          "title": "工作流配置",
          "hash": "workflow",
          "icon": "fas fa-diagram-project",
          "className": "px-0",
          "body": [<#include "workflow/index">]
        },
        {
          "title": "高级特性",
          "hash": "features",
          "icon": "fas fa-cogs",
          "className": "px-0",
          "body": [<#include "features/index">]
        },
        {
          "title": "访问控制",
          "hash": "accessControl",
          "icon": "fas fa-lock",
          "className": "px-0",
          "disabledOn": <#noparse>"${ !(fields && fields.length > 0 || relations && relations.length > 0) }"</#noparse>,
          "body": [<#include "accessControl/index">]
        },
        {
          "title": "自定义功能",
          "hash": "custom",
          "icon": "fas fa-code",
          "className": "px-0",
          "body": [<#include "custom/index">]
        }
      ]
    }
  ]
}
