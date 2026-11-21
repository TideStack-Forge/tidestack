# 核心引擎层

## 定位

核心引擎层对应 `core`，负责定义 Ouroboros 低代码平台最关键的运行时抽象与引擎能力，是整个平台可编排、可扩展、可生成的内核。

## 模块范围

核心引擎层覆盖 `ouroboros-data`、`ouroboros-business-model`、`ouroboros-logicflow`、`ouroboros-ui-model` 等核心引擎，以及与其相邻的工作流、Web API 模型、字段类型、菜单模型、应用模块模型等核心模型与执行框架。首轮层文档按"核心引擎群"描述这些模块，不在本页拆成逐模块摘要。

### 模块清单

| 模块 | 职责 |
|------|------|
| `ouroboros-data` | 动态数据模型引擎（元数据定义、CRUD、QueryDSL） |
| `ouroboros-business-model` | 业务模型抽象 |
| `ouroboros-logicflow` | 逻辑流引擎（编排、执行、静态分析） |
| `ouroboros-ui-model` | UI 模型表达（Schema、组件映射） |
| `ouroboros-approval-workflow` | 审批工作流引擎（Flowable 集成） |
| `ouroboros-field-type` | 字段类型系统 |
| `ouroboros-menu-model` | 菜单元模型 |
| `ouroboros-app-module-model` | 应用模块模型 |
| `ouroboros-error-code` | 错误码体系 |
| `ouroboros-i18n` | 国际化 |
| `ouroboros-resource` | 资源管理 |
| `ouroboros-statistics-reporting` | 统计报表 |

## 责任边界

核心引擎层负责提供动态数据模型、business-model 抽象、logicflow 执行、ui-model 表达、Web API 模型化、字段与菜单元模型等平台内核能力。它可以定义领域无关但平台相关的核心协议，却不应直接承担具体业务能力的装配、组织权限治理或平台交付编排。

## 典型协作关系

核心引擎层建立在 `libs` 之上，为 `security` 提供可被保护的核心对象与运行边界，为 `ability` 提供可复用的底层模型与执行能力，再由上层模块把这些引擎能力组合成配置、通知、组织、应用模块和平台运行时。典型协作方式是上层围绕 `ouroboros-data`、business-model、logicflow、ui-model 等引擎做装配，而不是把上层业务流程回灌到核心层。

## 契约与边界

### 对外契约（上层应通过什么依赖核心层）

