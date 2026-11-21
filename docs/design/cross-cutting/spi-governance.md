# Ouroboros SPI 治理

## 目标

本页用于统一说明 Ouroboros 中 Java SPI 的使用边界、注册方式、排序与缓存规则，以及它与 Spring Bean / AutoConfiguration 的协作关系。它关注的是共享扩展点如何长期可维护，而不是某个单独 provider 的实现细节。

## 什么时候适合用 SPI

在 Ouroboros 中，SPI 适合承接以下场景：

- 需要允许模块外追加多个实现，例如 `NodeBuilder`、`FieldTypeDefinitionProvider`、`RPCModelMetadataProvider`。
- 需要在运行时把多个定义来源汇总成统一结果。
- 扩展点需要被 `core` 或通用运行时以“接口 + 实现清单”的方式发现，而不直接依赖具体 Spring Bean 名称。

反过来说，如果只是启动期装配默认 Bean、注册控制器或挂配置属性，通常更适合放在 AutoConfiguration，而不是额外造一层 SPI。

## 注册方式

SPI 扩展点应至少具备三部分：

- 稳定的接口契约。
- `META-INF/services/<接口全名>` 服务声明文件。
- 在服务文件中列出的实现类。

当前仓库里的典型例子包括：

- `com.ouroboros.logicflow.nodes.NodeBuilder`
- `com.ouroboros.rpc.metadata.RPCModelMetadataProvider`
- `com.ouroboros.fieldtype.FieldTypeDefinitionProvider`

对于维护者来说，服务文件本身就是扩展点可发现性的正式入口之一，因此新增实现时不能只写代码而漏掉注册文件。

## Spring 与 SPI 的桥接模式

Spring/SPI 桥接继续使用正式 `META-INF/services` 入口。SPI 代理通过 `SpringContextUtil` 获取所属根容器中的 Bean，不在构造器中把裸 `this` 或服务写入新静态 holder。UI 路由、用户角色以及开发态 Inspector 已按此方式访问；动作/事件注解 Catalog 的扫描快照由各自 Bean 实例持有。

公共桥接采用单根合同：第一个绑定的容器保持所有者；其他容器关闭不能清除它。所有者关闭后静态绑定释放，不自动把另一存活容器提升为根；下一次启动可重新绑定。未绑定时需要 Bean 的访问明确抛出 `IllegalStateException`，关闭后不会继续返回旧 Bean。此机制不提供同一 JVM 多租户的上下文路由。历史 provider 中仍有原有静态代理模式，后续涉及这些 provider 时须迁入相同生命周期机制，不能将本轮迁移范围外的实现描述成已经完成改造。

## `@LowCodeProvider` 的语义

仓库中已有 `@LowCodeProvider` 标记，主要用于识别低代码元数据来源的 SPI 实现。它不改变 SPI 的注册机制，但会帮助维护者区分“来自开发工作区 / 文件系统 / 低代码资源的提供者”和其他普通实现。

因此，是否标记 `@LowCodeProvider`，表达的是来源语义，而不是是否需要注册到 `META-INF/services`。

## 排序与优先级

Ouroboros 的 `SPI` 工具类以 `@Priority` 作为统一排序依据。维护时需要明确：

- `getSortedServiceStream(...)` 代表按优先级从低到高读取。
- `getReversedServiceStream(...)` 代表按优先级从高到低读取。
- `getCachedSortedServiceStream(...)`、`getCachedReversedServiceStream(...)` 在此基础上增加结果缓存。

很多 provider / center 组合会借助 `@Priority` 决定覆盖顺序。例如低代码来源常通过更高或更低的优先级覆盖 JAR 内默认定义。若优先级策略没有写清，新增实现后就很容易出现“谁覆盖谁”不可预测的问题。

## 缓存与刷新

发现与缓存均遵循线程上下文 ClassLoader。缓存键包含契约 `Class` 身份及发现加载器，同名但不同加载器的契约不会串用提供者。加载器所有者在关闭前调用 `SPI.clearCache(loader)`；`SpringContextUtil` 关闭时回收该根容器加载器的缓存，同加载器的非根容器关闭不会清空仍存活根的缓存。`clearCache()` 用于全进程重发现，测试不得反射修改缓存内部结构。

Map 只负责登记缓存条目；每个条目用自己的可重入锁完成首次发现，保证同一清单只构造一次，不同条目可以并行初始化。缓存命中直接读取。Provider 构造期间可以继续发现其他 SPI；同一调用链再次进入正在初始化的条目时，会抛出包含接口名称的 `ServiceConfigurationError`。Provider 构造不放在 Map 的原子计算回调中，避免不同缓存键碰撞时误报递归更新。

清理移除缓存条目，正在构造的结果只写入原条目，不重新登记到 Map，因此不会重新写回已移除的条目。清理不撤销调用方已经取得的条目或 Provider 引用；清理后开始的查询重新发现实现。

SPI 本身只负责发现实现，不负责刷新外部状态。当前仓库的常见做法是：

- SPI 实现内部自行缓存读取结果，或由上层 center / integrator 做结果缓存。
- 当定义来源来自文件系统或低代码资源时，通过 `OuroborosMetadataRefreshingEvent`、`OuroborosMetadataRefreshedEvent` 等生命周期事件重建缓存。
- `SPI.getCached*` 主要缓存的是“实现清单”，不是外部资源内容本身。

这意味着：如果扩展点背后依赖可变资源，单靠 `getCached*` 不能解决刷新问题，仍然需要明确的事件驱动重载策略。

## 与 Provider / Center / Builder 的关系

SPI 在 Ouroboros 里通常不是单独出现，而是嵌在更完整的聚合链路里：

- Provider 用 SPI 暴露多个来源。
- Center 或 Integrator 负责排序、去重和最终视图。
- Builder 负责把元数据构造成运行时对象。

因此，SPI 只是发现机制，不应被误当成完整治理方案。真正的冲突裁决、结果缓存和运行时语义，仍应放在 provider-patterns 所描述的更高一层。

## 不建议的用法

以下情况通常说明 SPI 用过头了：

- 只有单一实现，却仍然引入完整 SPI 链路。
- 把需要 Spring 容器顺序保证的启动接线全塞进 SPI。
- 让 SPI 实现承担过重的业务流程，而不是只负责来源暴露或对象构建。
- 新增实现时只考虑“能注册上”，不明确优先级、去重和刷新语义。

## 维护约束

维护 SPI 扩展点时，应至少检查：

- 服务文件是否存在且路径正确。
- 新实现是否需要 `@Priority`。
- 如果实现依赖 Spring 状态，是否采用了稳定的桥接方式。
- 若来源可变，是否有刷新时机与缓存策略。
- 模块摘要或跨模块专题是否需要更新扩展约定。

## 深入阅读入口

- [extension-mechanisms.md](extension-mechanisms.md)
- [provider-patterns.md](provider-patterns.md)
- [spring-autoconfiguration.md](spring-autoconfiguration.md)
- [../../../libs/util/ouroboros-util/src/main/java/com/ouroboros/util/SPI.java](../../../libs/util/ouroboros-util/src/main/java/com/ouroboros/util/SPI.java)
- [../../../libs/common/common-app/GUIDE.md](../../../libs/common/common-app/GUIDE.md)
