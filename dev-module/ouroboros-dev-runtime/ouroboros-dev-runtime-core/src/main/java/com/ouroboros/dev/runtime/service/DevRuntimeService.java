package com.ouroboros.dev.runtime.service;

import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

public interface DevRuntimeService {
  Try<RuntimeInstance> create(RuntimeSpec spec);

  Try<RuntimeInstance> start(RuntimeIdentityKey key);

  Try<RuntimeInstance> stop(RuntimeIdentityKey key);

  Try<RuntimeInstance> restart(RuntimeIdentityKey key);

  Try<Void> delete(RuntimeIdentityKey key);

  Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec);

  Try<RuntimeInstance> getStatus(RuntimeIdentityKey key);

  /** Inspect the backend directly; never substitute a last-known local status. */
  default Try<RuntimeInstance> inspect(RuntimeIdentityKey key) {
    return Try.failure(new UnsupportedOperationException("Fresh runtime inspection is unavailable"));
  }

  Try<List<String>> getLogs(RuntimeIdentityKey key);

  default Try<List<RuntimeInstance>> recover(String appName, RuntimeKind runtimeKind) {
    return Try.success(java.util.Collections.<RuntimeInstance>emptyList());
  }

  default Try<List<String>> listImages(RuntimeIdentityKey key, String reference) {
    return Try.failure(new UnsupportedOperationException("Runtime image catalog is unavailable"));
  }

  default void resetLogStream(RuntimeIdentityKey key) {
  }
}
