package com.ouroboros.dev.runtime.service.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.dev.runtime.service.RuntimeOperationState;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;

public final class RuntimeContractTestSupport {

  private RuntimeContractTestSupport() {
  }

  public static InMemoryRuntimeStateStore stateStoreWithExisting(RuntimeSpec spec, long generation) {
    InMemoryRuntimeStateStore stateStore = stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    stateStore.saveInstance(key, runtime(spec, RuntimeStatus.RUNNING, generation));
    return stateStore;
  }

  public static InMemoryRuntimeStateStore stateStoreWithStoredSpec(RuntimeSpec spec) {
    InMemoryRuntimeStateStore stateStore = new InMemoryRuntimeStateStore();
    stateStore.saveSpec(RuntimeIdentityKey.from(spec), spec);
    return stateStore;
  }

  public static InMemoryRuntimeStateStore stateStoreWithExistingDifferentSpec(String image) {
    RuntimeSpec storedSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .image(image)
        .build();
    return stateStoreWithExisting(storedSpec, 5L);
  }

  public static InMemoryRuntimeStateStore stateStoreWithInstance(RuntimeIdentityKey key, RuntimeStatus status, long generation) {
    InMemoryRuntimeStateStore stateStore = new InMemoryRuntimeStateStore();
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId(runtimeId(key.getAppName(), key.getProfile()))
        .appName(key.getAppName())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind())
        .backendType(key.getBackendType())
        .runtimeGeneration(generation)
        .status(status)
        .capabilities(RuntimeCapability.builder().build())
        .build();
    stateStore.saveInstance(key, instance);
    return stateStore;
  }

  public static RuntimeInstance runtime(RuntimeSpec spec, RuntimeStatus status, long generation) {
    return RuntimeInstance.builder()
        .runtimeId(runtimeId(spec.getAppName(), spec.getProfile()))
        .appName(spec.getAppName())
        .profile(spec.getProfile())
        .runtimeKind(spec.getRuntimeKind())
        .backendType(spec.getBackendType())
        .runtimeGeneration(generation)
        .status(status)
        .workspaceRef(defaultWorkspace(spec))
        .capabilities(RuntimeCapability.builder().build())
        .build();
  }

  private static WorkspaceRef defaultWorkspace(RuntimeSpec spec) {
    if (spec.getWorkspaceMode() == WorkspaceMode.PVC) {
      return WorkspaceRef.builder()
          .workspaceMode(WorkspaceMode.PVC)
          .pvcName("dev-runtime")
          .namespace("ouroboros-dev")
          .subPath(spec.getAppName() + "/" + spec.getProfile())
          .build();
    }
    return WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .hostPath("/workspace/" + spec.getAppName() + "/" + spec.getProfile())
        .build();
  }

  private static String runtimeId(String appName, String profile) {
    return "rt-" + appName + "-" + profile;
  }

  public static class FakeWorkspaceProvisioner implements WorkspaceProvisioner {
    private final WorkspaceMode workspaceMode;
    private final List<String> operationTrace = new ArrayList<String>();

    public FakeWorkspaceProvisioner() {
      this(WorkspaceMode.HOST_PATH);
    }

    public FakeWorkspaceProvisioner(WorkspaceMode workspaceMode) {
      this.workspaceMode = workspaceMode;
    }

    public static FakeWorkspaceProvisioner forMode(WorkspaceMode workspaceMode) {
      return new FakeWorkspaceProvisioner(workspaceMode);
    }

    @Override
    public boolean supports(RuntimeSpec spec) {
      return workspaceMode == spec.getWorkspaceMode();
    }

    @Override
    public Try<WorkspaceRef> prepare(RuntimeSpec spec, Operation operation) {
      switch (operation) {
        case CREATE:
          operationTrace.add("provision");
          break;
        case START:
          operationTrace.add("provision-start");
          break;
        case RELOAD:
          operationTrace.add("provision-reload");
          break;
        default:
          break;
      }
      return Try.success(defaultWorkspace(spec));
    }

    public String invocation() {
      return operationTrace.isEmpty() ? null : operationTrace.get(0);
    }

    public List<String> operationTrace() {
      return Collections.unmodifiableList(operationTrace);
    }

    public int invocationCount() {
      return operationTrace.size();
    }
  }

  public static class FailingWorkspaceProvisioner extends FakeWorkspaceProvisioner {
    @Override
    public Try<WorkspaceRef> prepare(RuntimeSpec spec, Operation operation) {
      super.prepare(spec, operation);
      return Try.failure(new IllegalStateException("workspace prepare failed"));
    }
  }

  public static class FakeRuntimeBackend implements RuntimeBackend {
    private final BackendType backendType;
    private final RuntimeKind runtimeKind;
    private final List<String> operationTrace = new ArrayList<String>();
    private int createInvocationCount;
    private int startInvocationCount;
    private int reloadInvocationCount;
    private int stopInvocationCount;
    private int deleteInvocationCount;
    private int resetLogStreamInvocationCount;
    private RuntimeIdentityKey resetLogStreamKey;
    private boolean startMissingThenCreate;
    private final Map<RuntimeIdentityKey, List<String>> logsByKey = new LinkedHashMap<RuntimeIdentityKey, List<String>>();

    public FakeRuntimeBackend() {
      this(BackendType.DOCKER, RuntimeKind.TEST_APP);
    }

    public FakeRuntimeBackend(BackendType backendType) {
      this(backendType, RuntimeKind.TEST_APP);
    }

    public FakeRuntimeBackend(BackendType backendType, RuntimeKind runtimeKind) {
      this.backendType = backendType;
      this.runtimeKind = runtimeKind;
    }

    public static FakeRuntimeBackend startMissingThenCreate() {
      FakeRuntimeBackend backend = new FakeRuntimeBackend();
      backend.startMissingThenCreate = true;
      return backend;
    }

    public static FakeRuntimeBackend stopped() {
      return new FakeRuntimeBackend();
    }

    @Override
    public BackendType getBackendType() {
      return backendType;
    }

    @Override
    public RuntimeKind getRuntimeKind() {
      return runtimeKind;
    }

    @Override
    public boolean supports(RuntimeSpec spec) {
      return backendType == spec.getBackendType() && runtimeKind == spec.getRuntimeKind();
    }

    @Override
    public Try<RuntimeInstance> create(RuntimeSpec spec) {
      createInvocationCount++;
      operationTrace.add("create");
      return Try.success(runtime(spec, RuntimeStatus.RUNNING, 1L));
    }

    @Override
    public Try<RuntimeInstance> start(RuntimeSpec spec) {
      startInvocationCount++;
      if (startMissingThenCreate) {
        operationTrace.add("start-missing");
        return Try.failure(new IllegalStateException("runtime not found"));
      }
      operationTrace.add("start");
      return Try.success(runtime(spec, RuntimeStatus.RUNNING, 1L));
    }

    @Override
    public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
      stopInvocationCount++;
      operationTrace.add("stop");
      return Try.success(RuntimeInstance.builder()
          .runtimeId(runtimeId(key.getAppName(), key.getProfile()))
          .appName(key.getAppName())
          .profile(key.getProfile())
          .runtimeKind(key.getRuntimeKind())
          .backendType(key.getBackendType())
          .status(RuntimeStatus.STOPPED)
          .build());
    }

    @Override
    public Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration) {
      operationTrace.add("restart");
      return Try.success(runtime(spec, RuntimeStatus.RUNNING, runtimeGeneration));
    }

    @Override
    public Try<Void> delete(RuntimeIdentityKey key) {
      deleteInvocationCount++;
      operationTrace.add("delete");
      return Try.success(null);
    }

    @Override
    public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration) {
      reloadInvocationCount++;
      operationTrace.add("reload");
      return Try.success(runtime(spec, RuntimeStatus.RUNNING, runtimeGeneration));
    }

    @Override
    public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
      return Try.failure(new IllegalStateException("runtime not found"));
    }

    @Override
    public Try<List<String>> getLogs(RuntimeIdentityKey key) {
      return Try.success(logsByKey.getOrDefault(key, Collections.<String>emptyList()));
    }

    @Override
    public void resetLogStream(RuntimeIdentityKey key) {
      resetLogStreamInvocationCount++;
      resetLogStreamKey = key;
    }

    public FakeRuntimeBackend withLogs(RuntimeIdentityKey key, List<String> logs) {
      logsByKey.put(key, new ArrayList<String>(logs));
      return this;
    }

    public String invocation() {
      return operationTrace.isEmpty() ? null : operationTrace.get(0);
    }

    public List<String> operationTrace() {
      return Collections.unmodifiableList(operationTrace);
    }

    public int createInvocationCount() {
      return createInvocationCount;
    }

    public int startInvocationCount() {
      return startInvocationCount;
    }

    public int reloadInvocationCount() {
      return reloadInvocationCount;
    }

    public int stopInvocationCount() {
      return stopInvocationCount;
    }

    public int deleteInvocationCount() {
      return deleteInvocationCount;
    }

    public int resetLogStreamInvocationCount() {
      return resetLogStreamInvocationCount;
    }

    public RuntimeIdentityKey resetLogStreamKey() {
      return resetLogStreamKey;
    }
  }

  public static class RecordingApplicationEventPublisher implements ApplicationEventPublisher {
    private final List<Object> events = new ArrayList<Object>();

    @Override
    public void publishEvent(ApplicationEvent event) {
      events.add(event);
    }

    @Override
    public void publishEvent(Object event) {
      events.add(event);
    }

    public List<Object> events() {
      return Collections.unmodifiableList(events);
    }

    public List<DevRuntimeEventType> eventTypes() {
      return events.stream()
          .filter(event -> event instanceof DevRuntimeEvent)
          .map(event -> ((DevRuntimeEvent) event).getType())
          .collect(Collectors.toList());
    }
  }
}
