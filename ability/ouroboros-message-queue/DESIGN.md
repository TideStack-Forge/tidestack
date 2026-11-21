# ouroboros-message-queue 设计文档

## 1. 概述

`ouroboros-message-queue` 是 Ouroboros 能力层中的消息队列抽象模块，负责把消息绑定、生产消费和运行时接入统一为平台可复用的 MQ 能力，并把底层中间件差异收敛在适配层。它关注的是“平台如何组织消息能力”，而不是某一种消息产品的专有特性。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由四部分组成：

- `ouroboros-message-queue-core`
- `ouroboros-message-queue-rabbitmq`
- `ouroboros-message-queue-runtime`
- `ouroboros-message-queue-dev`

其中：

- `core` 定义 `MQBridge`、`MQProducer`、`MQConsumer` 及纯契约。生产者创建、绑定 configurer、ready-checker、自动配置和 Stream 桥接属于 `runtime`，避免核心包反向带入 Spring/Stream 实现。
- `rabbitmq` 提供当前默认 RabbitMQ 适配实现。
- `runtime` 负责低代码绑定加载、LogicFlow 消费/发送接入和自动配置。
- `dev` 负责 `mq-binding` 的开发平台管理、界面资源和 VCS 文件检查器。

当前逻辑流节点定义位于 `runtime` 中，而不是单独拆成独立模块。

## 3. 核心模型与机制

### 3.1 MQBridge

`MQBridge` 是统一的消息桥接入口，负责协调 producer、consumer、绑定配置和运行时延迟初始化。它是整个模块的中心骨架。

### 3.2 MQProducer 与 MQConsumer

`MQProducer`、`MQConsumer` 是消息生产和消费的顶层协议，用于把发送/订阅能力从具体中间件实现中抽离出来。

### 3.3 MQBindingProperties 与绑定来源

`MQBindingProperties` 是生产者/消费者共享的绑定配置基类；`MQBindingProvider`、`MQBindingIntegrator` 负责编排绑定来源与聚合装配。这说明消息绑定不是硬编码在应用里，而是允许多来源治理。

### 3.4 中间件适配扩展点

`MQProducerCreator`、`MQBindingPropertiesConfigurer`、`MQBinderReadyChecker` 是 runtime 中的中间件适配与运行时就绪治理扩展点；RabbitMQ 的实现依赖只落在 `rabbitmq` 与 runtime 的正式运行时路径。它们决定了“如何接入新的 MQ 产品”应落在哪些边界内。

## 4. 绑定到运行时消费的数据流

当前实现中的关键链路是：

- 绑定定义通过 provider / integrator 体系被发现和聚合。
- `MQBridge` 统一管理 producer、consumer 与绑定生命周期。
- 当前默认 RabbitMQ 适配层负责把通用抽象映射到具体 binder 能力。
- `runtime` 把 MQ 能力进一步接入 LogicFlow 与低代码运行时。
- `dev` 为绑定配置和版本治理提供维护入口。

这意味着维护时要守住两层边界：

- MQ 抽象与绑定治理属于本模块核心。
- 具体业务事件语义与具体中间件专有能力不应直接侵入核心抽象。

## 5. 维护重点

维护该模块时，应优先关注：

- `MQBridge`、producer/consumer 协议与绑定治理体系之间的职责分离。
- 新中间件接入优先通过 creator / configurer / ready-checker 等扩展点落地。
- 默认 RabbitMQ 路线应保持为适配层，而不是反向定义 MQ 核心抽象。
- 当前绑定、发送和就绪机制在本文件维护；单次评审与产品分析归关联任务，采纳结论再回写本文件。
