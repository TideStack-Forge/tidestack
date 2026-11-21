# 能力层

## 定位

能力层对应 `ability`，负责把下层稳定抽象封装为可复用的运行时和业务能力模块，形成平台级通用服务。它位于 `core` 与 `security` 之上、`application-layer` 交付模块之下，是连接底层引擎与上层应用装配的中间层。

## 模块范围

能力层覆盖配置、通知、组织结构、文件上传、调度、消息队列、业务日志、用户设置、应用运行时等横向模块。这些模块共同构成平台运行时所需的业务能力与基础服务，但不直接等同于最终应用或平台产品。

### 模块清单

| 模块 | 职责 |
|------|------|
| `ouroboros-configuration` | 动态配置管理 |
| `ouroboros-notification` | 通知（站内信、邮件等） |
| `ouroboros-organization-structure` | 组织架构（部门、岗位） |
| `ouroboros-file-upload` | 文件上传管理 |
| `ouroboros-scheduler` | 任务调度 |
| `ouroboros-message-queue` | 消息队列 |
| `ouroboros-business-log` | 业务日志 |
| `ouroboros-user-setting` | 用户设置 |
| `ouroboros-app-runtime` | 应用运行时 |
| `ouroboros-cache-management` | 公共平台缓存管理 |
| `ouroboros-websocket` | WebSocket 通信 |
| `ouroboros-ai` | AI 能力集成 |
| `ouroboros-data-dict` | 数据字典 |
| `ouroboros-rpc-model` | RPC 调用模型 |
| `ouroboros-platform-component-lib` | 平台组件库 |
| `ouroboros-widget` | 小部件 |
| `ouroboros-ability-all` | 能力层聚合 POM |

## 责任边界

能力层负责沉淀可被多个上层场景复用的运行时能力和业务能力，把 `core` 的底层抽象与 `security` 的安全约束组织成更接近交付侧的服务接口。它应避免承载特定 `app-module`、`dev-module`、`platform` 或 `frontends` 的页面流程和产品编排，否则会失去中间层复用价值。

## 典型协作关系

能力层向下消费 `core` 的模型与执行能力，并在 `security` 统一边界内运行；向上则为 application-layer 中的 `app-module`、`dev-module`、`platform` 与前端交付提供稳定的运行时服务。典型协作模式是上层模块组合能力层完成业务交付，而不是直接绕过能力层重复封装相同业务能力。

## 契约与边界

### 对外契约（向上暴露的关键接口 / SPI / 自动配置）

| 能力族 | 上层应依赖的接口（模块） | 扩展 SPI（上层可提供实现） | 自动配置类 |
|--------|--------------------------|----------------------------|------------|
| configuration | `ConfigurationService`（`ouroboros-configuration-core`，`ConfigurationService.java:17`）；运行时 `RuntimeConfigurationService`（`service/RuntimeConfigurationService.java:14`） | `ConfigurationValueValidator:9`、`ConfigurationMetadataProvider:5`、`ConfigurationGroupMetadataProvider:5` | `ConfigurationRuntimeAutoConfiguration`（`.imports:1`） |
| notification | `EmployeeNotificationDispatcher` / `AddressNotificationDispatcher`（`notification-core`，`:10` / `:9`）；`NotificationService`（`notification-runtime`，`service/NotificationService.java:11`） | `NotificationAdapter<D,O>`（`adapter/NotificationAdapter.java:15`）、`NotificationAdapterSupplier:6`、`NotificationTemplateProvider:9` | `NotificationAutoConfiguration` + 7 个渠道 `Notification*AutoConfiguration` |
| websocket | `StompMessageSender`（`ouroboros-websocket-core`，`StompMessageSender.java:5`） | `WebSocketBrokerStrategy:5`、`DistributedSessionRegistry:6`、`LocalWebSocketOperations:5` | `OuroborosWebSocketAutoConfiguration`（starter `.imports:1`）；broker 配置类按 `broker-type` 条件装配 |
| scheduler | `Scheduler`（`ouroboros-scheduler-core`，`Scheduler.java:10`；实现 `QuartzScheduler:28`） | `ScheduleTaskProvider:5` | `OuroborosSchedulerRuntimeAutoConfiguration:27`、`OuroborosSchedulerQuartzAutoConfiguration:14` |
| message-queue | `MQProducer`（`ouroboros-message-queue-core`，`MQProducer.java:5`）；门面 `MQBridge:31` | `MQProducerCreator:5`、`MQBindingProvider:5`、`MQBinderReadyChecker:6` | `OuroborosMQRuntimeAutoConfiguration:33`、`OuroborosCommonMQCoreAutoConfiguration:14` |
| file-upload | `UploadStorageService`（`ouroboros-file-upload-core`，`UploadStorageService.java:28`）、`StorageManagerService:10` | `StorageFileNameGeneratorBuilder:7`、`StorageFilePathGeneratorBuilder:7`、`FileAccessUrlGeneratorBuilder:7` | `FileUploadRuntimeAutoConfiguration:22` |
| organization-structure | `OrganizationFacadeService`（`organization-structure-runtime`，`:19`，Javadoc 要求"所有组织架构相关操作都应通过此门面服务进行"） | `OrganizationNodeProvider:6`、`EmployeeProvider:7`、`AppointmentProvider:11`、`PositionDefinitionCenter:8` | `OrganizationStructureAutoConfiguration:25` |
| ai | `AiInvocationService`（`ouroboros-ai-runtime`，`:8`） | `AiChatAdapter:8`、`AiEmbeddingAdapter:8`、`AiRetriever:7`、5 个 `Ai*DescriptorProvider:8` | `AiRuntimeAutoConfiguration:35` + OpenAI/Anthropic/RAG/MCP 四个 runtime 配置类 |

