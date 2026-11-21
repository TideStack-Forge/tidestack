<#import "../lib" as lib>
<#noparse>
{
  "title": "索引页",
  "mode": "horizontal",
  "icon": "far fa-rectangle-list",
  "body": [
    {
      "label": "启用",
      "type": "switch",
      "name": "views.index.isEnabled",
      "value": true
    },
    {
      "type": "container",
      "visibleOn": "${views.index.isEnabled}",
      "body": [
        {
          "type": "switch",
          "label": "自动视图",
          "name": "views.index.isAuto",
          "value": true,
          "visibleOn": "${ views.index.schema }"
        },
        {
          "type": "container",
          "visibleOn": "${ views.index.isAuto && (fields && fields.length > 0 || relations && relations.length > 0) }",
          "body": [
            {
              "type": "input-text",
              "name": "views.index.title",
              "label": "标题",
              "placeholder": "${ title }列表页",
              "visible": false
            },
            {
              "type": "list-select",
              "label": "类型",
              "name": "views.index.type",
              "multiple": false,
              "options": [
                {
                  "label": "表格",
                  "value": "表格"
                }
              ],
              "value": "表格"
            },
            {
              "type": "fieldSet",
              "title": "数据选择",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 }",
              "body": [
                </#noparse><#include "index.selectConfig"><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "显示配置",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 }",
              "body": [
                {
                  "type": "switch",
                  "label": "占满高度",
                  "name": "views.index.isAutoFillHeight",
                  "description": "开启后，自动适应父容器高度"
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "表格配置",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 && views.index.type == '表格' }",
              "body": [
                </#noparse><#include "index.tableConfig"><#noparse>
              ]
            },
            {
              "type": "fieldSet",
              "title": "操作列",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 && views.index.type == '表格' }",
              "body": [
                {
                  "type": "input-number",
                  "label": "列宽",
                  "name": "views.index.operationsColumn.width",
                  "size": "sm"
                },
                {
                  "type": "switch",
                  "label": "固定列",
                  "name": "views.index.operationsColumn.fixed",
                  "value": true
                },
                {
                  "type": "group",
                  "body": [
                    {
                      "type": "button-group-select",
                      "label": "按钮样式",
                      "name": "views.index.operationsColumn.buttonStyle",
                      "options": [
                        {
                          "label": "默认",
                          "value": "default"
                        },
                        {
                          "label": "按钮",
                          "value": "textButton"
                        },
                        {
                          "label": "自定义",
                          "value": "custom",
                          "hidden": true
                        }
                      ],
                      "value": "default",
                      "inline": true
                    },
                    {
                      "type": "select",
                      "label": "颜色",
                      "name": "views.index.operationsColumn.buttonColorSet",
                      "value": "primary-primary-danger",
                      "visibleOn": "${views.index.operationsColumn.buttonStyle == 'textButton'}",
                      "options": [
                        {
                          "label": "预置1",
                          "value": "primary-primary-danger"
                        },
                        {
                          "label": "预置2",
                          "value": "default-primary-danger"
                        }
                      ]
                    },
                    {
                      "type": "select",
                      "label": "大小",
                      "name": "views.index.operationsColumn.buttonSize",
                      "visibleOn": "${views.index.operationsColumn.buttonStyle == 'textButton'}",
                      "value": "sm",
                      "options": [
                        {
                          "label": "中",
                          "value": "md"
                        },
                        {
                          "label": "小",
                          "value": "sm"
                        },
                        {
                          "label": "极小",
                          "value": "xs"
                        }
                      ]
                    },
                    {
                      "type": "switch",
                      "label": "图标",
                      "name": "views.index.operationsColumn.buttonIcon",
                      "value": false,
                      "visibleOn": "${views.index.operationsColumn.buttonStyle == 'textButton'}"
                    }
                  ]
                },
                {
                  "type": "container",
                  "body": [
                    "<span class='text-secondary'>预览</span>",
                    {
                      "type": "button-toolbar",
                      "buttons": [
                        {
                          "type": "button",
                          "icon": "fa fa-eye",
                          "level": "link"
                        },
                        {
                          "type": "button",
                          "icon": "fa fa-pencil",
                          "level": "link"
                        },
                        {
                          "type": "button",
                          "icon": "fa fa-times text-danger",
                          "level": "link"
                        }
                      ],
                      "visibleOn": "${views.index.operationsColumn.buttonStyle == 'default'}"
                    },
                    {
                      "type": "amis-schema",
                      "autoRemapping": true,
                      "source": {
                        "type": "button-toolbar",
                        "visibleOn": "${views.index.operationsColumn.buttonStyle == 'textButton' && !views.index.operationsColumn.buttonIcon}",
                        "buttons": [
                          {
                            "type": "button",
                            "label": "查看",
                            "icon": "${!!views.index.operationsColumn.buttonIcon ? 'fa fa-eye' : undefined}",
                            "size": "${views.index.operationsColumn.buttonSize}",
                            "level": "${views.index.operationsColumn.buttonColorSet == 'default-primary-danger' ? 'default' : 'primary'}"
                          },
                          {
                            "type": "button",
                            "label": "编辑",
                            "icon": "${!!views.index.operationsColumn.buttonIcon ? 'fa fa-pencil' : undefined}",
                            "size": "${views.index.operationsColumn.buttonSize}",
                            "level": "primary"
                          },
                          {
                            "type": "button",
                            "label": "删除",
                            "icon": "${!!views.index.operationsColumn.buttonIcon ? 'fa fa-times' : undefined}",
                            "size": "${views.index.operationsColumn.buttonSize}",
                            "level": "danger"
                          }
                        ]
                      },
                      "visibleOn": "${views.index.operationsColumn.buttonStyle == 'textButton'}"
                    }
                  ]
                }
              ]
            },
            {
              "type": "fieldSet",
              "title": "过滤器",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 }",
              "body": [
                </#noparse><#include "index.filterConfig"><#noparse>
              ],
              "size": "xl",
              "className": "",
              "bodyClassName": "m-b-lg p-t"
            },
            {
              "type": "fieldSet",
              "title": "导入导出",
              "collapsable": true,
              "collapsed": true,
              "visibleOn": "${ fields && fields.length > 0 || relations && relations.length > 0 }",
              "body": [
                {
                  "type": "switch",
                  "label": "启用导出全部",
                  "name": "views.index.isExportAllEnabled",
                  "value": true
                },
                {
                  "type": "switch",
                  "label": "启用导出当前页",
                  "name": "views.index.isExportCurrentPageEnabled",
                  "value": true
                },
                {
                  "type": "switch",
                  "label": "启用导入",
                  "name": "views.index.isImportEnabled",
                  "value": false,
                  "visible": false
                },
                {
                  "type": "switch",
                  "label": "启用上传导入",
                  "name": "views.index.isImportByUploadEnabled",
                  "value": false,
                  "disabled": true,
                  "visible": false,
                  "description": "上传导入功能正在开发中，敬请期待"
                }
              ],
              "size": "xl",
              "className": "",
              "bodyClassName": "m-b-lg p-t"
            }
          ]
        },</#noparse>
        <@lib.customViewButton viewName="index" viewTitle="索引页"/>,
        <@lib.previewViewButton viewName="index" viewTitle="索引页" viewSubPath=""/>
      ]
    }
  ]
}