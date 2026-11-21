package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.impl.AppDevHelperContainerServiceImpl;
import com.ouroboros.dev.app.service.runtime.DevHelperRuntimeSpecFactory;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

@ExtendWith(MockitoExtension.class)
class AppDevHelperContainerServiceRuntimeDelegationTest {
  private static final String APP_NAME_LABEL = "OUROBOROS_APP_NAME";
  private static final String DEV_HELPER_LABEL = "ouroboros-dev-helper";
  private static final String APP_INCARNATION_ID = "incarnation-a";

  @Mock
  private DevRuntimeService devRuntimeService;

  @Mock
  private DevHelperRuntimeSpecFactory runtimeSpecFactory;

  @Mock
  private DevHelperCommandService devHelperCommandService;

  @Mock
  private ContainerService containerService;

  @Mock
  private DevContainerConfig devContainerConfig;

  @Mock
  private AppDevHelperLogService appDevHelperLogService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private InMemoryRuntimeStateStore runtimeStateStore;
  private Map<String, Consumer<CommandResult>> commandListeners;
  private AppDevHelperContainerServiceImpl service;

  @BeforeEach
  void setUp() {
    runtimeStateStore = new InMemoryRuntimeStateStore();
    commandListeners = new ConcurrentHashMap<String, Consumer<CommandResult>>();
    doAnswer(invocation -> {
      commandListeners.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(devHelperCommandService).registerCommandResultListener(any(String.class), any());
    lenient().when(devRuntimeService.recover(any(String.class), eq(RuntimeKind.DEV_HELPER)))
        .thenReturn(Try.success(Collections.<RuntimeInstance>emptyList()));
    lenient().when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Try.success(Collections.emptyList()));
    service = new AppDevHelperContainerServiceImpl(
        workspaceContext(),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        appDevHelperLogService,
        eventPublisher
    );
  }

  @Test
  void shouldRequestDockerMetadataWithCurrentGenerationWhenHelperComesOnline() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STARTING,
        Long.valueOf(7L)
    ));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runtimeStateStore.findInstance(key).orElseThrow()));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));

    ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(devHelperCommandService).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        paramsCaptor.capture()
    );
    assertEquals(Long.valueOf(7L), paramsCaptor.getValue().get("runtimeGeneration"));
    assertEquals("helper-a", paramsCaptor.getValue().get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY));
    assertEquals(Long.valueOf(1000L), paramsCaptor.getValue().get(
        MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY
    ));
    assertEquals(APP_INCARNATION_ID, paramsCaptor.getValue().get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY));
    assertFalse(paramsCaptor.getValue().get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY).toString().isEmpty());
  }

  @Test
  void shouldPublishTupleBoundRejectionWhenDockerRefreshCommandCannotBeSent() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));
    when(devHelperCommandService.exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    )).thenThrow(new IllegalStateException("mq unavailable"));

    IllegalStateException failure = assertThrows(
        IllegalStateException.class,
        () -> commandListeners.get(Commands.KNOCK_KNOCK.getCommand())
            .accept(helperOnline("crm", "helper-a", 1000L))
    );

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher, times(6)).publishEvent(eventCaptor.capture());
    assertTrue(eventCaptor.getAllValues().get(1) instanceof MetadataRefreshRequestedEvent);
    assertTrue(eventCaptor.getAllValues().get(2) instanceof MetadataSnapshotRejectedEvent);
    assertTrue(eventCaptor.getAllValues().get(3) instanceof MetadataRefreshRequestedEvent);
    assertTrue(eventCaptor.getAllValues().get(4) instanceof MetadataSnapshotRejectedEvent);
    MetadataRefreshRequestedEvent request = (MetadataRefreshRequestedEvent) eventCaptor.getAllValues().get(1);
    MetadataSnapshotRejectedEvent rejection = (MetadataSnapshotRejectedEvent) eventCaptor.getAllValues().get(2);
    MetadataRefreshRequestedEvent retryRequest = (MetadataRefreshRequestedEvent) eventCaptor.getAllValues().get(3);
    MetadataSnapshotRejectedEvent retryRejection = (MetadataSnapshotRejectedEvent) eventCaptor.getAllValues().get(4);
    assertEquals("mq unavailable", failure.getMessage());
    assertEquals(request.getAppName(), rejection.getAppName());
    assertEquals(request.getRuntimeGeneration(), rejection.getRuntimeGeneration());
    assertEquals(request.getHelperIdentity().getInstanceId(), rejection.getHelperInstanceId());
    assertEquals(request.getHelperIdentity().getStartedAtEpochMillis(), rejection.getHelperStartedAtEpochMillis());
    assertEquals(request.getHelperIdentity().getAppIncarnationId(), rejection.getAppIncarnationId());
    assertEquals(request.getRefreshOperationId(), rejection.getRefreshOperationId());
    assertEquals(request.getRuntimeTarget(), rejection.getRuntimeTarget());
    assertEquals("mq unavailable", rejection.getReason());
    assertEquals(CommandFailureType.TRANSPORT, rejection.getFailureType());
    assertFalse(request.getRefreshOperationId().equals(retryRequest.getRefreshOperationId()));
    assertEquals(retryRequest.getRefreshOperationId(), retryRejection.getRefreshOperationId());
    assertEquals(CommandFailureType.TRANSPORT, retryRejection.getFailureType());
    verify(devHelperCommandService, times(2)).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.UNKNOWN, service.getStatus("crm").get().get("status"));
    assertEquals(Boolean.FALSE, service.getStatus("crm").get().get("refreshPending"));
  }

  @Test
  void shouldRecoverWhenSecondDockerRefreshSendSucceeds() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));
    when(devHelperCommandService.exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    )).thenThrow(new IllegalStateException("mq unavailable"))
        .thenReturn(CompletableFuture.completedFuture(new CommandResult()));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));

    String retryOperation = pendingRefreshOperation("crm");
    assertTrue(retryOperation != null && !retryOperation.isEmpty());
    assertEquals(ContainerStatus.LAUNCHING, service.getStatus("crm").get().get("status"));
    verify(devHelperCommandService, times(2)).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(7L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        retryOperation,
        1L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));

    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.RUNNING, service.getStatus("crm").get().get("status"));
  }

  @Test
  void supersededHelperShouldNotOverwriteCurrentRefreshOperation() throws Exception {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    CountDownLatch firstStatusLookup = new CountDownLatch(1);
    CountDownLatch releaseFirstStatusLookup = new CountDownLatch(1);
    AtomicInteger statusPolls = new AtomicInteger();
    when(devRuntimeService.getStatus(key)).thenAnswer(invocation -> {
      if (statusPolls.getAndIncrement() == 0) {
        firstStatusLookup.countDown();
        if (!releaseFirstStatusLookup.await(5L, TimeUnit.SECONDS)) {
          throw new IllegalStateException("timed out waiting to supersede helper");
        }
      }
      return Try.success(running);
    });

    CompletableFuture<Void> oldHelperOnline = CompletableFuture.runAsync(
        () -> commandListeners.get(Commands.KNOCK_KNOCK.getCommand())
            .accept(helperOnline("crm", "helper-a", 1000L))
    );
    assertTrue(firstStatusLookup.await(5L, TimeUnit.SECONDS));
    try {
      commandListeners.get(Commands.KNOCK_KNOCK.getCommand())
          .accept(helperOnline("crm", "helper-b", 2000L));
    } finally {
      releaseFirstStatusLookup.countDown();
    }
    oldHelperOnline.get(5L, TimeUnit.SECONDS);

    ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(devHelperCommandService).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        paramsCaptor.capture()
    );
    assertEquals("helper-b", paramsCaptor.getValue().get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY));
    assertEquals(
        paramsCaptor.getValue().get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY),
        pendingRefreshOperation("crm")
    );
  }

  @Test
  void stoppedRuntimeShouldNotBeReactivatedByInFlightHandshake() throws Exception {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    CountDownLatch statusLookupStarted = new CountDownLatch(1);
    CountDownLatch releaseStatusLookup = new CountDownLatch(1);
    when(devRuntimeService.getStatus(key)).thenAnswer(invocation -> {
      statusLookupStarted.countDown();
      if (!releaseStatusLookup.await(5L, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out waiting for stop");
      }
      return Try.success(running);
    });
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STOPPED,
        Long.valueOf(7L)
    )));

    CompletableFuture<Void> handshake = CompletableFuture.runAsync(
        () -> commandListeners.get(Commands.KNOCK_KNOCK.getCommand())
            .accept(helperOnline("crm", "helper-a", 1000L))
    );
    assertTrue(statusLookupStarted.await(5L, TimeUnit.SECONDS));
    try {
      assertTrue(service.stop("crm").isSuccess());
    } finally {
      releaseStatusLookup.countDown();
    }
    handshake.get(5L, TimeUnit.SECONDS);

    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeActivatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(MetadataRefreshRequestedEvent.class));
  }

  @Test
  void deletedRuntimeShouldNotBeReactivatedByInFlightHandshake() throws Exception {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    CountDownLatch statusLookupStarted = new CountDownLatch(1);
    CountDownLatch releaseStatusLookup = new CountDownLatch(1);
    when(devRuntimeService.getStatus(key)).thenAnswer(invocation -> {
      statusLookupStarted.countDown();
      if (!releaseStatusLookup.await(5L, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out waiting for delete");
      }
      return Try.success(running);
    });
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STOPPED,
        Long.valueOf(7L)
    )));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));

    CompletableFuture<Void> handshake = CompletableFuture.runAsync(
        () -> commandListeners.get(Commands.KNOCK_KNOCK.getCommand())
            .accept(helperOnline("crm", "helper-a", 1000L))
    );
    assertTrue(statusLookupStarted.await(5L, TimeUnit.SECONDS));
    try {
      assertTrue(service.delete("crm").isSuccess());
    } finally {
      releaseStatusLookup.countDown();
    }
    handshake.get(5L, TimeUnit.SECONDS);

    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeActivatedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(MetadataRefreshRequestedEvent.class));
  }

  @Test
  void shouldLeaveKubernetesOnlineHandshakeToItsRuntimeBackend() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance starting = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.KUBERNETES,
        RuntimeStatus.STARTING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, starting);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(starting));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));

    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
  }

  @Test
  void shouldRejectLegacyHelperHandshakeWithoutRequestingMetadata() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setSuccess(true)
        .setData(Collections.singletonMap("version", "1.0.0")));

    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
    verify(appDevHelperLogService).appendSystemLog(
        "crm",
        "开发助手不支持当前元数据快照协议，请更新助手镜像"
    );
  }

  @Test
  void shouldRejectOldHelperIncarnationAfterAppIsRecreated() {
    RuntimeSpec oldSpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/helper:v1",
        Collections.singletonMap(APP_NAME_LABEL, "crm"),
        "incarnation-old"
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(oldSpec);
    RuntimeInstance oldInstance = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STARTING,
        Long.valueOf(7L)
    );
    runtimeStateStore.saveSpec(key, oldSpec);
    runtimeStateStore.saveInstance(key, oldInstance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(oldInstance));
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STOPPED,
        Long.valueOf(7L)
    )));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
        helperOnline("crm", "helper-a", 1000L, "incarnation-old")
    );
    assertTrue(service.delete("crm").isSuccess());

    runtimeStateStore.delete(key);
    RuntimeSpec recreatedSpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/helper:v1",
        Collections.singletonMap(APP_NAME_LABEL, "crm"),
        "incarnation-new"
    );
    RuntimeInstance recreatedInstance = runtimeInstance(
        "rt-crm-dev-helper-new",
        BackendType.DOCKER,
        RuntimeStatus.STOPPED,
        Long.valueOf(1L)
    );
    RuntimeInstance startedInstance = runtimeInstance(
        "rt-crm-dev-helper-new",
        BackendType.DOCKER,
        RuntimeStatus.STARTING,
        Long.valueOf(1L)
    );
    runtimeStateStore.saveSpec(key, recreatedSpec);
    runtimeStateStore.saveInstance(key, recreatedInstance);
    when(devRuntimeService.getStatus(key))
        .thenReturn(Try.success(recreatedInstance), Try.success(startedInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(startedInstance));
    assertTrue(service.start("crm").isSuccess());
    org.mockito.Mockito.clearInvocations(devHelperCommandService, appDevHelperLogService, eventPublisher);

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
        helperOnline("crm", "helper-a", 1000L, "incarnation-old")
    );

    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
    verify(appDevHelperLogService).appendSystemLog(
        "crm",
        "忽略不属于当前应用实例的开发助手协议握手"
    );

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
        helperOnline("crm", "helper-a", 1000L, "incarnation-new")
    );
    verify(devHelperCommandService).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        org.mockito.ArgumentMatchers.argThat(params ->
            "incarnation-new".equals(params.get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY))
        )
    );
  }

  @Test
  void shouldIncludeImageStateForDockerHelper() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/helper:v1");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER)));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/helper:v1")))
        .thenReturn(Try.success(Collections.singletonList("registry.example.com/helper:v1")));

    Map<String, Object> payload = service.getStatus("crm").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("v1", imageState.getTag());
    assertEquals(Boolean.FALSE, imageState.getUpdateRecommended());
    assertEquals("registry.example.com/helper:v1", payload.get("imageReference"));
    assertEquals("registry.example.com/helper", payload.get("imageRepository"));
    assertEquals("v1", payload.get("imageTag"));
    assertFalse(payload.containsKey("logTopic"));
    assertFalse(payload.containsKey("logSubscribeDestination"));
  }

  @Test
  void shouldRecommendUpdateForDockerHelperWhenLocalImageMissing() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER)));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));

    Map<String, Object> payload = service.getStatus("crm").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals(Boolean.TRUE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldRecommendUpdateForDockerHelperWhenConfiguredBaseImageChanges() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/helper:v2");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER)));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/helper:v2")))
        .thenReturn(Try.success(java.util.Arrays.asList("registry.example.com/helper:v1", "registry.example.com/helper:v2")));

    Map<String, Object> payload = service.getStatus("crm").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("registry.example.com/helper:v1", imageState.getReference());
    assertEquals(Boolean.TRUE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldRecommendUpdateForDockerHelperWithDigestStyleTagEvenWhenLocalImageExists() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/helper:stable");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:stable");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER));
    RuntimeInstance inspected = helperRuntime("crm", container("rt-crm-dev-helper", "sha256:deadbeefcafebabe"));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(inspected));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/helper:stable")))
        .thenReturn(Try.success(java.util.Arrays.asList(
            "sha256:deadbeefcafebabe",
            "registry.example.com/helper:stable"
        )));

    Map<String, Object> payload = service.getStatus("crm").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("deadbeefcafebabe", imageState.getTag());
    assertEquals(Boolean.TRUE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldKeepRuntimeIdentityStableWhenDockerHelperImageIsReportedOutdated() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));

    Map<String, Object> first = service.getStatus("crm").get();
    Map<String, Object> second = service.getStatus("crm").get();
    Map<String, Object> third = service.getStatus("crm").get();

    assertEquals("rt-crm-dev-helper", first.get("containerId"));
    assertEquals(first.get("containerId"), second.get("containerId"));
    assertEquals(second.get("containerId"), third.get("containerId"));
    assertEquals(ContainerStatus.RUNNING, first.get("status"));
    assertEquals(ContainerStatus.RUNNING, second.get("status"));
    assertEquals(ContainerStatus.RUNNING, third.get("status"));
    assertEquals(Boolean.TRUE, ((RuntimeImageViewDTO) first.get("imageState")).getUpdateRecommended());
    verify(devRuntimeService, times(3)).getStatus(key);
    verify(devRuntimeService, never()).delete(key);
    verify(devRuntimeService, never()).create(org.mockito.ArgumentMatchers.any());
    verify(devRuntimeService, never()).start(key);
  }

  @Test
  void shouldExposeLaunchingStatusDuringRestartUntilRefreshCompletes() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance runningInstance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runningInstance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runningInstance));
    when(devRuntimeService.restart(key)).thenReturn(Try.success(runningInstance));
    when(devRuntimeService.getLogs(key)).thenReturn(Try.success(java.util.Arrays.asList("assistant restarted", "index ready")));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));

    Try<Void> restartResult = service.restart("crm");
    Map<String, Object> launching = service.getStatus("crm").get();

    assertTrue(restartResult.isSuccess());
    assertEquals(com.ouroboros.container.ContainerStatus.LAUNCHING, launching.get("status"));
    assertTrue(launching.containsKey("lastLoadTime"));
    assertEquals(null, launching.get("lastLoadTime"));

    ReflectionTestUtils.invokeMethod(service, "handleRefreshResult", new CommandResult()
        .setAppName("crm")
        .setSuccess(true));

    Map<String, Object> stillLaunching = service.getStatus("crm").get();
    assertEquals(com.ouroboros.container.ContainerStatus.LAUNCHING, stillLaunching.get("status"));

    ReflectionTestUtils.invokeMethod(service, "handleRefreshResult", new com.ouroboros.devhelper.command.CommandResult()
        .setAppName("crm")
        .setSuccess(true)
        .setRuntimeGeneration(Long.valueOf(2L)));

    Map<String, Object> rejectedStillLaunching = service.getStatus("crm").get();
    assertEquals(com.ouroboros.container.ContainerStatus.LAUNCHING, rejectedStillLaunching.get("status"));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-b", 2000L));
    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-b",
        2000L,
        APP_INCARNATION_ID,
        pendingRefreshOperation("crm"),
        1L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));

    Map<String, Object> running = service.getStatus("crm").get();
    assertEquals(com.ouroboros.container.ContainerStatus.RUNNING, running.get("status"));
    verify(appDevHelperLogService).resetRuntimeOutput("crm");
    verify(devRuntimeService).resetLogStream(key);
    verify(appDevHelperLogService).appendRuntimeOutput("crm", java.util.Arrays.asList("assistant restarted", "index ready"));
    verify(eventPublisher).publishEvent(any(com.ouroboros.dev.app.event.DevHelperStatusChangedEvent.class));
  }

  @Test
  void shouldOnlyCompleteTheCurrentDockerReloadOperation() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    String initialOperation = pendingRefreshOperation("crm");

    assertTrue(service.reload("crm").isSuccess());
    String reloadOperation = pendingRefreshOperation("crm");
    assertFalse(initialOperation.equals(reloadOperation));

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        initialOperation,
        1L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));
    assertEquals(reloadOperation, pendingRefreshOperation("crm"));

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        reloadOperation,
        2L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));
    assertEquals(null, pendingRefreshOperation("crm"));
  }

  @Test
  void shouldRetryDockerResponseTimeoutOnceAndIgnoreLateFirstAttempt() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    String firstOperation = pendingRefreshOperation("crm");

    commandListeners.get(Commands.REFRESH_METADATA.getCommand()).accept(refreshFailure(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        firstOperation,
        CommandFailureType.RESPONSE_TIMEOUT,
        "等待开发助手响应超时"
    ));

    String retryOperation = pendingRefreshOperation("crm");
    assertFalse(firstOperation.equals(retryOperation));
    verify(devHelperCommandService, times(2)).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotRejected", retryableRejection(firstOperation));
    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        firstOperation,
        1L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));
    assertEquals(retryOperation, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.LAUNCHING, service.getStatus("crm").get().get("status"));

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        retryOperation,
        2L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));
    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.RUNNING, service.getStatus("crm").get().get("status"));
  }

  @Test
  void shouldNotDuplicateProviderFirstRetryAndShouldFailAfterRetryRejection() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    String firstOperation = pendingRefreshOperation("crm");

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotRejected", retryableRejection(firstOperation));
    String retryOperation = pendingRefreshOperation("crm");
    commandListeners.get(Commands.REFRESH_METADATA.getCommand()).accept(refreshFailure(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        firstOperation,
        CommandFailureType.RESPONSE_TIMEOUT,
        "等待开发助手响应超时"
    ));

    assertFalse(firstOperation.equals(retryOperation));
    assertEquals(retryOperation, pendingRefreshOperation("crm"));
    verify(devHelperCommandService, times(2)).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotRejected", retryableRejection(retryOperation));
    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.UNKNOWN, service.getStatus("crm").get().get("status"));
    assertEquals(Boolean.FALSE, service.getStatus("crm").get().get("refreshPending"));

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        retryOperation,
        2L,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));
    assertEquals(ContainerStatus.UNKNOWN, service.getStatus("crm").get().get("status"));
  }

  @Test
  void shouldOnlyCloseCurrentDockerRefreshOnFailure() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    String initialOperation = pendingRefreshOperation("crm");
    assertTrue(service.reload("crm").isSuccess());
    String currentOperation = pendingRefreshOperation("crm");

    commandListeners.get(Commands.REFRESH_METADATA.getCommand()).accept(refreshFailure(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        initialOperation
    ));
    assertEquals(currentOperation, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.LAUNCHING, service.getStatus("crm").get().get("status"));

    commandListeners.get(Commands.REFRESH_METADATA.getCommand()).accept(refreshFailure(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        currentOperation
    ));

    Map<String, Object> status = service.getStatus("crm").get();
    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.UNKNOWN, status.get("status"));
    assertEquals(Boolean.FALSE, status.get("refreshPending"));
  }

  @Test
  void shouldClearCurrentDockerRefreshWhenProviderRejectsSnapshot() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    String operationId = pendingRefreshOperation("crm");

    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotRejected", new MetadataSnapshotRejectedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        operationId,
        "malformed snapshot",
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    ));

    Map<String, Object> status = service.getStatus("crm").get();
    assertEquals(null, pendingRefreshOperation("crm"));
    assertEquals(ContainerStatus.UNKNOWN, status.get("status"));
    assertEquals(Boolean.FALSE, status.get("refreshPending"));
    verify(appDevHelperLogService).appendSystemLog(
        "crm",
        "开发助手元数据快照发布被拒绝: malformed snapshot"
    );
  }

  @Test
  void shouldNotRecommendUpdateForKubernetesHelperWithoutLocalCatalog() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:latest");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES)));

    Map<String, Object> payload = service.getStatus("crm").get();
    verify(devRuntimeService, never()).listImages(any(), any());

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertFalse(Boolean.TRUE.equals(imageState.getUpdateRecommended()));
  }

  @Test
  void shouldStopHelperBeforeDeletingIt() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));

    Try<Void> result = service.delete("crm");

    assertTrue(result.isSuccess());
    verify(appDevHelperLogService).clear("crm");
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).stop(key);
    order.verify(devRuntimeService).delete(key);
  }

  @Test
  void shouldDeleteAllHelperRuntimeRecordsForSameApp() {
    RuntimeSpec dockerSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeSpec kubernetesSpec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:v1");
    RuntimeIdentityKey dockerKey = RuntimeIdentityKey.from(dockerSpec);
    RuntimeIdentityKey kubernetesKey = RuntimeIdentityKey.from(kubernetesSpec);
    runtimeStateStore.saveSpec(dockerKey, dockerSpec);
    runtimeStateStore.saveInstance(dockerKey, runtimeInstance("rt-crm-dev-helper-docker", BackendType.DOCKER, RuntimeStatus.RUNNING));
    runtimeStateStore.saveSpec(kubernetesKey, kubernetesSpec);
    runtimeStateStore.saveInstance(kubernetesKey, runtimeInstance("rt-crm-dev-helper-k8s", BackendType.KUBERNETES, RuntimeStatus.RUNNING));
    when(devRuntimeService.stop(dockerKey)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper-docker", BackendType.DOCKER, RuntimeStatus.STOPPED)));
    when(devRuntimeService.stop(kubernetesKey)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper-k8s", BackendType.KUBERNETES, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(dockerKey)).thenReturn(Try.success(null));
    when(devRuntimeService.delete(kubernetesKey)).thenReturn(Try.success(null));

    Try<Void> result = service.delete("crm");

    assertTrue(result.isSuccess());
    verify(devRuntimeService).stop(dockerKey);
    verify(devRuntimeService).delete(dockerKey);
    verify(devRuntimeService).stop(kubernetesKey);
    verify(devRuntimeService).delete(kubernetesKey);
  }

  @Test
  void shouldClearHelperLogsBeforeCreatingNewRuntime() {
    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeInstance createdInstance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.STOPPED);
    when(runtimeSpecFactory.create(request)).thenReturn(spec);
    when(devRuntimeService.create(spec)).thenReturn(Try.success(createdInstance));

    String runtimeId = service.create(request).get();

    assertEquals("rt-crm-dev-helper", runtimeId);
    var order = inOrder(appDevHelperLogService, devRuntimeService);
    order.verify(appDevHelperLogService).clear("crm");
    order.verify(devRuntimeService).create(spec);
  }

  @Test
  void shouldAcceptHandshakeWhileFreshKubernetesRuntimeIsBeingCreatedAndStarted() {
    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance starting = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.KUBERNETES,
        RuntimeStatus.STARTING,
        Long.valueOf(1L)
    );
    when(runtimeSpecFactory.create(request)).thenReturn(spec);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(starting));
    when(devRuntimeService.create(any(RuntimeSpec.class))).thenAnswer(invocation -> {
      runtimeStateStore.saveSpec(key, spec);
      runtimeStateStore.saveInstance(key, starting);
      commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
          helperOnline("crm", "helper-a", 1000L)
      );
      return Try.success(starting);
    });
    when(devRuntimeService.start(key)).thenReturn(Try.success(starting));

    assertEquals("rt-crm-dev-helper", service.createAndStart(request).get());

    verify(eventPublisher).publishEvent(any(HelperRuntimeActivatedEvent.class));
  }

  @Test
  void shouldReuseStoredIncarnationWithoutRetiringHelperOnCreateRetry() {
    RuntimeSpec storedSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeSpec retrySpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/helper:v1",
        Collections.singletonMap("OUROBOROS_APP_NAME", "crm"),
        "incarnation-retry"
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(storedSpec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, storedSpec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));
    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    clearInvocations(eventPublisher);

    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    when(runtimeSpecFactory.create(request)).thenReturn(retrySpec);
    when(devRuntimeService.create(any(RuntimeSpec.class))).thenReturn(Try.success(running));

    assertEquals("rt-crm-dev-helper", service.create(request).get());

    ArgumentCaptor<RuntimeSpec> specCaptor = ArgumentCaptor.forClass(RuntimeSpec.class);
    verify(devRuntimeService).create(specCaptor.capture());
    assertEquals(
        APP_INCARNATION_ID,
        specCaptor.getValue().getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV)
    );
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeRetiredEvent.class));
  }

  @Test
  void shouldKeepActiveHelperOnEquivalentCreateAndStartRetry() {
    RuntimeSpec storedSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeSpec retrySpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/helper:v1",
        Collections.singletonMap("OUROBOROS_APP_NAME", "crm"),
        "incarnation-retry"
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(storedSpec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, storedSpec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));
    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    clearInvocations(eventPublisher);

    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    when(runtimeSpecFactory.create(request)).thenReturn(retrySpec);
    when(devRuntimeService.create(any(RuntimeSpec.class))).thenReturn(Try.success(running));
    when(devRuntimeService.start(key)).thenReturn(Try.success(running));

    assertEquals("rt-crm-dev-helper", service.createAndStart(request).get());

    verify(devRuntimeService).start(key);
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeRetiredEvent.class));
  }

  @Test
  void shouldKeepActiveHelperOnEquivalentStartRetry() {
    RuntimeSpec storedSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(storedSpec);
    RuntimeInstance running = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, storedSpec);
    runtimeStateStore.saveInstance(key, running);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(running));
    when(devRuntimeService.start(key)).thenReturn(Try.success(running));
    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-a", 1000L));
    clearInvocations(eventPublisher);

    assertTrue(service.start("crm").isSuccess());

    verify(devRuntimeService).start(key);
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeRetiredEvent.class));
  }

  @Test
  void concurrentCreateAndStartShouldNotRetireHelperFromWinningOperation() throws Exception {
    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance stopped = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STOPPED
    );
    RuntimeInstance starting = runtimeInstance(
        "rt-crm-dev-helper",
        BackendType.DOCKER,
        RuntimeStatus.STARTING,
        Long.valueOf(1L)
    );
    CountDownLatch firstStartEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstStart = new CountDownLatch(1);
    AtomicInteger starts = new AtomicInteger();
    when(runtimeSpecFactory.create(request)).thenReturn(spec);
    when(devRuntimeService.create(any(RuntimeSpec.class))).thenAnswer(invocation -> {
      RuntimeInstance current = runtimeStateStore.findInstance(key).orElse(null);
      if (current == null) {
        runtimeStateStore.saveSpec(key, spec);
        runtimeStateStore.saveInstance(key, stopped);
        return Try.success(stopped);
      }
      return Try.success(current);
    });
    when(devRuntimeService.getStatus(key)).thenAnswer(invocation -> Try.success(
        runtimeStateStore.findInstance(key).orElseThrow()
    ));
    when(devRuntimeService.start(key)).thenAnswer(invocation -> {
      if (starts.getAndIncrement() == 0) {
        runtimeStateStore.saveInstance(key, starting);
        commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
            helperOnline("crm", "helper-a", 1000L)
        );
        firstStartEntered.countDown();
        if (!releaseFirstStart.await(5L, TimeUnit.SECONDS)) {
          throw new IllegalStateException("timed out waiting to release first start");
        }
      }
      return Try.success(runtimeStateStore.findInstance(key).orElseThrow());
    });

    CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> service.createAndStart(request).get());
    assertTrue(firstStartEntered.await(5L, TimeUnit.SECONDS));
    CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> service.createAndStart(request).get());
    verify(devRuntimeService, times(1)).create(any(RuntimeSpec.class));
    assertFalse(second.isDone());
    releaseFirstStart.countDown();

    assertEquals("rt-crm-dev-helper", first.get(5L, TimeUnit.SECONDS));
    assertEquals("rt-crm-dev-helper", second.get(5L, TimeUnit.SECONDS));
    verify(eventPublisher, never()).publishEvent(any(HelperRuntimeRetiredEvent.class));
  }

  @Test
  void shouldBindHelperAndPublicationToTheRuntimeWithMatchingIncarnationAndTarget() {
    RuntimeSpec dockerSpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/helper:v1",
        Collections.singletonMap("OUROBOROS_APP_NAME", "crm"),
        "incarnation-old"
    );
    RuntimeSpec kubernetesSpec = runtimeSpec(
        "crm",
        BackendType.KUBERNETES,
        "registry.example.com/helper:v1",
        Collections.singletonMap("OUROBOROS_APP_NAME", "crm"),
        APP_INCARNATION_ID
    );
    RuntimeIdentityKey dockerKey = RuntimeIdentityKey.from(dockerSpec);
    RuntimeIdentityKey kubernetesKey = RuntimeIdentityKey.from(kubernetesSpec);
    RuntimeInstance docker = runtimeInstance(
        "rt-crm-dev-helper-docker",
        BackendType.DOCKER,
        RuntimeStatus.RUNNING,
        Long.valueOf(2L)
    );
    RuntimeInstance kubernetes = runtimeInstance(
        "rt-crm-dev-helper-k8s",
        BackendType.KUBERNETES,
        RuntimeStatus.STARTING,
        Long.valueOf(3L)
    );
    runtimeStateStore.saveSpec(dockerKey, dockerSpec);
    runtimeStateStore.saveInstance(dockerKey, docker);
    runtimeStateStore.saveSpec(kubernetesKey, kubernetesSpec);
    runtimeStateStore.saveInstance(kubernetesKey, kubernetes);
    when(devRuntimeService.getStatus(kubernetesKey)).thenReturn(Try.success(kubernetes));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(helperOnline("crm", "helper-k8s", 2000L));

    ArgumentCaptor<Object> activationCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(activationCaptor.capture());
    HelperRuntimeActivatedEvent activation = (HelperRuntimeActivatedEvent) activationCaptor.getValue();
    assertEquals(BackendType.KUBERNETES.name(), activation.getRuntimeTarget().getBackendType());
    assertEquals("rt-crm-dev-helper-k8s", activation.getRuntimeTarget().getRuntimeId());
    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );

    clearInvocations(eventPublisher);
    ReflectionTestUtils.invokeMethod(service, "handleMetadataSnapshotPublished", new MetadataSnapshotPublishedEvent(
        "crm",
        Long.valueOf(3L),
        "helper-k8s",
        2000L,
        APP_INCARNATION_ID,
        "k8s-operation",
        1L,
        runtimeTarget(BackendType.KUBERNETES, "rt-crm-dev-helper-k8s")
    ));

    verify(eventPublisher).publishEvent(any(com.ouroboros.dev.app.event.DevHelperStatusChangedEvent.class));
  }

  @Test
  void shouldRecoverKubernetesRuntimeBeforeValidatingRawHandshake() {
    RuntimeSpec recoveredSpec = runtimeSpec(
        "crm",
        BackendType.KUBERNETES,
        "registry.example.com/helper:v1"
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(recoveredSpec);
    RuntimeInstance recoveredInstance = runtimeInstance(
        "devrt-dev-helper-crm",
        BackendType.KUBERNETES,
        RuntimeStatus.STARTING,
        Long.valueOf(1L)
    );
    when(devRuntimeService.recover("crm", RuntimeKind.DEV_HELPER)).thenAnswer(invocation -> {
      runtimeStateStore.saveSpec(key, recoveredSpec);
      runtimeStateStore.saveInstance(key, recoveredInstance);
      return Try.success(Collections.singletonList(recoveredInstance));
    });
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(recoveredInstance));

    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(
        helperOnline("crm", "helper-k8s", 2000L)
    );

    verify(devRuntimeService).recover("crm", RuntimeKind.DEV_HELPER);
    ArgumentCaptor<Object> activationCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(activationCaptor.capture());
    HelperRuntimeActivatedEvent activation = (HelperRuntimeActivatedEvent) activationCaptor.getValue();
    assertEquals(BackendType.KUBERNETES.name(), activation.getRuntimeTarget().getBackendType());
    assertEquals("devrt-dev-helper-crm", activation.getRuntimeTarget().getRuntimeId());
    verify(devHelperCommandService, never()).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        anyMap()
    );
  }

  @Test
  void shouldRestartHelperAfterImageUpdateWhenRuntimeWasStillStarting() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance startingInstance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.STARTING);
    RuntimeInstance createdInstance = runtimeInstance("rt-crm-dev-helper-new", BackendType.DOCKER, RuntimeStatus.STOPPED);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, startingInstance);
    when(runtimeSpecFactory.recreate(spec)).thenReturn(spec);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(startingInstance));
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));
    when(devRuntimeService.create(spec)).thenReturn(Try.success(createdInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.RUNNING)));

    String runtimeId = service.updateImage("crm").get();

    assertEquals("rt-crm-dev-helper-new", runtimeId);
    verify(appDevHelperLogService, times(2)).clear("crm");
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).getStatus(key);
    order.verify(devRuntimeService).stop(key);
    order.verify(devRuntimeService).delete(key);
    order.verify(devRuntimeService).create(spec);
    order.verify(devRuntimeService).start(key);
  }

  @Test
  void shouldFailImageUpdateWhenHelperDoesNotExist() {
    Try<String> result = service.updateImage("crm");

    assertTrue(result.isFailure());
    assertEquals("助手容器不存在！", result.getCause().getMessage());
    verify(devRuntimeService, never()).create(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldRecoverHelperRuntimeFromExistingDockerContainerWhenStoreIsEmpty() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/helper:v1");
    Map<String, String> labels = recoveredHelperLabels(
        DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_DEFAULT,
        null,
        "CRM Helper"
    );
    Container container = helperContainer("crm", "cid-recovered", "registry.example.com/helper:v1", ContainerStatus.RUNNING, labels);
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER);
    RuntimeSpec recoveredSpec = runtimeSpec("crm", BackendType.DOCKER, container.getImage(), labels);
    RuntimeInstance recoveredInstance = helperRuntime("crm", container);
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/helper:v1")))
        .thenReturn(Try.success(Collections.singletonList("registry.example.com/helper:v1")));
    when(devRuntimeService.recover("crm", RuntimeKind.DEV_HELPER)).thenAnswer(invocation -> {
      runtimeStateStore.saveSpec(key, recoveredSpec);
      runtimeStateStore.saveInstance(key, recoveredInstance);
      return Try.success(Collections.singletonList(recoveredInstance));
    });
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(recoveredInstance));

    Map<String, Object> payload = service.getStatus("crm").get();

    assertEquals(ContainerStatus.RUNNING, payload.get("status"));
    assertEquals("cid-recovered", payload.get("containerId"));
    assertEquals("ouroboros-dev-helper-crm", payload.get("containerName"));
    assertTrue(runtimeStateStore.findSpec(key).isPresent());
    assertTrue(runtimeStateStore.findInstance(key).isPresent());
    assertEquals("registry.example.com/helper:v1", runtimeStateStore.findSpec(key).get().getImage());
    assertEquals(
        DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_DEFAULT,
        runtimeStateStore.findSpec(key).get().getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL)
    );
    verify(devRuntimeService).recover("crm", RuntimeKind.DEV_HELPER);
  }

  @Test
  void shouldPreserveRecoveredHelperImageSourceLabelsForUpdateImageFlow() {
    Map<String, String> labels = recoveredHelperLabels(
        DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_EXPLICIT,
        "registry.example.com/custom-helper:v9",
        "CRM Helper"
    );
    Container container = helperContainer("crm", "cid-recovered", "registry.example.com/custom-helper:v9", ContainerStatus.RUNNING, labels);
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER);
    RuntimeInstance runningInstance = helperRuntime("crm", container);
    RuntimeSpec recoveredRuntimeSpec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/custom-helper:v9",
        labels
    );
    RuntimeSpec recreatedSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/custom-helper:v10", labels);
    RuntimeInstance recreatedInstance = runtimeInstance("cid-new", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeInstance restartedInstance = runtimeInstance("cid-new", BackendType.DOCKER, RuntimeStatus.RUNNING);

    when(devRuntimeService.recover("crm", RuntimeKind.DEV_HELPER)).thenAnswer(invocation -> {
      runtimeStateStore.saveSpec(key, recoveredRuntimeSpec);
      runtimeStateStore.saveInstance(key, runningInstance);
      return Try.success(Collections.singletonList(runningInstance));
    });
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runningInstance));
    when(runtimeSpecFactory.recreate(any(RuntimeSpec.class))).thenReturn(recreatedSpec);
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance("cid-recovered", BackendType.DOCKER, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));
    when(devRuntimeService.create(recreatedSpec)).thenReturn(Try.success(recreatedInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(restartedInstance));

    String runtimeId = service.updateImage("crm").get();

    ArgumentCaptor<RuntimeSpec> recoveredSpecCaptor = ArgumentCaptor.forClass(RuntimeSpec.class);
    verify(runtimeSpecFactory).recreate(recoveredSpecCaptor.capture());
    RuntimeSpec recoveredSpec = recoveredSpecCaptor.getValue();

    assertEquals("cid-new", runtimeId);
    assertEquals("registry.example.com/custom-helper:v9", recoveredSpec.getImage());
    assertEquals(
        DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_EXPLICIT,
        recoveredSpec.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL)
    );
    assertEquals(
        "registry.example.com/custom-helper:v9",
        recoveredSpec.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL)
    );
    assertEquals("CRM Helper", recoveredSpec.getLabels().get(DEV_HELPER_LABEL));
    assertEquals("crm", recoveredSpec.getLabels().get(APP_NAME_LABEL));
  }

  @Test
  void shouldRecreateHelperFromStoredSpecInsteadOfEmptyRequestDefaults() {
    RuntimeSpec storedSpec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/custom-helper:v9");
    RuntimeSpec recreatedSpec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/custom-helper:v10");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(storedSpec);
    RuntimeInstance runningInstance = runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES, RuntimeStatus.RUNNING);
    RuntimeInstance createdInstance = runtimeInstance("rt-crm-dev-helper-new", BackendType.KUBERNETES, RuntimeStatus.STOPPED);
    runtimeStateStore.saveSpec(key, storedSpec);
    runtimeStateStore.saveInstance(key, runningInstance);
    when(runtimeSpecFactory.recreate(storedSpec)).thenReturn(recreatedSpec);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(runningInstance));
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));
    when(devRuntimeService.create(recreatedSpec)).thenReturn(Try.success(createdInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES, RuntimeStatus.RUNNING)));

    String runtimeId = service.updateImage("crm").get();

    assertEquals("rt-crm-dev-helper-new", runtimeId);
    verify(runtimeSpecFactory).recreate(storedSpec);
    verify(runtimeSpecFactory, never()).create(org.mockito.ArgumentMatchers.any());
    verify(devRuntimeService).create(recreatedSpec);
  }

  @Test
  void shouldReportRunningWhenRuntimeIsRunningEvenBeforeRefreshTimestampArrives() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:latest");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-dev-helper", BackendType.KUBERNETES);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));

    Map<String, Object> payload = service.getStatus("crm").get();
    verify(devRuntimeService, never()).listImages(any(), any());

    assertEquals(com.ouroboros.container.ContainerStatus.RUNNING, payload.get("status"));
    assertEquals(Long.valueOf(2L), payload.get("runtimeGeneration"));
    assertEquals(Boolean.FALSE, payload.get("refreshPending"));
  }

  @Test
  void shouldPreferRunningHelperRecordWhenSameAppHasMultipleRuntimeRecords() {
    RuntimeSpec dockerSpec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeSpec kubernetesSpec = runtimeSpec("crm", BackendType.KUBERNETES, "registry.example.com/helper:v1");
    RuntimeIdentityKey dockerKey = RuntimeIdentityKey.from(dockerSpec);
    RuntimeIdentityKey kubernetesKey = RuntimeIdentityKey.from(kubernetesSpec);
    runtimeStateStore.saveSpec(dockerKey, dockerSpec);
    runtimeStateStore.saveInstance(dockerKey, runtimeInstance("rt-crm-dev-helper-docker", BackendType.DOCKER, RuntimeStatus.NOT_FOUND));
    runtimeStateStore.saveSpec(kubernetesKey, kubernetesSpec);
    runtimeStateStore.saveInstance(kubernetesKey, runtimeInstance("rt-crm-dev-helper-k8s", BackendType.KUBERNETES, RuntimeStatus.RUNNING));
    when(devRuntimeService.getStatus(kubernetesKey)).thenReturn(Try.success(runtimeInstance("rt-crm-dev-helper-k8s", BackendType.KUBERNETES, RuntimeStatus.RUNNING)));

    Map<String, Object> payload = service.getStatus("crm").get();

    assertEquals(com.ouroboros.container.ContainerStatus.RUNNING, payload.get("status"));
    assertEquals("rt-crm-dev-helper-k8s", payload.get("containerId"));
  }

  @Test
  void shouldReportNotFoundWhenStoredRuntimePointsToMissingDockerContainer() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER, RuntimeStatus.STOPPED));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(RuntimeInstance.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .status(RuntimeStatus.NOT_FOUND)
        .build()));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ouroboros-mothership"))).thenReturn(Try.success(Collections.emptyList()));

    Map<String, Object> payload = service.getStatus("crm").get();

    assertEquals(com.ouroboros.container.ContainerStatus.NOT_FOUND, payload.get("status"));
    assertEquals(Boolean.FALSE, payload.get("refreshPending"));
    assertTrue(payload.containsKey("lastLoadTime"));
    assertEquals(null, payload.get("lastLoadTime"));
  }

  @Test
  void shouldExposeHelperDetailsFieldsInStatusContract() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/helper:v2");
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/helper:v2")))
        .thenReturn(Try.success(java.util.Arrays.asList("registry.example.com/helper:v1", "registry.example.com/helper:v2")));

    Map<String, Object> details = service.getStatus("crm").get();

    assertEquals("registry.example.com/helper:v1", details.get("imageReference"));
    assertEquals("registry.example.com/helper", details.get("imageRepository"));
    assertEquals("v1", details.get("imageTag"));
    assertEquals(Boolean.TRUE, details.get("updateRecommended"));
    assertFalse(details.containsKey("logTopic"));
    assertFalse(details.containsKey("logSubscribeDestination"));
  }

  @Test
  void shouldRecommendUpdateForHistoricalDefaultDerivedExplicitHelperWhenConfiguredBaseImageChanges() {
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.3");
    RuntimeSpec spec = runtimeSpec(
        "crm",
        BackendType.DOCKER,
        "registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2",
        helperLabels(
            DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_EXPLICIT,
            "registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2"
        )
    );
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-dev-helper", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.listImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.3")))
        .thenReturn(Try.success(java.util.Arrays.asList(
            "registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2",
            "registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.3"
        )));

    Map<String, Object> payload = service.getStatus("crm").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2", imageState.getReference());
    assertEquals(Boolean.TRUE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldSyncRuntimeLogsIntoHelperLogService() {
    RuntimeSpec spec = runtimeSpec("crm", BackendType.DOCKER, "registry.example.com/helper:v1");
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    when(devRuntimeService.getLogs(key)).thenReturn(Try.success(java.util.Arrays.asList("assistant started", "index ready")));

    Try<Void> result = service.syncRuntimeLogs("crm");

    assertTrue(result.isSuccess());
    verify(appDevHelperLogService).appendRuntimeOutput("crm", java.util.Arrays.asList("assistant started", "index ready"));
  }

  private static RuntimeSpec runtimeSpec(String appName, BackendType backendType, String image) {
    return runtimeSpec(appName, backendType, image, Collections.singletonMap("OUROBOROS_APP_NAME", appName));
  }

  private static CommandResult helperOnline(String appName, String helperInstanceId, long startedAtEpochMillis) {
    return helperOnline(appName, helperInstanceId, startedAtEpochMillis, APP_INCARNATION_ID);
  }

  private static CommandResult helperOnline(
      String appName,
      String helperInstanceId,
      long startedAtEpochMillis,
      String appIncarnationId
  ) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    MetadataRefreshProtocol.addHelperIdentity(
        data,
        new HelperIdentity(helperInstanceId, startedAtEpochMillis, appIncarnationId)
    );
    return new CommandResult()
        .setAppName(appName)
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setSuccess(true)
        .setData(data);
  }

  private static CommandResult refreshFailure(
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String refreshOperationId
  ) {
    return refreshFailure(
        appName,
        runtimeGeneration,
        helperInstanceId,
        helperStartedAtEpochMillis,
        refreshOperationId,
        null,
        "collector failed"
    );
  }

  private static CommandResult refreshFailure(
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String refreshOperationId,
      CommandFailureType failureType,
      String message
  ) {
    return new CommandResult()
        .setAppName(appName)
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(runtimeGeneration)
        .setHelperInstanceId(helperInstanceId)
        .setHelperStartedAtEpochMillis(Long.valueOf(helperStartedAtEpochMillis))
        .setAppIncarnationId(APP_INCARNATION_ID)
        .setRefreshOperationId(refreshOperationId)
        .setSuccess(false)
        .setFailureType(failureType)
        .setMessage(message);
  }

  private static MetadataSnapshotRejectedEvent retryableRejection(String refreshOperationId) {
    return new MetadataSnapshotRejectedEvent(
        "crm",
        Long.valueOf(2L),
        "helper-a",
        1000L,
        APP_INCARNATION_ID,
        refreshOperationId,
        "等待开发助手响应超时",
        CommandFailureType.RESPONSE_TIMEOUT,
        runtimeTarget(BackendType.DOCKER, "rt-crm-dev-helper")
    );
  }

  private String pendingRefreshOperation(String appName) {
    Map<String, Object> lifecycles = (Map<String, Object>) ReflectionTestUtils.getField(
        ReflectionTestUtils.getField(service, "refreshSessions"),
        "helperLifecycles"
    );
    Object lifecycle = lifecycles.get(appName);
    return lifecycle == null
        ? null
        : (String) ReflectionTestUtils.getField(lifecycle, "pendingRefreshOperationId");
  }

  private static RuntimeSpec runtimeSpec(String appName, BackendType backendType, String image, Map<String, String> labels) {
    return runtimeSpec(appName, backendType, image, labels, APP_INCARNATION_ID);
  }

  private static RuntimeSpec runtimeSpec(
      String appName,
      BackendType backendType,
      String image,
      Map<String, String> labels,
      String appIncarnationId
  ) {
    return RuntimeSpec.builder()
        .appName(appName)
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(backendType)
        .workspaceMode(backendType == BackendType.KUBERNETES ? WorkspaceMode.PVC : WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image(image)
        .env(Collections.singletonMap(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, appIncarnationId))
        .labels(labels)
        .build();
  }

  private static Map<String, String> helperLabels(String imageSource, String canonicalReference) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put(APP_NAME_LABEL, "crm");
    labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL, imageSource);
    if (canonicalReference != null) {
      labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL, canonicalReference);
    }
    return labels;
  }

  private static Map<String, String> recoveredHelperLabels(String imageSource, String canonicalReference, String helperLabel) {
    Map<String, String> labels = helperLabels(imageSource, canonicalReference);
    labels.put(DEV_HELPER_LABEL, helperLabel);
    return labels;
  }

  private static Container helperContainer(String appName, String runtimeId, String image, ContainerStatus status, Map<String, String> labels) {
    Container container = new Container();
    container.setId(runtimeId);
    container.setName("ouroboros-dev-helper-" + appName);
    container.setImage(image);
    container.setStatus(status);
    container.setLabels(labels);
    return container;
  }

  private static RuntimeInstance helperRuntime(String appName, Container container) {
    return RuntimeInstance.builder()
        .runtimeId(container.getId())
        .appName(appName)
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .status(runtimeStatusOf(container.getStatus()))
        .backendMetadata(java.util.Map.of("containerName", container.getName(), "imageReference", container.getImage()))
        .build();
  }

  private static RuntimeStatus runtimeStatusOf(ContainerStatus status) {
    if (status == null) {
      return RuntimeStatus.UNKNOWN;
    }
    switch (status) {
      case NOT_FOUND:
        return RuntimeStatus.NOT_FOUND;
      case LAUNCHING:
        return RuntimeStatus.STARTING;
      case RUNNING:
      case PAUSED:
        return RuntimeStatus.RUNNING;
      case STOPPED:
        return RuntimeStatus.STOPPED;
      default:
        return RuntimeStatus.UNKNOWN;
    }
  }

  private static RuntimeInstance runtimeInstance(String runtimeId, BackendType backendType) {
    return runtimeInstance(runtimeId, backendType, RuntimeStatus.RUNNING);
  }

  private static RuntimeInstance runtimeInstance(String runtimeId, BackendType backendType, RuntimeStatus status) {
    return runtimeInstance(runtimeId, backendType, status, Long.valueOf(2L));
  }

  private static RuntimeInstance runtimeInstance(
      String runtimeId,
      BackendType backendType,
      RuntimeStatus status,
      Long runtimeGeneration
  ) {
    return RuntimeInstance.builder()
        .runtimeId(runtimeId)
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(backendType)
        .runtimeGeneration(runtimeGeneration)
        .status(status)
        .backendMetadata(backendMetadata(runtimeId, backendType))
        .build();
  }

  private static HelperRuntimeTarget runtimeTarget(BackendType backendType, String runtimeId) {
    return new HelperRuntimeTarget(backendType.name(), "dev-helper", runtimeId);
  }

  private static Map<String, String> backendMetadata(String runtimeId, BackendType backendType) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    if (backendType == BackendType.DOCKER) {
      metadata.put("containerName", runtimeId);
      return metadata;
    }
    metadata.put("deployment", "devrt-dev-helper-crm");
    return metadata;
  }

  private static Container container(String name, String image) {
    Container container = new Container();
    container.setName(name);
    container.setImage(image);
    return container;
  }

  private static DevelopWorkspaceContext workspaceContext() {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return "/tmp";
      }

      @Override
      public String getDevRootDir() {
        return "/tmp/dev";
      }

      @Override
      public String getRunRootDir() {
        return "/tmp/run";
      }

      @Override
      public String getDebugSupportFilesDir() {
        return "/tmp/debug";
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return "/tmp/develop";
      }
    };
  }
}
