# common-dev 模块设计摘要

## 模块定位

`common-dev` 是 `common-*` 中专门服务开发态的共享抽象模块，负责沉淀开发工作空间、应用上下文、元数据提供者、开发事件和环境模型等基础契约。它是各个 `dev-module` 之间能够共享同一套开发态语言和上下文的底层公共层。

## 责任边界

该模块负责开发态通用抽象与事件模型，不负责具体开发平台页面、不负责某个管理模块的业务流程，也不负责应用版本控制、容器、组件库或数据源等具体产品能力。它的职责是定义开发态共同语言，而不是实现某一项开发平台功能。

## 依赖关系

`common-dev` 位于 `dev-module` 之下，向上被 `ouroboros-dev-app-management`、`ouroboros-dev-environment`、`ouroboros-dev-platform-component-lib` 等模块复用。它和 `common-app`、`common-model` 一样承担“共享契约层”的角色，使更上层模块能够围绕同一套开发态上下文进行协作。

## 关键抽象

- `DevelopWorkspaceContext`、`RunWorkspaceContext`：开发态和运行态工作空间路径抽象。
- `DevAppHolder`：线程级应用上下文持有器，用于在开发流程中绑定当前应用。
- `AppHomeResolver`：应用目录解析接口。
- `Metadata`、`MetadataProvider`、`MetadataProviderMixer`：开发态元数据收集与聚合抽象。
- `DevAppEvent`、`DevAppModelEvent` 及其子类：应用、模型、文件变更事件模型。
- `Environment`、`EnvironmentProvider`、`EnvironmentAggregator`：开发环境目录抽象。

## 关键子模块

- `com.ouroboros.dev`：工作空间、应用上下文和 home 解析基础抽象。
- `com.ouroboros.dev.metadata`：元数据模型、提供者和 mixer。
- `com.ouroboros.dev.event`：开发态事件层次。
- `com.ouroboros.dev.environment`：环境模型与 provider/aggregator 契约。

## 扩展机制

`common-dev` 的扩展方式主要不是 Java SPI，而是“上层模块实现这些接口后再由自己的装配层聚合使用”。其中 `MetadataProvider` / `MetadataProviderMixer` 与 `EnvironmentProvider` / `EnvironmentAggregator` 是最值得维护者优先遵守的提供者模式，相关治理可参考 [../../cross-cutting/provider-patterns.md](../../cross-cutting/provider-patterns.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-dev/GUIDE.md](../../../../libs/common/common-dev/GUIDE.md)
- `补充深度资料`：[跨模块提供者模式](../../cross-cutting/provider-patterns.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-dev` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-dev` 的角色已经比较稳定，但它也最容易在开发平台扩张时被塞入各种“顺手可复用”的新抽象。后续维护应继续把它控制在共享契约层，避免具体开发产品逻辑回流到这个底座模块。
