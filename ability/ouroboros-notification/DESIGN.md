# Ouroboros Notification 设计文档

## 1. 概述

`ouroboros-notification` 是平台的统一通知能力模块，负责把多种通知渠道抽象成一致的发送模型，并与模板系统、逻辑流和组织结构能力协同工作。它的设计核心是“统一通知抽象 + 可扩展渠道实现”，而不是围绕某个单一通知渠道构建。

## 2. 模块结构

聚合模块由五个主要部分组成：

- `ouroboros-notification-core`
- `ouroboros-notification-channel`
- `ouroboros-notification-dev`
- `ouroboros-notification-runtime`
- `ouroboros-notification-logicflow-node`

这种拆分把抽象层、渠道实现层、运行时层、开发管理层和逻辑流集成层分开，有利于在渠道持续增长时保持稳定内核。

## 3. 核心机制

### 3.1 适配器体系

`NotificationAdapter` 是渠道统一抽象。不同渠道通过适配器模式接入，使“发送通知”这一动作能够与邮件、短信、钉钉、微信、内部消息等具体实现解耦。

### 3.2 模板体系

通知模板与模板变量替换机制负责把通知内容从渠道实现中剥离出来。模板系统使维护者能够先定义通知结构，再决定由哪些渠道投递，从而把内容治理与发送治理分开。

渠道 schema / options 把模板结构和运行时参数与适配器解耦。

`NotificationTemplate` 定义通知模板和载荷结构。

### 3.3 分发体系

当前 `NotificationDispatcherImpl` 实现两类分发协议：

- `AddressNotificationDispatcher`
- `EmployeeNotificationDispatcher`

这说明通知系统既支持直接面向地址发送，也支持依托组织和人员模型进行投递。

### 3.4 持久投递计划与回执

[NotificationDispatcherImpl](ouroboros-notification-runtime/src/main/java/com/ouroboros/notification/NotificationDispatcherImpl.java) 的 `sendWithResult(NotificationDispatchRequest, NotificationDeliveryPolicy)` 使用原通知服务保存投递计划，以 `sourceFactId + ruleIdentity` 找到既有计划，并核对规则 revision、模板及接收人集合。计划按接收人和渠道保留地址、schema、options；后续尝试复用该快照。它要求入口没有外层事务，避免持有业务锁等待渠道调用。

每个单元先由服务认领尝试，再执行当前接收策略及渠道调用，最后确认当前尝试的结果。策略可跳过或阻断；渠道结果区分 `ACCEPTED`、`RETRYABLE`、`BLOCKED`、`UNKNOWN`。已调用渠道但无法确认结果时使用 UNKNOWN，不把异常直接当成可安全重发；持久化故障向调用方传播，由所属 Outbox 恢复。完成状态表示本协议已收口，不能直接推导用户已阅读或端到端 exactly-once。

[NotificationAdapter.sendWithResult](ouroboros-notification-core/src/main/java/com/ouroboros/notification/adapter/NotificationAdapter.java) 的默认空 Optional 明确表示“不支持，且未发送”；Dispatcher 随后调用原代理的 `send`，保留事务与拦截器边界。渠道返回 null 或调用抛错则作为未知结果处理。适配器不得先发送再返回空结果。

旧的地址/员工 `send` 和另一组 `sendWithResult` 仍返回渠道汇总，不自动获得持久投递计划语义。敏感载荷通过 `SensitiveNotificationPayload` 标识，旧入口不落库其 payload/options，并使用收敛的失败原因。回归入口为 [ReliableNotificationDispatchIntegrationTest](ouroboros-notification-runtime/src/test/java/com/ouroboros/notification/service/impl/ReliableNotificationDispatchIntegrationTest.java)；本页不记录单次运行结论。

### 3.5 逻辑流与脚本集成

通知能力还能通过 `logicflow-node` 和脚本系统进入编排场景，使“通知”不仅是业务代码中的一个 API，也能成为流程中的一类动作节点。

### 3.6 运行时配置管理提案

[消息通知运行时配置设计](docs/runtime-management-design.md)复用原数据库 `-20`、应用文件 `-10`、JAR `0` 的高优先级同名覆盖：在原模板表保存整份覆盖，撤销时条件删除覆盖行，由原模板中心回退读取默认定义。运行时管理用例服务承载默认对比、校验及事务编排，原持久化服务补条件写入且保持数据库职责，二者单向依赖；渠道管理描述从原类型补充生成，参数继续走统一配置。[列表、查看与编辑页面设计](docs/runtime-management-ui.md)是该方案的页面组成部分。模板管理闭环、迁移/CAS、渠道描述、凭据策略及正式 AMIS 页面已实施并通过本地组合验收；结果和全仓基线限制见[实施验收记录](docs/runtime-management-validation.md)。渠道参数在原统一配置命令中按元数据选项撤销数据库覆盖，其他组件保持原恢复规则。使用与部署前提见[管理指南](docs/runtime-management-guide.md)。

## 4. 分层边界

### 4.1 Core 层

负责通知抽象、模板模型、分发协议和统一适配器接口，是通知系统的稳定内核。

### 4.2 Channel 层

负责邮件、钉钉、微信、短信、内部消息等渠道实现。该层可以变化很快，但不应反向污染核心抽象。

### 4.3 Runtime / Dev / LogicFlow 层

这三层分别承接运行时支持、模板管理和流程编排集成，是统一通知能力向上层平台扩展的三个主要出口。

## 5. 扩展机制

本模块最核心的扩展面包括：

- 新渠道适配器
- 模板变量和模板编辑能力
- 分发配置
- 逻辑流节点注册

维护时应尽量把变化限制在 `channel` 和外围集成层，避免新增渠道逻辑侵入 `core` 的统一模型。

## 6. 维护重点

该模块后续演进的关键不是“继续增加多少渠道”，而是：

- 维持抽象层与实现层解耦
- 保持模板、分发、渠道三者职责分离
- 为高频扩展点提供稳定 SPI
- 避免把组织结构、用户模型或业务事件语义重新吸收到通知内核
