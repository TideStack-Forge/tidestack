package com.ouroboros.lock;

import java.util.Optional;

import com.ouroboros.util.SPI;

/**
 * 分布式锁管理接口，根据 key 获取锁实例。
 */
public interface DistributedLockManager {

    /**
     * 根据 key 获取分布式锁实例
     *
     * @param key 锁的唯一标识
     * @return 锁实例
     */
    DistributedLock getLock(String key);

    /**
     * 通过 SPI 获取优先级最高的 DistributedLockManager 实现。
     * 无 SPI 实现时回退到本地锁。
     */
    static DistributedLockManager getLockManager() {
        return Optional.ofNullable(SPI.getCachedPrimaryService(DistributedLockManager.class))
            .orElseGet(LocalDistributedLockManager.SingletonHolder::new);
    }
}
