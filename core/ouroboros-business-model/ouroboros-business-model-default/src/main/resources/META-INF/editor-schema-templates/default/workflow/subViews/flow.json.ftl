<#noparse>
{
  "title": "流程处理",
  "mode": "horizontal",
  "icon": "fas fa-tasks",
  "tab": [
    {
      "type": "switch",
      "labelWidth": 160,
      "name": "workflow.showProcessButton",
      "label": "在操作列显示处理按钮",
      "value": true
    },
    {
      "type": "button-group-select",
      "label": "处理表单打开方式",
      "name": "workflow.approvalFormShowMode",
      "labelWidth": 160,
      "multiple": false,
      "options": [
        {
          "label": "弹窗",
          "value": "dialog"
        },
        {
          "label": "抽屉",
          "value": "drawer"
        },
        {
          "label": "新标签",
          "value": "tab"
        }
      ],
      "value": "dialog"
    },
    {
      "type": "button-group-select",
      "label": "流程详情页面打开方式",
      "name": "workflow.detailShowMode",
      "labelWidth": 160,
      "multiple": false,
      "options": [
        {
          "label": "弹窗",
          "value": "dialog"
        },
        {
          "label": "抽屉",
          "value": "drawer"
        },
        {
          "label": "新标签",
          "value": "tab"
        }
      ],
      "value": "dialog"
    },
    {
      "type": "panel",
      "title": "允许动作",
      "body": [
        {
          "type": "checkbox",
          "name": "workflow.actions.pass.isEnabled",
          "label": false,
          "value": true,
          "disabled": true,
          "description": "显示通过按钮，点击后驱动流程引擎的节点流转",
          "option": "通过"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.reject.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允许驳回就会显示驳回按钮，驳回功能会将流程退回到最近的任务节点",
          "option": "驳回"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.sign.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允许加签就会显示加签按钮，加签功能会先让用户选择人员和加签方式，工作流引擎会自动创建相关任务",
          "option": "加签"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.transfer.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允许转办就会显示转办按钮，转办功能会先让用户选择人员，工作流引擎会将当前任务转交给该人员",
          "option": "转办"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.revoke.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允许撤回就会显示撤回按钮，撤回功能不会结束流程实例，而是把整个流程退回到开始节点",
          "option": "撤回"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.cancel.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允许撤销就会显示撤销按钮，撤销功能是结束流程实例并触发撤销事件",
          "option": "撤销"
        },
        {
          "type": "checkbox",
          "name": "workflow.actions.veto.isEnabled",
          "label": false,
          "value": true,
          "description": "节点允否决就会显示否决按钮，否决功能是结束流程实例并触发否决事件",
          "option": "否决"
        }
      ]
    },
    {
      "type": "panel",
      "title": "高级动作",
      "body": [
        {
          "type": "checkbox",
          "name": "workflow.advanceActions.freePass.isEnabled",
          "label": false,
          "value": true,
          "description": "自由审批是一个权限极大的能力，在工作流引擎相关节点开启后，在此节点的执行人可以任意指派人员进行审批，审批结束后再回到本节点，如此循环，直到本节点执行通过操作",
          "option": "允许自由审批"
        },
        {
          "type": "checkbox",
          "name": "workflow.advanceActions.freeReject.isEnabled",
          "label": false,
          "value": true,
          "description": "当在工作流引擎的节点配置中开启了允许自由驳回，当前节点会显示\"自由驳回\"按钮，用户可选择驳回至指定节点",
          "option": "允许自由驳回"
        },
        {
          "type": "checkbox",
          "name": "workflow.advanceActions.freeRecover.isEnabled",
          "label": false,
          "value": false,
          "description": "当调用驳回接口时传入了 `isFreedomRecovery` 参数，当前节点会显示\"选择下个节点\"按钮，允许用户自由选择重入的节点，只能选择当前节点到驳回节点之间的节点（包括驳回节点），此功能应谨慎使用，因为可能会跳过某些节点，容易产生责任不清的情况",
          "option": "允许自由重入"
        }
      ]
    }
  ]
}
</#noparse>
