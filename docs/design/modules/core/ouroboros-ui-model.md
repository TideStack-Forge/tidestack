# ouroboros-ui-model 模块设计摘要

## 模块定位

`ouroboros-ui-model` 负责定义平台中的 UI 元数据、Schema 表达与微前端承载方式，是连接后端模型能力与前端交付边界的核心引擎之一。它帮助维护者把“页面是什么”与“页面如何被渲染”分离开来。

## 责任边界

该模块负责 UI 元数据抽象、Schema 组织、低代码表单承载和 MicroWeb 相关的后端模型与运行时支撑，不负责真正的前端渲染实现或最终交付页面的产品逻辑。它解决的是 UI 模型化问题，而不是替代 `frontends` 的实现职责。

## 依赖关系

位于核心引擎层，内部按 `core`、`runtime`、`schema`、`lowcode`、`microweb`、`dev` 系列子模块分层；与业务模型、菜单模型、开发平台和前端交付密切协作，向上被 `dev-module`、`platform` 与前端基座消费。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-ui-model/DESIGN.md](../../../../core/ouroboros-ui-model/DESIGN.md)
- `推荐阅读`：[core/ouroboros-ui-model/ouroboros-ui-model-core/README.md](../../../../core/ouroboros-ui-model/ouroboros-ui-model-core/README.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

仍处于持续扩展的核心模块族，既连接低代码后端，也牵涉前端交付边界。当前深度资料以整体设计概要和 `core` 子模块 README 为主，后续仍值得补齐更明确的 schema 与 microweb 专题。
