<#noparse>
{
  "type": "panel",
  "title": "单据流配置",
  "visibleOn": "features.isEnabledBillFlow",
  "body": [
    {
      "type": "fieldSet",
      "title": "单据状态",
      "body": [
        {
          "type": "select",
          "label": "状态字段",
          "name": "billFlow.statusField.name",
          "source": "${fields|filter:type:equals:'数据字典'|pick:label~title,value~name}"
        },
        {
          "type": "select",
          "name": "billFlow.statusField.initStatus",
          "label": "初始状态",
          "valueField": "code",
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/data-dict/${fields | filter:name:equals:billFlow.statusField.name | first | pick:dictName}",
            "sendOn": "${billFlow.statusField.name}"
          }
        },
        {
          "type": "select",
          "name": "billFlow.statusField.approvedStatus",
          "label": "审核状态",
          "valueField": "code",
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/data-dict/${fields | filter:name:equals:billFlow.statusField.name | first | pick:dictName}",
            "sendOn": "${billFlow.statusField.name}"
          }
        },
        {
          "type": "select",
          "name": "billFlow.statusField.finishedStatus",
          "label": "完成状态",
          "valueField": "code",
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/data-dict/${fields | filter:name:equals:billFlow.statusField.name | first | pick:dictName}",
            "sendOn": "${billFlow.statusField.name}"
          }
        },
        {
          "type": "select",
          "name": "billFlow.statusField.canceledStatus",
          "label": "取消状态",
          "valueField": "code",
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/data-dict/${fields | filter:name:equals:billFlow.statusField.name | first | pick:dictName}",
            "sendOn": "${billFlow.statusField.name}"
          }
        },
        {
          "type": "select",
          "name": "billFlow.statusField.pauseStatus",
          "label": "暂停状态",
          "valueField": "code",
          "source": {
            "method": "get",
            "url": "/api/dev/${appName}/data-dict/${fields | filter:name:equals:billFlow.statusField.name | first | pick:dictName}",
            "sendOn": "${billFlow.statusField.name}"
          }
        }
      ]
    },
    {
      "type": "fieldSet",
      "title": "审核配置",
      "body": [
        {
          "type": "switch",
          "label": "开启审核",
          "name": "billFlow.audit.isEnabled"
        }
      ]
    },
    {
      "type": "fieldSet",
      "title": "表体配置",
      "body": [
        {
          "type": "select",
          "label": "子表关系",
          "name": "billFlow.detail.name",
          "source": "${relations | filter:type:equals:'child' | pick:label~title,value~name}"
        },
        {
          "type": "select",
          "label": "标识字段",
          "name": "billFlow.detail.codeField"
        },
        {
          "type": "select",
          "label": "规格字段",
          "name": "billFlow.detail.specFields"
        },
        {
          "type": "select",
          "label": "数量字段",
          "name": "billFlow.detail.quantityField"
        },
        {
          "type": "fieldSet",
          "title": "状态字段",
          "body": [
            {
              "type": "select",
              "label": "字段名",
              "name": "billFlow.detail.statusField.name"
            },
            {
              "type": "select",
              "name": "billFlow.detail.statusField.initStatus",
              "label": "初始状态"
            },
            {
              "type": "select",
              "name": "billFlow.detail.statusField.approvedStatus",
              "label": "审核状态"
            },
            {
              "type": "select",
              "name": "billFlow.detail.statusField.finishedStatus",
              "label": "完成状态"
            },
            {
              "type": "select",
              "name": "billFlow.detail.statusField.canceledStatus",
              "label": "取消状态"
            },
            {
              "type": "select",
              "name": "billFlow.detail.statusField.pauseStatus",
              "label": "暂停状态"
            }
          ]
        },
        {
          "type": "combo",
          "label": "数量约束",
          "name": "billFlow.detail.quantityConstraintRules",
          "multiple": true,
          "items": [
            {
              "type": "input-text",
              "name": "name",
              "label": "约束组名"
            },
            {
              "type": "select",
              "name": "ruleType",
              "label": "约束规则",
              "options": [
                {
                  "label": "不多于",
                  "value": "atMost"
                },
                {
                  "label": "不少于",
                  "value": "atLeast"
                },
                {
                  "label": "等于",
                  "value": "equalTo"
                },
                {
                  "label": "不约束",
                  "value": "noConstraint"
                }
              ]
            },
            {
              "type": "select",
              "name": "accumulationMethod",
              "label": "数量累计方式",
              "visible": false,
              "options": [
                {
                  "label": "数量字段",
                  "value": "cacheField"
                },
                {
                  "label": "实时统计",
                  "value": "realtime"
                }
              ]
            }
          ]
        }
      ]
    },
    {
      "type": "fieldSet",
      "title": "上游单据",
      "body": {
        "type": "combo",
        "name": "billFlow.upstream",
        "items": [
          {
            "type": "select",
            "name": "name",
            "label": "上游单据"
          },
          {
            "type": "input-text",
            "label": "数量约束组名"
          }
        ]
      }
    }
  ]
}
</#noparse>