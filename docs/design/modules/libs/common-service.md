# common-service 模块设计摘要

`common-service` 保留与数据引擎无关的 `ModelService`、`SoftDelete`、`SoftDeleteModelService` 合同和 outbox lease/backoff 规则。其生产依赖为 `common-exception-mapper`，不依赖 data 核心或实现。

Typed Data CRUD 实现已归 `ouroboros-data-typed-core` 的 `com.ouroboros.data.service`：`ModelServiceImpl`、`SimpleModelServiceImpl`、`SoftDeleteModelServiceImpl`、`SimpleSoftDeleteModelServiceImpl`。它们通过 `TypedDataModelCenter` 合同获取模型，运行时提供者由平台装配。上层继承这些实现时应直接声明 typed-core 依赖。

实现仍承担通用 CRUD、软删除过滤和异常翻译，不承担具体业务流程、权限编排或复杂跨模型查询。第三方数据异常翻译由 `ouroboros-data-platform-adapter` 注册的 SPI 提供，libs 只定义 mapper 合同。

使用示例见 [服务层指南](../../../../libs/common/common-service/GUIDE.md)，跨模块语义见[模型系统](../../cross-cutting/model-system.md)。
