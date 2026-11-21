# WebSocket 设计

## 责任与边界

本模块提供基于 STOMP 的发送/订阅、会话鉴权和可插拔 Broker。它不定义业务实时协议，也不承担持久消息/业务补偿平台职责。架构取舍唯一记录在[ADR-001](../../../adr/ADR-001-pluggable-websocket-broker.md)。

## 模块与主链

[聚合 POM](../../../../ability/ouroboros-websocket/pom.xml)包含 core、runtime、spring-boot-starter、三个 broker 实现及 integration-tests。core 定义 WebSocketBrokerStrategy、DistributedSessionRegistry、BrokerEnvelope 和本地操作合同；runtime 沿正式装配选择 broker，缺少所选实现时启动失败。Sender/subscriber、通道拦截和连接鉴权复用共有运行时，不另建业务注册链。

- SimpleBroker 使用单 JVM 内存，适合开发/单实例，不能跨实例共享订阅。
- RabbitMQ broker 使用 STOMP relay 路由和独立 AMQP 会话注册表事件；系统/客户端 relay 凭据与 Spring AMQP 配置分别维护。
- Redis broker 使用 Pub/Sub fanout，由来源实例标识去重；注册表有 TTL/heartbeat，提供会话信息，不能等同于持久消息或可靠投递保证。

## 生命周期、失败与扩展

会话由既有拦截链管理；分布式注册表续期和过期处理不替代客户端重连。新增 broker 实现正式 Strategy/Registry 合同并按已有自动配置注册，不改 sender 中的业务分支。订阅后的快照补偿和增量边界由[补偿订阅设计](../../cross-cutting/stomp-bootstrap-compensation-subscriptions.md)维护。

连接/鉴权/外部 broker 失败按[运维手册](../../../../ability/ouroboros-websocket/docs/operations-runbook.md)定位；多实例、重连与清理须由目标环境行为证据确认。不能从 ADR 的扩展目标推断高可用或投递保证已验证。

## 配置与验证入口

[配置指南](../../../../ability/ouroboros-websocket/docs/configuration-guide.md)对应[配置类](../../../../ability/ouroboros-websocket/ouroboros-websocket-core/src/main/java/com/ouroboros/websocket/config/WebSocketBrokerProperties.java)；操作迁移按[迁移指南](../../../../ability/ouroboros-websocket/docs/migration-guide.md)。集成用例位于[正式测试模块](../../../../ability/ouroboros-websocket/ouroboros-websocket-integration-tests)。

库内 application-websocket YAML 样例仍含旧字段/示例凭据，未在本文档任务修改工程输入，见 [DOC-013](../../../tech-debt-tracker.md#doc-013-websocket-工程样例配置)。使用当前配置指南，不能直接据旧样例判断已配置。
