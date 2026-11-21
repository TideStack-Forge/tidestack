# 分层设计

本目录存放 Ouroboros 各层的详细设计文档。

## 分层架构概览

Ouroboros 采用 6 层架构，每层有明确的职责和依赖方向：

```
┌─────────────────────────────────────┐
│  Platform Layer (平台运行时层)        │  ← 可执行应用
│  - ouroboros-develop                │
│  - ouroboros-mothership             │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Application Layer (应用层)          │  ← 业务应用模块
│  - app-module/*                     │
│  - dev-module/*                     │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Ability Layer (业务能力层)          │  ← 可复用业务能力
│  - configuration, notification      │
│  - websocket, scheduler, mq         │
│  - file-upload, org-structure, AI   │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Security Layer (安全层)             │  ← 安全框架
│  - web-security-*                   │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Core Layer (核心引擎层)             │  ← 低代码引擎
│  - data, ui-model, webapi-model     │
│  - logicflow, approval-workflow     │
│  - field-type, menu-model, i18n     │
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Libs Layer (基础设施层)             │  ← 工具和基础设施
│  - ouroboros-util, common-*         │
└─────────────────────────────────────┘
```

## 各层设计文档

### [Libs Layer（基础设施层）](support-layer.md)

**职责**: 提供纯工具类和共享基础设施。可执行应用属于 Platform 层。

**深入文档**:
- [支撑层架构说明](support-layer-architecture.md)：说明 `libs` 的模块族分工、扩展机制和上层集成边界。

**核心模块**:
- `ouroboros-util`: 纯工具类（Functions, Maps, Lists, Json, Dates, Paths, SPI）
- `common-*`: 通用组件（异常、Web、缓存、模板、脚本等）

**依赖**: 设计上仅依赖第三方库，不依赖其他层。

