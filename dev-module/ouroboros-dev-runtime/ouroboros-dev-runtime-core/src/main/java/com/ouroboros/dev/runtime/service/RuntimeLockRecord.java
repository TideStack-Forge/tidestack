package com.ouroboros.dev.runtime.service;

import java.time.Instant;

import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;

public class RuntimeLockRecord {
  private final RuntimeLockKey slot;
  private final ResolvedRuntimeLock lock;
  private final Instant savedAt;

  private RuntimeLockRecord(RuntimeLockKey slot, ResolvedRuntimeLock lock, Instant savedAt) {
    this.slot = slot;
    this.lock = lock;
    this.savedAt = savedAt;
  }

  public static RuntimeLockRecord of(RuntimeLockKey slot, ResolvedRuntimeLock lock) {
    Instant savedAt = lock == null ? null : lock.getResolvedAt();
    return new RuntimeLockRecord(slot, lock, savedAt);
  }

  public static RuntimeLockRecord of(RuntimeLockKey slot, ResolvedRuntimeLock lock, Instant savedAt) {
    if (savedAt == null && lock != null) {
      return new RuntimeLockRecord(slot, lock, lock.getResolvedAt());
    }
    return new RuntimeLockRecord(slot, lock, savedAt);
  }

  public RuntimeLockKey getSlot() {
    return slot;
  }

  public ResolvedRuntimeLock getLock() {
    return lock;
  }

  public Instant getSavedAt() {
    return savedAt;
  }
}