其余能力的自动配置与入口：`AppRuntimeAutoConfiguration:47`（`ouroboros-app-runtime`）、`BusinessLogRuntimeAutoConfiguration:15`、`UserSettingAutoConfiguration:13`、`DataDictRuntimeAutoConfiguration:38`、`RpcRuntimeAutoConfiguration:27`、`WidgetRuntimeAutoConfiguration:12`、`PlatformComponentLibAutoConfiguration:12`、`ApprovalWorkflowExternalIntegrationAutoConfiguration:26`（入口 `ApprovalWorkflowEngineAdapter:11`，适配器 SPI `ApprovalWorkflowEngineAdapterProvider:13`）。

上层应通过**接口 + Spring 注入**依赖能力层（部分 SPI 走 `META-INF/services`），不要直接 new 实现类，也不要绕过门面服务自行拼装。

### 聚合装配（无自有契约）

- `ability/ouroboros-ability-all` 是纯聚合 POM（`<packaging>pom</packaging>`，仅 `-core` / `-runtime` 两个子模块，无 `src/`），子模块 `ouroboros-ability-all-core` / `-runtime` 也只做依赖聚合（`<packaging>jar</packaging>`，无 main 源码）：**该家族无自有契约，仅做装配聚合**。真正的运行时依赖只在 `ouroboros-ability-all-runtime/pom.xml:169-312` 声明。
- `ouroboros-notification-channel-all` 同为依赖聚合 POM（`pom.xml:14`，`git ls-files` 只有 1 个文件）。
- `ability/ouroboros-platform-component-lib` 无 `public interface`，其对外形态是 `WebAppInfoContributor` 的 `META-INF/services` 注册。

### 错误语义