| 引擎 | 上层应依赖的接口（模块） | 扩展 SPI（上层提供实现） | 运行时自动配置类 |
|------|--------------------------|--------------------------|------------------|
| `ouroboros-data` | `com.ouroboros.data.model.TypedDataModel<PK,M>`（`ouroboros-data-typed-core`，`:16`）；`DataModel`（`ouroboros-data-core`，`:22`，模型级查询入口 `query()` `:116`）；链式查询 `com.ouroboros.data.dsl.query.DataModelQuery`（`ouroboros-data-core`）；查询编排 `orchestration.QueryOrchestrator`（`ouroboros-data-core`，`:28`） | `DataModelMetaProvider:11`、`DataOperationIdentityProvider:8`、`DataStationProvider:12`、`DataModelPlugin:13`、`DataModelMetaDecorator:5`、`DataModelValidator:6`、`ValueTypeBuilder:9`、`PrimaryKeyGeneratorFactory:7`、`OperatorFactory:12`、`QueryTranspiler:14`、`DataAdapterBuilder:16`、`JoinCapability:12`（均在 `ouroboros-data-core`） | `com.ouroboros.data.SpringDataStationAutoConfiguration`（`ouroboros-data-runtime`，`.imports:1`）、`TypedDataModelAutoConfiguration`（`ouroboros-data-typed-runtime`）、`DataInitializationAutoConfiguration`（`ouroboros-data-initialization-runtime`） |
| `ouroboros-ui-model` | `uischema.UISchemaProvider`（`ouroboros-ui-model-schema`，`:7`）、`uimodel.UIModelProvider`（`ouroboros-ui-model-core`，`:7`） | 同上 SPI 名单中的 schema 侧扩展点 | `uimodel.UIModelAutoConfiguration`（`.imports:1`，类体仅 `@ComponentScan`）、`uischema.UISchemaAutoConfiguration`（`ouroboros-ui-model-schema-runtime`，`.imports:1`） |
| `ouroboros-logicflow` | `logicflow.LogicFlowExecutionFacade`（`ouroboros-logicflow-core`；默认实现 `DefaultLogicFlowExecutionFacade` 留在 runtime；门控异常保持引擎独立，由 Web adapter 映射 HTTP 400） | `model.FlowModelProvider`（`ouroboros-logicflow-core`，`:6`） | `logicflow.LogicFlowAutoConfiguration`（`.imports:1`） |
| `ouroboros-webapi-model` | `webapi.WebAPIModelProvider`（`ouroboros-webapi-model-core`，`:5`）、`WebAPIRouteRegistry`（同模块 `:5`） | `WebAPIModelProvider`（runtime 中有对应服务声明） | `webapi.WebAPIAutoConfiguration`（`ouroboros-webapi-runtime`，`.imports:1`） |
| `ouroboros-field-type` | `fieldtype.FieldTypeCatalog`（`ouroboros-field-type-core`，`:9`） | `FieldTypeDefinitionProvider:5` | `fieldtype.runtime.FieldTypeRuntimeAutoConfiguration`（`.imports:1`） |
| `ouroboros-menu-model` | `menu.MenuModelProvider`（`ouroboros-menu-model-core`，`:8`）、`menu.service.MenuService`（`ouroboros-menu-model-runtime`，`:10`） | — | `menu.MenuModelAutoConfiguration`（`.imports:1`） |
| `ouroboros-app-module-model` | `appmodule.AppModuleIntegrator`（`ouroboros-app-module-model-core`，`:15`）、`AppModuleProvider:6` | — | `appmodule.runtime.AppModuleRuntimeAutoConfiguration`（`.imports:1`） |
| `ouroboros-business-model` | `businessmodel.BusinessModelMetaProvider`（`ouroboros-business-model-core`，`:8`）、`schema.EditorSchemaProvider:14` | — | `businessmodel.AppBusinessModelAutoConfiguration`（`.imports:1`）、`DefaultBusinessModelAutoConfiguration`（`ouroboros-business-model-default`） |
| `ouroboros-statistics-reporting` | `statistics.reporting.runtime.execution.DatasetExecutionService`（`ouroboros-statistics-reporting-runtime`，`:5`）；对上层主要经 REST 暴露 | `runtime/provider/DatasetDefinitionProvider:7` | `statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration`（`.imports:1`） |
| `ouroboros-i18n` | `i18n.error.ErrorMessageLocalizer`（`ouroboros-i18n-core`，`:5`）、`i18n.runtime.service.I18nLocaleService`（`:13`） | — | `i18n.runtime.I18nAutoConfiguration`（`.imports:1`） |
| `ouroboros-resource` | `resource.spi.ResourceService`（`ouroboros-resource-core`，`:18`） | `resource.provider.ResourceProvider:18` | `resource.runtime.config.ResourceAutoConfiguration`（`.imports:1`）、`ResourceRuntimeManageAutoConfiguration` |
| `ouroboros-error-code` | `errorcode.registry.ErrorCodeRegistry`（`ouroboros-error-code-core`，`:8`） | `ErrorCodeDefinitionProvider:7` | `errorcode.runtime.OuroborosErrorCodeRuntimeAutoConfiguration`（`.imports:1`） |
| `ouroboros-approval-workflow` | `workflow.approval.FlowInstanceService`（`ouroboros-approval-workflow-core`，`:20`）、`FlowDefinitionService:15` | — | `workflow.approval.ApprovalWorkflowAutoConfiguration` 等 5 个类（`ouroboros-approval-workflow-runtime/.../AutoConfiguration.imports:1-5`） |

两套 SPI 发现机制并存：data 内核用自研 `com.ouroboros.data.util.DataServices`（JDK `ServiceLoader`，`DataServices.java:20`），其余 core 引擎通常用 libs 的 `com.ouroboros.util.SPI`。实现必须登记在 `META-INF/services/<接口 FQCN>`。

