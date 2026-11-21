package com.ouroboros.dev.runtime.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRuntimeLockStore implements RuntimeLockStore {
  private final Map<RuntimeLockKey, RuntimeLockRecord> latestSuccessfulBySlot = new ConcurrentHashMap<RuntimeLockKey, RuntimeLockRecord>();

  @Override
  public Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot) {
    return Optional.ofNullable(latestSuccessfulBySlot.get(slot));
  }

  @Override
  public void saveLatestSuccessful(RuntimeLockRecord record) {
    latestSuccessfulBySlot.put(record.getSlot(), record);
  }
}
