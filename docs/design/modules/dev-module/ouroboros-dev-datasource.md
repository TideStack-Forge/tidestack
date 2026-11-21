# ouroboros-dev-datasource 模块设计摘要

## 模块定位

`ouroboros-dev-datasource` 是开发平台中的数据源管理模块，负责维护平台级数据源定义、应用到平台数据源的映射关系，以及开发期需要的表结构元数据浏览能力。它让低代码开发平台能够先管理“可用的数据连接”和“应用怎么引用这些连接”，再把上层模型设计建立在这些连接之上。

## 责任边界

该模块负责数据源注册、环境化定义保存、应用绑定关系管理和元数据探查，不负责运行时查询执行、ORM 映射、数据库迁移、凭据托管或连接池生命周期管理。它提供的是开发管理面上的数据源目录与探查能力，而不是完整的数据访问运行时。

## 依赖关系

从依赖和实现看，这个模块直接建立在 `ouroboros-data-core` 与 `ouroboros-data-typed-runtime` 之上，通过类型化数据模型存储 `PlatformDataSourceModel` 和 `AppDataSourceModel`。它同时依赖 Web 与安全基础设施暴露管理 API，并通过 Spring 注入多个 `DataSourceMetadataService` 实现来支持不同数据源类型的元数据探查。

## 关键抽象

- `PlatformDataSourceModel`：平台级数据源目录项，核心字段是名称、标题和按环境划分的 `definition`。
- `AppDataSourceModel`：应用到平台数据源的映射关系，定义应用内名称与平台数据源名的绑定。
- `DataSourceDefine`：运行时用于选择元数据探查实现的轻量数据源定义对象。
- `PlatformDataSourceService`：平台级数据源的增删改查和按环境列出表信息的服务入口。
- `AppDataSourceService`：应用侧映射关系管理和“取某应用某数据源可见表”的服务入口。
- `DataSourceMetadataService`：元数据探查扩展接口，当前默认实现是 `JDBCDataSourceMetadataService`。

## 关键子模块

- `controller/`：`PlatformDataSourceController` 和 `AppDataSourceController`，分别暴露平台管理面与应用管理面的 REST API。
- `service/` 与 `service/impl/`：封装 typed-model 持久化与表元数据探查逻辑。
- `model/` 与 `dto/`：承载平台数据源、应用映射和表信息传输对象。
- `META-INF/ouroboros/`：菜单、权限、UI Model 和 UI Schema 资源，说明该模块自带开发平台管理界面入口。
- `DevDatasourceAutoConfiguration`：通过 `spring.factories` 激活模块并补充鉴权 API 白名单。

## 扩展机制

这个模块最重要的扩展点是 `DataSourceMetadataService`。维护者新增数据库类型时，应优先新增新的元数据探查实现，而不是把多种连接细节继续堆进 `JDBCDataSourceMetadataService`。模块装配仍沿用 [../../cross-cutting/spring-autoconfiguration.md](../../cross-cutting/spring-autoconfiguration.md) 中的 imports 与 factories 双注册机制，管理面资源则遵循 [../../cross-cutting/meta-inf-resource-conventions.md](../../cross-cutting/meta-inf-resource-conventions.md)，提供者模式可参考 [../../cross-cutting/provider-patterns.md](../../cross-cutting/provider-patterns.md)。

## 深入文档索引

- `补充深度资料`：[跨模块提供者模式](../../cross-cutting/provider-patterns.md)
- `补充深度资料`：[META-INF 资源约定](../../cross-cutting/meta-inf-resource-conventions.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `ouroboros-dev-datasource` 自身的 `DESIGN.md` 或专题文档。

## 演进状态

`ouroboros-dev-datasource` 已经形成“平台数据源目录 + 应用映射 + 元数据探查”的清晰骨架，但元数据探查当前主要体现为 JDBC 场景，环境定义也仍以配置映射为中心。后续若继续演进，建议继续沿着“新增 provider、保持管理面稳定”的方向扩展，而不是把运行时访问语义带回该模块。
