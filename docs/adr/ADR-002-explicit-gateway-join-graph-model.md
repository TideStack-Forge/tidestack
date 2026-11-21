# ADR-002: Approval Workflow 分支与汇聚图模型统一

**状态**: Accepted\
**日期**: 2026-04-16\
**实施日期**: 2026-04-16\
**决策者**: Approval Workflow Team\
**相关模块**: `core/ouroboros-approval-workflow`, `frontends/web/ouroboros-approval-workflow-designer`

## 背景

审批流定义在前端设计器、测试夹具与后端校验/转译之间出现了分支语义不一致：

- 一部分文档/场景把 XOR 当作显式网关节点来表达。
- 一部分实现又把 branch-edge、ConditionNode、edge `name` 当成另一套分支实体。
- 后端执行语义要求 XOR 走“普通节点 + 条件边”的路径选择模型。

这种语义分裂会导致：

- 同一流程在前后端表示不同。
- golden scenarios 与后端转译语义偏离。
- 文档无法作为唯一可信来源。

## 决策

采用普通节点多出边表达分流、显式 OR Join 表达汇聚的统一图模型，替代显式 split / XOR Gateway 与独立 branch-edge / ConditionNode 的并行表示。AND 保留为 deferred 能力。

当前分流判定、边字段、前后端限制及迁移规则统一维护在[Gateway / Join 语义](../../core/ouroboros-approval-workflow/design/gateway-join-semantics.md)；本文保留决策背景与后果，不再维护另一份当前规则。

## 后果

### 正面影响

- 前后端对 XOR/OR 的语义边界清晰且一致。
- 测试夹具和执行语义对齐，回归稳定性提升。
- 文档与实现可以形成单一事实来源。

### 负面影响

- 旧的 XOR 显式网关样例需要迁移为“普通节点 + 条件边”。
- 旧的 AND 推导口径与 branch-edge 命名口径需要回收到兼容层或历史文档，不再当成当前 V2 事实来源。

## 参考

- [Approval Workflow Gateway/Join 语义说明](../../core/ouroboros-approval-workflow/design/gateway-join-semantics.md)
- [审批流定义元数据与图契约](../../core/ouroboros-approval-workflow/design/definition-metadata-and-graph-contracts/overview.md)
