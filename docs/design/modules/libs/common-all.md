# common-all 模块设计摘要

## 模块定位

`common-all` 是 `common-*` 组件族中的聚合模块，负责把一组最核心、最常一起使用的公共组件打包成单一依赖入口。它的意义不在于定义新能力，而在于降低上层模块一次性引入公共能力时的依赖管理成本。

## 责任边界

该模块负责 Maven 依赖聚合，不负责任何运行时代码、SPI 实现或额外语义。它不应该承载新的公共逻辑，也不应成为“所有 common 模块都必须塞进去”的黑盒；凡是没有进入聚合的模块，通常意味着它们更偏可选或场景化。

## 依赖关系

从 `pom.xml` 看，`common-all` 当前聚合的是 `common-web`、`common-expression`、`common-app`、`common-exception`、`common-model` 和 `common-script`。它明确没有把 `common-exception-mapper`、`common-service`、`common-dev`、`common-cache`、`common-template`、`common-lock` 纳入统一聚合，因此这些能力仍要求按需显式引入。

## 关键抽象

- `common-all` 本质上是一个 `packaging=pom` 的依赖聚合入口。
- 它表达的是“默认核心公共能力集合”，而不是“完整公共能力全集”。

## 关键子模块

- 被聚合的核心模块：`common-web`、`common-expression`、`common-app`、`common-exception`、`common-model`、`common-script`。
- 未被聚合但经常相关的可选模块：`common-cache`、`common-template`、`common-dev`、`common-service`、`common-exception-mapper`、`common-lock`。

## 扩展机制

该模块本身没有运行时扩展机制。维护时真正需要谨慎的是“聚合边界变更”：一旦调整包含列表，就会影响大量上层模块的默认依赖面，因此应把这类调整视为架构治理变更，而不是普通依赖顺手增减。

## 深入文档索引

- `推荐阅读`：[libs/common/common-all/GUIDE.md](../../../../libs/common/common-all/GUIDE.md)
- `补充深度资料`：[common-modules 组件族边界（跨模块专题）](../../cross-cutting/common-modules.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-all` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-all` 当前更像一个“约定好的默认组合”，而不是持续扩张的聚合全集。后续维护应优先保持它的克制，只有当某能力已经被证明确实属于大多数模块默认必需时，才考虑纳入聚合。
