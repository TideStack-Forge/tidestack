# Common Lock - 分布式锁工具指南

本文档描述 `common-lock` 模块的使用方法。该模块提供统一的分布式锁抽象、默认本地实现，以及基于 SPI 的可扩展机制。

## 模块概述

`common-lock` 提供以下能力：

- 统一锁管理入口（`DistributedLockManager`）
- 统一锁操作抽象（`DistributedLock`）
- 基于 key 的锁实例获取
- 默认本地锁实现（`ReentrantLock`）
- SPI 扩展（可替换为 Redis/Redisson 等实现）

## 核心类和接口

### DistributedLockManager 接口

路径：`com.ouroboros.lock.DistributedLockManager`

锁管理核心接口，用于按 key 获取锁实例。

主要方法：

- `DistributedLock getLock(String key)`：根据 key 获取锁实例
- `static DistributedLockManager getLockManager()`：通过 SPI 获取优先级最高实现；无 SPI 时回退本地实现

### DistributedLock 接口

路径：`com.ouroboros.lock.DistributedLock`

锁实例抽象，封装常见加锁/解锁操作。

主要方法：

- `void lock()`：阻塞获取锁
- `void lock(long leaseTime, TimeUnit unit)`：阻塞获取锁，允许实现支持租约语义
- `boolean tryLock()`：立即尝试获取锁
- `boolean tryLock(long waitTime, long leaseTime, TimeUnit unit)`：按等待时间尝试获取锁
- `void unlock()`：释放锁
- `boolean isLocked()`：判断是否被任意线程持有

### LocalDistributedLockManager 类

路径：`com.ouroboros.lock.LocalDistributedLockManager`

默认本地实现，作为 SPI 回退方案：

- 使用 `ConcurrentHashMap<String, LocalDistributedLock>` 管理 key 到锁实例的映射
- 内部锁为 `ReentrantLock`，线程级可重入
- `SingletonHolder` 使用 `@Priority(0)` 作为默认 SPI 实现

## 使用示例

### 基本用法

```java
import com.ouroboros.lock.DistributedLock;
import com.ouroboros.lock.DistributedLockManager;

public void updateOrder(String orderId) {
  DistributedLockManager lockManager = DistributedLockManager.getLockManager();
  DistributedLock lock = lockManager.getLock("order:update:" + orderId);

  lock.lock();
  try {
    // 临界区逻辑
    doUpdate(orderId);
  } finally {
    lock.unlock();
  }
}
```

### tryLock 用法（带等待时间）

```java
import java.util.concurrent.TimeUnit;
import com.ouroboros.lock.DistributedLock;
import com.ouroboros.lock.DistributedLockManager;

public boolean submit(String key) throws InterruptedException {
  DistributedLock lock = DistributedLockManager.getLockManager().getLock("submit:" + key);
  if (!lock.tryLock(2, 30, TimeUnit.SECONDS)) {
    return false;
  }
  try {
    // 业务处理
    return true;
  } finally {
    lock.unlock();
  }
}
```

### 锁 key 设计建议

```java
String key1 = "user:profile:update:" + userId;
String key2 = "invoice:issue:" + companyId + ":" + invoiceNo;
String key3 = "workflow:node:execute:" + flowId + ":" + nodeId;
```

建议统一使用 `module:resource:action[:id...]` 模式，避免 key 冲突。

## 自定义实现（SPI）

可以通过 SPI 提供自定义锁管理实现，替换默认本地实现。

1. 实现 `DistributedLockManager`：

```java
import jakarta.annotation.Priority;
import com.ouroboros.lock.DistributedLock;
import com.ouroboros.lock.DistributedLockManager;

@Priority(100)
public class CustomDistributedLockManager implements DistributedLockManager {
  @Override
  public DistributedLock getLock(String key) {
    // 返回你的锁实现
    return null;
  }
}
```

2. 在 `META-INF/services/com.ouroboros.lock.DistributedLockManager` 中注册实现类：

```text
com.yourpackage.CustomDistributedLockManager
```

优先级说明：

- 多个实现并存时，框架按 `@Priority` 选择优先级最高者
- 默认本地实现为 `@Priority(0)`，自定义实现建议设置更高优先级

## 注意事项

### 1. 一定使用 try-finally 释放锁

不论 `lock()` 还是 `tryLock()`，成功后都应在 `finally` 中调用 `unlock()`，避免死锁。

### 2. 可重入语义是线程级

本地默认实现基于 `ReentrantLock`，同一线程可重入；不同线程不具备重入语义。

### 3. `leaseTime` 在本地实现不自动过期

`LocalDistributedLockManager` 中 `lock(long leaseTime, TimeUnit unit)` 当前行为等同 `lock()`，不会按租约时间自动释放。

### 4. `tryLock(waitTime, leaseTime, unit)` 的本地行为

本地实现只使用 `waitTime` 作为等待时间，`leaseTime` 不生效。

### 5. key 粒度需要合理

- 过粗：会把大量不相关请求串行化
- 过细：可能无法保护共享资源

## 使用建议

1. 封装统一的加锁模板，减少遗漏 `unlock()` 的风险
2. 全项目统一 key 命名规范
3. 在高并发场景优先使用可观测、可监控的外部分布式锁实现（通过 SPI 接入）
4. 对锁冲突场景设计明确的重试、降级或快速失败策略

## 扩展阅读

- SPI 工具：`libs/util/ouroboros-util/GUIDE.md`
- 锁管理入口：`libs/common/common-lock/src/main/java/com/ouroboros/lock/DistributedLockManager.java`
- 默认本地实现：`libs/common/common-lock/src/main/java/com/ouroboros/lock/LocalDistributedLockManager.java`
