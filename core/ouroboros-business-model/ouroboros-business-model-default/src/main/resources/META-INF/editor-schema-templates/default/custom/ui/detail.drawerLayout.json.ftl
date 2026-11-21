{
  "type": "fieldSet",
  "visibleOn": "<#noparse>${</#noparse>ui.mode === 'drawer'}",
  "title": "抽屉配置",
  "collapsable": true,
  "collapsed": true,
  "className": "mb-5",
  "body": [
    {
      "type": "button-group-select",
      "label": "抽屉尺寸",
      "name": "ui.drawer.size",
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
      "name": "ui.drawer.resizable",
      "className": "mb-5"
    }
  ]
}
