# ouroboros-message-queue 模块设计摘要

## 模块定位

`ouroboros-message-queue` 是能力层中的消息队列抽象模块，负责把消息绑定、生产消费和运行时接入统一为平台可复用的 MQ 能力，并把底层中间件差异收敛在适配层。它关注的是“平台如何组织消息能力”，而不是某一种消息产品的专有特性。

## 责任边界

该模块负责消息生产者/消费者抽象、绑定配置模型、运行时桥接、低代码 MQ 绑定管理和默认中间件适配，不负责把业务事件语义塞进核心层，也不负责替代完整的事件驱动架构平台。维护时需要守住“统一 MQ 抽象”与“具体 RabbitMQ 实现”“具体业务事件”的边界。

## 依赖关系

模块分为 `core`、`rabbitmq`、`runtime`、`dev` 四层：`core` 定义 `MQBridge`、绑定模型和建造器，`rabbitmq` 提供默认 binder 侧适配，`runtime` 负责低代码绑定加载、LogicFlow 消费/发送接入和自动配置，`dev` 负责 `mq-binding` 的开发平台管理与 VCS 文件检查器。新增中间件适配应优先通过 `MQProducerCreator`、`MQBindingPropertiesConfigurer`、`MQBinderReadyChecker` 等扩展点落地，而不是直接在 `MQBridge` 中硬编码新的中间件逻辑。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-message-queue/DESIGN.md](../../../../ability/ouroboros-message-queue/DESIGN.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 维护入口

当前机制在模块 DESIGN.md 持续维护。历史评审与产品分析退出默认阅读链，不作为当前功能或支持承诺。
