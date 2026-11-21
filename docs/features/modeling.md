# 模型与应用

为应用开发者提供数据结构、业务对象和可复用应用模块，作为表单、流程与接口的建模基础。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 数据与字段 | 定义动态/类型化数据模型、查询 DSL、数据源适配和字段值类型；字段语义由字段类型扩展，业务读写走统一查询 API。 |
| 业务模型 | 在数据内核之上组织业务对象、操作和插件；不在数据引擎内重新定义安全或业务页面。 |
| 业务模型关系画布 | 开发态业务模型页面提供列表和可视化建模 Tab，支持拖拽、模块树分组和独立切面。画布操作通过 Yjs、基座 STOMP 和应用 VCS 实时协同。进入业务模型编辑器时继续使用该编辑器现有流程。 |
| 业务行为与事实 | 动态 Action 可绑定同步 Handler 或 LogicFlow，由统一 Runtime 执行授权、输入校验和业务事务；Event 保存不可变业务事实，订阅通过 Outbox 至少一次投递。可用性预检不代替执行时重验。 |
| 行为建模与调用 | 中文工作台统一编辑动作、事件和订阅，支持逻辑流选择、数据结构与 params/data 映射、自动检查及来源只读边界；支持 HTTP/内部调用、交付诊断及显式重驱；Java/TypeScript 客户端按需从已解析契约派生，运行时校验仍是权威。 |
| 应用模块 | AppModule 组织业务交付单元；dynamic-form 提供动态表单，document-management 提供文档业务，ui-schema-management 管理 UI Schema。 |

## 实现情况

当前代码包含建模、查询和应用模块装配；业务模型与数据模型属于不同抽象。数据源/插件按实际装配启用，查询权限、回收站和索引能力按对应设计判断，恢复扫描非唯一索引欠缺继续由 DOC-010 跟踪。

关系画布为全局总览和每个切面分别保存 VCS 文件，自由与模块分组布局独立维护。分组拖动将节点约束在所属框内，模块整体移动包含子模块，同级框重叠时回退。切面从模型库拖入或添加模型并显示已有关系，支持搜索、详情、直接关联、缩放和平移。协同当前限于单工作区场景，完整环境验收及其他边界见[模型实时协同](../design/cross-cutting/model-collaboration.md#当前边界与演进)。

本页按源码与配置说明能力。测试入口说明可复跑资产，实际运行与线上发布状态由对应版本的交付证据确认。

## 启用条件

需要数据源、已注册元数据和目标应用权限；业务模块须加入正式应用装配。业务行为还要求可用的已发布契约、Runtime/EventPort、对应权限和事务资源；源事务证明首期仅覆盖同一实际 JDBC 连接，不承诺跨数据源原子性、投递恰好一次或全局顺序。独立 Spreadsheet 微服务是历史交付边界，不能视作当前默认模块。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [core/ouroboros-data](../../core/ouroboros-data) · [设计/指南](../design/modules/core/ouroboros-data.md) | [测试入口](../../core/ouroboros-data/ouroboros-data-builders/src/test/java/com/ouroboros/data/model/ModelMetaBuilderTest.java) |
| [core/ouroboros-field-type](../../core/ouroboros-field-type) · [设计/指南](../design/modules/core/ouroboros-field-type.md) | [测试入口](../../core/ouroboros-field-type/ouroboros-field-type-core/src/test/java/com/ouroboros/data/migration/DefaultFieldTypeLiquibaseCompatibilityTest.java) |
| [core/ouroboros-business-model](../../core/ouroboros-business-model) · [设计/指南](../design/modules/core/ouroboros-business-model.md) | [插件测试](../../core/ouroboros-business-model/ouroboros-business-model-core/src/test/java/com/ouroboros/businessmodel/plugin/BusinessModelFeaturePluginRegistryTest.java)、[业务行为集成场景](../../testing/integration/feature/lowcode/business-model-actions-events/contract.md) |
| [业务模型关系画布](../../frontends/web/ouroboros-amis-runtime/src/renderers/BusinessModelGraph.tsx) · [布局与关系几何](../../frontends/web/ouroboros-amis-runtime/src/renderers/business-model-graph.ts) | [布局行为测试](../../frontends/web/main-app/tests/unit/src/components/amis/renderers/business-model-graph.test.js)、[renderer 行为测试](../../frontends/web/main-app/tests/unit/src/components/amis/renderers/business-model-graph-runtime.test.js) |
| [行为编辑器与运行诊断](../../core/ouroboros-business-model/ouroboros-business-model-default/src/main/java/com/ouroboros/businessmodel/) · [SDK](../../frontends/web/ouroboros-sdk/src/) | [编辑器场景及边界](../../frontends/web/main-app/tests/integration/business-behavior-catalog/README.md) |
| [core/ouroboros-app-module-model](../../core/ouroboros-app-module-model) · [设计/指南](../design/modules/core/ouroboros-app-module-model.md) | [测试入口](../../core/ouroboros-app-module-model/ouroboros-app-module-model-core/src/test/java/com/ouroboros/appmodule/AppModuleDiffUtilTest.java) |
| [app-module/dynamic-form](../../app-module/dynamic-form) · [设计/指南](../design/modules/app-module/dynamic-form.md) | [测试入口](../../app-module/dynamic-form/src/test/java/com/ouroboros/dynamicform/controller/DynamicFormDataControllerHttpStatusTest.java) |
| [app-module/ui-schema-management](../../app-module/ui-schema-management) · [设计/指南](../design/modules/app-module/ui-schema-management.md) | [测试入口](../../app-module/ui-schema-management/src/test/java/com/ouroboros/uischema/CustomUiSchemaAmisEditorContextTest.java) |
| [app-module/document-management](../../app-module/document-management) · [设计/指南](../design/modules/app-module/document-management.md) | [测试入口](../../app-module/document-management/document-management-autoconfigure/src/test/java/com/ouroboros/documentmanagement/autoconfigure/DocumentManagementAutoConfigurationTest.java) |

## 关联文档

- [docs/design/cross-cutting/model-system.md](../design/cross-cutting/model-system.md)
- [docs/guides/app-development-guide.md](../guides/app-development-guide.md)
- [业务行为开发指南](../guides/business-model-behaviors.md)、[稳定行为契约](../../core/ouroboros-business-model/docs/business-model-behavior-contracts.md)、[业务行为运维](../runbooks/business-model-behaviors.md)
