# P0-3 Family Splitting

本页维护分层检查的当前实现与源码归属。采用子包和模块上下文建模的决定与理由统一在 [ADR-003](../../adr/ADR-003-p0-3-family-splitting-strategy.md) 维护；本页不维护第二份决策正文。

## 当前实现

正式规则入口是 `testing/architecture-tests/src/test/java/com/ouroboros/architecture/ArchitectureTestWithAllowlist.java`；`ArchitectureClassImportSupport` 提供模块位置识别。物理 Maven 模块、包族和 P0-3 赋层是三种不同维度，不能互相替代。

| 包族或边界 | 当前检查行为 |
| --- | --- |
| `uischema.schema`、`wrapper`、`util` | schema 模块归 Core |
| `uischema.dev` | schema-dev 模块归 Dev |
| `uischema.model/service` 及管理 controller | ui-schema-management 模块归 App |
| UI schema runtime 的 controller/服务 | runtime 模块归 Bridge，保留专项边界检查 |
| statistics definition/validation | core 模块归 Core |
| statistics runtime/dev | 分别归 Ability/Dev |
| appmodule core/runtime/procode | 三个现有权限与菜单投影模块归 Bridge，含 bare root |
| appmodule dev | 归 Dev |
| Platform | 正式归 Platform，可访问其下各层 |

两套入口共享 `ArchitectureLayerSupport`，使用 `consideringOnlyDependenciesInLayers()` 并强制每个导入生产类型归层。现有模块新包继承模块角色，未知模块根目录拒绝。测试只导入正式 reactor 输出，不从 tmp、归档或任意缓存借类型。

## 源码归属

### 子包与归属模块（每个子包给一个真实文件）

| 子包 | 归属模块 | 证据文件 |
|------|----------|----------|
| `uischema.schema` / `.schema.event` / `.schema.decorator` | `ouroboros-ui-model-schema` | `core/ouroboros-ui-model/ouroboros-ui-model-schema/src/main/java/com/ouroboros/uischema/schema/AbstractSchema.java`、`.../schema/event/EventActionConfig.java`、`.../schema/decorator/WithAPI.java` |
| `uischema.wrapper` / `.util` | `ouroboros-ui-model-schema` | `.../uischema/wrapper/SchemaNodeWrapper.java`、`.../uischema/util/UISchemaUtils.java` |
| `uischema.dev` / `.dev.service.impl` / `.dev.inspector` | `ouroboros-ui-model-schema-dev` | `core/ouroboros-ui-model/ouroboros-ui-model-schema-dev/src/main/java/com/ouroboros/uischema/dev/UISchemaDevAutoConfiguration.java`、`.../dev/service/impl/UISchemaDevService.java`、`.../dev/inspector/UISchemaInspector.java` |
| `uischema.model` / `.service` / `.service.impl` | `app-module/ui-schema-management` | `app-module/ui-schema-management/src/main/java/com/ouroboros/uischema/model/CustomUISchema.java`、`.../service/CustomUISchemaService.java`、`.../service/impl/CustomUISchemaServiceImpl.java` |
| `uischema.controller`（模块上下文感知） | 两侧各一：core 侧 `ouroboros-ui-model-schema-runtime`、app 侧 `ui-schema-management` | `.../ouroboros-ui-model-schema-runtime/.../uischema/controller/UISchemaController.java` 与 `app-module/ui-schema-management/.../uischema/controller/CustomUISchemaController.java` |
| `appmodule.model` | **跨 2 个模块**：`ouroboros-app-module-model-core`（8 个文件）、`ouroboros-app-module-model-procode`（4 个文件） | `.../-core/.../appmodule/model/ArrangedModuleAuthority.java`、`.../-procode/.../appmodule/model/AuthoritiesAppModule.java` |
| `appmodule.runtime` / `.dev` / `.dev.controller` / `.dev.controller.vo` / `.dev.inspector` | `ouroboros-app-module-model-runtime` / `-dev` | `.../-runtime/.../appmodule/runtime/RuntimeAppModuleIntegrator.java`、`.../-dev/.../appmodule/dev/AppModuleAggregator.java`、`.../dev/controller/AppModuleReorganizeController.java`、`.../dev/controller/vo/TreeNodeVO.java`、`.../dev/inspector/ModuleDiffInspector.java` |
| bare root `appmodule` | 跨 2 个模块，主源码共 11 个文件（7 在 `-core`，4 在 `-procode`） | `.../-core/.../appmodule/{AppModule,AppModuleDiff,AppModuleDiffProvider,AppModuleDiffUtil,AppModuleIntegrator,AppModuleProvider,BaseAppModule}.java`；`.../-procode/.../appmodule/{JarAppModuleProvider,ProCodeAppModuleProvider,ProCodeAuthorityAppModuleProjector,ProCodeMenuAppModuleProjector}.java` |
| `statistics.reporting.definition` / `.validation` | `ouroboros-statistics-reporting-core` | `core/ouroboros-statistics-reporting/ouroboros-statistics-reporting-core/.../statistics/reporting/definition/DatasetDefinition.java`、`.../reporting/validation/DatasetDefinitionValidator.java` |
| `statistics.reporting.runtime` 下的公开执行契约、请求与结果类型 | `ouroboros-statistics-reporting-core` | `.../ouroboros-statistics-reporting-core/.../reporting/runtime/execution/DatasetExecutionService.java`、`.../execution/DatasetExecutionRequest.java` |
| `statistics.reporting.runtime` 下的执行实现与自动配置 | `ouroboros-statistics-reporting-runtime` | `.../ouroboros-statistics-reporting-runtime/.../reporting/runtime/StatisticsReportingRuntimeAutoConfiguration.java` |
| `statistics.reporting.dev` | `ouroboros-statistics-reporting-dev` | `.../ouroboros-statistics-reporting-dev/.../reporting/dev/StatisticsReportingDevAutoConfiguration.java` |


