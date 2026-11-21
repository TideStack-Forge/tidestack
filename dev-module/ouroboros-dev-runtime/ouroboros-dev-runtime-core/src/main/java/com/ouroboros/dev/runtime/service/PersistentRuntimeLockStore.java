package com.ouroboros.dev.runtime.service;

import java.util.Optional;

public class PersistentRuntimeLockStore implements RuntimeLockStore {
  private final PersistenceGateway persistenceGateway;

  public PersistentRuntimeLockStore(PersistenceGateway persistenceGateway) {
    this.persistenceGateway = persistenceGateway;
  }

  @Override
  public Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot) {
    return persistenceGateway.findLatestSuccessful(slot);
  }

  @Override
  public void saveLatestSuccessful(RuntimeLockRecord record) {
    persistenceGateway.saveLatestSuccessful(record);
  }

  public interface PersistenceGateway {
    Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot);

    void saveLatestSuccessful(RuntimeLockRecord record);
  }
}
