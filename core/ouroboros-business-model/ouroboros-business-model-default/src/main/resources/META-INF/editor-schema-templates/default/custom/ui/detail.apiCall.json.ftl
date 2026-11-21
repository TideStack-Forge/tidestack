<#noparse>
{
  "type": "fieldSet",
  "visibleOn": "${ ui.mode == 'api' }",
  "title": "API调用",
  "collapsable": true,
  "collapsed": false,
  "body": [
	{
	  "type": "switch",
	  "name": "ui.apiConfirm",
	  "label": "调用前确认"
	},
	{
	  "type": "input-text",
	  "name": "ui.apiConfirmTitle",
	  "label": "标题",
	  "placeholder": "留空则使用默认标题",
	  "visibleOn": "${ui.apiConfirm}"
	},
	{
	  "type": "textarea",
	  "name": "ui.apiConfirmText",
	  "label": "文字",
	  "required": true,
	  "placeholder": "请填写提示信息",
	  "visibleOn": "${ui.apiConfirm}",
	  "description": "在操作栏按钮中可以使用\\${字段名}的方式嵌入当前数据记录的字段值"
	},
	{
	  "type": "divider",
	  "visibleOn": "${ui.apiConfirm}"
	},
	{
	  "type": "switch",
	  "name": "ui.apiFeedback",
	  "label": "成功反馈弹窗"
	},
	{
	  "type": "input-text",
	  "name": "ui.apiFeedbackTitle",
	  "label": "弹窗标题",
	  "placeholder": "留空则使用默认标题",
	  "visibleOn": "${ui.apiFeedback}"
	},
	{
	  "type": "amis-schema-editor",
	  "name": "ui.apiFeedbackBody",
	  "label": "弹窗内容",
	  "required": true,
	  "visibleOn": "${ui.apiFeedback}",
	  "autoRemapping": true,
	  "switchLayout": false,
	  "defaultSchema": {
		"type": "tpl",
		"tpl": "${title}执行成功"
	  }
	},
	{
	  "type": "divider",
	  "visibleOn": "${ui.apiFeedback}"
	},
	{
	  "type": "switch",
	  "name": "ui.apiReload",
	  "label": "成功后刷新列表",
	  "visible": false
	},
	{
	  "type": "switch",
	  "name": "ui.apiRedirect",
	  "label": "成功后打开页面"
	},
	{
	  "type": "input-text",
	  "name": "ui.apiRedirectUrl",
	  "label": "页面地址",
	  "required": true,
	  "visibleOn": "ui.apiRedirect",
	  "description": "在操作栏按钮中可以使用\\${字段名}的方式嵌入当前数据记录的字段值"
	}
  ]
}
</#noparse>
