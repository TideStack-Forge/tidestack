<#noparse>
{
  "type": "wrapper",
  "className": "h-full max-h-full p-0 flex flex-col",
  "body": [
    {
      "type": "panel",
      "className": "p-0 flex-grow-0",
      "bodyClassName": "p-0",
      "body": [
        {
          "type": "collapse",
          "className": "border-0 shadow-none",
          "headingClassName": "px-4 py-3 w-full",
          "bodyClassName": "px-4 pb-4 pt-0",
          "showArrow": false,
          "collapsed": true,
          "collapsable": true,
          "mountOnEnter": true,
          "unmountOnExit": true,
          "header": {
            "type": "wrapper",
            "className": "flex p-0 justify-between items-start w-full",
            "body": [
              {
                "type": "static-text",
                "name": "fullName",
                "mode": "horizontal",
                "label": "仪表板",
                "className": "mb-0",
                "labelWidth": 90
              },
              {
                "type": "wrapper",
                "className": "p-0 flex items-center gap-4",
                "body": [
                  {
                    "type": "switch",
                    "label": false,
                    "name": "isEnabled",
                    "onText": "启用仪表板",
                    "offText": "停用仪表板",
                    "className": "mb-0"
                  },
                  {
                    "type": "wrapper",
                    "className": "p-0 text-secondary flex items-center hover:text-primary mt-2",
                    "body": [
                      "展开",
                      {
                        "type": "icon",
                        "vendor": "",
                        "className": "ml-1",
                        "icon": "fas fa-angle-down"
                      }
                    ]
                  }
                ]
              }
            ]
          },
          "collapseHeader": {
            "type": "wrapper",
            "className": "flex p-0 justify-between items-start w-full",
            "body": [
              {
                "type": "static-text",
                "name": "fullName",
                "mode": "horizontal",
                "label": "仪表板",
                "className": "mb-0",
                "labelWidth": 90
              },
              {
                "type": "wrapper",
                "className": "p-0 flex items-center gap-4",
                "body": [
                  {
                    "type": "switch",
                    "label": false,
                    "name": "isEnabled",
                    "onText": "启用仪表板",
                    "offText": "停用仪表板",
                    "className": "mb-0"
                  },
                  {
                    "type": "wrapper",
                    "className": "p-0 text-secondary flex items-center hover:text-primary mt-2",
                    "body": [
                      "收起",
                      {
                        "type": "icon",
                        "vendor": "",
                        "className": "ml-1",
                        "icon": "fas fa-angle-up"
                      }
                    ]
                  }
                ]
              }
            ]
          },
          "body": [
            {
              "type": "group",
              "className": "mt-4",
              "body": [
                {
                  "type": "input-text",
                  "name": "title",
                  "label": "仪表板标题",
                  "placeholder": "请输入仪表板标题",
                  "required": true
                },
                {
                  "type": "ouroboros-icon-picker",
                  "label": "图标",
                  "name": "icon",
                  "columnRatio": 4,
                  "labelWidth": 40,
                  "placeholder": "请输入图标名或地址"
                }
              ]
            },
            {
              "type": "textarea",
              "name": "description",
              "label": "仪表板描述",
              "placeholder": "请输入仪表板描述"
            },
            {
              "type": "group",
              "className": "mt-4",
              "body": [
                {
                  "type": "input-number",
                  "name": "layout.cols",
                  "label": "栅格列数",
                  "min": 1,
                  "max": 24,
                  "value": 24,
                  "required": true
                },
                {
                  "type": "input-number",
                  "name": "layout.rowHeight",
                  "label": "栅格行高",
                  "min": 1,
                  "value": 30,
                  "required": true
                },
                {
                  "type": "switch",
                  "name": "layout.draggable",
                  "label": "允许拖动",
                  "value": true
                },
                {
                  "type": "switch",
                  "name": "layout.resizable",
                  "label": "允许调整大小",
                  "value": true
                }
              ]
            }
          ]
        }
      ]
    },
    { "type": "hidden", "name": "namespace" },
    { "type": "hidden", "name": "name" },
    { "type": "hidden", "name": "modelType", "value": "dashboard" },
    { "type": "hidden", "name": "formatVersion", "value": "1.0" },
    {
      "type": "tabs",
      "tabsMode": "simple",
      "className": "flex flex-col flex-grow h-full",
      "style": { "height": "200px" },
      "contentClassName": "flex-grow h-full overflow-y-auto",
      "linksClassName": "flex justify-center",
      "tabs": [
        {
          "title": "画布",
          "hash": "canvas",
          "icon": "fas fa-table-cells-large",
          "className": "px-0",
          "body": [
                {
                  "type": "dashboard",
                  "persistAsFields": true,
                  "editablePlaceholder": {
                    "type": "container",
                    "className": "dashboard-editor-empty-state",
                    "body": [
                      {
                        "type": "tpl",
                        "tpl": "<div class=\"dashboard-editor-empty-state__title\">开始布置仪表板</div>"
                      },
                      {
                        "type": "tpl",
                        "tpl": "<div class=\"dashboard-editor-empty-state__description\">从组件列表中添加图表并调整布局。</div>"
                      },
                      {
                        "type": "button",
                        "label": "开始编辑",
                        "level": "primary",
                        "actionType": "dashboard-edit-mode"
                      }
                    ]
                  },
                  "source": [
                    {
                      "name": "chart",
                      "title": "图表",
                      "description": "显示图表定义",
                      "category": "统计分析",
                      "allowHeader": true,
                      "allowFooter": false,
                      "sizeConstraints": {
                        "min": { "rows": 6, "cols": 6 },
                        "max": { "rows": 24, "cols": 24 }
                      },
                      "defaultSize": { "rows": 10, "cols": 12 },
                      "defaultConfig": {
                        "title": "图表",
                        "showHeader": true
                      },
                      "thumbSchema": {
                        "type": "container",
                        "className": "statistics-chart-widget-thumb",
                        "body": [
                          {
                            "type": "tpl",
                            "tpl": "<div class=\"statistics-chart-widget-thumb__icon\"><i class=\"fa fa-bar-chart\"></i></div>"
                          },
                          {
                            "type": "tpl",
                            "tpl": "<div class=\"statistics-chart-widget-thumb__title\">图表</div>"
                          },
                          {
                            "type": "tpl",
                            "tpl": "<div class=\"statistics-chart-widget-thumb__description\">选择图表后显示数据</div>"
                          }
                        ]
                      },
                      "configFormItems": [
                        {
                          "type": "select",
                          "name": "chartName",
                          "label": "图表名称",
                          "required": true,
                          "searchable": true,
                          "source": "/api/dev/${appName}/chart/options",
                          "description": "只能选择已登记的图表。"
                        }
                      ],
                      "schemaTpl": {
                        "type": "chart-runtime",
                        "chartName": "#{chartName}",
                        "api": {
                          "method": "post",
                          "url": "/api/dev/${appName}/chart/#{chartName}/preview"
                        }
                      },
                      "configDialogSize": "md"
                    }
                  ],
                  "description": "拖拽图表组件完成布局，保存后写入仪表板业务模型。"
                }
          ]
        }
      ]
    }
  ]
}
</#noparse>
