# ouroboros-field-type 模块设计摘要

## 模块定位

`ouroboros-field-type` 是核心引擎层中的字段类型目录，负责把字段类型定义、字段模板参数化、默认字段集合和开发态自定义字段组织成统一来源。它解决的是“平台有哪些字段类型、这些字段类型如何生成数据与 UI 元信息”，而不是直接定义某个业务模型里的字段实例。

## 责任边界

该模块负责字段类型定义、字段模板解析、字段类型提供者聚合、默认字段装配和开发态字段类型管理，不负责业务模型字段实例的生命周期，也不负责最终页面渲染引擎本身。维护时应把“字段类型模板”与“字段实例使用场景”分开。

## 依赖关系

模块内部按 `core`、`default-types`、`runtime`、`dev` 四层组织，向上被业务模型、数据模型和 UI schema 相关模块消费。新增字段能力应优先通过字段模板和 provider 接入，而不是把特殊字段逻辑直接塞进 `FieldTypeCatalog`。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-field-type/DESIGN.md](../../../../core/ouroboros-field-type/DESIGN.md)
- `推荐阅读`：[core/ouroboros-field-type/docs/field-template.md](../../../../core/ouroboros-field-type/docs/field-template.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

源码审计表明模块边界相对稳定，当前已经整理出 `DESIGN.md` 作为权威入口。`field-template.md` 继续保留为模板格式补充资料，而不是替代设计入口。
