# ouroboros-error-code 模块设计摘要

## 模块定位

`ouroboros-error-code` 是核心引擎层中的统一错误码能力模块，负责把平台级错误码定义、运行时查询和开发态维护组织成同一条链路。它解决的是“稳定错误契约是什么、定义从哪里来、运行时如何补全”，而不是直接承担异常抛出策略或多语言翻译存储本身。

## 责任边界

该模块负责错误码定义模型、定义来源聚合、运行时注册表和开发平台中的错误码管理入口，不负责替代 `common-exception` 的异常层次，也不负责直接维护 i18n 各语言文本内容。维护时应把“错误码主数据”与“错误消息在不同语言下如何展示”分开：前者归 `ouroboros-error-code`，后者归 `ouroboros-i18n`。

## 依赖关系

从聚合结构和源码看，`ouroboros-error-code` 包含 `core`、`runtime`、`dev` 三层：`core` 定义 `ErrorCodeDefinition`、`ErrorCodeDefinitionProvider` 和 `ErrorCodeRegistry`；`runtime` 负责从资源目录与开发工作区聚合错误码定义，并通过自动配置暴露注册表；`dev` 提供根目录固定集合文件 `error-codes.error.json` 的开发态管理、VCS inspector 和菜单/UI 元数据。它向上被 `ouroboros-app-runtime` 和 `ouroboros-i18n-runtime` 消费。

`libs/common/common-exception` 唯一依赖的是 **libs 侧的 `common-error-code`**（`ErrorCodeReference` / `ThrowableErrorDescriptorMapper` 等），与 `core/ouroboros-error-code` 既无 Maven 依赖也无代码引用——两者只是共用 `com.ouroboros.errorcode` 根包名。两套体系的桥接点是 `ouroboros-app-runtime` 的 `ErrorResponseResolver`（同时消费 `ThrowableErrorDescriptorMapper` 与 `ErrorCodeRegistry`）与 `ouroboros-i18n-runtime` 的 `ErrorCodeTextExtractor` / `DefaultErrorMessageLocalizer`；`libs/common/common-error-code` 的模块摘要见 [libs/common-error-code.md](../libs/common-error-code.md)。

`ouroboros-ability-all-runtime` 装配默认错误码注册表，供普通应用的错误响应补全和 i18n 文案提取消费；`ouroboros-dev-module-all` 装配错误码编辑入口。自定义注册表仍通过 `ErrorCodeRegistry` Bean 覆盖默认实现。

## 关键抽象

- `ErrorCodeDefinition`：统一错误码主数据模型，包含 `code`、标题、默认消息模板、业务标识等字段。
- `ErrorCodeDefinitionProvider`：错误码定义来源扩展点，允许资源文件和工作区文件同时供给定义。
- `ErrorCodeRegistry`：运行时查询入口，用于按错误码解析完整定义。
- `ErrorCodeDevService`：开发态错误码定义管理服务，负责校验、检索和文件落盘。

## 关键子模块

- `ouroboros-error-code-core`：错误码模型、SPI 和注册表协议。
- `ouroboros-error-code-runtime`：默认注册表、资源定义加载、工作区定义加载与自动配置。
- `ouroboros-error-code-dev`：开发平台错误码管理、工作区文件检查器和 UI 资源。

## 扩展机制

本模块最重要的扩展面是新增 `ErrorCodeDefinitionProvider`。若需要引入新的错误码来源，应优先通过 provider 聚合进入 `ErrorCodeRegistry`，而不是在运行时出口硬编码额外查询逻辑。开发态扩展应继续复用根目录固定集合文件 `error-codes.error.json` 与现有 dev-module/VCS 机制，避免引入额外的静态表配置。

## 深入文档索引

- [common-error-code](../libs/common-error-code.md)、[common-exception](../libs/common-exception.md)：基础错误描述与异常分层。
- [应用运行时](../ability/ouroboros-app-runtime.md)、[国际化](ouroboros-i18n.md)：错误响应补全与文案提取。

## 演进状态

当前模块已经完成统一错误码的一期底座：错误码定义可以由资源和开发工作区共同提供，运行时可以按稳定 `code` 查找定义，开发平台也已经具备错误码文件管理入口。后续演进重点应放在历史模块逐步迁移接入，而不是重新拆分错误码契约与翻译 key。
