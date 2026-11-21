# common-lock 模块设计摘要

## 模块定位

`common-lock` 是 `common-*` 中的锁抽象模块，负责为平台提供统一的分布式锁接口和默认本地锁实现。它的目标不是直接提供某种生产级分布式锁产品，而是把“按 key 获取锁并围绕它执行临界区”的编程模型抽象出来。

## 责任边界

该模块负责锁接口、锁管理入口、默认本地实现以及 SPI 替换点；它不负责事务协调、不负责具体 Redis/Redisson 部署、不负责竞争监控和告警，也不负责上层业务的重试/幂等策略。它解决的是锁抽象问题，而不是完整并发治理问题。

## 依赖关系

模块直接依赖 `ouroboros-util` 的 SPI 能力和 JDK 并发原语。默认情况下，`DistributedLockManager.getLockManager()` 会回退到 `LocalDistributedLockManager.SingletonHolder`；如果上层模块提供更高优先级实现，就可以无感替换为真正的分布式锁。

## 关键抽象

- `DistributedLockManager`：按 key 获取锁实例的统一入口。
- `DistributedLock`：锁实例接口，封装 `lock`、`tryLock`、`unlock` 和 `isLocked` 语义。
- `LocalDistributedLockManager`：默认本地实现，基于 `ConcurrentHashMap + ReentrantLock`。
- `SingletonHolder`：默认 SPI 回退实现。

## 关键子模块

- `DistributedLockManager`：平台统一依赖入口。
- `DistributedLock`：锁能力抽象。
- `LocalDistributedLockManager`：开发态和未接入外部锁时的默认实现。

## 扩展机制

该模块的核心扩展点是 `DistributedLockManager` SPI。维护者若要引入 Redis、Redisson 或其他锁实现，优先新增管理器实现并通过 `META-INF/services` 注册，而不是要求业务模块直接依赖某种锁客户端。SPI 治理可参考 [../../cross-cutting/spi-governance.md](../../cross-cutting/spi-governance.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/common-lock/GUIDE.md](../../../../libs/common/common-lock/GUIDE.md)
- `补充深度资料`：[SPI 治理](../../cross-cutting/spi-governance.md)
- `暂无独立深度设计文档`：当前仓库中尚无 `common-lock` 自身的 `DESIGN.md` 或专题设计页。

## 演进状态

`common-lock` 当前保持“接口稳定、默认实现保守”的状态。后续维护重点应放在替换实现的无感接入和锁语义一致性上，而不是在默认本地实现里不断叠加越来越多伪分布式能力。
