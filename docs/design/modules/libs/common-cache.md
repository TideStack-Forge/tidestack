# common-cache 模块设计摘要

## 模块定位

`common-cache` 是 `common-*` 中的统一缓存抽象模块，负责为平台提供命名空间化的缓存接口、默认内存实现以及对 Spring Cache 的适配基类。它的核心作用不是提供某一种缓存产品，而是把平台各模块对缓存的使用方式收敛到统一接口上。

## 责任边界

该模块负责缓存读写、过期时间控制、命名空间隔离、惰性加载和默认实现选择；它不负责具体业务缓存策略、不负责分布式一致性、不负责 Redis/Caffeine 等具体产品配置，也不负责缓存指标与运维治理。它做的是缓存抽象层，而不是缓存中台。

## 依赖关系

模块直接依赖 `ouroboros-util` 的 SPI 机制和 Spring Cache 抽象。它向上被会话、配置、认证和开发平台等场景复用，向下则通过 `AbstractSpringCacheManagerAdapter` 连接具体缓存实现。默认情况下，`CacheManager.getCacheManager()` 会回退到 `MemoryCacheManager.SingletonHolder`。

## 关键抽象

- `CacheManager`：统一缓存接口，定义命名空间、过期、清理和 `getOrPut` 语义。
- `AbstractSpringCacheManagerAdapter`：Spring Cache 适配基类，封装常见读写和基于调度线程的过期处理。
- `MemoryCacheManager`：默认内存实现，也是当前 SPI 默认提供者。
- `getAndEvict(...)`：一次性消费式读取接口，适合 token/state 这类单次使用场景。

## 关键子模块

- `CacheManager`：平台唯一推荐直接依赖的缓存契约。
- `AbstractSpringCacheManagerAdapter`：面向 Redis、Caffeine 等具体实现的桥接入口。
- `MemoryCacheManager`：默认回退实现。

## 扩展机制

该模块最关键的扩展点是 `CacheManager` SPI。若平台要引入分布式缓存或替换默认实现，优先通过 `META-INF/services/com.ouroboros.cache.CacheManager` 注册新的高优先级实现，而不是让业务模块直接绕过抽象调用具体缓存客户端。共享治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-cache/GUIDE.md](../../../../libs/common/common-cache/GUIDE.md)
- `补充深度资料`：[SPI 治理](../../cross-cutting/spi-governance.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-cache` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-cache` 当前处在“接口稳定、实现可替换”的阶段。后续维护应继续把它保持为统一抽象层，避免把具体缓存产品特性、复杂业务缓存语义或运维策略直接压进公共接口。
