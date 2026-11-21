# ouroboros-data 模块设计摘要

## 模块定位

`ouroboros-data` 是 Ouroboros 低代码平台的数据模型与查询运行时基础，负责把动态模型、查询 DSL、数据源适配和运行时模型访问组织成统一内核。对维护者来说，它是理解整个平台“如何定义并操作数据模型”的第一入口。

## 责任边界

该模块负责动态数据模型抽象、查询表达、数据访问适配、运行时模型支持和数据源接入，不负责业务模型投影、统一安全治理或具体平台页面交付。它可以向上提供被复用的数据内核，但不应该在本模块内混入上层业务能力装配。

## 依赖关系

`ouroboros-data` 直接建立在 `libs`/`common-*` 支撑之上，并作为 `core` 内其他引擎、上层 runtime 能力和开发模块的重要基础能力被消费；安全、业务模型、Web API 等模块会围绕它工作，但不应把这些上层规则反向压回本模块。适配器、插件链与数据源装配的共享扩展约定见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- [模块共同设计](../../../../core/ouroboros-data/DESIGN.md)：查询管线、模型与扩展边界。
- [模块说明](../../../../core/ouroboros-data/README.md)：使用与布局。
- [元数据装饰器](../../../../core/ouroboros-data/design/metadata-decorator/overview.md)、[SCD2](../../../../core/ouroboros-data/design/scd2-history-plugin.md)、[注解投影](../../../../core/ouroboros-data/design/typed-plugin-annotations.md)：独立机制。
- [DSL 用例与测试方法](../../../../core/ouroboros-data/design/dsl-test-cases/overview.md)：行为预期与验证归属。

历史重构问答和阶段计划不定义当前管线，变更时从共同设计及其专题继续阅读。
