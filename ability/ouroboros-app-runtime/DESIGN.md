# Ouroboros App Runtime 设计文档

## 1. 概述

`ouroboros-app-runtime` 是能力层中的应用运行时承载模块，负责把配置、安全、组织结构、通知、缓存、消息队列与一组运行时插件能力收口成统一的应用运行环境。它不是新的业务能力模块，而是面向上层平台应用的运行时装配层。

该模块的核心价值有两点：

- 为平台应用提供稳定的 Spring Boot 运行时装配入口。
- 在运行时统一承接生命周期防护、系统信息、缓存、分布式 ID、脚本上下文和数据模型插件能力。

## 2. 模块结构

当前目录以单模块形式交付，`pom.xml` 显示它直接依赖：

- 配置能力：`ouroboros-configuration-core`
- 安全能力：`ouroboros-web-security-core`、`ouroboros-web-security-annotation`
- UI 与组织结构能力：`ouroboros-ui-model-microweb`、`ouroboros-organization-structure-core`
- 通知与日志能力：`ouroboros-notification-core`、`ouroboros-notification-channel-all`、`ouroboros-business-log-runtime`
- 应用文件资源：`ouroboros-resource-core`、`ouroboros-resource-runtime`
- 运行时基础设施：`ouroboros-message-queue-core`、`common-cache`、`common-lock`、Redis / Redisson

这种依赖形态说明它不是纯粹的工具模块，而是能力层中的“运行时集成节点”。

## 3. 核心职责

### 3.1 自动配置与生命周期防护

`AppRuntimeAutoConfiguration` 是模块最核心的装配入口，用来把运行时所需的控制器、插件和基础设施组件接入 Spring 容器。`LifecycleGuardFilter` 则负责应用生命周期防护，避免在不合适的生命周期阶段对外暴露不稳定能力。

### 3.2 数据模型插件体系

设计稿中最重要的一组扩展是数据模型插件，包括：

- `BasicAudit` operator identity provider integration
- `AccessControlDataModelPlugin`
- `DataOperationIdentityProvider` security context integration for core data plugins
- 组织结构相关插件

这些插件说明 `ouroboros-app-runtime` 不只是“暴露几个接口”，而是在运行时阶段统一为数据模型附加审计、访问控制和组织上下文等横切行为。

数据模型插件体系还包括删除策略（软删除）相关插件。

### 3.3 运行时 Web / API 支撑

该模块还承接了运行时信息暴露与异常处理职责，例如：

- `SystemInfoController`
- `AppMetadataController`
- `ExceptionControllerAdvice`

因此它既是装配层，也是运行时观察面的一部分。

### 3.4 基础设施辅助能力

除应用运行时装配外，该模块还提供：

- `SpringBeanCacheManager` 等缓存管理能力
- Redisson 分布式锁管理
- 脚本上下文扩展
- 运行时依赖锁读取与启动输入校验（`ResolvedRuntimeLockProvider`）

这些能力共同构成了“应用运行时承载层”的最小闭环。

## 4. 扩展与集成

`ouroboros-app-runtime` 的典型扩展点包括：

- 数据模型插件
- Web 信息提供者
- 脚本上下文包装器

这些扩展点虽然分散在不同技术面上，但治理原则一致：都应通过统一的 SPI、自动配置和共享运行时协议接入，而不是在模块内部再造第二套注册机制。

## 5. 维护重点

维护该模块时，应长期守住以下边界：

- 它是运行时承载层，不是业务能力汇总黑洞。
- 新增通用能力前，先判断是否应该下沉到更专门的 `ability` 模块。
- 新增插件或控制器时，要优先复用现有 SPI 与自动配置机制。
- 任何对外暴露的运行时 API，都应谨慎评估是否属于本模块的职责边界。
