# ouroboros-logicflow 模块设计摘要

## 模块定位

`ouroboros-logicflow` 是平台的节点式流程编排与执行引擎，负责把业务流程定义为可组合、可扩展、可运行的执行模型。对维护者来说，它代表平台在"业务逻辑编排"上的核心运行时能力。

## 责任边界

该模块负责流程模型、节点抽象、执行引擎、节点扩展和运行时适配，不负责具体业务审批策略、应用管理页面或统一安全规则本身。它提供执行骨架，但不替代上层业务能力或平台交付逻辑。

## 依赖关系

以 `ouroboros-logicflow-core` 为中心，向外扩展出 `authoring`、`builtin-nodes`、`data-model`、`web-adapter`、`dev`、`runtime`、`debug-helper`、`extra-nodes` 等子模块（见 `core/ouroboros-logicflow/pom.xml`），并依赖 `ouroboros-util`、`common-model`、`common-expression`、`common-script` 等支撑能力。上层业务模型、能力模块与开发模块把它作为流程执行内核消费。

> 已移除：`ouroboros-logicflow-all` 聚合模块已由 `9c16a8103a`（2026-07-27）删除，不再是本模块的子模块。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-logicflow/DESIGN.md](../../../../core/ouroboros-logicflow/DESIGN.md)（模块权威设计）
- `推荐阅读`：[core/ouroboros-logicflow/README.md](../../../../core/ouroboros-logicflow/README.md)（模块入口与阅读顺序）
- `推荐阅读`：[design/reading-paths-and-module-boundaries/overview.md](../../../../core/ouroboros-logicflow/design/reading-paths-and-module-boundaries/overview.md)（阅读路径与模块边界）
- `补充深度资料`：[ports-and-data-contracts](../../../../core/ouroboros-logicflow/design/ports-and-data-contracts/overview.md)、[result-contracts-and-diagnostic-surfaces](../../../../core/ouroboros-logicflow/design/result-contracts-and-diagnostic-surfaces/overview.md)、[core-abstractions-and-patterns](../../../../core/ouroboros-logicflow/design/core-abstractions-and-patterns/overview.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)、[docs/logicflow 轻量索引](../../../logicflow/README.md)
- [运行时管理设计](../../cross-cutting/logicflow-runtime-management.md)说明持久覆盖、独立刷新协调与逐流程观测的跨模块机制。

## 演进状态

已形成以模块级 `README.md` + `DESIGN.md` + `design/` 专题目录为主链路的分层设计体系；早期"抽象骨架/设计模式"概览的内容已吸纳进 `design/core-abstractions-and-patterns/` 等专题，`docs/logicflow/` 只保留 repo 级轻量索引。聚合模块 `ouroboros-logicflow-all` 已删除。
