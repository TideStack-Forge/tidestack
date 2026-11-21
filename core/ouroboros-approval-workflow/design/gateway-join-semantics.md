# Approval Workflow Gateway / Join 语义

## 目标

统一前端设计器、测试夹具与后端执行语义，避免 XOR/OR 表达不一致，并把 V2 图模型收紧到一套不会再引入 split node / branch-edge 歧义的规则。

这份文档只解释“分支与汇聚怎么表达”。审批流定义在顶层字段、节点子结构、连线字段、候选人和表单权限上的正式数据契约，另见 [`core/ouroboros-approval-workflow/design/definition-metadata-and-graph-contracts/overview.md`](definition-metadata-and-graph-contracts/overview.md)。

## 唯一模型

### 分流起点

- V2 **没有显式 split node 概念**。
- 任意持久化节点只要 `outgoing > 1`，就表示它是分流起点。
- 不按节点类型区分“谁能 split”；规则只看连线数量与下游 Join 关系。

### XOR（互斥分支）

- 没有下游 `JoinNode` 时，多出边分流默认按 XOR 理解。
- 运行时只选择一条命中的路线继续前进。
- 不使用显式 XOR Gateway 或 XOR Join 节点。

### OR（允许多路并发命中）

- 使用显式 `JoinNode(joinType=OR)` 作为汇聚点。
- 某组上游分支是否属于 OR，不在 split 时单独声明，而是由它们最终汇聚到同一个 OR JoinNode 反向界定。
- OR 允许多条命中路线同时继续执行；连接到 OR JoinNode 的场景不应再被当成“非法 merge”。

### AND

- AND 在这一轮明确 `deferred`。
- 当前不提供 `JoinNode(joinType=AND)` 给编辑器新增，也不通过“全无条件边”之类规则隐式推导 AND。

## 边语义

- 分支与汇聚语义统一使用 `SEQUENCE` 边。
- 不存在独立的 branch-edge 实体；普通 edge 与条件 edge 是同一种对象。
- `condition` 与 `order` 只是 edge 元数据，由编辑器操作生成。
- `edge.name` 不是稳定 V2 contract，不作为区分边语义的依据。

## 前后端一致性

- 前端设计器不暴露 XOR Gateway/Join 节点，也不再暴露 split node。
- 前端在 XOR 场景仅输出“普通节点 + `SEQUENCE` 边”；条件只是边元数据。
- 前端在 OR 场景通过显式 `JoinNode(joinType=OR)` 表达允许并发汇聚。
- 后端 Validator 不接受显式拆分网关节点；当前轮次只接受 `JoinNode(joinType=OR)` 作为稳定能力。
- 后端/转译层对 `outgoing > 1` 的节点负责映射 split 语义，但不要求定义层先有 split node。

## 迁移约束

- 历史中显式 Gateway 节点、XOR Join、branch-edge 命名语义都属于迁移对象，不再推荐新增。
- 新增示例、测试夹具、golden scenarios 必须使用本文件定义的唯一模型。

## 相关 ADR

- [ADR-002: Approval Workflow 分支与汇聚图模型统一](../../../docs/adr/ADR-002-explicit-gateway-join-graph-model.md)
- [审批流定义元数据与图契约](definition-metadata-and-graph-contracts/overview.md)
