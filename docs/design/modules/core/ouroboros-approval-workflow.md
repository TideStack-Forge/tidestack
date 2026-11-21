# ouroboros-approval-workflow 模块设计摘要

## 模块定位

`ouroboros-approval-workflow` 是平台审批流引擎模块，负责把审批流程定义、流程转译、候选人解析、任务操作和流程事件统一组织起来。它和 `logicflow` 都处理“流程”，但前者更聚焦审批语义和工作流引擎适配，后者更偏通用逻辑编排引擎。

## 责任边界

该模块负责审批流程定义、审批节点语义、工作流引擎转译、候选人解析与事件处理，不负责通用逻辑流编排内核本身，也不负责业务模型或动态表单的通用定义。它可以集成动态表单和组织结构能力，但不应取代这些模块的核心职责。

## 依赖关系

该模块内部已分成流程定义层、引擎适配层、共享测试契约层、管理层和交付层，且当前生产装配路径只有 `flowable` 一条；它会消费组织结构、表单和平台运行能力，但与 `logicflow` 的边界应保持清晰——审批流强调审批状态机和工作流语义。共享扩展约定见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-approval-workflow/DESIGN.md](../../../../core/ouroboros-approval-workflow/DESIGN.md)
- `设计与实现契约`：[自定义表单接入审批流](../../../../core/ouroboros-approval-workflow/design/custom-form.md)
- `补充深度资料`：[元数据格式与图契约](../../../../core/ouroboros-approval-workflow/design/definition-metadata-and-graph-contracts/overview.md)
- `补充深度资料`：[Gateway / Join 语义](../../../../core/ouroboros-approval-workflow/design/gateway-join-semantics.md)
- `补充深度资料`：[审批流外部流程引擎对接设计（模块内权威设计；Accepted）](../../../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)
- `使用入口`：[core/ouroboros-approval-workflow/README.md](../../../../core/ouroboros-approval-workflow/README.md)

## 演进状态

`ouroboros-approval-workflow` 的设计稿已经把审批语义、引擎适配和候选人系统描述得比较完整，但在维护上最需要持续守住的是与 `logicflow`、动态表单和组织结构的边界。