> 注意包归属：`com.ouroboros.data.dict.*` 属 `ability/ouroboros-data-dict`；`com.ouroboros.workflow.approval.external.*` 属 `ability/ouroboros-approval-workflow-external-integration` —— 二者都**不在** core。另外 `ouroboros-data-spi`、`ouroboros-data-provider` 两个模块在仓库中**不存在**（`core/ouroboros-data/pom.xml:53-69` 的 `<module>` 列表无此二者）。

### 错误语义

- **data 引擎内核异常不继承平台异常体系**（对应 AGENTS.md 第 5 条的内核豁免）：`DataModelException extends RuntimeException`（`data/exception/DataModelException.java:3`）→ `DataValidationException:11` → `RecordValidationException:16`（推荐使用的校验异常）→ `com.ouroboros.data.exception.ValidationException:16`（`@Deprecated`，仅为向后兼容，保留 `getErrors()/getJoinedErrors()/getErrorMessage()`）。`core/ouroboros-data/**` main 源码**零**处 import `com.ouroboros.exception.*`。
- 易混点：全仓有两个 `ValidationException`。`workflow/approval/web/controller/base/BaseController.java:165` 用的是**平台** `com.ouroboros.exception.ValidationException`（import 见同文件 `:23`），不是 data 的那个。
- data 侧其余异常：`UniqueConstraintViolationException`（`UniqueConstraintViolationException.java:12`，触发 `DuplicateDataCheckerDataModelPlugin.java:83`、`:91`）、`NormalizeException`（`NormalizeException.java:12`，主源码 81 处抛点）、`TranspileException`（`:12`）、`StatementException:11`、`TypeMismatchException:11`、`EntityNotFoundException:11`、`QueryExecutionException`（`DefaultDataModel.java:190`）、`PrimaryKeyGenerationException`（`FixLengthCodingGeneratorFactory.java:157`、`:170`）、`OrchestrationException extends RuntimeException`（`orchestration/OrchestrationException.java:8`）。
- logicflow 侧：`FlowRuntimeException extends RuntimeException`（`exception/FlowRuntimeException.java:7`）、`DesignException`（抽象，`:3`）及其子类 `NodeDesignException:5`、`ValidationFailedException:7`，另有 `LogicFlowExecutionGateException extends com.ouroboros.exception.InternalException`（`ouroboros-logicflow-runtime`，`:5`）。
- 无自有异常、直接用平台异常的引擎：`ouroboros-webapi-model`（0 个 `*Exception` 类，`RequestMappingIntegrationCenter.java:319`、`:338` 抛 `PlatformException`）、`ouroboros-statistics-reporting`（0 个 `*Exception` 类，主要用 JDK 异常）。
- 其余：`FlowDefinitionCodecException extends RuntimeException`（`ouroboros-approval-workflow-core/.../codec/FlowDefinitionCodecException.java:3`）、`FieldTypeCompileException extends RuntimeException`（`ouroboros-field-type-core/.../FieldTypeCompileException.java:3`）、`ResourceNotFoundException extends BusinessException`（`ouroboros-resource-core/.../exception/ResourceNotFoundException.java:8`）、`BusinessDefineException extends BusinessException`（抽象，`ouroboros-business-model-default/.../BusinessDefineException.java:10`）。
- 审批流、i18n、Web API 等边界使用平台异常；具体错误合同在各模块共同正文维护，不从全仓构造次数推导统一行为。
- **必须 fail-closed 的场景**：logicflow 执行门禁——流程名为空 / 模型未定义 / 定义有误 → `LogicFlowExecutionGateException`（`DefaultLogicFlowExecutionFacade.java:37`、`:50`、`:62`）；data 转译遇到无 SPI 支持的操作符 → `TranspileException`，不返回 null（`SExpressionTranspiler.java:27-30`）；字段类型不存在 → `Either.left(FieldTypeCompileException)`（`DefaultFieldTypeCatalog.java:79`）；statistics-reporting 无法唯一确定 DataSource → `IllegalArgumentException("sql datasource is required")`（`SpringSqlDataSourceProvider.java:93`）；审批接口默认方法显式失败（`FlowDefinitionService.java:20`、`FlowInstanceService.java:276`、`:289`）。
- **注意 fail-open 面**：`DataServices.getPrimaryService` 以 `orElse(null)` 收尾（`DataServices.java:45-47`），无实现时返回 `null` 而非抛错；`FlowInstanceService.java:293-295` 默认返回 `Collections.emptyList()`（静默降级）。
- `@ConditionalOnMissingBean` 在 core 共 41 处、仅 5 个文件（statistics-reporting-runtime 26、data-initialization-runtime 8、resource-runtime 4、data-test-support 2、error-code-runtime 1）；data / ui-model / logicflow / webapi / field-type / menu-model / app-module / business-model / approval-workflow 的 runtime 自动配置**未使用**它。

