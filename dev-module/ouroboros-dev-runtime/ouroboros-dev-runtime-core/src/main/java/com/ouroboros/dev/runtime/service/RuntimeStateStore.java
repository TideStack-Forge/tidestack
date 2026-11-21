package com.ouroboros.dev.runtime.service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

public interface RuntimeStateStore {
  Optional<RuntimeSpec> findSpec(RuntimeIdentityKey key);

  Optional<RuntimeInstance> findInstance(RuntimeIdentityKey key);

  Optional<RuntimeOperationState> findActiveOperation(RuntimeIdentityKey key);

  void saveSpec(RuntimeIdentityKey key, RuntimeSpec spec);

  void saveInstance(RuntimeIdentityKey key, RuntimeInstance instance);

  void saveActiveOperation(RuntimeIdentityKey key, RuntimeOperationState operationState);

  void clearActiveOperation(RuntimeIdentityKey key);

  void delete(RuntimeIdentityKey key);

  Map<RuntimeIdentityKey, RuntimeSpec> allSpecs();

  Map<RuntimeIdentityKey, RuntimeInstance> allInstances();

  default Optional<RuntimeIdentityKey> findKeyByRuntimeId(String runtimeId) {
    if (runtimeId == null || runtimeId.trim().isEmpty()) {
      return Optional.empty();
    }
    return allInstances().entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> runtimeId.equals(entry.getValue().getRuntimeId()))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  default Map<RuntimeIdentityKey, RuntimeSpec> allSpecs(RuntimeKind runtimeKind) {
    if (runtimeKind == null) {
      return Collections.emptyMap();
    }
    return allSpecs().entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> runtimeKind == entry.getValue().getRuntimeKind())
        .collect(java.util.stream.Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (left, right) -> right,
            java.util.LinkedHashMap::new
        ));
  }
}
