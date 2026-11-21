package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FailingWorkspaceProvisioner;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeRuntimeBackend;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeWorkspaceProvisioner;

class DefaultDevRuntimeServiceTest {

  @Test
  void shouldProvisionWorkspaceBeforeCreate() {
    FakeWorkspaceProvisioner provisioner = new FakeWorkspaceProvisioner();
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    InMemoryRuntimeStateStore stateStore = new InMemoryRuntimeStateStore();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(provisioner),
        stateStore
    );

    service.create(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build());

    assertEquals(
        Arrays.asList("provision", "create"),
        Arrays.asList(provisioner.invocation(), backend.invocation())
    );
  }

  @Test
  void shouldReturnExistingInstanceWhenCreateRequestIsEquivalent() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithExisting(spec, 7L);
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(new FakeRuntimeBackend()),
        Arrays.asList(new FakeWorkspaceProvisioner()),
        stateStore
    );

    RuntimeInstance instance = service.create(spec).get();

    assertEquals("rt-crm-alice", instance.getRuntimeId());
    assertEquals(Long.valueOf(7L), instance.getRuntimeGeneration());
  }

  @Test
  void shouldRejectCreateWhenExistingRuntimeSpecDiffers() {
    RuntimeSpec requested = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .image("app:v2")
        .build();
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithExistingDifferentSpec("app:v1");
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(new FakeWorkspaceProvisioner()),
        stateStore
    );

    Try<RuntimeInstance> result = service.create(requested);

    assertTrue(result.isFailure());
    assertEquals(0, backend.createInvocationCount());
  }

  @Test
  void shouldProvisionWorkspaceBeforeStartAndReloadWorkspace() {
    FakeWorkspaceProvisioner provisioner = new FakeWorkspaceProvisioner();
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(
        RuntimeSpec.builder()
            .appName("crm")
            .profile("alice")
            .runtimeKind(RuntimeKind.TEST_APP)
            .backendType(BackendType.DOCKER)
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .build()
    );
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(provisioner),
        stateStore
    );

    service.start(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER));
    service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build());

    assertEquals(Arrays.asList("provision-start", "provision-reload"), provisioner.operationTrace());
    assertEquals(Arrays.asList("start", "reload"), backend.operationTrace());
  }

  @Test
  void shouldCreateFromStoredSpecWhenStartFindsNoRuntime() {
    FakeRuntimeBackend backend = FakeRuntimeBackend.startMissingThenCreate();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(
        RuntimeSpec.builder()
            .appName("crm")
            .profile("alice")
            .runtimeKind(RuntimeKind.TEST_APP)
            .backendType(BackendType.DOCKER)
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .build()
    );
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance result = service.start(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    ).get();

    assertEquals("rt-crm-alice", result.getRuntimeId());
    assertEquals(Arrays.asList("start-missing", "create"), backend.operationTrace());
  }

  @Test
  void shouldNotFallbackToCreateWhenStartFailsForOtherReasons() {
    FakeRuntimeBackend backend = new FakeRuntimeBackend() {
      @Override
      public Try<RuntimeInstance> start(RuntimeSpec spec) {
        return Try.failure(new IllegalStateException("image pull failed"));
      }
    };
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(
        RuntimeSpec.builder()
            .appName("crm")
            .profile("alice")
            .runtimeKind(RuntimeKind.TEST_APP)
            .backendType(BackendType.DOCKER)
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .build()
    );
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    Try<RuntimeInstance> result = service.start(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    );

    assertTrue(result.isFailure());
    assertEquals(0, backend.createInvocationCount());
  }

  @Test
  void shouldReturnCurrentInstanceWhenStartIsAlreadyRunningOrStarting() {
    RuntimeIdentityKey runningKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    FakeRuntimeBackend runningBackend = new FakeRuntimeBackend();
    InMemoryRuntimeStateStore runningStateStore = RuntimeContractTestSupport.stateStoreWithInstance(
        runningKey,
        RuntimeStatus.RUNNING,
        7L
    );
    DevRuntimeService runningService = new DefaultDevRuntimeService(
        Arrays.asList(runningBackend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        runningStateStore
    );
    RuntimeIdentityKey startingKey = RuntimeIdentityKey.of("crm", "bob", RuntimeKind.TEST_APP, BackendType.DOCKER);
    FakeRuntimeBackend startingBackend = new FakeRuntimeBackend();
    InMemoryRuntimeStateStore startingStateStore = RuntimeContractTestSupport.stateStoreWithInstance(
        startingKey,
        RuntimeStatus.STARTING,
        9L
    );
    DevRuntimeService startingService = new DefaultDevRuntimeService(
        Arrays.asList(startingBackend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        startingStateStore
    );

    RuntimeInstance runningResult = runningService.start(runningKey).get();
    RuntimeInstance startingResult = startingService.start(startingKey).get();

    assertEquals(RuntimeStatus.RUNNING, runningResult.getStatus());
    assertEquals(Long.valueOf(7L), runningResult.getRuntimeGeneration());
    assertEquals(RuntimeStatus.STARTING, startingResult.getStatus());
    assertEquals(Long.valueOf(9L), startingResult.getRuntimeGeneration());
    assertEquals(0, runningBackend.startInvocationCount());
    assertEquals(0, startingBackend.startInvocationCount());
  }

  @Test
  void shouldReturnNotFoundWhenStartAndReloadHaveNoStoredRuntime() {
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(new FakeRuntimeBackend()),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        new InMemoryRuntimeStateStore()
    );

    Try<RuntimeInstance> startResult = service.start(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    );
    Try<RuntimeInstance> reloadResult = service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build());

    assertTrue(startResult.isFailure());
    assertTrue(reloadResult.isFailure());
  }

  @Test
  void shouldNotInvokeBackendWhenWorkspaceProvisionFails() {
    FailingWorkspaceProvisioner provisioner = new FailingWorkspaceProvisioner();
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(provisioner),
        new InMemoryRuntimeStateStore()
    );

    Try<RuntimeInstance> result = service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build());

    assertTrue(result.isFailure());
    assertEquals(0, backend.reloadInvocationCount());
  }

  @Test
  void shouldApplyGenerationAwareRulesForRestartAndReloadWorkspace() {
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(new FakeWorkspaceProvisioner()),
        RuntimeContractTestSupport.stateStoreWithStoredSpec(
            RuntimeSpec.builder()
                .appName("crm")
                .profile("alice")
                .runtimeKind(RuntimeKind.TEST_APP)
                .backendType(BackendType.DOCKER)
                .workspaceMode(WorkspaceMode.HOST_PATH)
                .build()
        ).withActiveOperation(RuntimeOperationState.restart(11L))
    );

    RuntimeInstance restartOne = service.restart(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    ).get();
    RuntimeInstance restartTwo = service.restart(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    ).get();
    RuntimeInstance reloadOne = service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build()).get();
    RuntimeInstance reloadTwo = service.reloadWorkspace(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build()).get();

    assertTrue(restartTwo.getRuntimeGeneration() > restartOne.getRuntimeGeneration());
    assertTrue(reloadTwo.getRuntimeGeneration() > reloadOne.getRuntimeGeneration());
  }

  @Test
  void shouldSerializeConcurrentLifecycleOperationsForSameRuntime() throws Exception {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-crm-dev-helper", RuntimeStatus.RUNNING, Long.valueOf(1L)));
    CountDownLatch firstBackendCall = new CountDownLatch(1);
    CountDownLatch releaseFirstBackendCall = new CountDownLatch(1);
    CountDownLatch secondBackendCall = new CountDownLatch(1);
    AtomicInteger backendCalls = new AtomicInteger();
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.KUBERNETES, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec runtimeSpec, long runtimeGeneration) {
        if (backendCalls.incrementAndGet() == 1) {
          firstBackendCall.countDown();
          try {
            if (!releaseFirstBackendCall.await(5, TimeUnit.SECONDS)) {
              return Try.failure(new IllegalStateException("first reload was not released"));
            }
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Try.failure(exception);
          }
        } else {
          secondBackendCall.countDown();
        }
        return Try.success(runtimeInstance(
            runtimeSpec,
            "rt-crm-dev-helper",
            RuntimeStatus.RELOADING,
            Long.valueOf(runtimeGeneration)
        ));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<RuntimeInstance> first = executor.submit(() -> service.reloadWorkspace(spec).get());
      assertTrue(firstBackendCall.await(5, TimeUnit.SECONDS));
      Future<RuntimeInstance> second = executor.submit(() -> service.reloadWorkspace(spec).get());

      assertFalse(secondBackendCall.await(200, TimeUnit.MILLISECONDS));
      releaseFirstBackendCall.countDown();

      assertEquals(Long.valueOf(2L), first.get(5, TimeUnit.SECONDS).getRuntimeGeneration());
      assertEquals(Long.valueOf(3L), second.get(5, TimeUnit.SECONDS).getRuntimeGeneration());
      assertEquals(2, backendCalls.get());
    } finally {
      releaseFirstBackendCall.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void shouldRefreshStoredRuntimeStatusFromBackend() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    stateStore.saveInstance(key, RuntimeContractTestSupport.runtime(spec, RuntimeStatus.STARTING, 1L));
    FakeRuntimeBackend backend = new FakeRuntimeBackend() {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(RuntimeContractTestSupport.runtime(spec, RuntimeStatus.RUNNING, 2L));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance refreshed = service.getStatus(key).get();

    assertEquals(RuntimeStatus.RUNNING, refreshed.getStatus());
    assertEquals(Long.valueOf(2L), refreshed.getRuntimeGeneration());
    assertEquals(RuntimeStatus.RUNNING, stateStore.findInstance(key).get().getStatus());
  }

  @Test
  void shouldPreserveKnownGenerationWhenBackendStatusCannotObserveIt() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    stateStore.saveInstance(key, RuntimeContractTestSupport.runtime(spec, RuntimeStatus.STARTING, 7L));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(RuntimeInstance.builder()
            .runtimeId("rt-crm-dev-helper")
            .appName("crm")
            .profile("dev-helper")
            .runtimeKind(RuntimeKind.DEV_HELPER)
            .backendType(BackendType.DOCKER)
            .status(RuntimeStatus.RUNNING)
            .build());
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance refreshed = service.getStatus(key).get();

    assertEquals(Long.valueOf(7L), refreshed.getRuntimeGeneration());
    assertEquals(Long.valueOf(7L), stateStore.findInstance(key).get().getRuntimeGeneration());
  }

  @Test
  void shouldEstablishGenerationWhenFirstObservingAnExistingRuntime() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(runtimeInstance(spec, "existing-container", RuntimeStatus.RUNNING, null));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance recovered = service.getStatus(key).get();

    assertEquals("existing-container", recovered.getRuntimeId());
    assertEquals(Long.valueOf(1L), recovered.getRuntimeGeneration());
    assertEquals(Long.valueOf(1L), stateStore.findInstance(key).orElseThrow().getRuntimeGeneration());
  }

  @Test
  void shouldPreserveKnownGenerationAcrossStartAndStopWhenBackendOmitsIt() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-crm-dev-helper", RuntimeStatus.STOPPED, Long.valueOf(7L)));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> start(RuntimeSpec runtimeSpec) {
        return Try.success(runtimeInstance(runtimeSpec, "rt-crm-dev-helper", RuntimeStatus.RUNNING, null));
      }

      @Override
      public Try<RuntimeInstance> stop(RuntimeIdentityKey runtimeKey) {
        return Try.success(runtimeInstance(spec, "rt-crm-dev-helper", RuntimeStatus.STOPPED, null));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance started = service.start(key).get();
    RuntimeInstance stopped = service.stop(key).get();

    assertEquals(Long.valueOf(7L), started.getRuntimeGeneration());
    assertEquals(Long.valueOf(7L), stopped.getRuntimeGeneration());
    assertEquals(Long.valueOf(7L), stateStore.findInstance(key).get().getRuntimeGeneration());
  }

  @Test
  void shouldIgnoreLowerGenerationReportedForSameRuntimeIdentity() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-crm-dev-helper", RuntimeStatus.STARTING, Long.valueOf(7L)));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(runtimeInstance(spec, "rt-crm-dev-helper", RuntimeStatus.RUNNING, Long.valueOf(2L)));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance refreshed = service.getStatus(key).get();

    assertEquals(Long.valueOf(7L), refreshed.getRuntimeGeneration());
  }

  @Test
  void shouldAdvanceGenerationAcrossRuntimeIdentityChange() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-old", RuntimeStatus.STOPPED, Long.valueOf(7L)));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> start(RuntimeSpec runtimeSpec) {
        return Try.success(runtimeInstance(runtimeSpec, "rt-new", RuntimeStatus.RUNNING, null));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance started = service.start(key).get();

    assertEquals("rt-new", started.getRuntimeId());
    assertEquals(Long.valueOf(8L), started.getRuntimeGeneration());
  }

  @Test
  void shouldAdvanceGenerationAcrossNullRuntimeIdentityTransition() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-old", RuntimeStatus.RUNNING, Long.valueOf(7L)));
    AtomicInteger statusPoll = new AtomicInteger();
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        if (statusPoll.getAndIncrement() == 0) {
          return Try.success(runtimeInstance(spec, null, RuntimeStatus.NOT_FOUND, null));
        }
        return Try.success(runtimeInstance(spec, "rt-new", RuntimeStatus.STARTING, null));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance missing = service.getStatus(key).get();
    assertEquals("rt-old", missing.getRuntimeId());
    service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );
    RuntimeInstance recreated = service.getStatus(key).get();

    assertEquals(Long.valueOf(7L), missing.getRuntimeGeneration());
    assertEquals("rt-new", recreated.getRuntimeId());
    assertEquals(Long.valueOf(8L), recreated.getRuntimeGeneration());
  }

  @Test
  void shouldAdvanceGenerationWhenReplacementRuntimeReportsLowerGeneration() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithStoredSpec(spec);
    stateStore.saveInstance(key, runtimeInstance(spec, "rt-old", RuntimeStatus.RUNNING, Long.valueOf(7L)));
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(runtimeInstance(spec, "rt-new", RuntimeStatus.STARTING, Long.valueOf(1L)));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance recovered = service.getStatus(key).get();

    assertEquals("rt-new", recovered.getRuntimeId());
    assertEquals(Long.valueOf(8L), recovered.getRuntimeGeneration());
  }

  @Test
  void shouldRecoverDiscoveredRuntimeThroughBackendExtensionPoint() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    InMemoryRuntimeStateStore stateStore = new InMemoryRuntimeStateStore();
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.KUBERNETES, RuntimeKind.DEV_HELPER) {
      @Override
      public Try<Optional<RuntimeSpec>> discover(String appName) {
        return Try.success("crm".equals(appName) ? Optional.of(spec) : Optional.empty());
      }

      @Override
      public Try<RuntimeInstance> getStatus(RuntimeIdentityKey runtimeKey) {
        return Try.success(runtimeInstance(spec, "deployment-crm", RuntimeStatus.STARTING, null));
      }
    };
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.PVC)),
        stateStore
    );

    List<RuntimeInstance> recovered = service.recover("crm", RuntimeKind.DEV_HELPER).get();

    assertEquals(1, recovered.size());
    assertEquals("deployment-crm", recovered.get(0).getRuntimeId());
    assertEquals(Long.valueOf(1L), recovered.get(0).getRuntimeGeneration());
    assertEquals(spec, stateStore.findSpec(key).orElseThrow());
    assertEquals(recovered.get(0), stateStore.findInstance(key).orElseThrow());
  }

  @Test
  void shouldIgnoreStaleRestartOperationWhenNewerGenerationIsAlreadyStored() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    InMemoryRuntimeStateStore stateStore = RuntimeContractTestSupport.stateStoreWithExisting(spec, 12L)
        .withActiveOperation(RuntimeOperationState.restart(11L));
    FakeRuntimeBackend backend = new FakeRuntimeBackend();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        stateStore
    );

    RuntimeInstance restarted = service.restart(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    ).get();

    assertEquals(Long.valueOf(13L), restarted.getRuntimeGeneration());
  }

  @Test
  void shouldKeepStopAndDeleteIdempotent() {
    FakeRuntimeBackend backend = FakeRuntimeBackend.stopped();
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(new FakeWorkspaceProvisioner()),
        new InMemoryRuntimeStateStore()
    );

    Try<RuntimeInstance> stopResult = service.stop(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    );
    Try<Void> deleteResult = service.delete(
        RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)
    );

    assertTrue(stopResult.isSuccess());
    assertTrue(deleteResult.isSuccess());
    assertEquals(0, backend.stopInvocationCount());
    assertEquals(0, backend.deleteInvocationCount());
  }

  @Test
  void shouldReadRuntimeLogsFromResolvedBackend() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER)
        .withLogs(key, Arrays.asList("assistant started", "index ready"));
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        RuntimeContractTestSupport.stateStoreWithStoredSpec(spec)
    );

    List<String> logs = service.getLogs(key).get();

    assertEquals(Arrays.asList("assistant started", "index ready"), logs);
  }

  @Test
  void shouldResetRuntimeLogStreamOnResolvedBackend() {
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    FakeRuntimeBackend backend = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER);
    DevRuntimeService service = new DefaultDevRuntimeService(
        Arrays.asList(backend),
        Arrays.asList(FakeWorkspaceProvisioner.forMode(WorkspaceMode.HOST_PATH)),
        RuntimeContractTestSupport.stateStoreWithStoredSpec(spec)
    );

    service.resetLogStream(key);

    assertEquals(1, backend.resetLogStreamInvocationCount());
    assertEquals(key, backend.resetLogStreamKey());
  }

  private static RuntimeInstance runtimeInstance(
      RuntimeSpec spec,
      String runtimeId,
      RuntimeStatus status,
      Long runtimeGeneration
  ) {
    return RuntimeInstance.builder()
        .runtimeId(runtimeId)
        .appName(spec.getAppName())
        .profile(spec.getProfile())
        .runtimeKind(spec.getRuntimeKind())
        .backendType(spec.getBackendType())
        .runtimeGeneration(runtimeGeneration)
        .status(status)
        .build();
  }
}
