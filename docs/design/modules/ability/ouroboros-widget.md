# ouroboros-widget 模块设计摘要

## 模块定位

`ouroboros-widget` 是 ability 层的组件（Widget）能力模块，负责组件定义的发现与生成、组件 Schema 管理、以及基于组件的用户仪表盘与快捷入口能力。它把“组件是什么”和“组件如何被用户摆放”分开治理：定义与 Schema 属组件层，仪表盘布局属用户层。

## 责任边界

该模块负责 `WidgetDefinition` 契约、定义提供者与脚手架生成器、组件 Schema 的查询与管理入口、用户仪表盘及其模板的增删改查，不负责前端渲染器实现，也不负责组件库包（component-lib）的安装与部署。组件元数据只描述结构，运行时渲染由前端微应用承担。

## 依赖关系

- 该域由 7 个子模块聚合而成，父 pom 仅声明 `jakarta.annotation-api`，不承载业务依赖。
- `ouroboros-widget-core` 是最底层，只依赖 `jackson-annotations` 与 `ouroboros-ui-model-schema`，其余子模块都以它为根。
- `ouroboros-widget-runtime` 依赖 `ouroboros-widget-core`、`common-metadata`（元数据目录加载）与 `swagger-annotations`；`ouroboros-webapi-runtime` 仅用于测试。
- `ouroboros-widget-manage` 通过 core 的 `WidgetDefinitionService` 契约查询定义，并引入 `ouroboros-data-typed-runtime`、`ouroboros-web-security-core`、`spring-security-core`、`common-exception-mapper`。
- `ouroboros-widget-user-dashboard` 依赖 core、`ouroboros-data-typed-runtime`、`ouroboros-user-setting-core`、安全与异常映射组件。
- `ouroboros-widget-common`、`-user-profile`、`-shortcut` 只依赖 `ouroboros-widget-core`。
- 被依赖面：`ability/ouroboros-ability-all/ouroboros-ability-all-runtime` 依赖 runtime、manage 与 user-dashboard；core 与其余子模块在 `bom/pom.xml` 中统一管理版本。

## 关键抽象

- `WidgetDefinition`、`WidgetConfig`、`WidgetInstance`：组件定义、组件配置与组件实例三层数据形态。
- `WidgetDefinitionProvider`：定义提供者契约，提供 `getWidgetDefinition(name)` 与 `getAllWidgetDefinitions()`。
- `WidgetDefinitionGenerator`：定义脚手架契约，通过 `getFormItems()` 暴露配置表单项，并由 `buildWidgetDefinition(WidgetSchema)` 生成定义。
- `WidgetSchema` / `WidgetSchemaProvider`：组件 Schema 与 Schema 提供者，manage 侧以 `WidgetSchemaProviderImpl` 落地。
- `WidgetDefinitionService`：定义查询服务，聚合全部 SPI 提供者并按名称大小写不敏感去重排序。
- `Dashboard`、`GridPosition`、`GridSize`：用户仪表盘的栅格布局模型。
- `WidgetSchemaRefreshed`：Schema 刷新事件，用于驱动定义生成器的重建。

## 关键子模块

- `ouroboros-widget-core`：模型、查询服务与 SPI 契约。
- `ouroboros-widget-runtime`：查询服务实现与装配，含 `JarWidgetDefinitionProvider`、`GeneratorWidgetDefinitionProvider`、`SimpleWidgetDefinitionGenerator` 与 `WidgetDefinitionController`。
- `ouroboros-widget-manage`：7 个文件，组件 Schema 管理面。
- `ouroboros-widget-user-dashboard`：8 个文件，用户仪表盘与仪表盘模板两块服务。
- `ouroboros-widget-common`、`ouroboros-widget-user-profile`、`ouroboros-widget-shortcut`：**零 Java 文件**的元数据模块，只在 `src/main/metadata/` 下提供 `.widget.json`（Table/List/Chart/LineChart/BarChart/PieChart、UserProfile、Shortcut），运行时由 `JarWidgetDefinitionProvider` 扫描加载。

## 扩展机制

扩展点是 SPI 注册的三个接口，而非硬编码：`META-INF/services/com.ouroboros.widget.WidgetDefinitionProvider` 注册 `JarWidgetDefinitionProvider` 与 `GeneratorWidgetDefinitionProvider`，`...WidgetDefinitionGenerator` 注册 `SimpleWidgetDefinitionGenerator`，manage 侧 `...WidgetSchemaProvider` 注册 `WidgetSchemaProviderImpl$Proxy`；`WidgetDefinitionServiceImpl` 通过 `SPI.getSortedServiceStream` 与 `SPI::sortByPriority` 做优先级排序，因此新增组件可以只交付一个 `.widget.json` 元数据文件而不写 Java。自动配置 `WidgetRuntimeAutoConfiguration`、`WidgetManageAutoConfiguration`、`UserDashboardAutoConfiguration` 均双处注册在 `spring.factories` 与 `AutoConfiguration.imports`。通用规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `暂无独立深度设计文档`：当前仓库中尚无该模块的补充专题页。

## 演进状态

拆分层级清晰（core / runtime / manage / user-dashboard + 元数据子模块），33 个 main java 文件中 core 占 11 个、runtime / manage / user-dashboard 合计 22 个，core 保持无 Spring 依赖，收敛度良好。两点需要在演进时留意：(1) `-common`、`-user-profile`、`-shortcut` 没有 Java 代码，其真实职责是元数据交付，容易被误读为“空模块”；(2) `GeneratorWidgetDefinitionProvider` 的刷新依赖 `WidgetRuntimeAutoConfiguration` 监听 `OuroborosContextRefreshedEvent` 后转发 `WidgetSchemaRefreshed` 事件，这条隐式链路目前只存在于运行时事件流中，新增定义来源时需确认是否同样触发刷新。
