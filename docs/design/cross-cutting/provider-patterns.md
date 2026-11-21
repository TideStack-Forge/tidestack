# Ouroboros Provider 模式与聚合链路

## 目标

本页用于统一说明 Ouroboros 中常见的 provider、center、integrator、builder 等角色分工，帮助维护者在新增扩展点时判断“应该新增来源、修改聚合、还是增加构建步骤”。

## 为什么需要这套模式

Ouroboros 很多能力都不是单一来源直接消费，而是“多来源定义 -> 统一聚合 -> 运行时消费”的链路。字段类型、菜单、应用模块、流程模型、RPC 模型、MQ 绑定都符合这种模式。它们共享的核心问题是：平台既要允许模块外追加定义，又要在最终消费前形成一份稳定、去重、有顺序的全局结果。

## 常见角色

### Provider

Provider 负责暴露某一类定义的单个来源。它通常回答“我这里有哪些定义”，而不负责全局冲突治理。

典型例子：

- `FieldTypeDefinitionProvider` 暴露字段类型定义。
- `MenuModelProvider` 暴露菜单定义。
- `AppModuleProvider` 暴露应用模块定义。
- `MQBindingProvider` 暴露消息绑定定义。
- `RPCModelMetadataProvider` 暴露 RPC 元数据定义。
- `BusinessOperationTemplateProvider` 显式声明模板来源并暴露该来源下的日志模板。

### Center

Center 负责把多个 provider 的结果汇总成平台可直接查询的统一视图。它通常处理去重、排序、缓存或只读访问入口。

典型例子：

- `FieldTypeCatalog` 以大小写无关的键聚合多个 `FieldTypeDefinitionProvider`。
- `MenuModelCenter` 聚合所有菜单并按 `order` 排序。
- `FlowModelCenter` 聚合流程模型提供者并作为运行时读取入口。
- `RPCModelDefinitionCenter` 维护刷新后的 RPC 静态定义映射。

### Integrator

Integrator 适用于“仅仅拼接 provider 结果还不够”的场景。它会在 provider 之上再做一层平台语义整合，例如冲突覆盖、结构重排、默认补位或面向运行时的统一视图。

典型例子：

- `MQBindingIntegrator` 负责把多个绑定来源整成一份最终 binding 列表。
- `AppModuleIntegrator` 负责根据模块定义重排菜单和权限，而不是直接暴露原始模块元数据。

### Builder

Builder 负责把元数据或节点描述构造成最终运行时对象。它更偏“从描述到可执行对象”的转换，而不是简单聚合。

典型例子：

- `RPCModelDefinitionBuilder` 根据 `RPCModelMetadata` 构建静态定义，`RpcProtocolInvoker` 承担具体协议执行。
- `NodeBuilder` 根据流程节点元信息构建具体 `FlowNode`。

## 两条典型数据流

当前仓库里最常见的共享链路主要有两条：

### 多来源聚合链

`source -> provider -> center/integrator -> runtime consumer`

适用于字段、菜单、MQ binding、流程模型等场景。这里的重点是“允许追加来源，但最终只暴露一份平台视图”。

### 元数据到运行时对象构建链

`metadata provider -> definition builder -> definition center -> executor -> protocol invoker`

适用于 RPC 模型。这里的重点是定义始终保持描述性，Center 只发布静态目录；副作用集中在 executor 与 protocol invoker 边界。

## Spring 与 SPI 的桥接方式

这套模式经常需要同时跨越 Spring Bean 与 Java SPI 两侧。仓库中的典型做法是：

- Spring Bean 负责真正读取文件、VCS、数据库或应用上下文。
- 内部静态 `Proxy` / `SingletonHolder` 实现 SPI 接口，把 Spring 管理对象暴露给全局聚合链。
- Center 或 Integrator 再通过 `SPI.getSortedServiceStream(...)`、`SPI.getCachedSortedServiceStream(...)`、`SPI.getCachedReversedServiceStream(...)` 汇总所有实现。

例如消息队列的低代码绑定提供者就是一个 Spring 组件，但真正进入聚合链的是内部 `Proxy` 实现的 `MQBindingProvider`。

## 刷新、顺序与去重

Provider 链路一旦允许多个来源，就必须明确四件事：

- 什么时候刷新：很多实现会监听 `OuroborosMetadataRefreshingEvent` 或 `OuroborosMetadataRefreshedEvent` 来重建缓存。
- 如何排序：SPI 通常通过 sorted/cached/reversed 变体控制优先级和遍历顺序。
- 如何去重：聚合结果经常会落到大小写无关的 `TreeMap`，以名称作为最终冲突裁决点。
- 如何标识来源：来源身份必须由 Provider 合同或定义数据显式给出，禁止通过实现类名、包名或发现顺序推断。

如果这四件事没有在 center / integrator 层统一处理，provider 数量一多，最终行为就会变得不可预测。

## 什么时候新增哪一层

维护时可按下面的判断规则选型：

- 只是新增一个同类来源：优先新增 provider。
- 已有多个 provider，但还缺少统一冲突治理或最终视图：新增或调整 integrator。
- 平台需要统一的查询入口、缓存或排序结果：新增或调整 center。
- 元数据还不能直接执行，需要按类型挑选构建逻辑：新增 builder。

反过来说，如果只是增加一种来源，却去改 center 的核心读取契约，往往说明边界放错了。

## 维护约束

维护这套模式时，应坚持以下约束：

- Provider 返回的应是同一语义层的定义，不要混入过多派生结果。
- 多来源 Provider 必须显式声明来源身份与冲突键；同层冲突应失败或遵循正式覆盖规则，不能静默依赖 ServiceLoader 顺序。
- Center 不要反向承载来源读取细节；它应依赖 provider，而不是自己去读文件或数据库。
- Integrator 只处理全局整合，不要偷偷承担业务执行。
- Builder 的 `support` 判断应稳定、可解释，避免多个 builder 对同一元数据产生模糊竞争。

## 深入阅读入口

- [extension-mechanisms.md](extension-mechanisms.md)
- [spring-autoconfiguration.md](spring-autoconfiguration.md)
- [../modules/core/ouroboros-field-type.md](../modules/core/ouroboros-field-type.md)
- [../modules/core/ouroboros-menu-model.md](../modules/core/ouroboros-menu-model.md)
- [../modules/ability/ouroboros-message-queue.md](../modules/ability/ouroboros-message-queue.md)
- [../modules/ability/ouroboros-rpc-model.md](../modules/ability/ouroboros-rpc-model.md)
