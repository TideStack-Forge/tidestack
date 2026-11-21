<#noparse>
{
  "type": "container",
  "body": [
    {
      "type": "switch",
      "name": "statusFlow.isEnabled",
      "label": "启用状态流",
      "value": false,
      "disabled": true,
      "description": "状态流功能开发中，敬请期待…"
    },
    {
      "type": "combo",
      "name": "statusFlow.statuses",
      "value": [],
      "multiple": true,
      "items": [
        {
          "name": "name",
          "type": "input-text",
          "placeholder": "状态",
          "value": "",
          "mode": "inline",
          "columnClassName": "w-sm"
        },
        {
          "name": "scope",
          "mode": "inline",
          "placeholder": "限定范围",
          "type": "select",
          "options": [
            "本部门及上级",
            "仅当前部门"
          ],
          "size": "sm"
        },
        {
          "name": "fields",
          "mode": "inline",
          "placeholder": "关联字段",
          "type": "select",
          "source": "${fields|pick:label~name,value~name}"
        },
        {
          "name": "isRejectable",
          "mode": "inline",
          "placeholder": "可驳回",
          "type": "switch",
          "label": "可驳回"
        },
        {
          "name": "isRevokable",
          "mode": "inline",
          "type": "switch",
          "label": "可撤销"
        }
      ],
      "labelWidth": 1,
      "visibleOn": "this.statusFlow && this.statusFlow.isEnabled"
    }
  ],
  "visible": false
}
</#noparse>