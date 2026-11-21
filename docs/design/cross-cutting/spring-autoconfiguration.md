# Ouroboros Spring AutoConfiguration 装配模式

## 目标

本页用于说明 Ouroboros 中“启动期自动装配”这类共享模式应当放在哪里、承担什么职责，以及它与 SPI/Provider 链路的边界。它关注的是平台装配规则，而不是某个具体模块自己的 Bean 明细。

## 当前仓库现状

主线以 Spring Boot 3 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 作为自动配置发现入口，同时保留 `META-INF/spring.factories` 的兼容声明。新增或修改主线自动配置时，两份资源应声明同一组配置类：imports 每行一个类名，factories 在 `org.springframework.boot.autoconfigure.EnableAutoConfiguration` 下列出类名。

`legacy-compat`、`quickbuilder` 中仍有只保留 factories 的历史入口，不作为新增模块模板。其他用途的 factories 条目不因自动配置双注册而删除。

## AutoConfiguration 负责什么

在 Ouroboros 中，AutoConfiguration 更适合承接以下职责：

- 把 runtime、dev、adapter 模块中的组件扫描、默认 Bean 和配置属性装入 Spring 容器。
- 把某个能力模块的“运行时接线”组织起来，例如桥接器、控制器、默认 integrator、第三方 binder 适配等。
- 把启动期或刷新期需要监听的生命周期事件注册进去，让静态中心或缓存有机会在合适时机重建。

它不适合直接替代 SPI 的多实现发现，也不适合把大量业务编排逻辑塞进配置类本身。

## 常见放置位置

从当前仓库结构看，自动装配通常落在“真正需要被应用引入”的子模块，而不是抽象定义模块：

- `*-runtime` 负责业务运行时接线，例如消息队列 runtime 会在容器启动后聚合 MQ 绑定并接入 LogicFlow 消费。
- `*-dev` 负责开发管理能力接线，例如消息队列 dev 模块负责把管理界面、开发服务和文件检查器接进开发平台。
- 适配子模块可拥有自己的自动配置，例如具体中间件或容器接入模块。
- `core` 更偏向抽象、模型和扩展协议定义，除非某部分本身就是通用 starter，否则不应把大量运行时装配压回 `core`。

这条规则的目的，是让“抽象定义”和“应用引入时的默认接线”分离。

## 注册与配置属性

配置类可沿用模块现有的 `@Configuration` 或 `@AutoConfiguration` 写法；注解不能代替资源注册。主线同时维护：

```text
# META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.ouroboros.example.ExampleAutoConfiguration
```

```properties
# META-INF/spring.factories
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ouroboros.example.ExampleAutoConfiguration
```

配置属性则应在自动装配边界附近声明，例如容器 Docker 配置通过 `@ConfigurationProperties(prefix = "ouroboros.dev.container.docker-api")` 绑定外部配置。对于第三方属性对象或非组件属性对象，再按模块需要引入 `@EnableConfigurationProperties`。

对维护者来说，关键不是注解写法本身，而是确保“可外部配置的内容”通过属性类进入，而不是散落在硬编码常量或 Bean 构造器里。

## 生命周期与刷新时机

Ouroboros 的自动装配经常不止参与“容器启动”，还要参与“元数据与模型刷新”。这部分应优先复用 `common-app` 中的生命周期事件，而不是自定义一套刷新协议：

- `OuroborosMetadataRefreshingEvent` 适合在刷新前重扫文件、VCS 或低代码定义来源。
- `OuroborosMetadataRefreshedEvent` 适合在元数据稳定后重建中心缓存或衍生运行时模型。
- `OuroborosContextRefreshedEvent` 适合在上下文完整可用后触发最终接线，例如订阅 consumer、准备 binding 或执行只应发生一次的启动流程。

例如：

- 消息队列低代码绑定提供者在 `OuroborosMetadataRefreshingEvent` 时重扫 `mq-binding/`。
- RPC 运行时在 `OuroborosMetadataRefreshedEvent` 后刷新 `RPCModelDefinitionCenter` 与认证中心。
- `OuroborosContextRefreshLifecycle` 统一给出了上下文、元数据、模型三段刷新顺序。

因此，自动装配类更像是“把刷新链路接进 Spring 生命周期”的入口，而不是缓存重建逻辑本身的最终落点。

## 与 SPI / Provider 的边界

AutoConfiguration 和 SPI 往往会同时出现，但职责不同：

- AutoConfiguration 负责把默认实现和运行时接线装进容器。
- SPI / Provider 负责让多来源定义、多实现扩展和平台级聚合可以被追加。

典型做法是：自动装配类在容器内创建一个默认 integrator、bridge 或 controller；真正的可扩展来源则通过 SPI provider 提供。这样新增来源时，只需要补 provider，而不需要修改自动装配类本身。

## 维护约束

维护 AutoConfiguration 时，应坚持以下约束：

- 配置类只做装配，不做厚重业务逻辑。
- 默认 Bean 应清楚表达“缺省实现”语义，避免与模块外自定义实现互相覆盖。
- 启动日志可以保留，但不要把模块真实状态判断完全寄托在日志文案上。
- 新增、重命名或删除主线自动配置时，同步两份注册资源，并检查打包后资源是否完整。

## 深入阅读入口

- [extension-mechanisms.md](extension-mechanisms.md)
- [provider-patterns.md](provider-patterns.md)
- [../modules/ability/ouroboros-message-queue.md](../modules/ability/ouroboros-message-queue.md)
- [../modules/ability/ouroboros-rpc-model.md](../modules/ability/ouroboros-rpc-model.md)
- [../modules/core/ouroboros-field-type.md](../modules/core/ouroboros-field-type.md)
