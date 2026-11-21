# ui-schema-management 模块设计摘要

## 模块定位

`ui-schema-management` 是应用交付层中的 UI Schema 管理模块，负责把“数据库中的自定义 UI Schema”纳入平台正式管理面，并以运行时 `UISchemaProvider` 的形式向前端交付链路提供覆盖能力。它的价值在于管理与投影，不在于重写 `core/ouroboros-ui-model` 的核心模型体系。

## 责任边界

该模块负责自定义 UI Schema 的数据模型、CRUD 管理接口、启停控制、管理页面资源，以及数据库到运行时 `UISchemaProvider` 的投影。它不负责定义 UI Schema 规范本身，不负责前端渲染器，也不应承载更宽泛的配置中心职责。

## 依赖关系

从 `pom.xml` 和主要实现可见，该模块依赖 `ouroboros-data-typed-runtime`、`ouroboros-ui-model-schema`、`common-all` 与异常映射组件。它通过 `CustomUISchema` Typed Model 做持久化，通过 `CustomUISchemaService` 完成管理操作，再由 `DBUISchemaProvider` 以 SPI 方式将数据库中的启用项暴露给 UI Schema 运行时。

## 关键抽象

- `CustomUISchema`：数据库中的自定义页面定义模型，包含路径、Schema 内容、说明与启用状态。
- `CustomUISchemaService`：自定义 UI Schema 的管理服务接口。
- `DBUISchemaProvider`：运行时查询数据库并输出可用 Schema 的 Provider。
- `CustomUISchemaController`：系统设置侧的管理 API 入口。
- `DbUiSchemaAutoConfiguration`：模块装配入口。

## 关键子模块

- 数据模型与服务层：`CustomUISchema`、`CustomUISchemaService` 及其实现。
- 运行时投影层：`DBUISchemaProvider` 与 `UISchemaProvider` SPI 注册。
- 管理界面资源：系统设置菜单、Amis UI 模型与 UI Schema。
- 自动配置与缓存：模块装配、缓存刷新与异常映射。

## 扩展机制

该模块最关键的扩展点是 `UISchemaProvider` SPI，以及建立在 Typed Model、缓存和自动配置之上的运行时装配方式。维护者应把它视为 UI Schema 运行时的一个可替换来源，而不是与核心 UI 模型体系并行竞争的第二套中心。具体共享规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[app-module/ui-schema-management/pom.xml](../../../../app-module/ui-schema-management/pom.xml)
- `推荐阅读`：[app-module/ui-schema-management/src/main/java/com/ouroboros/uischema/DBUISchemaProvider.java](../../../../app-module/ui-schema-management/src/main/java/com/ouroboros/uischema/DBUISchemaProvider.java)
- `推荐阅读`：[app-module/ui-schema-management/src/main/java/com/ouroboros/uischema/model/CustomUISchema.java](../../../../app-module/ui-schema-management/src/main/java/com/ouroboros/uischema/model/CustomUISchema.java)
- `推荐阅读`：[app-module/ui-schema-management/src/main/metadata/system-setting/custom-ui-schema.ui-schema.json](../../../../app-module/ui-schema-management/src/main/metadata/system-setting/custom-ui-schema.ui-schema.json)

## 演进状态

当前模型、Provider、Controller 和管理页面资源均有正式入口。`DBUISchemaProvider` 查询异常时返回空结果，排查覆盖缺失时需要核对数据库查询，不能把空结果直接认定为没有自定义 Schema。后续维护重点是持续守住“数据库化 UI Schema 管理入口”边界，不让它演化成泛化的前端配置平台。
