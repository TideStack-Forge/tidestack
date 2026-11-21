package com.ouroboros.dev.runtime.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

public class DefaultDevRuntimeService implements DevRuntimeService {
  private final List<WorkspaceProvisioner> workspaceProvisioners;
  private final List<RuntimeBackend> backends;
  private final RuntimeStateStore stateStore;
  private final RuntimeBackendResolver backendResolver;
  private final DevRuntimeEventPublisher eventPublisher;
  private final Map<RuntimeIdentityKey, LifecycleLock> lifecycleLocks =
      new ConcurrentHashMap<RuntimeIdentityKey, LifecycleLock>();

  public DefaultDevRuntimeService(List<RuntimeBackend> backends, List<WorkspaceProvisioner> workspaceProvisioners, RuntimeStateStore stateStore) {
    this(backends, workspaceProvisioners, stateStore, new DevRuntimeProperties(), DevRuntimeEventPublisher.noop());
  }

  public DefaultDevRuntimeService(List<RuntimeBackend> backends, List<WorkspaceProvisioner> workspaceProvisioners, RuntimeStateStore stateStore, DevRuntimeProperties properties) {
    this(backends, workspaceProvisioners, stateStore, properties, DevRuntimeEventPublisher.noop());
  }

  public DefaultDevRuntimeService(List<RuntimeBackend> backends, List<WorkspaceProvisioner> workspaceProvisioners, RuntimeStateStore stateStore, DevRuntimeEventPublisher eventPublisher) {
    this(backends, workspaceProvisioners, stateStore, new DevRuntimeProperties(), eventPublisher);
  }

  public DefaultDevRuntimeService(List<RuntimeBackend> backends, List<WorkspaceProvisioner> workspaceProvisioners, RuntimeStateStore stateStore, DevRuntimeProperties properties, DevRuntimeEventPublisher eventPublisher) {
    this.backends = Collections.unmodifiableList(new ArrayList<RuntimeBackend>(backends));
    this.workspaceProvisioners = workspaceProvisioners;
    this.stateStore = stateStore;
    this.backendResolver = new RuntimeBackendResolver(backends, properties);
    this.eventPublisher = eventPublisher == null ? DevRuntimeEventPublisher.noop() : eventPublisher;
  }

