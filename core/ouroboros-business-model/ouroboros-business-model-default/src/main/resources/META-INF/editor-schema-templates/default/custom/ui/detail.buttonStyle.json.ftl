<#noparse>
{
  "type": "fieldSet",
  "title": "按钮样式",
  "className": "mb-5",
  "collapsable": true,
  "collapsed": true,
  "hiddenOn": "${ ui.mode == 'custom' }",
  "body": [
    {
      "type": "flex",
      "justify": "flex-start",
      "items": [
      {
        "type": "wrapper",
        "className": "p-0  w-1/2",
        "body": [
        {
          "type": "select",
          "label": "样式",
          "name": "ui.buttonLevel",
          "menuTpl": {
          "type": "button",
          "label": "${label}",
          "size": "sm",
          "level": "${value}"
          },
          "options": [
            {
              "label": "默认",
              "value": "default"
            },
          {
            "label": "主要",
            "value": "primary"
          },
          {
            "label": "次要",
            "value": "secondary"
          },
          {
            "label": "提示",
            "value": "info"
          },
          {
            "label": "成功",
            "value": "success"
          },
          {
            "label": "警告",
            "value": "warning"
          },
          {
            "label": "危险",
            "value": "danger"
          },
          {
            "label": "浅色",
            "value": "light"
          },
          {
            "label": "深色",
            "value": "dark"
          },
          {
            "label": "强调",
            "value": "enhance"
          },
          {
            "label": "链接",
            "value": "link"
          }
          ],
          "multiple": false
        },
        {
          "type": "button-group-select",
          "label": "尺寸",
          "name": "ui.buttonSize",
          "options": [
          {
            "label": "极小",
            "value": "xs"
          },
          {
            "label": "小",
            "value": "sm"
          },
          {
            "label": "中等",
            "value": "md"
          },
          {
            "label": "大",
            "value": "lg"
          }
          ],
          "multiple": false
        },
        {
          "type": "group",
          "body": [
          {
            "name": "ui.buttonLabel",
            "type": "input-text",
            "label": "按钮文字",
            "columnRatio": 8,
            "editorState": "default",
            "placeholder": "留空则使用功能标题作为按钮文字"
          },
          {
            "type": "switch",
            "label": false,
            "option": "",
            "name": "ui.buttonLabelVisible",
            "falseValue": false,
            "trueValue": true,
            "onText": "显示",
            "offText": "隐藏"
          }
          ]
        },
        {
          "name": "ui.buttonIcon",
          "type": "ouroboros-icon-picker",
          "label": "按钮图标",
          "placeholder": "留空则使用功能图标作为按钮图标"
        },
        {
          "type": "fieldSet",
          "title": "高级",
          "collapseTitle": "收起",
          "titlePosition": "bottom",
          "collapsable": true,
          "collapsed": true,
          "body": [
          {
            "type": "input-color",
            "name": "ui.buttonBackgroundColor",
            "label": "背景颜色",
            "clearable": true
          },
          {
            "type": "input-color",
            "name": "ui.buttonForegroundColor",
            "label": "文字颜色",
            "clearable": true
          }
          ]
        }
        ]
      },
      {
        "type": "wrapper",
        "className": "w-1/2 border border-dashed border-gray-200 ml-5",
        "body": [
        {
          "type": "tpl",
          "tpl": "预览",
          "className": "text-secondary"
        },
        {
          "type": "wrapper",
          "body": [
          {
            "type": "amis-schema",
            "autoRemapping": true,
            "source": {
            "type": "button",
            "label": "${ ui.buttonLabelVisible && (ui.buttonLabel || title) }",
            "icon": "${ ui.buttonIcon || icon }",
            "size": "${ ui.buttonSize || 'md'}",
            "level": "${ ui.buttonLevel || 'default'}",
            "style": {
              "background-color": "${ ui.buttonBackgroundColor }",
              "color": "${ ui.buttonForegroundColor }"
            }
            }
          }
          ]
        }
        ]
      }
      ]
    }
  ]
}
</#noparse>
