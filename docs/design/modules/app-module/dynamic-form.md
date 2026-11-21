# dynamic-form 模块设计摘要

## 模块定位

`dynamic-form` 是应用交付层中的低代码表单运行时模块，负责把表单定义、字段配置、部署生命周期和生成式 UI/API 组织成一套可直接交付的动态表单能力。它不是新的数据引擎或 UI 引擎，而是把 `ouroboros-data`、字段类型目录和 UI Schema 生成能力装配成业务可用产品。

## 责任边界

该模块负责动态表单定义管理、部署/下线/归档状态流转、运行时数据模型装载、面向用户的表单与表格页面生成，以及对应的数据 CRUD 接口。它不负责重新定义底层数据模型体系，也不应取代通用的 UI 模型、字段类型目录或审批流引擎。

## 依赖关系

对外 `DynamicForm`、`FormField` 和定义/数据服务契约归 `core/ouroboros-dynamic-form-core`；应用实现保留在本模块。审批动态表单适配只依赖这些契约，记录查询 Controller 随审批动态表单模块装配；审批基础 runtime 不再引入表单应用实现。`ouroboros-develop` 与 `ouroboros-mothership` 在平台装配处显式组合表单应用实现和审批适配器，保留原有运行时能力。

从 `pom.xml` 和实现类可以看出，`dynamic-form` 直接依赖 `ouroboros-data-typed-runtime`、`ouroboros-data-sql`、`ouroboros-field-type-core` 以及 `common-all`。它通过 `DynamicFormDataStation` 将表单定义投影为运行时数据模型，通过 `DynamicFormUIModelProvider` 和 `DynamicFormFieldSchemaBuilder` 生成页面交付面，并通过菜单、权限和 UI 资源把能力挂入系统设置入口。

## 关键抽象

- `DynamicForm`：动态表单定义聚合根，承载字段、状态和审计信息。
- `FormField`：表单字段定义与部署状态。
- `DynamicFormDefineService`：表单定义与部署生命周期服务。
- `DynamicFormDataStation`：动态表单专属数据站，负责运行时数据模型挂载与迁移。
- `DynamicFormUIModelProvider` 与 `UISchemaBuilder`：把表单定义投影为 Amis UI 模型与页面结构。

## 关键子模块

- 定义管理与生命周期：表单定义 Controller、Service 与部署状态控制。
- 运行时数据访问：`DynamicFormDataStation` 与 `DynamicFormDataService`。
- 生成式交付面：`/dynamic-form/form/*`、`/dynamic-form/table/*` 等 UI 路径与对应 CRUD Schema。
- 平台接入：系统设置菜单、权限声明、自动配置与 SPI 注册。

## 扩展机制

该模块的扩展主要建立在共享机制之上，包括 `DataStationProvider`、`UIModelProvider` SPI、字段类型目录和自动配置装配。维护者在扩展动态表单时，应优先复用这些平台级入口，而不是在模块内部再造并行机制。具体共享规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[app-module/dynamic-form/pom.xml](../../../../app-module/dynamic-form/pom.xml)
- `推荐阅读`：[app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/station/DynamicFormDataStation.java](../../../../app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/station/DynamicFormDataStation.java)
- `推荐阅读`：[app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/provider/DynamicFormUIModelProvider.java](../../../../app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/provider/DynamicFormUIModelProvider.java)
- `推荐阅读`：[app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/controller/DynamicFormDefineController.java](../../../../app-module/dynamic-form/src/main/java/com/ouroboros/dynamicform/controller/DynamicFormDefineController.java)

## 演进状态

`dynamic-form` 已经具备足够清晰的 live repo 结构，可以被视作成熟的应用装配模块。后续维护重点是继续守住“在平台模型体系之上做表单交付”的定位，避免把更多通用数据建模或 UI 运行时语义回卷进该模块内部。