  @Override
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(normalizedSpec);
    return withLifecycleLock(key, () -> {
      Optional<RuntimeSpec> existingSpec = stateStore.findSpec(key);
      if (existingSpec.isPresent() && !isEquivalent(existingSpec.get(), normalizedSpec)) {
        return Try.failure(new IllegalStateException("runtime spec conflict"));
      }
      Optional<RuntimeInstance> existingInstance = stateStore.findInstance(key);
      if (existingSpec.isPresent() && existingInstance.isPresent()) {
        return Try.success(existingInstance.get());
      }
      return prepare(normalizedSpec, WorkspaceProvisioner.Operation.CREATE)
          .flatMap(this::createWithBackend)
          .map(instance -> persistCreatedRuntime(key, normalizedSpec, instance));
    });
  }

  @Override
  public Try<RuntimeInstance> start(RuntimeIdentityKey key) {
    return withLifecycleLock(key, () -> {
      Optional<RuntimeInstance> current = stateStore.findInstance(key);
      if (current.isPresent() && isStableStartState(current.get().getStatus())) {
        return Try.success(current.get());
      }
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      if (!storedSpec.isPresent()) {
        return Try.failure(new IllegalStateException("runtime not found"));
      }
      RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
      return prepare(normalizedSpec, WorkspaceProvisioner.Operation.START)
          .flatMap(resolvedSpec -> {
            RuntimeBackend backend = backendResolver.resolve(resolvedSpec);
            return backend.start(resolvedSpec)
                .map(instance -> persistRuntimeWithStatusEvent(
                    RuntimeIdentityKey.from(resolvedSpec),
                    resolvedSpec,
                    current.orElse(null),
                    instance
                ))
                .recoverWith(error -> isNotFound(error)
                    ? backend.create(resolvedSpec)
                        .map(instance -> persistCreatedRuntime(RuntimeIdentityKey.from(resolvedSpec), resolvedSpec, instance))
                    : Try.failure(error));
          });
    });
  }

  @Override
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
    return withLifecycleLock(key, () -> {
      Optional<RuntimeInstance> current = stateStore.findInstance(key);
      if (!current.isPresent()) {
        return Try.success(stoppedInstance(key));
      }
      if (current.get().getStatus() == RuntimeStatus.STOPPED) {
        return Try.success(current.get());
      }
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      if (!storedSpec.isPresent()) {
        return Try.success(current.get());
      }
      RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
      RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
      return backend.stop(key).map(instance -> persistRuntimeWithStatusEvent(
          key,
          normalizedSpec,
          current.get(),
          instance
      ));
    });
  }

  @Override
  public Try<RuntimeInstance> restart(RuntimeIdentityKey key) {
    return withLifecycleLock(key, () -> {
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      if (!storedSpec.isPresent()) {
        return Try.failure(new IllegalStateException("runtime not found"));
      }
      RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
      long runtimeGeneration = generationFor(key, true);
      stateStore.saveActiveOperation(key, RuntimeOperationState.restart(runtimeGeneration));
      RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
      return backend.restart(normalizedSpec, runtimeGeneration)
          .map(instance -> persistRuntimeAndClearOperationWithStatusEvent(key, normalizedSpec, instance));
    });
  }

  @Override
  public Try<Void> delete(RuntimeIdentityKey key) {
    return withLifecycleLock(key, () -> {
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      Optional<RuntimeInstance> current = stateStore.findInstance(key);
      if (!storedSpec.isPresent() && !current.isPresent()) {
        return Try.success(null);
      }
      if (!storedSpec.isPresent()) {
        if (current.isPresent()) {
          stateStore.delete(key);
          eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_DELETED, null, current.get()));
        }
        return Try.success(null);
      }
      RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
      RuntimeInstance deletedRuntime = deletedRuntime(key, normalizedSpec, current.orElse(null));
      RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
      return backend.delete(key).map(value -> {
        stateStore.delete(key);
        eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_DELETED, normalizedSpec, deletedRuntime));
        return value;
      });
    });
  }

  @Override
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec) {
    RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(normalizedSpec);
    return withLifecycleLock(key, () -> prepare(normalizedSpec, WorkspaceProvisioner.Operation.RELOAD)
        .flatMap(resolvedSpec -> {
          Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
          Optional<RuntimeInstance> current = stateStore.findInstance(key);
          if (!storedSpec.isPresent() && !current.isPresent()) {
            return Try.failure(new IllegalStateException("runtime not found"));
          }
          RuntimeSpec specToUse = storedSpec.isPresent() ? mergeWorkspace(storedSpec.get(), resolvedSpec.getWorkspaceRef()) : resolvedSpec;
          RuntimeSpec effectiveSpec = backendResolver.normalizeBackend(specToUse);
          long runtimeGeneration = generationFor(key, false);
          stateStore.saveActiveOperation(key, RuntimeOperationState.reloadWorkspace(runtimeGeneration));
          RuntimeBackend backend = backendResolver.resolve(effectiveSpec);
          return backend.reloadWorkspace(effectiveSpec, runtimeGeneration)
              .map(instance -> persistRuntimeAndClearOperationWithStatusEvent(key, effectiveSpec, instance));
        }));
  }

  @Override
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return withLifecycleLock(key, () -> {
      Optional<RuntimeInstance> current = stateStore.findInstance(key);
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      if (!storedSpec.isPresent()) {
        return current.map(Try::success)
            .orElseGet(() -> Try.failure(new IllegalStateException("runtime not found")));
      }
      RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
      RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
      return backend.getStatus(key)
          .map(instance -> persistRuntimeFromStatusPoll(key, normalizedSpec, current.orElse(null), instance))
          .recoverWith(error -> current.map(Try::success).orElseGet(() -> Try.failure(error)));
    });
  }

  @Override
  public Try<RuntimeInstance> inspect(RuntimeIdentityKey key) {
    return backendFor(key).flatMap(backend -> backend.getStatus(key));
  }

  @Override
  public Try<List<String>> listImages(RuntimeIdentityKey key, String reference) {
    return backendFor(key).flatMap(backend -> backend.listImages(reference));
  }

  private Try<RuntimeBackend> backendFor(RuntimeIdentityKey key) {
    return Try.of(() -> backends.stream().filter(backend -> backend.getRuntimeKind() == key.getRuntimeKind()
        && backend.getBackendType() == key.getBackendType()).findFirst()
        .orElseThrow(() -> new IllegalStateException("Runtime backend is unavailable")));
  }

  @Override
  public Try<List<String>> getLogs(RuntimeIdentityKey key) {
    Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
    if (!storedSpec.isPresent()) {
      return Try.success(Collections.<String>emptyList());
    }
    RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
    RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
    return backend.getLogs(key);
  }

  @Override
  public Try<List<RuntimeInstance>> recover(String appName, RuntimeKind runtimeKind) {
    if (appName == null || appName.trim().isEmpty() || runtimeKind == null) {
      return Try.failure(new IllegalArgumentException("appName and runtimeKind are required for runtime recovery"));
    }
    String normalizedAppName = appName.trim();
    Try<List<RuntimeInstance>> recovered = Try.success(new ArrayList<RuntimeInstance>());
    for (RuntimeBackend backend : backends) {
      if (backend.getRuntimeKind() != runtimeKind) {
        continue;
      }
      recovered = recovered.flatMap(instances -> backend.discover(normalizedAppName)
          .flatMap(discovered -> discovered
              .map(spec -> recoverDiscoveredRuntime(backend, normalizedAppName, runtimeKind, spec)
                  .map(instance -> {
                    instances.add(instance);
                    return instances;
                  }))
              .orElseGet(() -> Try.success(instances))));
    }
    return recovered.map(instances -> Collections.unmodifiableList(new ArrayList<RuntimeInstance>(instances)));
  }

  @Override
  public void resetLogStream(RuntimeIdentityKey key) {
    Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
    if (!storedSpec.isPresent()) {
      return;
    }
    RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(storedSpec.get());
    RuntimeBackend backend = backendResolver.resolve(normalizedSpec);
    backend.resetLogStream(key);
  }

  private Try<RuntimeSpec> prepare(RuntimeSpec spec, WorkspaceProvisioner.Operation operation) {
    if (spec.getWorkspaceMode() == com.ouroboros.dev.runtime.model.WorkspaceMode.NONE) return Try.success(spec);
    return Try.of(() -> workspaceProvisioners.stream()
        .filter(provisioner -> provisioner.supports(spec))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No workspace provisioner supports " + spec.getWorkspaceMode())))
        .flatMap(provisioner -> provisioner.prepare(spec, operation)
            .map(spec::withWorkspaceRef));
  }

  private Try<RuntimeInstance> recoverDiscoveredRuntime(
      RuntimeBackend backend,
      String appName,
      RuntimeKind runtimeKind,
      RuntimeSpec discoveredSpec
  ) {
    if (discoveredSpec == null
        || !Objects.equals(appName, discoveredSpec.getAppName())
        || runtimeKind != discoveredSpec.getRuntimeKind()
        || !backend.supports(discoveredSpec)) {
      return Try.failure(new IllegalStateException("runtime backend returned an invalid recovery candidate"));
    }
    RuntimeSpec normalizedSpec = backendResolver.normalizeBackend(discoveredSpec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(normalizedSpec);
    return withLifecycleLock(key, () -> {
      Optional<RuntimeSpec> storedSpec = stateStore.findSpec(key);
      Optional<RuntimeInstance> storedInstance = stateStore.findInstance(key);
      if (storedSpec.isPresent() && storedInstance.isPresent()) {
        return Try.success(storedInstance.get());
      }
      RuntimeSpec effectiveSpec = storedSpec.orElse(normalizedSpec);
      return backend.getStatus(key)
          .map(instance -> persistRuntimeFromStatusPoll(
              key,
              effectiveSpec,
              storedInstance.orElse(null),
              instance
          ));
    });
  }

  private Try<RuntimeInstance> createWithBackend(RuntimeSpec spec) {
    return Try.of(() -> backendResolver.resolve(spec))
        .flatMap(backend -> backend.create(spec));
  }

  private RuntimeInstance persistRuntime(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
    stateStore.saveSpec(key, spec);
    stateStore.saveInstance(key, instance);
    return instance;
  }

  private RuntimeInstance persistRuntimeFromStatusPoll(RuntimeIdentityKey key,
                                                       RuntimeSpec spec,
                                                       RuntimeInstance previous,
                                                       RuntimeInstance current) {
    RuntimeInstance observed = establishObservedRuntimeGeneration(
        key,
        preserveKnownRuntimeGeneration(previous, current)
    );
    RuntimeInstance persisted = persistRuntime(key, spec, observed);
    if (statusChanged(previous, observed)) {
      eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, spec, persisted));
    }
    return persisted;
  }

  private RuntimeInstance establishObservedRuntimeGeneration(
      RuntimeIdentityKey key,
      RuntimeInstance observed
  ) {
    if (observed == null
        || observed.getRuntimeId() == null
        || observed.getRuntimeGeneration() != null
        || observed.getRuntimeKind() != RuntimeKind.DEV_HELPER) {
      return observed;
    }
    long generation = 1L;
    Optional<RuntimeInstance> stored = stateStore.findInstance(key);
    if (stored.isPresent() && stored.get().getRuntimeGeneration() != null) {
      generation = Math.max(generation, stored.get().getRuntimeGeneration().longValue());
    }
    Optional<RuntimeOperationState> activeOperation = stateStore.findActiveOperation(key);
    if (activeOperation.isPresent() && activeOperation.get().getRuntimeGeneration() != null) {
      generation = Math.max(generation, activeOperation.get().getRuntimeGeneration().longValue());
    }
    return copyRuntimeIdentity(observed, observed.getRuntimeId(), Long.valueOf(generation));
  }

  private RuntimeInstance preserveKnownRuntimeGeneration(
      RuntimeInstance previous,
      RuntimeInstance current
  ) {
    if (previous == null || current == null || previous.getRuntimeGeneration() == null) {
      return current;
    }
    if (current.getRuntimeId() == null && previous.getRuntimeId() != null) {
      Long generation = current.getRuntimeGeneration() != null
              && current.getRuntimeGeneration().longValue() >= previous.getRuntimeGeneration().longValue()
          ? current.getRuntimeGeneration()
          : previous.getRuntimeGeneration();
      return copyRuntimeIdentity(current, previous.getRuntimeId(), generation);
    }
    if (current.getRuntimeId() != null
        && !Objects.equals(previous.getRuntimeId(), current.getRuntimeId())) {
      long nextGeneration = previous.getRuntimeGeneration().longValue() + 1L;
      if (current.getRuntimeGeneration() != null) {
        nextGeneration = Math.max(nextGeneration, current.getRuntimeGeneration().longValue());
      }
      return copyRuntimeIdentity(
          current,
          current.getRuntimeId(),
          Long.valueOf(nextGeneration)
      );
    }
    if (current.getRuntimeId() == null
        || current.getRuntimeGeneration() != null
            && current.getRuntimeGeneration().longValue() >= previous.getRuntimeGeneration().longValue()) {
      return current;
    }
    return copyRuntimeIdentity(current, current.getRuntimeId(), previous.getRuntimeGeneration());
  }

  private RuntimeInstance copyRuntimeIdentity(
      RuntimeInstance current,
      String runtimeId,
      Long runtimeGeneration
  ) {
    return RuntimeInstance.builder()
        .runtimeId(runtimeId)
        .appName(current.getAppName())
        .workloadId(current.getWorkloadId())
        .profile(current.getProfile())
        .runtimeKind(current.getRuntimeKind())
        .backendType(current.getBackendType())
        .runtimeGeneration(runtimeGeneration)
        .status(current.getStatus())
        .webEndpoint(current.getWebEndpoint())
        .apiEndpoint(current.getApiEndpoint())
        .debugEndpoint(current.getDebugEndpoint())
        .workspaceRef(current.getWorkspaceRef())
        .accessRef(current.getAccessRef())
        .capabilities(current.getCapabilities())
        .backendMetadata(current.getBackendMetadata())
        .build();
  }

  private RuntimeInstance persistCreatedRuntime(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
    RuntimeSpec effectiveSpec = spec.withWorkspaceRef(instance.getWorkspaceRef() == null ? spec.getWorkspaceRef() : instance.getWorkspaceRef());
    RuntimeInstance persisted = persistRuntime(key, effectiveSpec, instance);
    eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_CREATED, effectiveSpec, persisted));
    return persisted;
  }

  private RuntimeInstance persistRuntimeWithStatusEvent(
      RuntimeIdentityKey key,
      RuntimeSpec spec,
      RuntimeInstance previous,
      RuntimeInstance current
  ) {
    RuntimeInstance persisted = persistRuntime(key, spec, preserveKnownRuntimeGeneration(previous, current));
    eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, spec, persisted));
    return persisted;
  }

  private DevRuntimeEvent runtimeEvent(DevRuntimeEventType type, RuntimeSpec spec, RuntimeInstance runtime) {
    return DevRuntimeEvent.builder()
        .type(type)
        .runtimeId(runtime.getRuntimeId())
        .appName(runtime.getAppName())
        .workloadId(runtime.getWorkloadId())
        .profile(runtime.getProfile())
        .runtimeKind(runtime.getRuntimeKind())
        .backendType(runtime.getBackendType())
        .exposureStrategy(spec == null ? null : spec.getExposureStrategy())
        .runtimeGeneration(runtime.getRuntimeGeneration())
        .status(runtime.getStatus())
        .webEndpoint(runtime.getWebEndpoint())
        .apiEndpoint(runtime.getApiEndpoint())
        .debugEndpoint(runtime.getDebugEndpoint())
        .workspaceRef(runtime.getWorkspaceRef())
        .accessRef(runtime.getAccessRef())
        .capabilities(runtime.getCapabilities())
        .backendMetadata(runtime.getBackendMetadata())
        .build();
  }

  private RuntimeInstance persistRuntimeAndClearOperation(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
    RuntimeInstance persisted = persistRuntime(key, spec, instance);
    stateStore.clearActiveOperation(key);
    return persisted;
  }

  private RuntimeInstance persistRuntimeAndClearOperationWithStatusEvent(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
    RuntimeInstance persisted = persistRuntimeAndClearOperation(key, spec, instance);
    eventPublisher.publish(runtimeEvent(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, spec, persisted));
    return persisted;
  }

  private boolean statusChanged(RuntimeInstance previous, RuntimeInstance current) {
    if (previous == null && current == null) {
      return false;
    }
    if (previous == null || current == null) {
      return true;
    }
    return previous.getStatus() != current.getStatus();
  }

  private RuntimeInstance deletedRuntime(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance current) {
    if (current != null) {
      return RuntimeInstance.builder()
          .runtimeId(current.getRuntimeId())
          .appName(current.getAppName())
          .workloadId(current.getWorkloadId())
          .profile(current.getProfile())
          .runtimeKind(current.getRuntimeKind())
          .backendType(current.getBackendType())
          .runtimeGeneration(current.getRuntimeGeneration())
          .status(RuntimeStatus.NOT_FOUND)
          .webEndpoint(current.getWebEndpoint())
          .apiEndpoint(current.getApiEndpoint())
          .debugEndpoint(current.getDebugEndpoint())
          .workspaceRef(current.getWorkspaceRef())
          .accessRef(current.getAccessRef())
          .capabilities(current.getCapabilities())
          .backendMetadata(current.getBackendMetadata())
          .build();
    }
    return RuntimeInstance.builder()
        .runtimeId("rt-" + key.getAppName() + "-" + key.getProfile())
        .appName(key.getAppName())
        .workloadId(key.getWorkloadId())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind() == null ? spec.getRuntimeKind() : key.getRuntimeKind())
        .backendType(key.getBackendType() == null ? spec.getBackendType() : key.getBackendType())
        .status(RuntimeStatus.NOT_FOUND)
        .workspaceRef(spec.getWorkspaceRef())
        .build();
  }

  private RuntimeSpec mergeWorkspace(RuntimeSpec spec, com.ouroboros.dev.runtime.model.WorkspaceRef workspaceRef) {
    return spec.withWorkspaceRef(workspaceRef);
  }

  private boolean isStableStartState(RuntimeStatus status) {
    return status == RuntimeStatus.RUNNING || status == RuntimeStatus.STARTING;
  }

  private long generationFor(RuntimeIdentityKey key, boolean dedupeRestart) {
    Long currentGeneration = null;
    Optional<RuntimeInstance> current = stateStore.findInstance(key);
    if (current.isPresent()) {
      currentGeneration = current.get().getRuntimeGeneration();
    }
    Optional<RuntimeOperationState> activeOperation = stateStore.findActiveOperation(key);
    if (dedupeRestart && activeOperation.isPresent() && activeOperation.get().getType() == RuntimeOperationState.Type.RESTART) {
      Long activeGeneration = activeOperation.get().getRuntimeGeneration();
      if (activeGeneration != null && (currentGeneration == null || activeGeneration.longValue() >= currentGeneration.longValue())) {
        return activeGeneration;
      }
    }
    long generation = 0L;
    if (currentGeneration != null) {
      generation = Math.max(generation, currentGeneration.longValue());
    }
    if (activeOperation.isPresent() && activeOperation.get().getRuntimeGeneration() != null) {
      generation = Math.max(generation, activeOperation.get().getRuntimeGeneration());
    }
    return generation + 1L;
  }

  private boolean isNotFound(Throwable error) {
    return error != null
        && error.getMessage() != null
        && error.getMessage().toLowerCase().contains("not found");
  }

  private boolean isEquivalent(RuntimeSpec left, RuntimeSpec right) {
    return Objects.equals(left.getWorkloadId(), right.getWorkloadId()) && Objects.equals(left.getAppName(), right.getAppName())
        && Objects.equals(left.getProfile(), right.getProfile())
        && left.getRuntimeKind() == right.getRuntimeKind()
        && left.getBackendType() == right.getBackendType()
        && left.getWorkspaceMode() == right.getWorkspaceMode()
        && left.getExposureStrategy() == right.getExposureStrategy()
        && Objects.equals(left.getImage(), right.getImage())
        && Objects.equals(left.getEnv(), right.getEnv())
        && Objects.equals(left.getLabels(), right.getLabels())
        && Objects.equals(left.getResourceLimits(), right.getResourceLimits())
        && Objects.equals(left.getRequestedPorts(), right.getRequestedPorts())
        && Objects.equals(left.getNetwork(), right.getNetwork())
        && Objects.equals(left.getTtl(), right.getTtl());
  }

  private <T> Try<T> withLifecycleLock(RuntimeIdentityKey key, Supplier<Try<T>> operation) {
    LifecycleLock lifecycleLock = lifecycleLocks.compute(key, (ignored, current) -> {
      LifecycleLock acquired = current == null ? new LifecycleLock() : current;
      acquired.references++;
      return acquired;
    });
    lifecycleLock.lock.lock();
    try {
      return operation.get();
    } finally {
      lifecycleLock.lock.unlock();
      lifecycleLocks.computeIfPresent(key, (ignored, current) -> {
        if (current != lifecycleLock) {
          return current;
        }
        current.references--;
        return current.references == 0 ? null : current;
      });
    }
  }

  private RuntimeInstance stoppedInstance(RuntimeIdentityKey key) {
    return RuntimeInstance.builder()
        .runtimeId("rt-" + key.getAppName() + "-" + key.getProfile())
        .appName(key.getAppName())
        .workloadId(key.getWorkloadId())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind() == null ? RuntimeKind.TEST_APP : key.getRuntimeKind())
        .backendType(key.getBackendType())
        .status(RuntimeStatus.STOPPED)
        .build();
  }

  private static final class LifecycleLock {
    private final ReentrantLock lock = new ReentrantLock();
    private int references;
  }
}
