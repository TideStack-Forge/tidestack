<#noparse>
{
  "visibleOn": "${ui.isEnabled}",
  "type": "container",
  "body": [
    {
      "type": "divider"
    },
    {
      "label": "入口",
      "name": "ui.entryPoint",
      "type": "button-group-select",
      "options": [
        {
          "label": "独立菜单",
          "value": "menu",
          "visible": false
        },
        {
          "label": "工具栏",
          "value": "toolbar"
        },
        {
          "label": "操作列",
          "value": "operation"
        },
        {
          "label": "单条动作",
          "value": "itemActions"
        },
        {
          "label": "批量动作",
          "value": "bulkActions"
        }
      ],
      "multiple": false,
      "labelRemark": {
        "icon": "fa fa-question-circle",
        "content": "<ul><li><b>工具栏</b>:位于表格的头部</li><li><b>操作列</b>:位于表格最后一列</li><li><b>单条动作</b>:位于表格的头部，选中数据前不可用</li><li><b>批量动作</b>:位于表格头部，选中数据前不可用（或不可见）</li></ul>"
      },
      "columnRatio": 4
    },
    {
      "label": "打开方式",
      "name": "ui.mode",
      "type": "button-group-select",
      "options": [
        {
          "label": "弹窗",
          "value": "dialog",
          "hiddenOn": "${ ui.entryPoint == 'menu' }"
        },
        {
          "label": "抽屉",
          "value": "drawer",
          "hiddenOn": "${ ui.entryPoint == 'menu' }"
        },
        {
          "label": "新标签",
          "value": "tab"
        },
        {
          "label": "全屏",
          "value": "fullScreen",
          "visible": false
        },
        {
          "label": "API调用",
          "value": "api",
          "hiddenOn": "${ ui.entryPoint == 'menu' }",
          "disabledOn": "${ !api.isEnabled }"
        },
        {
          "label": "自定义",
          "value": "custom"
        }
      ],
      "multiple": false
    },
    {
      "type": "button-group-select",
      "name": "ui.buttonAlign",
      "label": "对齐方式",
      "visibleOn": "${ ui.entryPoint == 'toolbar' }",
      "options": [
        {
          "label": "左对齐",
          "value": "left"
        },
        {
          "label": "右对齐",
          "value": "right"
        }
      ]
    },
    {
      "type": "input-number",
      "name": "ui.buttonOrder",
      "label": "按钮顺序",
      "inline": true,
      "keyboard": true,
      "step": 1,
      "editorState": "default",
      "description": "按照从小到大依次排序，操作栏中查看、编辑、删除按钮的序号依次为10、20、30，工具栏中新建按钮序号为10",
      "placeholder": "顺序号"
    },</#noparse>
    {
      "type": "input-text",
      "name": "ui.buttonDisabledOn",
      "label": "禁用条件",
      "inline": true,
      "inputClassName": "w-60",
      "description": "<#noparse>需填写表达式，如：\\${ status == 1}",
      "hiddenOn": "${ ui.mode === 'custom'}</#noparse>"
    },
    <#include "detail.buttonStyle">,
    <#include "detail.dialogLayout">,
    <#include "detail.drawerLayout">,
    <#include "detail.pageDesign">,
    <#include "detail.apiCall">,
    <#include "detail.customAction">
  ]
}
