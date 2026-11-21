package com.ouroboros.dev.runtime.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

public interface RuntimeBackend {
  BackendType getBackendType();

  RuntimeKind getRuntimeKind();

  boolean supports(RuntimeSpec spec);

  Try<RuntimeInstance> create(RuntimeSpec spec);

  Try<RuntimeInstance> start(RuntimeSpec spec);

  Try<RuntimeInstance> stop(RuntimeIdentityKey key);

  Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration);

  Try<Void> delete(RuntimeIdentityKey key);

  Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration);

  Try<RuntimeInstance> getStatus(RuntimeIdentityKey key);

  default Try<Optional<RuntimeSpec>> discover(String appName) {
    return Try.success(Optional.empty());
  }

  default Try<List<String>> getLogs(RuntimeIdentityKey key) {
    return Try.success(Collections.<String>emptyList());
  }

  /** Optional backend image catalog, filtered by the configured reference. */
  default Try<List<String>> listImages(String reference) {
    return Try.failure(new UnsupportedOperationException("Runtime backend has no local image catalog"));
  }

  default void resetLogStream(RuntimeIdentityKey key) {
  }
}
