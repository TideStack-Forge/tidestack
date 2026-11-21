package com.ouroboros.dev.runtime.docker.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.docker.workspace.HostPathWorkspaceProvisioner;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.service.DefaultDevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

class DockerDevHelperRuntimeBackendTest {

  @Test
  void shouldWaitForBusinessReadySignalBeforeReportingRunning() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    RuntimeInstance created = backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "current-generation");
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(started.getRuntimeGeneration().longValue() - 1L),
        "helper-a",
        1000L,
        "app-incarnation",
        "stale-generation",
        1L,
        dockerTarget()
    ));
    RuntimeInstance stillStarting = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "current-generation",
        1L,
        dockerTarget()
    ));
    RuntimeInstance running = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();

    assertFalse(created.getCapabilities().isSupportsPause());
    assertEquals(RuntimeStatus.STOPPED, created.getStatus());
    assertEquals(RuntimeStatus.STARTING, started.getStatus());
    assertEquals(RuntimeStatus.STARTING, stillStarting.getStatus());
    assertEquals(RuntimeStatus.RUNNING, running.getStatus());
    assertEquals(1, eventPublisher.events().size());
    assertEquals(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, eventPublisher.events().get(0).getType());
    assertEquals(RuntimeStatus.RUNNING, eventPublisher.events().get(0).getStatus());
    assertEquals(started.getRuntimeGeneration(), eventPublisher.events().get(0).getRuntimeGeneration());
  }

  @Test
  void shouldKeepRuntimeGenerationWhenOnlyHelperProcessIdentityChanges() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    activate(backend, "crm", "helper-b", 2000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-b", 2000L, "current-identity");
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-b",
        1000L,
        "app-incarnation",
        "wrong-identity",
        1L,
        dockerTarget()
    ));
    RuntimeInstance wrongIdentity = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-b",
        2000L,
        "app-incarnation",
        "current-identity",
        1L,
        dockerTarget()
    ));
    RuntimeInstance running = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();

    assertEquals(RuntimeStatus.STARTING, wrongIdentity.getStatus());
    assertEquals(RuntimeStatus.RUNNING, running.getStatus());
    assertEquals(started.getRuntimeGeneration(), running.getRuntimeGeneration());
  }

  @Test
  void shouldRejectPublishedGenerationThatDoesNotMatchRuntimeLifecycle() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "generation-current");
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(7L),
        "helper-a",
        1000L,
        "app-incarnation",
        "generation-current",
        1L,
        dockerTarget()
    ));

    RuntimeInstance stillStarting = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(6L),
        "helper-a",
        1000L,
        "app-incarnation",
        "generation-current",
        2L,
        dockerTarget()
    ));
    RuntimeInstance afterStaleSnapshot = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();

    assertEquals(RuntimeStatus.STARTING, stillStarting.getStatus());
    assertEquals(started.getRuntimeGeneration(), stillStarting.getRuntimeGeneration());
    assertEquals(started.getRuntimeGeneration(), afterStaleSnapshot.getRuntimeGeneration());
  }

  @Test
  void duplicateKnockShouldNotClearReadyState() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "initial");
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "initial",
        1L,
        dockerTarget()
    ));

    activate(backend, "crm", "helper-a", 1000L);

    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void retiredHelperShouldNotReactivateAfterRestart() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    RuntimeInstance restarted = backend.restart(DockerRuntimeTestSupport.helperSpec(), 2L).get();

    activate(backend, "crm", "helper-a", 1000L);
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        restarted.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "late-old",
        2L,
        dockerTarget()
    ));

    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void retiredLifecycleShouldNotPublishRunningAfterInspection() throws Exception {
    CountDownLatch inspectionStarted = new CountDownLatch(1);
    CountDownLatch continueInspection = new CountDownLatch(1);
    AtomicBoolean blockNextInspection = new AtomicBoolean();
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService() {
      @Override
      public io.vavr.control.Either<Exception, Container> getContainerInfoByName(String name) {
        if (blockNextInspection.compareAndSet(true, false)) {
          inspectionStarted.countDown();
          try {
            if (!continueInspection.await(5L, TimeUnit.SECONDS)) {
              return io.vavr.control.Either.left(new IllegalStateException("timed out waiting for restart"));
            }
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return io.vavr.control.Either.left(exception);
          }
        }
        return super.getContainerInfoByName(name);
      }
    };
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "in-flight");
    blockNextInspection.set(true);
    CompletableFuture<Void> publication = CompletableFuture.runAsync(() -> backend.onMetadataSnapshotPublished(
        new MetadataSnapshotPublishedEvent(
            "crm",
            started.getRuntimeGeneration(),
            "helper-a",
            1000L,
            "app-incarnation",
            "in-flight",
            1L,
            dockerTarget()
        )
    ));
    assertTrue(inspectionStarted.await(5L, TimeUnit.SECONDS));
    try {
      backend.restart(DockerRuntimeTestSupport.helperSpec(), 2L).get();
    } finally {
      continueInspection.countDown();
    }
    publication.get(5L, TimeUnit.SECONDS);

    assertTrue(eventPublisher.events().isEmpty());
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void stoppedContainerShouldNotPublishRunningFromSnapshot() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "stopped-container");
    containerService.stop("ouroboros-dev-helper-crm");

    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "stopped-container",
        1L,
        dockerTarget()
    ));

    assertTrue(eventPublisher.events().isEmpty());
    assertEquals(RuntimeStatus.STOPPED, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void readyStateShouldNotTransferToSameNameReplacementContainer() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "ready");
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "ready",
        1L,
        dockerTarget()
    ));

    Container replacement = new Container();
    replacement.setId("cid-replacement");
    replacement.setName("ouroboros-dev-helper-crm");
    replacement.setImage("ouroboros/dev-helper:latest");
    replacement.setStatus(ContainerStatus.RUNNING);
    java.util.Map<String, String> labels = new java.util.LinkedHashMap<String, String>();
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");
    labels.put(DockerRuntimeMapper.DEV_HELPER_LABEL, "crm");
    replacement.setLabels(labels);
    replacement.setEnvironmentalVariables(DockerRuntimeTestSupport.helperSpec().getEnv());
    containerService.put(replacement);

    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void firstPublicationShouldNotTransferToReplacementContainer() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "from-container-a");

    Container replacement = replacementContainer("cid-replacement");
    containerService.put(replacement);
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "from-container-a",
        1L,
        dockerTarget()
    ));

    assertTrue(eventPublisher.events().isEmpty());
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());

    HelperRuntimeTarget replacementTarget = dockerTarget("cid-replacement");
    backend.onHelperRuntimeActivated(new HelperRuntimeActivatedEvent(
        "crm",
        new HelperIdentity("helper-b", 2000L, "app-incarnation"),
        replacementTarget
    ));
    backend.onMetadataRefreshRequested(new MetadataRefreshRequestedEvent(
        "crm",
        started.getRuntimeGeneration(),
        new HelperIdentity("helper-b", 2000L, "app-incarnation"),
        "from-container-b",
        replacementTarget
    ));
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-b",
        2000L,
        "app-incarnation",
        "from-container-b",
        1L,
        replacementTarget
    ));

    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
    assertEquals(1, eventPublisher.events().size());
  }

  @Test
  void publicationShouldRecheckContainerAfterInitialInspection() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "recheck");
    containerService.runAfterNextInfoRead(() -> containerService.stop("ouroboros-dev-helper-crm"));

    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "recheck",
        1L,
        dockerTarget()
    ));

    assertTrue(eventPublisher.events().isEmpty());
    assertEquals(RuntimeStatus.STOPPED, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
  }

  @Test
  void recoveredBackendShouldAdoptPersistedHigherGenerationForRefresh() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend original = new DockerDevHelperRuntimeBackend(containerService);
    original.create(DockerRuntimeTestSupport.helperSpec()).get();
    original.restart(DockerRuntimeTestSupport.helperSpec(), 7L).get();

    DockerDevHelperRuntimeBackend recovered = new DockerDevHelperRuntimeBackend(containerService);
    RuntimeInstance recoveredStarted = recovered.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(recovered, "crm", "helper-a", 1000L);
    request(recovered, "crm", recoveredStarted.getRuntimeGeneration(), "helper-a", 1000L, "recovered-generation");
    recovered.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        recoveredStarted.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "recovered-generation",
        1L,
        dockerTarget()
    ));

    RuntimeInstance running = recovered.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();
    assertEquals(RuntimeStatus.RUNNING, running.getStatus());
    assertEquals(Long.valueOf(8L), running.getRuntimeGeneration());
  }

  @Test
  void missingContainerShouldNotPublishRunningFromSnapshot() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "missing-container");
    containerService.delete("ouroboros-dev-helper-crm");

    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "missing-container",
        1L,
        dockerTarget()
    ));

    assertTrue(eventPublisher.events().isEmpty());
  }

  @Test
  void currentOperationRejectionShouldCommitFailedAndRejectOldPublication() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "operation-a");
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "operation-b");

    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "operation-a",
        1L,
        dockerTarget()
    ));
    backend.onMetadataSnapshotRejected(new MetadataSnapshotRejectedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "operation-b",
        "collector failed",
        dockerTarget()
    ));
    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "operation-a",
        1L,
        dockerTarget()
    ));

    RuntimeInstance failed = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_REFRESH_FAILED", failed.getBackendMetadata().get("failureCode"));
    assertEquals("collector failed", failed.getBackendMetadata().get("failureMessage"));
    assertTrue(eventPublisher.events().isEmpty());
  }

  @Test
  void shouldDiscoverExistingHelperContainerForRuntimeRecovery() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend creator = new DockerDevHelperRuntimeBackend(containerService);
    creator.create(DockerRuntimeTestSupport.helperSpec()).get();
    creator.start(DockerRuntimeTestSupport.helperSpec()).get();
    DockerDevHelperRuntimeBackend recovering = new DockerDevHelperRuntimeBackend(containerService);

    RuntimeSpec discovered = recovering.discover("crm").get().orElseThrow();

    assertEquals(BackendType.DOCKER, discovered.getBackendType());
    assertEquals("ouroboros/dev-helper:latest", discovered.getImage());
    assertEquals(
        "app-incarnation",
        discovered.getEnv().get("OUROBOROS_APP_INCARNATION_ID")
    );
    assertEquals("default", discovered.getLabels().get("ouroboros.dev/helper-image-source"));
  }

  @Test
  void shouldRecoverPersistedGenerationAfterBackendAndStateStoreRestart() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RuntimeSpec spec = DockerRuntimeTestSupport.helperSpec();
    DockerDevHelperRuntimeBackend original = new DockerDevHelperRuntimeBackend(containerService);
    original.create(spec).get();
    original.restart(spec, 7L).get();

    DockerDevHelperRuntimeBackend recoveredBackend = new DockerDevHelperRuntimeBackend(containerService);
    DefaultDevRuntimeService recoveredService = new DefaultDevRuntimeService(
        Arrays.asList(recoveredBackend),
        Collections.emptyList(),
        new InMemoryRuntimeStateStore()
    );

    List<RuntimeInstance> recovered = recoveredService.recover("crm", RuntimeKind.DEV_HELPER).get();
    RuntimeInstance recoveredInstance = recovered.get(0);
    activate(recoveredBackend, "crm", "helper-a", 1000L);
    request(recoveredBackend, "crm", recoveredInstance.getRuntimeGeneration(), "helper-a", 1000L, "recovered-state");
    recoveredBackend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        "crm",
        recoveredInstance.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "recovered-state",
        1L,
        dockerTarget()
    ));

    assertEquals(1, recovered.size());
    assertEquals(Long.valueOf(7L), recoveredInstance.getRuntimeGeneration());
    assertEquals(RuntimeStatus.RUNNING, recoveredBackend.getStatus(RuntimeIdentityKey.from(spec)).get().getStatus());
    assertEquals(Long.valueOf(7L), recoveredBackend.getStatus(RuntimeIdentityKey.from(spec)).get().getRuntimeGeneration());
  }

  @Test
  void shouldRecoverPersistedGenerationWhenCreateAcquiresRunningContainer() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RuntimeSpec spec = DockerRuntimeTestSupport.helperSpec();
    DockerDevHelperRuntimeBackend original = new DockerDevHelperRuntimeBackend(containerService);
    original.create(spec).get();
    original.restart(spec, 7L).get();
    DockerDevHelperRuntimeBackend recoveredBackend = new DockerDevHelperRuntimeBackend(containerService);
    DefaultDevRuntimeService recoveredService = new DefaultDevRuntimeService(
        Arrays.asList(recoveredBackend),
        Collections.singletonList(new HostPathWorkspaceProvisioner("/workspace/run")),
        new InMemoryRuntimeStateStore()
    );

    RuntimeInstance acquired = recoveredService.create(spec).get();
    RuntimeInstance idempotentStart = recoveredService.start(RuntimeIdentityKey.from(spec)).get();

    assertEquals(RuntimeStatus.STARTING, acquired.getStatus());
    assertEquals(Long.valueOf(7L), acquired.getRuntimeGeneration());
    assertEquals(Long.valueOf(7L), idempotentStart.getRuntimeGeneration());
  }

  @Test
  void newAppIncarnationShouldNotReusePersistedGeneration() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RuntimeSpec originalSpec = DockerRuntimeTestSupport.helperSpec();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(originalSpec);
    DockerDevHelperRuntimeBackend original = new DockerDevHelperRuntimeBackend(containerService);
    original.create(originalSpec).get();
    original.restart(originalSpec, 7L).get();
    original.delete(key).get();
    RuntimeSpec recreatedSpec = originalSpec.withEnv(Collections.singletonMap(
        MetadataRefreshProtocol.APP_INCARNATION_ID_ENV,
        "new-app-incarnation"
    ));
    DockerDevHelperRuntimeBackend recreated = new DockerDevHelperRuntimeBackend(containerService);

    recreated.create(recreatedSpec).get();
    RuntimeInstance started = recreated.start(recreatedSpec).get();

    assertEquals(Long.valueOf(1L), started.getRuntimeGeneration());
  }

  @Test
  void externallyStoppedRuntimeShouldRetireTupleBeforeOldPublicationIsReplayed() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    java.util.List<Object> lifecycleEvents = new java.util.ArrayList<Object>();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(
        containerService,
        eventPublisher,
        lifecycleEvents::add
    );

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    activate(backend, "crm", "helper-a", 1000L);
    request(backend, "crm", started.getRuntimeGeneration(), "helper-a", 1000L, "operation-a");
    MetadataSnapshotPublishedEvent oldPublication = new MetadataSnapshotPublishedEvent(
        "crm",
        started.getRuntimeGeneration(),
        "helper-a",
        1000L,
        "app-incarnation",
        "operation-a",
        1L,
        dockerTarget()
    );

    containerService.stop("ouroboros-dev-helper-crm");
    backend.onMetadataSnapshotPublished(oldPublication);
    containerService.start("ouroboros-dev-helper-crm");
    backend.onMetadataSnapshotPublished(oldPublication);

    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get().getStatus());
    assertTrue(eventPublisher.events().isEmpty());
    assertEquals(1, lifecycleEvents.stream().filter(HelperRuntimeRetiredEvent.class::isInstance).count());
  }

  @Test
  void shouldReuseExistingHelperContainerInsteadOfCreatingDuplicate() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    Container existing = new Container();
    existing.setId("cid-existing");
    existing.setName("ouroboros-dev-helper-crm");
    existing.setImage("ouroboros/dev-helper:latest");
    existing.setStatus(ContainerStatus.STOPPED);
    java.util.Map<String, String> labels = new java.util.LinkedHashMap<String, String>();
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");
    labels.put(DockerRuntimeMapper.DEV_HELPER_LABEL, "crm");
    existing.setLabels(labels);
    existing.setEnvironmentalVariables(DockerRuntimeTestSupport.helperSpec().getEnv());
    containerService.put(existing);

    RuntimeInstance created = backend.create(DockerRuntimeTestSupport.helperSpec()).get();

    assertEquals("cid-existing", created.getRuntimeId());
    assertEquals(RuntimeStatus.STOPPED, created.getStatus());
    assertTrue(containerService.operationTrace().isEmpty());
  }

  @Test
  void shouldRejectExistingHelperContainerFromAnotherAppIncarnation() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    Container existing = new Container();
    existing.setId("cid-existing");
    existing.setName("ouroboros-dev-helper-crm");
    existing.setImage("ouroboros/dev-helper:latest");
    existing.setStatus(ContainerStatus.STOPPED);
    java.util.Map<String, String> labels = new java.util.LinkedHashMap<String, String>();
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");
    labels.put(DockerRuntimeMapper.DEV_HELPER_LABEL, "crm");
    existing.setLabels(labels);
    existing.setEnvironmentalVariables(java.util.Collections.singletonMap(
        com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.APP_INCARNATION_ID_ENV,
        "old-incarnation"
    ));
    containerService.put(existing);

    assertTrue(backend.create(DockerRuntimeTestSupport.helperSpec()).isFailure());
    assertTrue(containerService.operationTrace().isEmpty());
  }

  @Test
  void shouldReportNotFoundWhenHelperContainerDisappears() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    containerService.delete("ouroboros-dev-helper-crm");

    RuntimeInstance missing = backend.getStatus(
        RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)
    ).get();

    assertEquals(RuntimeStatus.NOT_FOUND, missing.getStatus());
    assertEquals("crm", missing.getAppName());
  }

  @Test
  void shouldReadHelperRuntimeLogsFromContainer() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    containerService.putLogs("ouroboros-dev-helper-crm", Arrays.asList("assistant started", "index ready"));

    assertEquals(
        Arrays.asList("assistant started", "index ready"),
        backend.getLogs(RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER)).get()
    );
  }

  private static void activate(
      DockerDevHelperRuntimeBackend backend,
      String appName,
      String helperInstanceId,
      long startedAtEpochMillis
  ) {
    backend.onHelperRuntimeActivated(new HelperRuntimeActivatedEvent(
        appName,
        new HelperIdentity(helperInstanceId, startedAtEpochMillis, "app-incarnation"),
        dockerTarget()
    ));
  }

  private static void request(
      DockerDevHelperRuntimeBackend backend,
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long startedAtEpochMillis,
      String refreshOperationId
  ) {
    backend.onMetadataRefreshRequested(new MetadataRefreshRequestedEvent(
        appName,
        runtimeGeneration,
        new HelperIdentity(helperInstanceId, startedAtEpochMillis, "app-incarnation"),
        refreshOperationId,
        dockerTarget()
    ));
  }

  private static HelperRuntimeTarget dockerTarget() {
    return dockerTarget("cid-1");
  }

  private static HelperRuntimeTarget dockerTarget(String runtimeId) {
    return new HelperRuntimeTarget(BackendType.DOCKER.name(), "dev-helper", runtimeId);
  }

  private static Container replacementContainer(String runtimeId) {
    Container replacement = new Container();
    replacement.setId(runtimeId);
    replacement.setName("ouroboros-dev-helper-crm");
    replacement.setImage("ouroboros/dev-helper:latest");
    replacement.setStatus(ContainerStatus.RUNNING);
    java.util.Map<String, String> labels = new java.util.LinkedHashMap<String, String>();
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");
    labels.put(DockerRuntimeMapper.DEV_HELPER_LABEL, "crm");
    replacement.setLabels(labels);
    replacement.setEnvironmentalVariables(DockerRuntimeTestSupport.helperSpec().getEnv());
    return replacement;
  }

  private static class RecordingRuntimeEventPublisher implements DevRuntimeEventPublisher {
    private final java.util.List<DevRuntimeEvent> events = new java.util.ArrayList<DevRuntimeEvent>();

    @Override
    public void publish(DevRuntimeEvent event) {
      events.add(event);
    }

    java.util.List<DevRuntimeEvent> events() {
      return events;
    }
  }
}
