# LogicFlow Reference Index

更新日期：`2026-03-18`

这份索引只保留未来工作真正稳定、高价值的入口。长篇设计说明以模块目录 [`core/ouroboros-logicflow`](../../core/ouroboros-logicflow/README.md) 为准。

## Canonical Docs

- [README.md](../../core/ouroboros-logicflow/README.md)
  - 模块入口和阅读导航。
- [DESIGN.md](../../core/ouroboros-logicflow/DESIGN.md)
  - 当前架构快照。
- [logicflow metadata format spec](../../core/ouroboros-logicflow/design/ports-and-data-contracts/overview.md)
  - `FlowModelMeta / FlowNodeMeta / FlowConnectionMeta` 的 canonical 格式规范，以及 validation 状态相关字段分层。
- [reading-paths-and-module-boundaries](../../core/ouroboros-logicflow/design/reading-paths-and-module-boundaries/overview.md)
  - 角色化阅读路径和模块边界。
- [historical baseline (2026-03-18)](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-18-report-current-baseline.md)
  - 当时的实现事实和残余问题；属于历史审查材料，当前行为以模块设计与源码为准。
- [design assessment](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-17-report-design-assessment.md)
  - 外部讨论总结与代码现实的对照。
- [original user request](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/context/2026-03-17-evidence-user-request.md)
  - 原始任务背景。
- [discussion summary](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/context/2026-03-17-evidence-discussion-summary.md)
  - 用户提供的外部设计讨论总结。

## Core Entry Points

- [FlowModel.java](../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModel.java)
  - 公开执行合同。
- [DefaultFlowModel.java](../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)
  - 模型装配与静态校验入口。
- [LogicFlowUtil.java](../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)
  - 图分析、终点互斥、输入冲突与类型兼容。
- [FunctionalFlowCompiler.java](../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
  - 运行时编译、branch skip、error flow 执行。
- [FlowNodePortMeta.java](../../core/ouroboros-logicflow/ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java)
  - 输出端口互斥元数据。
- [ReturnNode.java](../../core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ReturnNode.java)
  - 内置成功终点。

## Dev And Editor Entry Points

- [LogicFlowValidationSnapshotSupport.java](../../core/ouroboros-logicflow/ouroboros-logicflow-dev/src/main/java/com/ouroboros/logicflow/dev/service/support/LogicFlowValidationSnapshotSupport.java)
  - 后端校验快照归一。
- [NodeDefine.java](../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/java/com/ouroboros/logicflow/authoring/model/NodeDefine.java)
  - authoring contract。
- [index.jsx](../../frontends/web/ouroboros-logicflow-editor/src/logicFlowEditor/index.jsx)
  - 编辑器顶层宿主桥接面。
- [outputPortSemantics.mjs](../../frontends/web/ouroboros-logicflow-editor/src/node/outputPortSemantics.mjs)
  - 前端 selective-output 语义消费。
- [validate.js](../../frontends/web/ouroboros-logicflow-editor/src/node/validate.js)
  - 前端图校验。

## Cross-Stack Assets

- [scenario.yaml](../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml)
  - validation diagnostics 跨栈场景入口。
- [contract.md](../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/contract.md)
  - 场景目标与验收口径。
- [frontend fixture](../../testing/common/fixtures/frontends/logicflow-editor-static/fixture.yaml)
  - editor 静态前端夹具。
- [data fixture](../../testing/common/fixtures/data/logicflow-validation-diagnostics/fixture.yaml)
  - develop 侧无效模型种子夹具。
