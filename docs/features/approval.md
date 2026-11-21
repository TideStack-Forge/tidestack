# 审批

为业务应用提供审批定义、候选人解析、发起/任务动作、状态与流程事件，并对接外部审批权威。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 内置审批 | 定义模型、图模型转译、候选人和任务动作由审批引擎合同组织，当前生产装配为 Flowable。 |
| 设计与查看 | React Flow 是审批设计器和只读流程图的唯一当前 renderer，定义与画布通过既有适配层衔接。 |
| 外部审批 | 外部集成在审批内核之外处理渠道连接、绑定、动作及结果事实；按渠道、authority 和实际 capability 判断。 |

## 实现情况

发起与任务主链及外部接入骨架已有实现。Activiti6 已移到 legacy-compat，不参与主线反应堆；外部镜像投影链已删除，钉钉 RESULT_ONLY 与企业微信/飞书能力不能混为一种承诺。待办中心仍是提案。真实企业验证和后置能力见外部规格及 DOC-001/002。

本页按源码与配置说明能力。测试入口说明可复跑资产，实际运行与线上发布状态由对应版本的交付证据确认。

## 启用条件

内置流程需 Flowable、数据源、身份/候选人来源及流程定义；外部渠道还需企业权限、连接和密钥引用、回调配置。不能由源码存在推断租户已可用。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [core/ouroboros-approval-workflow](../../core/ouroboros-approval-workflow) · [设计/指南](../design/modules/core/ouroboros-approval-workflow.md) | [测试入口](../../core/ouroboros-approval-workflow/ouroboros-approval-workflow-business-model-form/src/test/java/com/ouroboros/workflow/approval/businessmodelform/ApprovalWorkflowBusinessModelFormAutoConfigurationTest.java) |
| [ability/ouroboros-approval-workflow-external-integration](../../ability/ouroboros-approval-workflow-external-integration) · [设计/指南](../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md) | [测试入口](../../ability/ouroboros-approval-workflow-external-integration/ouroboros-approval-workflow-external-integration-core/src/test/java/com/ouroboros/workflow/approval/external/EngineIntegrationContractsTest.java) |
| [frontends/web/ouroboros-approval-workflow-designer](../../frontends/web/ouroboros-approval-workflow-designer) | [测试入口](../../frontends/web/ouroboros-approval-workflow-designer/tests/integration/workflow/canvas-assist-toolbar.spec.js) |

## 关联文档

- [docs/requirements/workflow/external-engine.md](../requirements/workflow/external-engine.md)
- [docs/requirements/workflow/available-workflows.md](../requirements/workflow/available-workflows.md)
- [core/ouroboros-approval-workflow/DESIGN.md](../../core/ouroboros-approval-workflow/DESIGN.md)
