# ouroboros-dev-environment 模块设计摘要

## 模块定位

`ouroboros-dev-environment` 是开发平台里的环境目录模块，负责向上层开发管理模块暴露统一的环境列表与查询接口。它的职责很薄，但很关键，因为很多开发态配置都需要基于“平台认定有哪些环境”来组织，例如数据源定义、发布流转或环境感知配置。

## 责任边界

该模块负责环境对象的聚合、默认环境列表提供和只读查询接口，不负责部署编排、环境变量托管、配置中心同步，也不负责运行时路由切换。它解决的是“开发平台如何统一认识环境集合”，而不是“环境如何真正运行”。

## 依赖关系

模块依赖 `common-dev` 中定义的 `Environment`、`EnvironmentProvider` 与 `EnvironmentAggregator` 契约，并用 `common-web` / Spring Web 暴露查询接口。它本身是这些共享开发契约在开发平台中的默认实现层，向上会被数据源、应用管理等其他开发模块消费。

## 关键抽象

- `Environment`：环境名称、标题和描述的基础模型，定义在 `common-dev`。
- `EnvironmentProvider`：环境列表提供者接口，允许不同模块补充环境来源。
- `EnvironmentAggregator`：对多个环境提供者进行聚合的统一入口。
- `EnvironmentProviderImpl`：当前默认实现，提供 `dev`、`test`、`uat`、`prod` 四个固定环境。
- `EnvironmentAggregatorImpl`：按 Spring 注入顺序合并所有 `EnvironmentProvider`。
- `EnvironmentController`：对外暴露 `/api/dev-environment` 查询接口。

## 关键子模块

- `impl/EnvironmentProviderImpl`：默认环境集合定义。
- `impl/EnvironmentAggregatorImpl`：聚合多个 provider 的实现。
- `controller/EnvironmentController`：开发平台查询入口。
- `DevEnvironmentAutoConfiguration`：通过 imports 与 factories 双注册激活模块。

## 扩展机制

该模块的主要扩展面不是新的控制器，而是新增 `EnvironmentProvider`。如果以后要引入项目级、租户级或插件级环境集合，优先增加新的 provider 并交由 aggregator 汇总，而不是修改默认实现硬编码更多逻辑。共享提供者模式可参考 [../../cross-cutting/provider-patterns.md](../../cross-cutting/provider-patterns.md)，自动装配约定可参考 [../../cross-cutting/spring-autoconfiguration.md](../../cross-cutting/spring-autoconfiguration.md)。

## 深入文档索引

- `补充深度资料`：[libs/common/common-dev/GUIDE.md](../../../../libs/common/common-dev/GUIDE.md)
- `补充深度资料`：[跨模块提供者模式](../../cross-cutting/provider-patterns.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `ouroboros-dev-environment` 自身的 `DESIGN.md` 或专题文档。

## 演进状态

`ouroboros-dev-environment` 目前是一个刻意保持轻量的模块，当前默认环境集也相对固定。它最重要的维护原则不是“把功能做多”，而是守住环境目录边界，让真正的环境运行和配置逻辑继续留在更合适的模块里。