### 边界条件

- **SPI 注册与排序**：实现必须登记 `META-INF/services`（`DataServices.java:20`）；排序依据 `jakarta.annotation.Priority`，`getCachedReversedServiceStream` 为反序（值大者先），`getPrimaryService` 取最高优先级（`DataServices.java:41`、`:45`、`:57-63`）；转译与插件按"首个匹配者胜出"（`SExpressionTranspiler.java:27-30`、`EnhancedDataModelProxy.java:133`、`:148-153`）；错误码按 provider `getOrder()` 分层，同层先到先得（`DefaultErrorCodeRegistry.java:51`、`:74`）。
- **缓存不变量**：SPI 实例被 `computeIfAbsent` 永久缓存，只有 `clearCache()` 能失效（`DataServices.java:32`、`:49`）——实现集合变化需重启或显式清缓存。
- **事务要求**：`DBCodingSequencer.next(...)` 标注 `@Transactional` 且经自注入 `self` 调用以保证代理生效（`DBCodingSequencer.java:16`、`:26`、`:35`）；审批 `ApprovalCaseService` 的轮次保留/关联/回滚/事件消费均 `@Transactional`（`:42`、`:63`、`:90`、`:114`）。
- **默认 Bean**：`@ConditionalOnMissingBean(ErrorCodeRegistry.class)` → `DefaultErrorCodeRegistry`（`OuroborosErrorCodeRuntimeAutoConfiguration.java:13-16`）。
- **可降级点（已核实）**：`MenuServiceImpl` 缺 `AppModuleIntegrator` 时用 `ObjectProvider.getIfAvailable(() -> Collections::emptyList)` 降级（`:40-42`）。
- **参数校验风格**：core 主源码**不用** `Assert.notNull`，用 `Objects.requireNonNull`（如 `DefaultDataModelQuery.java:64`、`:390`）。
- **查询上下文并发边界**：`OrchestrationContext` 使用普通 Map 与队列，不保证同一实例的并发读写；调用方须隔离或协调实例的并发使用。`OperatorAliasResolver` 的并发缓存不能替代上下文自身的同步。
- **无自有契约、仅做装配聚合**：`core` 本身及 13 个家族聚合 POM（`ouroboros-app-module-model`、`-approval-workflow`、`-business-model`、`-data`、`-error-code`、`-field-type`、`-i18n`、`-logicflow`、`-menu-model`、`-resource`、`-statistics-reporting`、`-ui-model`、`-webapi-model`）均无 `src/main/java`。另外 `ouroboros-data-spring-boot-starter`、`ouroboros-data-sql-legacy-spring-boot-starter` **不是模块**（目录内只有未跟踪的 `target/`，且不在 `core/ouroboros-data/pom.xml:53-69` 的 `<module>` 列表中）。
- **明确不做**：不负责统一认证授权、不负责面向业务场景的横向能力封装、不负责具体应用交付与平台发布入口。

## 不负责什么

核心引擎层不负责统一认证授权，不负责面向业务场景的横向能力封装，不负责具体应用交付、开发工具页面或平台发布入口。它也不在本页展开共享扩展机制细节；共享机制统一由跨模块专题说明。
