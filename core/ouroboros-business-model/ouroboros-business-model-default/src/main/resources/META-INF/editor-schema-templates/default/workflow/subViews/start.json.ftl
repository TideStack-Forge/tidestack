<#noparse>
{
  "title": "流程发起",
  "mode": "horizontal",
  "icon": "fas fa-play",
  "tab": [
    {
      "type": "button-group-select",
      "name": "workflow.startMode",
      "label": "发起方式",
      "description": "若选择提交表单自动发起，则表单填写完成后点击提交按钮即可发起流程；若选择手动发起，则需要在列表页中点击\"发起流程\"按钮进行发起；无论是否选择了手动发起，列表的相关业务在已提交状态下都会显示\"发起流程\"按钮",
      "value": "auto",
      "options": [
        {
          "label": "提交表单自动发起",
          "value": "auto"
        },
        {
          "label": "手动发起",
          "value": "manual"
        }
      ]
    },
    {
      "type": "checkboxes",
      "name": "workflow.restartableStatus",
      "label": "可重新发起状态",
      "description": "此处状态判断以映射后的业务状态为准，若在状态映射中未区分三种异常结束状态，则相同业务状态的结束状态均可可重新发起",
      "joinValues": false,
      "extractValue": true,
      "value": [],
      "options": [
        {
          "label": "否决",
          "value": "veto"
        },
        {
          "label": "撤销",
          "value": "canceled"
        },
        {
          "label": "终止",
          "value": "terminated"
        }
      ]
    },
    {
      "type": "switch",
      "name": "workflow.sendEditUrl",
      "label": "发送编辑连接",
      "description": "勾选后，发起流程时会发送 editUrl 参数给流程引擎，统一门户中的待办功能会在恰当的时机使用此参数来跳转至编辑页面",
      "value": false
    },
    {
      "type": "button-group-select",
      "label": "流程表单数据",
      "name": "workflow.formDataMode",
      "value": "${ workflow.formDataMode | default:all-fields }",
      "className": "mt-5",
      "tooltip": "流程会自动把表单数据传递给工作流引擎",
      "options": [
        {
          "label": "全部字段",
          "value": "all-fields"
        },
        {
          "label": "选择字段",
          "value": "select-fields",
          "hidden": true
        },
        {
          "label": "数据映射",
          "value": "custom"
        }
      ],
      "onEvent": {
        "change": {
          "actions": [
            {
              "actionType": "setValue",
              "componentId": "workflowFormDataMapping",
              "expression": "${value === 'custom'}",
              "args": {
                "value": "${ ${ ARRAYMAP(CONCAT(fields, ARRAYFILTER(relations, r => r.type !== 'child')), f => ({ name: f.name, expr: f.name })) } | arrayToObject:name:expr }"
              }
            },
            {
              "actionType": "setValue",
              "componentId": "workflowFormDataMapping",
              "expression": "${value === 'auto'}",
              "args": {
                "value": null
              }
            }
          ]
        }
      }
    },
    {
      "id": "workflowFormDataMapping",
      "name": "workflow.formDataMapping",
      "type": "hidden"
    },
    {
      "type": "input-kv",
      "label": "数据映射",
      "name": "workflow.formDataMapping",
      "keyPlaceholder": "流程引擎表单字段名",
      "id": "workflowFormDataMappingInputKV",
      "wrapperCustomStyle": {
        ".cxd-TextControl-inputSuffix,.cxd-TextControl-inputPrefix": {
          "color": "var(--primary)"
        },
        ".cxd-Form-col:first-child": {
          "flex-basis": "200px",
          "flex-grow": "0",
          "flex-shrink": "0"
        }
      },
      "keySchema": {
        "type": "input-text",
        "required": true
      },
      "valueSchema": {
        "type": "script-editor", "language": "spel",
        "codeSite": "business-model.workflow-mapping", "codeResourceType": "business-model",
        "codePointer": "/workflow/formDataMapping/**",
        "intelligenceApi": "/api/dev/${appName}/code-intelligence/context",
        "prefix": "\\${",
        "suffix": "}",
        "required": true,
        "placeholder": "映射表达式（SpEL），可以直接用模型的字段名取值"
      },
      "visibleOn": "${ workflow.formDataMode === 'custom' }"
    }
  ]
}
</#noparse>
