package com.ouroboros.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.Priority;

/**
 * 基于 JDK 本地锁的 {@link DistributedLockManager} 默认实现。
 */
public class LocalDistributedLockManager implements DistributedLockManager {

  private final ConcurrentMap<String, LocalDistributedLock> locks = new ConcurrentHashMap<>();

  @Override
  public DistributedLock getLock(String key) {
    return locks.computeIfAbsent(key, k -> new LocalDistributedLock());
  }

  @Priority(0)
  public static class SingletonHolder implements DistributedLockManager {
    private static final LocalDistributedLockManager INSTANCE = new LocalDistributedLockManager();

    @Override
    public DistributedLock getLock(String key) {
      return INSTANCE.getLock(key);
    }
  }

  private static class LocalDistributedLock implements DistributedLock {

    private final ReentrantLock lock = new ReentrantLock();

    private LocalDistributedLock() {
    }

    @Override
    public void lock() {
      lock.lock();
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
      lock.lock();
    }

    @Override
    public boolean tryLock() {
      return lock.tryLock();
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
      return lock.tryLock(waitTime, unit);
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
