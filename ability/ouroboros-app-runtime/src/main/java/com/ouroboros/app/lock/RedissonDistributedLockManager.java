package com.ouroboros.app.lock;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Priority;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.lock.DistributedLock;
import com.ouroboros.lock.DistributedLockManager;
import com.ouroboros.lock.LocalDistributedLockManager;

/**
 * 基于 Redisson 的分布式锁管理实现。
 */
@Component
public class RedissonDistributedLockManager implements DistributedLockManager {
  private final ObjectProvider<RedissonClient> redissonClientProvider;

  public RedissonDistributedLockManager(ObjectProvider<RedissonClient> redissonClientProvider) {
    this.redissonClientProvider = redissonClientProvider;
    SingletonHolder.INSTANCE = this;
  }

  @Override
  public DistributedLock getLock(String key) {
    return Optional.ofNullable(redissonClientProvider.getIfAvailable())
        .<DistributedLock>map(client -> new RedissonDistributedLock(client.getLock(key)))
        .orElseGet(() -> new LocalDistributedLockManager.SingletonHolder().getLock(key));
  }

  @Priority(1)
  public static class SingletonHolder implements DistributedLockManager {
    static RedissonDistributedLockManager INSTANCE;

    @Override
    public DistributedLock getLock(String key) {
      return Optional.ofNullable(INSTANCE)
          .map(ins -> ins.getLock(key))
          .orElseGet(() -> new LocalDistributedLockManager.SingletonHolder().getLock(key));
    }
  }

  private static class RedissonDistributedLock implements DistributedLock {
    private final RLock lock;

    private RedissonDistributedLock(RLock lock) {
      this.lock = lock;
    }

    @Override
    public void lock() {
      lock.lock();
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
      lock.lock(leaseTime, unit);
    }

    @Override
    public boolean tryLock() {
      return lock.tryLock();
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
      return lock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public void unlock() {
      lock.unlock();
    }

    @Override
    public boolean isLocked() {
      return lock.isLocked();
    }
  }
}
