# ouroboros-develop 模块设计摘要

## 模块定位

`ouroboros-develop` 是当前维护者进入平台的主运行入口之一，负责把能力层运行时、开发管理模块和平台启动基础设施组合成可直接启动的开发平台应用。对维护者来说，它不是新的领域能力，而是把下层能力编排成“开发平台”这一产品形态的入口模块。

## 责任边界

该模块负责 Spring Boot 应用启动、开发平台运行时装配、数据库与缓存等基础设施依赖汇聚，以及镜像/打包层的交付边界，不负责重新定义 `ability`、`dev-module` 或 `core` 的内部抽象。它解决的是开发平台入口与装配问题，而不是替代下层模块本身。

## 依赖关系

`platform/ouroboros-develop/pom.xml` 明确显示它依赖 `ouroboros-ability-all-runtime`、`ouroboros-dev-module-all` 和 `ouroboros-dev-helper-dev-metadata-provider`，同时结合 Spring Boot Web、Cache、Redis、Liquibase 与数据库驱动形成完整运行时。因此，维护者应把它看成“平台装配器”：下接公共能力和开发模块，上接最终可运行应用，而不是逐层展开所有下游模块细节。

## 关键抽象

- `OuroborosDevelopApplication`：Spring Boot 启动入口。
- 平台装配边界：通过聚合依赖把能力层 runtime 与开发管理模块并入同一个应用进程。
- 运行基础设施：Web、缓存、Redis、Liquibase 和数据库驱动组成应用运行环境。
- 打包与交付配置：模块自身的 `jar` 打包、Docker 文件和资源过滤配置共同决定发布形态。

## 关键子模块

- 应用启动入口：`src/main/java/com/ouroboros/develop/OuroborosDevelopApplication.java`
- 能力聚合依赖：`ouroboros-ability-all-runtime`
- 开发平台聚合依赖：`ouroboros-dev-module-all`
- 开发元数据补充依赖：`ouroboros-dev-helper-dev-metadata-provider`
- 交付资产：`docker/dockerfile` 与模块内资源过滤配置

## 扩展机制

`ouroboros-develop` 自身几乎不定义新的共享扩展协议，它主要消费下层模块的 AutoConfiguration 与聚合依赖来完成装配。关于这些共享扩展入口的统一规则，请查看 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[platform/ouroboros-develop/README.md](../../../../platform/ouroboros-develop/README.md)
- `暂无独立深度设计文档`：当前仓库中尚无比 README 更完整的 `ouroboros-develop` 设计专题，维护者需要结合 `pom.xml`、启动类与部署文件理解其装配角色。

## 演进状态

`ouroboros-develop` 在平台中承担的是“稳定入口、持续装配”的角色：运行入口相对稳定，但它依赖的下层能力会持续演进。因此，本摘要页的目标不是枚举所有依赖，而是帮助维护者先抓住它作为平台入口和装配器的角色。