> 存量偏离：`libs` 存在指向 `core` 的依赖（如 `common-web` → `ouroboros-data-core`），已登记在 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv` 的 P0-3 基线中，属待收敛项。

### [Core Layer (核心引擎层)](core-layer.md)

**职责**: 提供低代码平台的核心引擎和元数据系统。

**核心模块**:
- `ouroboros-data`: 动态数据模型引擎（QueryDSL、多数据源）
- `ouroboros-ui-model`: UI 组件和 Schema 系统
- `ouroboros-webapi-model`: REST API 动态生成
- `ouroboros-logicflow`: 逻辑流引擎
- `ouroboros-approval-workflow`: 审批工作流（主线 Flowable；Activiti6 位于 legacy-compat）
- `ouroboros-field-type`: 字段类型系统

**依赖**: libs（设计上不得依赖 security / ability / dev-module / app-module）

> 存量偏离：`core` 当前存在指向 `security`、`ability`、`dev-module` 的依赖，均已登记在 P0-3 基线，属待收敛项。

### [Security Layer (安全层)](../../../security/DESIGN.md)

**职责**: 提供 Web 安全框架和 RBAC 权限管理。

**核心模块**:
- `ouroboros-web-security-annotation`: 安全注解定义
- `ouroboros-web-security-core`: 核心安全抽象
- `ouroboros-web-security-spring-adapter`: Spring Security 适配
- `ouroboros-web-security-rbac-manage`: RBAC 管理
- `ouroboros-web-security-runtime`: 运行时实现

**依赖**: libs, core（设计上不得依赖 ability 及更上层）

> 存量偏离：`security` 当前存在指向 `ability` 的依赖（如 `ouroboros-web-security-runtime` → `ouroboros-configuration-core`），已登记在 P0-3 基线。

### [Ability Layer (业务能力层)](ability-layer.md)

**职责**: 提供可复用的业务能力服务。

**核心模块**:
- `ouroboros-configuration`: 统一配置管理
- `ouroboros-notification`: 多渠道通知系统
- `ouroboros-websocket`: WebSocket 支持（可插拔 Broker）
- `ouroboros-scheduler`: 定时任务管理（Quartz）
- `ouroboros-message-queue`: 消息队列抽象（RabbitMQ）
- `ouroboros-file-upload`: 文件上传处理
- `ouroboros-organization-structure`: 组织架构和用户管理
- `ouroboros-ai`: AI 能力集成（OpenAI、Anthropic、RAG、MCP）

**依赖**: libs, core, security

### [Application Layer (应用层)](application-layer.md)

**职责**: 提供具体的业务应用模块和开发工具。

**核心模块**:
- `app-module/*`: 业务应用模块（dynamic-form, document-management, ui-schema-management）
- `dev-module/*`: 开发工具模块（app-management, datasource, environment, VCS）

**依赖**: libs, core, security, ability

### [P0-3 Family Splitting](p0-3-family-splitting.md)

**职责**: 为 `P0-3` 的 family/subpackage 建模提供专门的分层决策输入。

**核心主题**:
- 为什么 `uischema` 和 `appmodule` 不能继续按 family root 赋层
- 哪些子包应按 Core / App / Dev 处理
- 哪些位置需要 module-context-aware 分类

### [AppModule Authority Bridge](appmodule-authority-bridge.md)

**职责**: 解释 `appmodule.model` 中 Authority 相关类型为何不能按 strict Core 处理，以及桥接带决策对 `P0-3` 的影响。

**核心主题**:
- authority-linked cluster 是否应视为合法 bridge / projection band
- `appmodule` 的下一步 `P0-3` 细化应如何继续

### Platform Layer (平台运行时层)

> Platform 层（`platform/` 下的 `ouroboros-develop`、`ouroboros-mothership`、`ouroboros-base`）**没有独立的分层设计页**；`support-layer.md` 讲的是支撑层（`libs`），不是 Platform。Platform 的职责见 [application-layer.md](application-layer.md) 与 [support-layer-architecture.md](support-layer-architecture.md)。

## 各层契约与边界速查

各层共同正文说明对外契约、错误语义与边界条件；Libs 的详细契约见[支撑层架构说明](support-layer-architecture.md)。下表只提供跨层索引，具体行为以对应模块共同正文为准。

| 层 | 底层向上暴露的代表性契约（模块） | 错误语义基线 | 缺失协作方时的取向 |
|----|----------------------------------|--------------|--------------------|
| Libs（`libs`） | `OuroborosException` 体系（`common-exception`）、`WebApiResult`/`AbstractWebApiResult`（`common-web`）、`SPI` 工具类（`ouroboros-util`）、通过 `META-INF/services` 声明的 SPI | 自有 5 个异常类型；`ValidationException` 已 `@Deprecated`，改用 `com.ouroboros.data.exception.RecordValidationException` | **fail-open**：`SPI.getPrimaryService` → `orElse(null)`，消费方 `orElseGet` 内建默认实现（`SPI.java:365-370`） |
| Core（`core`） | 数据引擎 `TypedDataModel`/`DataModel`/`DataModelQuery`（`ouroboros-data-typed-core`/`-core`）、`LogicFlowExecutionFacade`（`ouroboros-logicflow-runtime`）、各引擎 `*Provider` SPI | data 内核用**自有**异常树（`DataModelException` → `DataValidationException` → `RecordValidationException`），不继承平台异常；approval-workflow / i18n / webapi 抛平台异常 | **fail-closed** 于执行门禁与转译（`LogicFlowExecutionGateException`、`TranspileException`）；`DataServices.getPrimaryService` 仍可返回 `null` |
| Security（`security`） | `@Anonymous`/`@Authority`（`ouroboros-web-security-annotation`）、`Authority`/`AccessControlDefine`/`AuthorityCenter`/`CurrentSecurityContextHolder`（`ouroboros-web-security-core`）、~25 个 `*Provider` SPI | `SecurityException` → `TokenException` → `InvalidToken`/`TokenRevoked`/`SessionException`；未认证 `UnauthenticatedException`，未授权 Spring `AccessDeniedException` | **fail-closed**：未认证即 `UnauthenticatedException`，URI 不匹配任何已授予 API 即拒绝；main 源码 `@ConditionalOnMissingBean` 为 0 |
| Ability（`ability`） | 各能力族门面接口 + `*AutoConfiguration`（configuration/notification/websocket/scheduler/message-queue/file-upload/organization-structure/ai 等） | 模块自有异常均挂平台体系：`NotificationSendException`、`AiInvocationException`、`WorkflowScopeUnconfiguredException`、`RPCException` | **部分 fail-closed**（broker 缺失启动失败、`FailClosedExternalBusinessRecordPermissionGuard`）；scheduler/notification/MQ 订阅侧存在**静默降级**，详见 [ability-layer.md](ability-layer.md) |
| Application（`app-module`/`dev-module`/`platform`） | 各交付模块的 runtime/autoconfigure + 门面服务（`DocumentLibraryManagementFacade`、`DynamicFormDataService`、`AppManagementService` 等）；两个 `@SpringBootApplication` 入口 | 自有异常继承平台体系（`CircularReferenceException`/`ResourceNotFoundException`/`StorageException`/`VCSException` 等） | **默认装配**：`@ConditionalOnMissingBean` 提供平台默认实现；`document-management` 开关 `matchIfMissing=true` |

> 无自有契约、仅做装配聚合的位置：`ability/ouroboros-ability-all`（父模块为聚合 POM，子模块以 JAR 坐标聚合依赖，无 main Java 源码）、`dev-module/ouroboros-dev-module-all`、`core` 下各家族聚合 POM。`platform/ouroboros-base` **不是 Maven 模块**（无 `pom.xml`，只在 `platform/pom.xml:16-17` 之外存在 `docker/`）。详见各层页面。

## 分层规则

### 依赖方向规则

1. **单向依赖**: 只能从上层依赖下层，禁止反向依赖
2. **禁止反向依赖**: 下层不能依赖上层（如 `core` 依赖 `ability`）；上层可以直接使用允许的下层，例如 `ability → libs`。具体约束见[架构约束](../../architecture-constraints.md)。
3. **接口隔离**: 通过接口定义依赖，而非具体实现

### SPI 扩展机制

**SPI 依赖反转**: 下层定义接口（SPI），上层提供实现。它保持编译依赖朝向下层，不豁免反向依赖。

真实例子（`core` 定义端口，上层提供实现）：

```
core/ouroboros-logicflow             定义 NodeBuilder SPI
    ↑ (实现)
ability/ouroboros-configuration/
  ouroboros-configuration-logicflow-node  提供 GetConfigNode.Builder
```

### 横切关注点

横切关注点（如配置、安全、可观测性）通过以下方式接入各层：
- SPI 扩展点
- Spring Boot 自动配置
- AOP 切面
- 事件总线

详见 [横切关注点设计](../cross-cutting)。

## 架构验证

分层规则通过以下方式验证：
1. **ArchUnit 测试**: 自动检查分层依赖、循环依赖（`testing/architecture-tests`，开启构建测试时随 CI 的 `build` 阶段运行）
2. **Maven Enforcer**: 只检查 Maven/Java 版本、bannedDependencies 与 dependencyConvergence，**不检查依赖方向**（`pom.xml:239-271`）
3. **代码审查**: 人工审查架构决策

详见[架构约束](../../architecture-constraints.md)。

## 相关文档

- [架构设计](../architecture) - 系统架构概览
- [模块设计](../modules) - 各模块详细设计
- [横切关注点](../cross-cutting) - 跨层设计
- [P0-3 Family Splitting](p0-3-family-splitting.md) - P0-3 family/subpackage 分层决策输入
- [AppModule Authority Bridge](appmodule-authority-bridge.md) - appmodule authority-linked cluster 的桥接带决策说明
