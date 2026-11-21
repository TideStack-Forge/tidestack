{
  "type": "panel",
  "title": "特性开关",
  "body": [
    {
      "type": "switch",
      "name": "features.isEnabledSoftDelete",
      "label": "数据软删除",
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentId": "businessModelFeaturesPanel",
              "expression": <#noparse>"${value}"</#noparse>,
              "args": {
                "value": {
                  "enableArchiveDelete": false
                }
              }
            }
          ]
        }
      },
      "disabledOn": <#noparse>"${ !(fields && fields.length > 0 || relations && relations.length > 0) }"</#noparse>
    },
    {
      "type": "switch",
      "name": "enableArchiveDelete",
      "label": "归档删除",
      "description": "归档删除会将删除数据转存到归档模型，并作为删除策略替代软删除。",
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentId": "businessModelFeaturesPanel",
              "expression": <#noparse>"${value}"</#noparse>,
              "args": {
                "value": {
                  "features": {
                    "isEnabledSoftDelete": false
                  }
                }
              }
            }
          ]
        }
      }
    },
    {
      "type": "switch",
      "name": "enableScd2History",
      "label": "SCD2历史追踪"
    },
    {
      "type": "switch",
      "name": "features.isEnabledAuditFields",
      "label": "基本审计字段",
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "broadcast",
              "args": {
                "eventName": "features_isEnabledAuditFields_changed"
              },
              "data": {
                "features": {
                  "isEnabledAuditFields": <#noparse>"${value}"</#noparse>
                }
              }
            }
          ]
        }
      },
      "disabledOn": <#noparse>"${ !(fields && fields.length > 0 || relations && relations.length > 0) }"</#noparse>
    },
    {
      "label": "允许批量删除",
      "type": "switch",
      "name": "features.isEnabledBatchDelete",
      "value": false
    },
    {
      "label": "支持数据导入",
      "type": "switch",
      "name": "features.isEnabledImport",
      "value": false
    },
    <#include "subViews/import">,
    {
      "label": "服务端数据导出",
      "type": "switch",
      "name": "features.isEnabledServerSideExport",
      "value": false
    },
    <#include "subViews/serverSideExport">,
    {
      "type": "switch",
      "name": "features.isEnabledChangeLog",
      "label": "数据变更日志",
      "disabled": true,
      "visible": false,
      "description": "数据变更日志功能正在开发中，敬请期待"
    },
    {
      "type": "switch",
      "name": "features.isEnabledExclusiveLock",
      "label": "启用独占编辑",
      "disabled": true,
      "visible": false,
      "description": "独占编辑功能正在开发中，敬请期待"
    },
    {
      "type": "switch",
      "name": "features.isEnabledDraft",
      "label": "启用草稿状态",
      "disabled": true,
      "visible": false,
      "description": "草稿状态功能正在开发中，敬请期待"
    }
  ],
  "id": "businessModelFeaturesPanel"
}