## 已知边界

- `appmodule.model` 跨 `-core` 与 `-procode`，实施分层时必须同时考虑模块上下文。
- `com.ouroboros.workflow.approval` 跨 core、外部集成 ability 和 legacy-compat，不能整体描述为当前已检查的 Core family。
- 现有 allowlist 的条目、到期和退出条件以 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv` 为准；本页不复制某次扫描的条数。
- 已按 ADR-003 落地到共享架构层模型与真实编译夹具；后续规则变化仍需分别验证规则行为和 allowlist 变化；单次 dry-run、计划和审查结果归任务附件。

## 3. Statistics Reporting family

### 特殊情况：data security adapter

`DataOperationIdentityProvider` 是 data core 暴露的 SPI；当前安全上下文实现属于 security adapter 职责，应位于 `com.ouroboros.security..` 包下，避免被 `com.ouroboros.data..` Core root 捕获。

### 关于 `DataOperationIdentityProvider`（本文第 3 节）

- 它是 `ouroboros-data-core` 暴露的 SPI 接口——`core/ouroboros-data/ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataOperationIdentityProvider.java:8`；消费方 `DataOperationIdentityProviders.java:25-26`，注册在 `core/ouroboros-data/ouroboros-data-platform-adapter/src/main/resources/META-INF/services/com.ouroboros.data.model.DataOperationIdentityProvider:1`。
- 唯一生产实现 `com.ouroboros.security.data.adapter.CurrentSecurityDataOperationIdentityProvider:8` 的**包名**已避开 `com.ouroboros.data..` Core root（符合本文诉求），但**文件物理位置仍在 `core/ouroboros-data/ouroboros-data-platform-adapter` 模块内**——"归属 security adapter"目前只在包名层面成立，模块归属仍待决。

## 验证入口

- 按 `package` 声明与各模块 `pom.xml` 核对包/模块归属；模块上下文规则见 `ArchitectureClassImportSupport`。
- 定向验证按[测试规则](../../rules/testing.md)执行架构测试，附 HEAD、命令、测试计数和 allowlist 变化。规则行为见 `ArchitectureLayerSupportTest`，正式仓库依赖由两套架构入口验证。
- 决策：[ADR-003](../../adr/ADR-003-p0-3-family-splitting-strategy.md)。
- 未决事项：[技术债跟踪器](../../tech-debt-tracker.md)。
