# Ouroboros Business Model 设计文档

> 设计依据：[业务模型 Action / Event：面向本体的动态行为建模](docs/business-model-actions-and-events.md)。当前实现见本文 §8；使用方式见[业务行为开发指南](../../docs/guides/business-model-behaviors.md)。
>
> 动作、事件、订阅的当前契约见[行为契约](docs/business-model-behavior-contracts.md)，设计取舍见 [ADR-005](../../docs/adr/ADR-005-business-model-behavior-ontology.md)。
>
> 图解入口：[总体架构](docs/business-model-actions-and-events.md#3-语义模型与边界)、[订阅定义 UML](docs/business-model-actions-and-events.md#63-订阅定义-uml)、[三种执行方式与恢复流程](docs/business-model-actions-and-events.md#711-三种执行方式的事务边界)。
>
> 稳定约束与验收要求见[业务行为契约](docs/business-model-behavior-contracts.md)。模型执行与派发接口、默认 CRUD 事实和前置条件的当前机制见 §8。目标环境的验收结果归对应任务或 CI 附件。

## 1. 概述

`ouroboros-business-model` 是 Ouroboros 低代码平台的业务建模核心模块，负责把业务语义统一建模，并投影为数据模型、UI、Web API、权限、菜单、逻辑流与工作流等多维技术产物。它不是单一运行时组件，而是连接多个核心引擎的业务投影中枢。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由以下主要子模块组成：

- `ouroboros-business-model-core`
- `ouroboros-business-model-default`
- `ouroboros-business-model-runtime`
- `ouroboros-business-model-dev`
- `ouroboros-business-model-test`
- `ouroboros-business-model-util`

其中：

- `core` 负责业务模型、字段/关系读取能力、公开元数据、格式解析及编辑 SPI、投影器、处理步骤与验证框架等核心抽象。
- `default` 负责默认实现和内置投影处理链。
- `runtime` 负责运行时集成与自动配置。
- `dev` 负责开发平台中的模型管理与编辑支持。
- `util` 提供同一业务模型后端共用的注解绑定、Schema/类型生成和模块适配支持；注解贡献继续通过原 SPI 注册，组件继续由原包扫描装配。`test` 提供测试支撑。

跨能力消费者使用 `BusinessModel` 的字段、关系、行为及视图读取能力，不强转默认实现。开发编辑通过 `BusinessModelFormatUpgrade` / `BusinessModelScriptAction` SPI 选择默认后端；实现和注册位于 `default`，`runtime` 与 `dev` 的生产依赖不引入 `default`。平台聚合负责选择默认模型实现。

## 3. 核心模型与机制

### 3.1 BusinessModel

`BusinessModel` 是模块最核心的抽象，描述模型元数据、验证能力以及 `buildModelBundle()` 这样的总投影入口。它承载的是业务模型语义，而不是某个具体技术产物本身。

### 3.2 BusinessModelMeta

`BusinessModelMeta` 负责承接模型名称、命名空间、来源、版本和扩展属性等元数据。它使业务模型既能被稳定识别，也能以扩展属性形式挂载更多配置。

### 3.3 ModelBundle 与投影器

`ModelBundle` 是多维投影结果容器，`ModelBundleProjector` / `DefaultModelBundleProjector` 负责把单个业务模型投影成多个目标模型。这是该模块区别于普通配置模型的关键设计点。

### 3.4 ModelProcessStep 管道

处理管道是模块的真正执行骨架。各类 `ModelProcessStep` 会把业务模型逐步投影到：

- 数据模型
- 权限规则
- 逻辑流
- UI 与 UI Schema
- Web API
- 菜单
- 工作流

因此，`ouroboros-business-model` 的核心设计不是单一对象模型，而是“业务模型 + 投影管道 + 结果集合”的三段式结构。

### 3.5 `submitTogether` 聚合生命周期

`submitTogether` 表示主业务模型拥有的子集合随主模型 add/update 一起提交。它的解释权在 business-model 聚合编排层，而不是 DataModel 或子模型公开 CRUD flow。

当前实现的核心运行组件是 `syncChildCollections` LogicFlow 节点，节点背后由 `ChildCollectionSynchronizer` 执行聚合同步：

- 投影期把 owned relation 编译为 `ChildCollectionSyncPlan`，并携带 owner/child DataModel 结构 fingerprint；运行期只校验 fingerprint，不重新解释 BusinessModelCenter。
- add/update 自动流程在主模型自身写入成功后调用一个 `syncChildCollections` 节点，节点通过正式 `DataModel` 接口做 snapshot、diff、既有 update 循环、batchInsert、delete 和结果归并。
- 根 create/replace 只初始化 owner provenance；每一层只为 `EXISTING` owner 读取 snapshot，`NEW` owner 按 create 语义处理，嵌套同步继续传播 provenance。
- 缺失 relation field 表示不修改；显式空集合表示删除当前 owner 的全部子项；客户端提交的 reference field 始终被当前 owner key 覆盖，不能把 child 重挂到其他 owner。
- 聚合提交不调用子模型公开 CRUD flow，也不执行子模型 before/after script；权限、审计、唯一性、逻辑删除、SCD2 等仍由子 DataModel 的正式插件链执行。
- 节点不创建局部事务，也不直接调用 `DataAdapter`；Web/API 外层事务负责主记录与全部子写入的一致提交或回滚。
- 父集合遗漏删除触发 owned descendant cascade 时，按 cascade owner keys 批量 discovery，并从叶到根删除；存在更新或删除时完成后重读该层；只有插入或无差异时，复用既有 snapshot 与 `batchInsert` 返回值归并结果，不额外重读。

## 4. 依赖与协作边界

该模块直接依赖：

- `ouroboros-logicflow-core`
- `ouroboros-web-security-core`
- `ouroboros-menu-model-core`
- `ouroboros-ui-model-schema`
- `ouroboros-data-core`
- `ouroboros-webapi-model-core`

这说明它本身不替代这些引擎，而是通过统一建模把它们组织起来。维护时必须持续守住“协调投影”而非“吞并实现”的边界。

## 5. 维护重点

维护该模块时，应优先关注：

- `BusinessModel`、`ModelBundle`、`ModelBundleProjector`、`ModelProcessStep` 之间的职责分离。
- 新功能优先以新的处理步骤或验证器接入，而不是把默认实现做成不可维护巨石。
- 投影目标的边界必须清晰，避免业务模型反向耦合具体引擎实现细节。
- 多代设计文档并存时，以本文件作为权威入口，旧设计稿仅作补充或历史参考。

## 6. 插件模型类型过滤（已实现）

业务模型插件按**两层过滤**协作，二者语义不同、不可合并：

- **L1 类型适用性**：`BusinessModelFeaturePluginRegistry.getPluginsForModelType(String modelType)` 按 `BusinessModelMeta.modelType` 选出注册表中适用于该类型的插件，返回不可变列表并保持注册顺序。类型词表由 `BusinessModelTypes` 统一声明：`ALL = "*"`、`DEFAULT = "default"`、`DASHBOARD = "dashboard"`、`COMPOSITE_VIEW = "composite-view"`；未声明适用类型的插件不参与调度。
- **L2 feature enablement**：`getEnabledPlugins(BusinessModelMeta)` 读取模型 `extraProps` 的 `enabledPlugins` 或 `plugins.enabled`，并自动补齐 `PluginMetadata.getRequiredPlugins()` 声明的依赖插件，按注册优先级返回。

`PluginFeaturesMPS` 走 L1+L2 组合调度；`@FunctionalInterface` 已从 `PluginProjector` 移除，投影契约固定为两参 `void project(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context)`，投影目标由上下文按 `meta.getFullName()` 取得，不再把 `DataModelMeta` 暴露为接口参数。

## 7. 业务模型类型：dashboard 与 composite-view

`dashboard` 与 `composite-view` 都是在业务模型定义管线中定义的正式 `modelType`（不是独立的平行体系）：

- **composite-view**：`CompositeViewBusinessModelMeta` 描述 source / columns / filters / actions / layout，`CompositeViewBusinessModel` 负责校验数据源声明、列配置、过滤器与操作按钮绑定，`BusinessModelEditorUISchemaProvider` 为 `modelType=composite-view` 生成编辑 schema；运行时执行入口 `POST /api/runtime/composite-view/{viewName}/execute`，只读查询继续走统计分析 Dataset 执行流水线。
- **dashboard**：`DashboardBusinessModelMeta` 描述 layout / widgets，widget 使用 `chart` 类型并通过顶层 `chartName` 引用 Chart。`DashboardBusinessModel` 按业务模型完整名称投射 UI-model、结构 WebAPI 和权限；结构 API 只返回 Dashboard 结构，Chart 数据由 widget 独立请求 Chart Web API。编辑面经 `DashboardEditorSchemaProvider` 和标准业务模型编辑 SPI 暴露。
- 全局筛选绑定的方向是「响应式数据域 + widget 字段映射」：`globalFilterBinding.source` 默认为 `reactive-data-domain`，不基于 EventBus。

## 8. 动态业务行为与事实

默认业务模型通过 `actions`、`events` 和消费方 `eventSubscriptions` 定义业务行为及事实。`fields` 和 `relations` 分别承接本体属性与关系；关系身份不复制到字段或 Action Schema。动态定义与注解 / SPI 贡献沿 `BusinessModelBehaviorResolver → BehaviorDefinitionValidator → BehaviorMPS → ModelBundle → BusinessModelExport` 汇合，冲突和投影错误拒绝整个候选。正式刷新与同步执行共享生命周期锁；刷新失败拒绝业务执行，成功完整刷新后恢复，不执行旧实现。

行为契约编辑器由正式模板生成 `BusinessBehaviorCatalog` 工作台，合并只读解析目录与原始动态定义编辑；自动检查复用开发服务的完整草稿预览。字段结构复用 AMIS JSON Schema 编辑器，高级结构保留 JSON 编辑；参数映射只构造已有 EL 计划，不在浏览器求值。动态动作选择逻辑流，订阅选择事件与动作；代码贡献保持其来源所有权。详见[编辑器与输入操作](../../docs/guides/business-model-behaviors.md#定义动作与事件)。

格式兼容由 `BusinessModelMetaParser` 统一判定。`RelationValidator` 新增的名称冲突、关系类型与引用键结构检查仅用于 `formatVersion=2.0`；缺省、`0.1`、`1.0` 保留原有关系校验与投影规则，不删除或改写关系，原有关联目标可用性检查继续生效。旧格式生成实现的自定义 API 缺少 `actionInputMapping` 时，在配置读取层默认映射 `request.params` 与 `request.body`，校验、投影和显式格式升级共用此规则；新版动作引用仍须显式声明映射。

`BusinessModel.invokeAction` / `emitEvent` 经 core SPI 访问当前 Runtime / EventPort；不缓存 Bean、请求或旧行为快照，模型离线构建不要求启动运行环境。`BusinessActionRuntime` 是 HTTP、内部与订阅 Action 的唯一执行入口。CRUD / custom 保留原操作身份并归一化为 Action；关闭原开关同步禁用 Action，getOne / search 默认只读。运行时重验 Authority、对象 / 字段访问、输入输出 Schema、只读约束和绑定能力，在已配置事务管理器上执行 LogicFlow 或同步 `BusinessActionHandler`。对象仅接受模型声明的单个 `id` 主键；对象参数按当前状态和授权解析。审计记录实际写集、事件身份与父执行，不复制业务受控字段。

只读 Action 通过正式业务写入守卫和 EventPort 禁止业务写入与 emit；底层事务仍允许平台审计写入，使完成审计与源事务共同提交。只读不等于将 JDBC 连接设为禁止全部 DML。

前置条件由发布快照编译成受限 EL 计划，availability 与实际调用共用；写动作经正式授权锁查询重读对象状态，并对非 id 关系键锁后重验。字段不可读返回授权阻塞，条件不满足为 PRECONDITION；订阅执行时的条件竞争先回滚消费事务，再按 claim 所有权登记 PRECONDITION_BLOCKED。非空 authorization 在发布时明确拒绝。

默认 CRUD 的 mutation 计划生成 recordsCreated / recordsUpdated / recordsDeleted；只读取自身直接写集，子调用只合并审计，子集合不自动获得子 Action 事实。事实按 ID/字节限额拆分，每份独立消费、无重组或顺序承诺。默认事实仅披露实际 ID，保留完整无过滤模型读取限制，删除后交付不依赖对象仍存在；源写入、事实和交付责任同事务提交。

Action 使用 `BusinessActionInput<P,D>`，根只含 params/data；没有特殊 target，所有对象角色（含数组）在 Schema 内声明，状态按 JSON Pointer 放在可信 Context。发布快照编译校验与 Flow 输入投影计划，所有输入先完成默认值、严格转换、Schema 和对象授权再执行。高代码 SPI 接收 Context/Input；注解方法通过 BusinessActionParam / BusinessActionData 绑定自由参数表。Flow 工作视图兼容 RequestInfoMap：保留 params/data/context，body 默认别名到 data，未映射的 HTTP 属性惰性读取原请求；params 的已映射键及显式 null 优先，其他键保留原参数及不可枚举 Header 回退。脚本工作修改与原业务输入隔离，高代码 Input 不增加 HTTP 字段。

WebAPI 显式使用 BusinessAction 类型及 businessModel/action/actionInputMapping。Lazy HTTP Input 身份直达 Runtime/Handler，body 单次解析；完整 RequestInfoMap 作为可选 context.request 保留，同步 Controller / 嵌套调用可使用，独立 Event/Outbox 不继承。必需请求通过 binding.requiresHttpRequest 声明。CRUD 投影保留原节点契约，batchDelete 仍接 data.ids，import 保留原生同步文件；update 显式检查重复 id，主子表仍沿正式插件链。详见[输入设计](docs/business-action-input-design.md)。Event payload 始终是可持久化 JSON 对象。

`BusinessEventPort` 校验业务事实，在源事务内保存不可变 `BusinessEventOccurrence` 与逐订阅 Outbox 责任。非 Action 生产者通过 `inTransaction` 包住业务写入与 emit，并显式声明已注册生产者与 Authority；Action 须声明 `emits`。订阅以 `sourceEvent / executionMode / target` 表达来源、执行边界及动作/监听器目标；统一 `target.inputMapping`，监听器静态配置使用 `target.config`。JOIN_SOURCE_TRANSACTION 在 emit 内同步执行并共享源事务，失败标记整个源事务回滚；同步成功责任随源事务提交，不由 Outbox 重放。AFTER_COMMIT_TRANSACTIONAL 使用正式 DataModel、行锁、租约和退避恢复，本地业务效果与确认共同提交。AFTER_COMMIT_INDEPENDENT 在释放行锁且无消费事务时调用具备独立回执/幂等恢复能力的监听器。责任创建时固定执行模式，重试不允许改换模式。平台从系统配置按消费模型解析已授权服务账号，订阅不携带账号或身份覆盖；原 actor 保留为审计来源。每个 attempt 使用当前完整发布快照并按当前消费身份重新授权；授权或契约阻塞保留事实，修复后显式重驱。Spring 只适配选定的同步监听调用，不能替代平台确认或向广播暴露载荷。

资源证明首期覆盖 `DataSourceTransactionManager` / `JdbcTransactionManager` 管理的同一实际 JDBC Connection。未参与资源、不同事务连接和无法证明的事务管理器在写入前拒绝；首期没有 JTA/XA 资源证明，不承诺跨数据源原子性。审批事实及待处理表保留原存储与身份，仅共享 `OutboxLease` / `OutboxBackoff`。部署与恢复见[运行手册](../../docs/runbooks/business-model-behaviors.md)。

可选类型生成读取开发端正式 Bundle 中的行为契约，派生 Java 客户端 / DTO / Event 载荷和 TypeScript 客户端；动态成员发布不要求生成类。生成客户端始终调用上述 runtime / EventPort。Action 指纹含 params-data-v1 输入协议与输入输出 Schema；Event 指纹包含 payload Schema。兼容改绑不改变契约，过期 SDK 在执行前拒绝；生成监听器同时核对原始事实与当前 Schema。无法保真表达的 Schema 使用 Map/Object/unknown 保留动态形状，运行时校验始终权威。

正式[集成场景](../../testing/integration/feature/lowcode/business-model-actions-events/contract.md)覆盖真实 MVC、正式业务投影、Java/TypeScript 编译调用、Handler 与编译 LogicFlow 改绑、事务、Outbox 恢复、授权与存量审批 / submitTogether 回归。

## 9. 业务权限的 OAuth2 委托声明

本次沿用原权限与数据投影链，在 `accessControl.<search|add|update|delete>.delegable` 显式声明操作是否允许第三方委托，默认关闭。模型编辑器提供中文开关；`AbstractAuthorityMPS → BusinessModelAuthority` 保留该声明及完整字段定义。可委托操作由原数据权限插件执行，角色数据约束、原授权快照及当前凭据范围共同生效，详见[委托授权设计](../../security/delegated-authorization-design.md#10-业务模型的委托声明与授权快照)。
