package com.ouroboros.dev.runtime.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

public class InMemoryRuntimeStateStore implements RuntimeStateStore {
  private final Map<RuntimeIdentityKey, RuntimeSpec> specs = new ConcurrentHashMap<RuntimeIdentityKey, RuntimeSpec>();
  private final Map<RuntimeIdentityKey, RuntimeInstance> instances = new ConcurrentHashMap<RuntimeIdentityKey, RuntimeInstance>();
  private final Map<RuntimeIdentityKey, RuntimeOperationState> activeOperations = new ConcurrentHashMap<RuntimeIdentityKey, RuntimeOperationState>();
  private RuntimeIdentityKey lastTouchedKey;

  @Override
  public Optional<RuntimeSpec> findSpec(RuntimeIdentityKey key) {
    return Optional.ofNullable(specs.get(key));
  }

  @Override
  public Optional<RuntimeInstance> findInstance(RuntimeIdentityKey key) {
    return Optional.ofNullable(instances.get(key));
  }

  @Override
  public Optional<RuntimeOperationState> findActiveOperation(RuntimeIdentityKey key) {
    return Optional.ofNullable(activeOperations.get(key));
  }

  @Override
  public void saveSpec(RuntimeIdentityKey key, RuntimeSpec spec) {
    specs.put(key, spec);
    lastTouchedKey = key;
  }

  @Override
  public void saveInstance(RuntimeIdentityKey key, RuntimeInstance instance) {
    instances.put(key, instance);
    lastTouchedKey = key;
  }

  @Override
  public void saveActiveOperation(RuntimeIdentityKey key, RuntimeOperationState operationState) {
    activeOperations.put(key, operationState);
    lastTouchedKey = key;
  }

  @Override
  public void clearActiveOperation(RuntimeIdentityKey key) {
    activeOperations.remove(key);
  }

  @Override
  public void delete(RuntimeIdentityKey key) {
    specs.remove(key);
    instances.remove(key);
    activeOperations.remove(key);
    if (key.equals(lastTouchedKey)) {
      lastTouchedKey = null;
    }
  }

  @Override
  public Map<RuntimeIdentityKey, RuntimeSpec> allSpecs() {
    return Collections.unmodifiableMap(new LinkedHashMap<RuntimeIdentityKey, RuntimeSpec>(specs));
  }

  @Override
  public Map<RuntimeIdentityKey, RuntimeInstance> allInstances() {
    return Collections.unmodifiableMap(new LinkedHashMap<RuntimeIdentityKey, RuntimeInstance>(instances));
  }

  public InMemoryRuntimeStateStore withActiveOperation(RuntimeOperationState operationState) {
    if (lastTouchedKey == null) {
      throw new IllegalStateException("No runtime key available for active operation");
    }
    saveActiveOperation(lastTouchedKey, operationState);
    return this;
  }
}