- 平台异常体系：`ConfigurationService.java:102` 配置缺失 → `PlatformException`；`RuntimeConfigurationServiceImpl.java:242` 批量 CAS 缺少 `expectedRevision` → `PlatformException`；`UploadStorageService.java:83` 读文件失败 → `InternalException`；`OrganizationFacadeService` 同族的 `EmployeeController.java:378` 用户名冲突 → `BusinessException`。
- 模块自有异常均挂在共享体系下：`NotificationSendException extends BusinessException`（`notification/exception/NotificationSendException.java:8`，内置 `ErrorDefine` 枚举 1001-1302）、`AiInvocationException extends PlatformException`（`ai/exception/AiInvocationException.java:5`）、`AiPolicyViolationException` / `AiProviderProtocolException`、`WorkflowScopeUnconfiguredException extends PlatformException:14`、`RPCException extends OuroborosException:5`。
- 仅 `ExternalOperationFailure`、`RuntimeLockConsumptionException` 直接继承 `RuntimeException`（未纳入平台体系）。
- **fail-closed（必须抛、不得降级）**：broker 类型无对应 `WebSocketBrokerStrategy` bean → 启动失败 `IllegalStateException`（`WebSocketBrokerStrategySelectionConfiguration.java:22-24`，含未知类型 `:58`）；MQ 发送端创建失败 → `IllegalStateException`（`MQBridge.java:251`）；外部权限守卫未装配 → `PlatformException("[EXTERNAL_PERMISSION_DENIED]…")`（`FailClosedExternalBusinessRecordPermissionGuard.java:13-15`，由 `ApprovalWorkflowExternalIntegrationAutoConfiguration.java:41-45` 的 `@ConditionalOnMissingBean` 兜底）；`WorkflowScopeResolver` 缺失 → `WorkflowScopeUnconfiguredException`（`DefaultWorkflowScopeResolver.java:13-14`）；AI 未配置凭据 / 不支持的 option / transport → `AiInvocationException` / `AiProviderProtocolException`（`AnthropicAdapterFactory.java:63`、`AnthropicChatAdapter.java:236`、`AiMcpClientRegistry.java:157`）；AI 关键协作者为 null → `Objects.requireNonNull`（`DefaultAiRuntimeSnapshotResolver.java:16-17`）；配置初始化失败 → `PlatformException("配置初始化失败：")`（`ConfigurationRuntimeAutoConfiguration.java:51-52`）。
- **已核实的静默降级（设计时不要假设 fail-closed）**：调度器 bean 缺失只 `logger.error("计划任务调度器未初始化")` 后返回（`OuroborosSchedulerRuntimeAutoConfiguration.java:65-67`）；通知渠道适配器未加载的异常被外层 `catch (Exception e)` 吞成 FAIL 状态（`NotificationDispatcherImpl.java:244`、`:253`、`:271`）；MQ 订阅侧配置不全降级为 `DisabledConsumer`（`MQBridge.java:206-210`、`:144-148`，注意发送侧是 fail-closed）。
- 错误传播按调用面判断：例如通知模板管理抛 `BusinessException`，通知资源 I/O 抛 `InternalException`，调度脚本在缺少 Scheduler 时返回失败。不能把某一适配层的 `IllegalStateException` / `Try.failure` 概括为整个能力族的唯一错误形式。

### 边界条件

- **前置条件**：`ConfigurationValueValidator` 必须无副作用，校验失败直接抛平台异常使批次回滚（Javadoc `ConfigurationValueValidator.java:6-7`）。
- **不变量**：`ConfigurationService` 返回的 revision token 在有效值变化时必然变化、值未变时保持稳定（跨重启跨实例一致），调用方用它做缓存键而不自行算指纹（Javadoc `ConfigurationService.java:38-39`）；批量 CAS 每个 key 都要有 `expectedRevision`（`RuntimeConfigurationServiceImpl.java:238-242`）。
- **写序不变量**：配置更新固定为清缓存 → 重载 → 发事件（`RuntimeConfigurationServiceImpl.java:249`、`:252`、`:255`）。
- **并发/幂等**：MQ 在 context refresh 前发出的动作先入队、READY 后只重放一次（`MQBridge.java:66-81`）；外部动作要求同一幂等键重复调用返回首次结果且不重复产生远程副作用、同键不同命令必须失败（`ExternalWorkflowActionExecutor.java:14-15`），恢复查询缺少幂等键或请求指纹直接 `IllegalArgumentException`（`ExternalStartRecoveryQuery.java:21-23`）；scheduler 已存在任务时先 `cancel` 再 `scheduleCron`，以 `TriggerKey.triggerKey(taskName, defaultGroup)` 为幂等标识（`QuartzScheduler.java:40-49`）；websocket 会话注册表 TTL 60s 必须大于心跳 20s（`WebSocketBrokerProperties.java:242-246`）。
- **禁止回退**：外部集成 scope 必须来自已认证的应用/租户上下文，禁止回退到全局默认作用域（`WorkflowScopeResolver.java:6`）。
- **明确不做**：不定义平台核心引擎协议；不做独立安全层治理；不承载具体页面流程与产品编排；能力层 SPI 的线程安全契约**未核实**（main 源码无相关声明）。
- **HTTP 前置门禁**：`LifecycleGuardFilter.java:62-64` 在 Ouroboros 生命周期未到 `READY` 前直接写启动中响应并返回（order `-1000`，`:29`）。

## 不负责什么

能力层不负责定义平台核心引擎本身，不负责独立的安全层治理，也不负责具体应用产品、开发管理界面、平台启动入口或前端基座交付。它面向复用的运行时与业务能力，而不是最终 application-layer 交付物。
