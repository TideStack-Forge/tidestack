<#noparse>
{
  "type": "page",
  "title": false,
  "initApi": "/api/approval-workflow/instance/${instanceId}/info",
  "body": [
    {
      "type": "button-group-select",
      "name": "currentTab",
      "label": "",
      "inline": false,
      "value": "formData",
      "options": [
        {
          "label": "</#noparse>${title}信息<#noparse>",
          "value": "formData"
        },
        {
          "label": "审批记录",
          "value": "processDetail"
        }
      ],
      "multiple": false
    },
    {
      "type": "switch-container",
      "items": [
        {
          "title": "业务数据",
          "body": [
            </#noparse>${Jsons.toJsonString(formSchema)}<#noparse>
          ],
          "index": 0,
          "visibleOn": "${!currentTab || currentTab === 'formData'}"
        },
        {
          "title": "审批记录",
          "body": [
            {
              "type": "property",
              "items": [
                {"label": "状态", "content": "${status}"},
                {"label": "结果", "content": "${outcome || '-'}"}
              ]
            },
            {
              "type": "button",
              "label": "打开外部审批",
              "actionType": "url",
              "url": "${externalUrl}",
              "blank": true,
              "visibleOn": "${!!externalUrl}"
            },
            {
              "type": "service",
              "visibleOn": "${diagramAvailable === true}",
              "api": {
                "method": "GET",
                "url": "/api/approval-workflow/instance/${instanceId}/diagram"
              },
              "body": {
                "type": "workflow-diagram-viewer",
                "definition": "${definition}",
                "activeNodeIds": "${activeNodeIds}",
                "height": 500
              }
            },
            {
              "type": "fieldSet",
              "visibleOn": "${timelineAvailable === true}",
              "title": "审批记录",
              "collapsable": true,
              "collapsed": false,
              "body": [
                {
                  "type": "workflow-timeline",
                  "instanceId": "${instanceId}"
                }
              ]
            }
          ],
          "visibleOn": "${currentTab === 'processDetail'}"
        }
      ],
      "style": {
        "position": "static",
        "display": "block"
      },
      "isFixedHeight": false,
      "isFixedWidth": false
    }
  ],
  "actions": </#noparse>${Jsons.toJsonString(actions)}<#noparse>
}
</#noparse>
