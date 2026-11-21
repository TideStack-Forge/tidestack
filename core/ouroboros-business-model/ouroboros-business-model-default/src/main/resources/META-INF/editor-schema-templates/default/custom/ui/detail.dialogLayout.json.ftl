{
  "type": "fieldSet",
  "visibleOn": "<#noparse>${</#noparse>ui.mode === 'dialog'}",
  "title": "弹窗配置",
  "collapsable": true,
  "collapsed": true,
  "className": "mb-5",
  "body": [
    {
      "type": "button-group-select",
      "label": "弹窗尺寸",
      "name": "ui.dialog.size",
      "className": "mb-5",
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
