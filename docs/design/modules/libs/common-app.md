# common-app 模块设计摘要

## 模块定位

`common-app` 是 `common-*` 中面向应用运行上下文的共享模块，负责提供应用基本信息读取、应用刷新生命周期事件，以及前端 `webAppInfo` 扩展相关的共享契约。它是很多运行时和开发平台模块围绕“一个应用是什么、何时刷新、如何暴露给前端”协作的共同底座。

## 责任边界

该模块负责应用 home 路径抽象、`app.metadata.json` 读取、生命周期事件定义以及 `WebAppInfoContributor` / `WebAppInfoModifier` 协议；它不负责具体应用管理产品逻辑、不负责微前端部署实现、不负责应用版本控制，也不负责某个具体配置来源。它提供的是共享应用契约，而不是应用管理产品本身。

## 依赖关系

模块基于 `common-model` 的模型基类定义本模块的 `com.ouroboros.model.AppInfo`，并依赖基础 JSON 与元数据读取工具，向上被 `platform`、`ability`、`dev-module` 和前端集成相关模块共同消费。尤其是 `webAppInfo` 相关接口，已经成为平台运行时向前端暴露聚合配置信息的重要接缝。

## 关键抽象

- `AppContext`：应用上下文接口，提供 home 路径、应用元信息和 debug 状态。
- `AppInfo`：应用元数据模型，对应 `app.metadata.json` 的核心结构。
- `WebAppInfoContributor`：贡献前端应用信息片段的接口。
- `WebAppInfoModifier`：在聚合后对最终 `webAppInfo` 进行增强或修正的接口。
- `OuroborosContextRefreshLifecycle`：定义应用刷新时的标准事件发布顺序。
- `OuroborosContextRefreshingEvent` 等生命周期事件：承载刷新过程中的阶段语义。
- `@LowCodeProvider`：低代码提供者标记注解。

应用级能力接入这些事件时，应通过 `AppContext.homePath` 定位应用工作目录，并复用各模块的正式元数据提供者；例如审批流定义由 runtime 的 `ApprovalWorkflowMetadataDefinitionProvider` 合并 classpath 资产与 `${homePath}/workflow/**/*.workflow.json`。公共 `common-app` 不承载具体审批流格式、导入规则或 Flowable 部署逻辑。

## 关键子模块

- `AppContext` 与 `AppInfo`：应用基础元信息层。
- `WebAppInfoContributor`、`WebAppInfoModifier`：前端运行配置扩展层。
- `lifecycle/` 与 `lifecycle/event/`：应用刷新生命周期抽象。
- `annotation/LowCodeProvider`：低代码来源标记。

## 扩展机制

该模块的重要扩展面是 `WebAppInfoContributor` 和 `WebAppInfoModifier` 这两个共享协议，上层模块通常通过 Spring 或 Java SPI 机制把实现接入到最终的应用信息聚合流程中。相关治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md) 与 [../../cross-cutting/integration-boundaries.md](../../cross-cutting/integration-boundaries.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-app/GUIDE.md](../../../../libs/common/common-app/GUIDE.md)
- `补充深度资料`：[集成边界](../../cross-cutting/integration-boundaries.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-app` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-app` 的共享契约角色已经比较清晰，尤其是 `webAppInfo` 和生命周期事件这两条主线。后续维护应继续守住这些“共享接口层”边界，不把具体应用产品实现不断回流到这个公共模块。
