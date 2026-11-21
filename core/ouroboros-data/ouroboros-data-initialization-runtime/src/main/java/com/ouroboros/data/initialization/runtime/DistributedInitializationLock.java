package com.ouroboros.data.initialization.runtime;

import java.util.Objects;
import java.util.function.Supplier;

import com.ouroboros.data.initialization.InitializationLock;
import com.ouroboros.lock.DistributedLockManager;

public class DistributedInitializationLock implements InitializationLock {
  private final DistributedLockManager lockManager;

  public DistributedInitializationLock(DistributedLockManager lockManager) {
    this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
  }

  @Override
  public <T> T execute(String key, Supplier<T> action) {
    var lock = lockManager.getLock(key);
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }
}
