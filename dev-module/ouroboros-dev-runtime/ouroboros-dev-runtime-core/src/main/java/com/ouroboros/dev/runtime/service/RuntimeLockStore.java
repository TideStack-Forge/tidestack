package com.ouroboros.dev.runtime.service;

import java.util.Optional;

public interface RuntimeLockStore {
  Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot);

  void saveLatestSuccessful(RuntimeLockRecord record);
}
