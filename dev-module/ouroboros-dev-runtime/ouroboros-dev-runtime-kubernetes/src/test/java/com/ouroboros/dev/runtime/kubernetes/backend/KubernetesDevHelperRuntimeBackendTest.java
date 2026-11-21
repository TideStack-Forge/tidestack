package com.ouroboros.dev.runtime.kubernetes.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

class KubernetesDevHelperRuntimeBackendTest {

  private KubernetesMockServerSupport server;

  @BeforeEach
  void setUp() {
    server = KubernetesMockServerSupport.crudHttps();
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void shouldIgnoreOldMqCallbacksAfterRestartAndKeepHelperInternalOnly() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    RuntimeInstance generationOne = backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest generationOneRequest = lastRequest(commandService);
    RuntimeInstance generationTwo = backend.restart(KubernetesRuntimeTestSupport.helperSpec(), 2L).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    assertEquals(1, commandService.requests().size());
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-b", 2000L));
    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(generationOneRequest));

    Deployment deployment = helperDeployment();
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
    assertEquals(generationTwo.getRuntimeGeneration(), backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getRuntimeGeneration());
    assertTrue(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().stream()
        .anyMatch(mount -> "/app/deploy".equals(mount.getMountPath())));
    assertTrue(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().stream()
        .anyMatch(mount -> "/app/metadata".equals(mount.getMountPath())));
    assertNull(server.getClient().services()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())))
        .get());
    assertNull(server.getClient().network().v1().ingresses()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.ingressName(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())))
        .get());
    assertEquals(generationTwo.getRuntimeGeneration(), lastRequest(commandService).getRuntimeGeneration());
  }

  @Test
  void commandExecutionTimeoutShouldFailWithoutRetryAndIgnoreLatePublication() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    RuntimeInstance starting = backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest refreshRequest = lastRequest(commandService);
    backend.onCommandResult(KubernetesRuntimeTestSupport.timeoutResult(refreshRequest));

    RuntimeInstance failed = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_HANDSHAKE_TIMEOUT", failed.getBackendMetadata().get("failureCode"));
    assertEquals(1, commandService.requests().size());

    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(refreshRequest));
    assertEquals(RuntimeStatus.FAILED, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(refreshRequest));
    assertEquals(RuntimeStatus.FAILED, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
  }

  @Test
  void responseTimeoutShouldRetryOnceAndOnlyRetryPublicationShouldComplete() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest firstRequest = lastRequest(commandService);

    backend.onCommandResult(KubernetesRuntimeTestSupport.responseTimeoutResult(firstRequest));

    CommandRequest retryRequest = lastRequest(commandService);
    assertEquals(2, commandService.requests().size());
    assertNotEquals(firstRequest.getRefreshOperationId(), retryRequest.getRefreshOperationId());
    assertEquals(firstRequest.getRuntimeGeneration(), retryRequest.getRuntimeGeneration());
    assertEquals(firstRequest.getHelperInstanceId(), retryRequest.getHelperInstanceId());

    backend.onMetadataSnapshotRejected(retryableRejection(firstRequest));
    backend.onCommandResult(KubernetesRuntimeTestSupport.responseTimeoutResult(firstRequest));
    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(firstRequest));
    assertEquals(2, commandService.requests().size());
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(retryRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void secondResponseTimeoutShouldFailAndIgnoreLateRetryPublication() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    backend.onCommandResult(KubernetesRuntimeTestSupport.responseTimeoutResult(lastRequest(commandService)));
    CommandRequest retryRequest = lastRequest(commandService);

    backend.onCommandResult(KubernetesRuntimeTestSupport.responseTimeoutResult(retryRequest));

    RuntimeInstance failed = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(2, commandService.requests().size());
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_HANDSHAKE_TIMEOUT", failed.getBackendMetadata().get("failureCode"));

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(retryRequest));
    assertEquals(RuntimeStatus.FAILED, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void newHelperShouldRequestAnotherRefreshAfterRecoverableFailure() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    java.util.List<Object> applicationEvents = new java.util.ArrayList<Object>();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        DevRuntimeEventPublisher.noop(),
        null,
        applicationEvents::add
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest failedRequest = lastRequest(commandService);
    backend.onCommandResult(KubernetesRuntimeTestSupport.timeoutResult(failedRequest));

    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-b", 2000L));
    CommandRequest recoveryRequest = lastRequest(commandService);

    assertEquals(2, commandService.requests().size());
    assertEquals("helper-b", recoveryRequest.getParams().get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY));
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
    assertEquals(2, applicationEvents.stream().filter(MetadataRefreshRequestedEvent.class::isInstance).count());
    MetadataRefreshRequestedEvent event = applicationEvents.stream()
        .filter(MetadataRefreshRequestedEvent.class::isInstance)
        .map(MetadataRefreshRequestedEvent.class::cast)
        .reduce((left, right) -> right)
        .orElseThrow();
    assertEquals(recoveryRequest.getRefreshOperationId(), event.getRefreshOperationId());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(recoveryRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void lateFailureShouldNotOverwritePublishedRunningState() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest request = lastRequest(commandService);
    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(request));

    backend.onCommandResult(KubernetesRuntimeTestSupport.timeoutResult(request));

    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void externallyDeletedDeploymentShouldRetireActiveHelperBeforeLatePublication() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    java.util.List<Object> applicationEvents = new java.util.ArrayList<Object>();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        DevRuntimeEventPublisher.noop(),
        null,
        applicationEvents::add
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec());

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest request = lastRequest(commandService);
    server.getClient().apps().deployments()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .delete();

    assertEquals(RuntimeStatus.NOT_FOUND, backend.getStatus(key).get().getStatus());
    assertEquals(1, applicationEvents.stream().filter(HelperRuntimeRetiredEvent.class::isInstance).count());
    HelperRuntimeRetiredEvent retired = applicationEvents.stream()
        .filter(HelperRuntimeRetiredEvent.class::isInstance)
        .map(HelperRuntimeRetiredEvent.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals("helper-a", retired.getHelperInstanceId());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(request));
    assertEquals(RuntimeStatus.NOT_FOUND, backend.getStatus(key).get().getStatus());
  }

  @Test
  void externallyScaledDownDeploymentShouldRetireActiveHelper() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    java.util.List<Object> applicationEvents = new java.util.ArrayList<Object>();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        DevRuntimeEventPublisher.noop(),
        null,
        applicationEvents::add
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec());

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    Deployment deployment = helperDeployment();
    deployment.getSpec().setReplicas(Integer.valueOf(0));
    server.getClient().apps().deployments()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(deployment)
        .createOrReplace();

    assertEquals(RuntimeStatus.STOPPED, backend.getStatus(key).get().getStatus());
    assertEquals(1, applicationEvents.stream().filter(HelperRuntimeRetiredEvent.class::isInstance).count());
  }

  @Test
  void oldRefreshOperationShouldNotCompleteNewReload() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest initialRequest = lastRequest(commandService);
    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(initialRequest));
    backend.reloadWorkspace(KubernetesRuntimeTestSupport.helperSpec(), 2L).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-b", 2000L));
    CommandRequest reloadRequest = lastRequest(commandService);

    backend.onMetadataSnapshotPublished(new MetadataSnapshotPublishedEvent(
        reloadRequest.getAppName(),
        reloadRequest.getRuntimeGeneration(),
        reloadRequest.getHelperInstanceId(),
        reloadRequest.getHelperStartedAtEpochMillis().longValue(),
        reloadRequest.getAppIncarnationId(),
        initialRequest.getRefreshOperationId(),
        2L,
        KubernetesRuntimeTestSupport.runtimeTarget("crm")
    ));
    assertEquals(RuntimeStatus.RELOADING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(reloadRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void shouldApplyCurrentGenerationReloadCallbacksAndFailures() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    RuntimeInstance starting = backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest startingRequest = lastRequest(commandService);
    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(startingRequest));
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(startingRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());

    RuntimeInstance reloading = backend.reloadWorkspace(KubernetesRuntimeTestSupport.helperSpec(), 2L).get();
    assertEquals(RuntimeStatus.RELOADING, reloading.getStatus());
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-b", 2000L));
    CommandRequest reloadingRequest = lastRequest(commandService);
    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(startingRequest));
    assertEquals(RuntimeStatus.RELOADING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(reloadingRequest));
    assertEquals(RuntimeStatus.RELOADING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(reloadingRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());

    RuntimeInstance reloadingFailure = backend.reloadWorkspace(KubernetesRuntimeTestSupport.helperSpec(), 3L).get();
    assertEquals(RuntimeStatus.RELOADING, reloadingFailure.getStatus());
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-c", 3000L));
    backend.onCommandResult(KubernetesRuntimeTestSupport.timeoutResult(lastRequest(commandService)));
    RuntimeInstance failed = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_REFRESH_FAILED", failed.getBackendMetadata().get("failureCode"));
  }

  @Test
  void reloadApplyFailureShouldCommitFailedLifecycleState() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService(),
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );
    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    failNextDeploymentPut();

    io.vavr.control.Try<RuntimeInstance> result = backend.reloadWorkspace(
        KubernetesRuntimeTestSupport.helperSpec(),
        2L
    );

    assertTrue(result.isFailure());
    RuntimeInstance failed = backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals(Long.valueOf(2L), failed.getRuntimeGeneration());
    assertEquals("HELPER_APPLY_FAILED", failed.getBackendMetadata().get("failureCode"));
  }

  @Test
  void restartApplyFailureShouldCommitFailedLifecycleState() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService(),
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );
    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    failNextDeploymentPut();

    io.vavr.control.Try<RuntimeInstance> result = backend.restart(
        KubernetesRuntimeTestSupport.helperSpec(),
        2L
    );

    assertTrue(result.isFailure());
    RuntimeInstance failed = backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals(Long.valueOf(2L), failed.getRuntimeGeneration());
    assertEquals("HELPER_APPLY_FAILED", failed.getBackendMetadata().get("failureCode"));
  }

  @Test
  void shouldRemainStartingWhenHelperPodIsReadyBeforeRefreshCallbackArrives() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService(),
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    RuntimeInstance starting = backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(KubernetesRuntimeTestSupport.readyPod(KubernetesRuntimeTestSupport.helperSpec()))
        .createOrReplace();

    RuntimeInstance running = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();

    assertEquals(RuntimeStatus.STARTING, starting.getStatus());
    assertEquals(RuntimeStatus.STARTING, running.getStatus());
    assertEquals(starting.getRuntimeGeneration(), running.getRuntimeGeneration());
  }

  @Test
  void shouldRequestRefreshOnCompatibleHelperOnlineAndWaitForSnapshotPublication() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    RuntimeInstance starting = backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();

    assertEquals(0, commandService.requests().size());

    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));

    RuntimeInstance stillStarting = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    CommandRequest retriedRequest = commandService.requests().get(commandService.requests().size() - 1);

    assertEquals(RuntimeStatus.STARTING, stillStarting.getStatus());
    assertEquals(1, commandService.requests().size());
    assertEquals(Commands.REFRESH_METADATA.getCommand(), retriedRequest.getCommand());
    assertEquals(starting.getRuntimeGeneration(), retriedRequest.getRuntimeGeneration());
    assertEquals("helper-a", retriedRequest.getHelperInstanceId());
    assertEquals(
        Integer.valueOf(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION),
        retriedRequest.getParams().get(MetadataRefreshProtocol.SCHEMA_VERSION_KEY)
    );

    backend.onCommandResult(KubernetesRuntimeTestSupport.successResult(retriedRequest));
    assertEquals(RuntimeStatus.STARTING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(retriedRequest));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
  }

  @Test
  void rejectedSnapshotShouldFailMatchingPendingRefresh() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));
    CommandRequest request = lastRequest(commandService);

    backend.onMetadataSnapshotRejected(new MetadataSnapshotRejectedEvent(
        request.getAppName(),
        request.getRuntimeGeneration(),
        request.getHelperInstanceId(),
        request.getHelperStartedAtEpochMillis().longValue(),
        request.getAppIncarnationId(),
        request.getRefreshOperationId(),
        "malformed snapshot",
        KubernetesRuntimeTestSupport.runtimeTarget("crm")
    ));

    RuntimeInstance failed = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_REFRESH_FAILED", failed.getBackendMetadata().get("failureCode"));
    assertEquals("malformed snapshot", failed.getBackendMetadata().get("failureMessage"));

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(request));
    assertEquals(RuntimeStatus.FAILED, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void synchronousRefreshSendFailureShouldRetryAndRecoverAfterPublication() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    commandService.failNextExec(new IllegalStateException("mq unavailable"));
    List<Object> lifecycleEvents = new ArrayList<Object>();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        DevRuntimeEventPublisher.noop(),
        null,
        lifecycleEvents::add
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));

    RuntimeInstance retrying = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.STARTING, retrying.getStatus());
    assertEquals(1, commandService.requests().size());
    assertEquals(3, lifecycleEvents.size());
    MetadataRefreshRequestedEvent request = (MetadataRefreshRequestedEvent) lifecycleEvents.get(0);
    MetadataSnapshotRejectedEvent rejection = (MetadataSnapshotRejectedEvent) lifecycleEvents.get(1);
    assertEquals(request.getRuntimeGeneration(), rejection.getRuntimeGeneration());
    assertEquals(request.getHelperIdentity().getInstanceId(), rejection.getHelperInstanceId());
    assertEquals(request.getHelperIdentity().getStartedAtEpochMillis(), rejection.getHelperStartedAtEpochMillis());
    assertEquals(request.getHelperIdentity().getAppIncarnationId(), rejection.getAppIncarnationId());
    assertEquals(request.getRefreshOperationId(), rejection.getRefreshOperationId());
    assertEquals(request.getRuntimeTarget(), rejection.getRuntimeTarget());
    assertEquals(CommandFailureType.TRANSPORT, rejection.getFailureType());
    MetadataRefreshRequestedEvent retry = (MetadataRefreshRequestedEvent) lifecycleEvents.get(2);
    assertNotEquals(request.getRefreshOperationId(), retry.getRefreshOperationId());

    backend.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(lastRequest(commandService)));
    assertEquals(RuntimeStatus.RUNNING, backend.getStatus(
        RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())
    ).get().getStatus());
  }

  @Test
  void repeatedSynchronousRefreshSendFailureShouldFailRuntime() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    commandService.failNextExec(new IllegalStateException("mq unavailable"));
    commandService.failNextExec(new IllegalStateException("mq still unavailable"));
    List<Object> lifecycleEvents = new ArrayList<Object>();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        DevRuntimeEventPublisher.noop(),
        null,
        lifecycleEvents::add
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    backend.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));

    RuntimeInstance failed = backend.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.FAILED, failed.getStatus());
    assertEquals("HELPER_REFRESH_FAILED", failed.getBackendMetadata().get("failureCode"));
    assertEquals("mq still unavailable", failed.getBackendMetadata().get("failureMessage"));
    assertEquals(0, commandService.requests().size());
    assertEquals(4, lifecycleEvents.size());
    MetadataRefreshRequestedEvent firstRequest = (MetadataRefreshRequestedEvent) lifecycleEvents.get(0);
    MetadataSnapshotRejectedEvent firstRejection = (MetadataSnapshotRejectedEvent) lifecycleEvents.get(1);
    MetadataRefreshRequestedEvent retryRequest = (MetadataRefreshRequestedEvent) lifecycleEvents.get(2);
    MetadataSnapshotRejectedEvent retryRejection = (MetadataSnapshotRejectedEvent) lifecycleEvents.get(3);
    assertEquals(firstRequest.getRefreshOperationId(), firstRejection.getRefreshOperationId());
    assertEquals(CommandFailureType.TRANSPORT, firstRejection.getFailureType());
    assertNotEquals(firstRequest.getRefreshOperationId(), retryRequest.getRefreshOperationId());
    assertEquals(retryRequest.getRefreshOperationId(), retryRejection.getRefreshOperationId());
    assertEquals(CommandFailureType.TRANSPORT, retryRejection.getFailureType());
  }

  @Test
  void activationShouldHydrateExistingRuntimeBeforeFirstStatusPoll() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    properties.setHelperReadyTimeout(Duration.ofSeconds(5));
    KubernetesDevHelperRuntimeBackend creator = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService(),
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );
    RuntimeInstance created = creator.start(KubernetesRuntimeTestSupport.helperSpec()).get();

    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService recoveringCommandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    KubernetesDevHelperRuntimeBackend recovering = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        recoveringCommandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null)
    );

    assertEquals(0, recoveringCommandService.requests().size());
    RuntimeSpec discovered = recovering.discover("crm").get().orElseThrow();
    assertEquals(BackendType.KUBERNETES, discovered.getBackendType());
    assertEquals("ouroboros/dev-helper:latest", discovered.getImage());
    assertEquals(
        "incarnation-a",
        discovered.getEnv().get("OUROBOROS_APP_INCARNATION_ID")
    );
    assertEquals("explicit", discovered.getLabels().get("ouroboros.dev/helper-image-source"));
    assertEquals(
        "ouroboros/dev-helper:latest",
        discovered.getLabels().get("ouroboros.dev/helper-image-source-reference")
    );

    recovering.onHelperRuntimeActivated(KubernetesRuntimeTestSupport.helperActivated("crm", "helper-a", 1000L));

    assertEquals(1, recoveringCommandService.requests().size());
    assertEquals(created.getRuntimeGeneration(), recoveringCommandService.requests().get(0).getRuntimeGeneration());
    RuntimeInstance recovered = recovering.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get();
    assertEquals(RuntimeStatus.STARTING, recovered.getStatus());

    CommandRequest recoveredRequest = lastRequest(recoveringCommandService);
    recovering.onCommandResult(KubernetesRuntimeTestSupport.successResult(recoveredRequest));
    assertEquals(RuntimeStatus.STARTING, recovering.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());

    recovering.onMetadataSnapshotPublished(KubernetesRuntimeTestSupport.publishedSnapshot(recoveredRequest));
    assertEquals(RuntimeStatus.RUNNING, recovering.getStatus(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())).get().getStatus());
  }

  @Test
  void shouldReadHelperRuntimeLogsFromKubernetesPod() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec());
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(KubernetesRuntimeTestSupport.readyPod(KubernetesRuntimeTestSupport.helperSpec()))
        .createOrReplace();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService(),
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        pod -> Arrays.asList("assistant started", "index ready")
    );

    List<String> logs = backend.getLogs(key).get();

    assertEquals(Arrays.asList("assistant started", "index ready"), logs);
  }

  @Test
  void shouldPublishHelperLogSnapshotsAsRuntimeLogEvents() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        eventPublisher,
        pod -> Arrays.asList("assistant started", "index ready")
    );

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(KubernetesRuntimeTestSupport.readyPod(KubernetesRuntimeTestSupport.helperSpec()))
        .createOrReplace();

    backend.refreshHelperLogStreaming(RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.KUBERNETES));

    List<DevRuntimeEvent> events = eventPublisher.events();
    assertEquals(1, events.size());
    assertEquals(DevRuntimeEventType.RUNTIME_LOG_OUTPUT, events.get(0).getType());
    assertEquals(Arrays.asList("assistant started", "index ready"), events.get(0).getLogLines());
  }

  @Test
  void shouldPublishSameSnapshotAgainAfterResettingLogStream() {
    KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
    KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService = new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    KubernetesDevHelperRuntimeBackend backend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        properties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
        eventPublisher,
        pod -> Arrays.asList("assistant started", "index ready")
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.KUBERNETES);

    backend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(KubernetesRuntimeTestSupport.readyPod(KubernetesRuntimeTestSupport.helperSpec()))
        .createOrReplace();

    backend.refreshHelperLogStreaming(key);
    backend.resetLogStream(key);
    backend.refreshHelperLogStreaming(key);

    List<DevRuntimeEvent> events = eventPublisher.events();
    assertEquals(2, events.size());
    assertEquals(Arrays.asList("assistant started", "index ready"), events.get(1).getLogLines());
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

  private Deployment helperDeployment() {
    return server.getClient().apps().deployments()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.deploymentName(RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec())))
        .get();
  }

  private void failNextDeploymentPut() {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec());
    server.expect()
        .put()
        .withPath(
            "/apis/apps/v1/namespaces/"
                + KubernetesRuntimeTestSupport.namespace()
                + "/deployments/"
                + KubernetesResourceNameFactory.deploymentName(key)
        )
        .andReturn(500, "apply failed")
        .always();
  }

  private static CommandRequest lastRequest(
      KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService
  ) {
    return commandService.requests().get(commandService.requests().size() - 1);
  }

  private static MetadataSnapshotRejectedEvent retryableRejection(CommandRequest request) {
    return new MetadataSnapshotRejectedEvent(
        request.getAppName(),
        request.getRuntimeGeneration(),
        request.getHelperInstanceId(),
        request.getHelperStartedAtEpochMillis().longValue(),
        request.getAppIncarnationId(),
        request.getRefreshOperationId(),
        "等待开发助手响应超时",
        CommandFailureType.RESPONSE_TIMEOUT,
        KubernetesRuntimeTestSupport.runtimeTarget(request.getAppName())
    );
  }
}
