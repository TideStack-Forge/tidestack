package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.container.model.Container;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.impl.AppContainerServiceImpl;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.app.service.support.BaseImageReferenceResolver;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.app.model.RuntimeAccessViewDTO;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.model.AppInfo;

@ExtendWith(MockitoExtension.class)
class AppContainerServiceRuntimeDelegationTest {

  @TempDir
  Path tempDir;

  @Mock
  private AppManagementService appManagementService;

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private ContainerService containerService;

  @Mock
  private DevContainerConfig devContainerConfig;

  @Mock
  private AppDataSourceService appDataSourceService;

  @Mock
  private PlatformDataSourceService platformDataSourceService;

  @Mock
  private DevRuntimeService devRuntimeService;

  @Mock
  private TestAppRuntimeSpecFactory specFactory;

  private InMemoryRuntimeStateStore runtimeStateStore;
  private InMemoryDebugSessionStore debugSessionStore;
  private AppContainerServiceImpl service;
  private DevelopWorkspaceContext workspaceContext;

  @BeforeEach
  void setUp() throws IOException {
    runtimeStateStore = new InMemoryRuntimeStateStore();
    debugSessionStore = new InMemoryDebugSessionStore();
    workspaceContext = workspaceContext(tempDir);
    Files.createDirectories(tempDir.resolve("dev/crm"));
    Files.createDirectories(tempDir.resolve("debug-support"));
    Files.write(tempDir.resolve("dev/crm/app.json"), "{}".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDir.resolve("debug-support/agent.jar"), "debug".getBytes(StandardCharsets.UTF_8));

    service = new AppContainerServiceImpl(
        appManagementService,
        configurationService,
        workspaceContext,
        containerService,
        devContainerConfig,
        appDataSourceService,
        platformDataSourceService,
        devRuntimeService,
        runtimeStateStore,
        debugSessionStore,
        specFactory,
        new AppRuntimeViewAssembler(new DebugSessionViewAssembler(), new RuntimeImageViewService()),
        new RuntimeImageViewService(),
        new BaseImageQuerySupport(new BaseImageReferenceResolver())
    );
  }

  @Test
  void shouldDelegateCreateToRuntimeServiceUsingBuiltSpec() {
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(datasource("memory"));
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.KUBERNETES);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.KUBERNETES);
    when(specFactory.create(any(AppContainerRequest.class), eq("alice"))).thenReturn(spec);
    when(devRuntimeService.create(spec)).thenReturn(Try.success(instance));

    Try<String> result = service.create(request, "alice");

    assertEquals("rt-crm-alice", result.get());
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/app.json")));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/extend/agent.jar")));
    verify(specFactory).create(any(AppContainerRequest.class), eq("alice"));
    verify(devRuntimeService).create(spec);
  }

  @Test
  void shouldCreateAndStartRuntimeWithoutRebuildingWorkspaceTwice() throws Exception {
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(datasource("memory"));
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance created = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeInstance running = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.RUNNING);
    when(specFactory.create(any(AppContainerRequest.class), eq("alice"))).thenReturn(spec);
    when(devRuntimeService.create(spec)).thenAnswer(invocation -> {
      Files.write(tempDir.resolve("run/crm/alice/metadata/create-sentinel.txt"), "created".getBytes(StandardCharsets.UTF_8));
      return Try.success(created);
    });
    when(devRuntimeService.start(key)).thenReturn(Try.success(running));

    Try<String> result = service.createAndStart(request, "alice");

    assertEquals("rt-crm-alice", result.get());
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/app.json")));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/create-sentinel.txt")));
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).create(spec);
    order.verify(devRuntimeService).start(key);
  }

  @Test
  void shouldGenerateWebAndDebugPortsForCreateInit() {
    when(configurationService.getRequired(String.class, ConfigurationConstant.TEST_APP_PORT_RANGE))
        .thenReturn("32080-32090");
    when(containerService.searchContainerInfo(any()))
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList(existingPortContainer())));

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32083), result.get("web"));
    assertEquals(Integer.valueOf(32084), result.get("apiDebug"));
    assertEquals(2, result.size());
    assertFalse(result.containsKey("api"));
  }

  @Test
  void shouldDelegateStartAndReloadWorkspaceUsingStoredRuntimeIdentity() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.start(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.reloadWorkspace(spec)).thenReturn(Try.success(instance));

    Try<Void> startResult = service.start("rt-crm-alice");
    Try<Void> reloadResult = service.reloadWorkspace("rt-crm-alice");

    assertTrue(startResult.isSuccess());
    assertTrue(reloadResult.isSuccess());
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/app.json")));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/extend/agent.jar")));
    verify(devRuntimeService).start(key);
    verify(devRuntimeService).reloadWorkspace(spec);
  }

  @Test
  void shouldSkipSymbolicLinksWhenBuildingRunWorkspace() throws Exception {
    Path externalSecret = Files.write(tempDir.resolve("outside-secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
    Path devSymlink = tempDir.resolve("dev/crm/linked-secret.txt");
    Path debugSymlink = tempDir.resolve("debug-support/linked-agent.jar");
    Files.createSymbolicLink(devSymlink, externalSecret);
    Files.createSymbolicLink(debugSymlink, externalSecret);

    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.start(key)).thenReturn(Try.success(instance));

    Try<Void> result = service.start("rt-crm-alice");

    assertTrue(result.isSuccess());
    assertFalse(Files.exists(tempDir.resolve("run/crm/alice/metadata/linked-secret.txt")));
    assertFalse(Files.exists(tempDir.resolve("run/crm/alice/extend/linked-agent.jar")));
  }

  @Test
  void shouldStopRuntimeBeforeDeletingIt() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.stop(key)).thenReturn(Try.success(runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED)));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));

    Try<Void> result = service.delete("rt-crm-alice");

    assertTrue(result.isSuccess());
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).stop(key);
    order.verify(devRuntimeService).delete(key);
  }

  @Test
  void shouldBuildRuntimeBackedContainerViewsFromStateStore() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.KUBERNETES);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.KUBERNETES));
    debugSessionStore.save(activeSession(key));
    AppInfo appInfo = new AppInfo().setName("crm").setTitle("CRM").setDescription("CRM app");
    when(appManagementService.listApps()).thenReturn(Try.success(Collections.singletonList(
        new AppInfoWithMeta(appInfo, LocalDateTime.now(), LocalDateTime.now())
    )));
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(containerService.getAllImageNames("ouroboros-mothership"))
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v1")));

    List<AppContainersDTO> result = service.getAll().get();

    assertEquals(1, result.size());
    assertEquals(1, result.get(0).getChildren().size());
    assertEquals("alice", result.get(0).getChildren().get(0).getProfile());
    assertEquals("TEST_APP", result.get(0).getChildren().get(0).getRuntimeKind());
    assertEquals(Long.valueOf(3L), result.get(0).getChildren().get(0).getRuntimeGeneration());
    assertEquals("KUBERNETES", result.get(0).getChildren().get(0).getBackendType());
    assertEquals("TEMP_PORT", result.get(0).getChildren().get(0).getExposureStrategy());
    assertEquals("service=devrt-test-app-crm-alice-svc", result.get(0).getChildren().get(0).getBackendSummary());
    assertEquals("devrt-test-app-crm-alice-svc", result.get(0).getChildren().get(0).getBackendDetails().get("service"));
    assertEquals("v1", result.get(0).getChildren().get(0).getImageState().getTag());
    assertEquals("http://127.0.0.1:32080", result.get(0).getChildren().get(0).getWebEndpoint());
    assertEquals("dbg-001", result.get(0).getChildren().get(0).getActiveDebugSessionId());
    assertEquals("SESSION_TUNNEL", result.get(0).getChildren().get(0).getDebugCapability().getStrategy());
    verify(containerService, never()).searchContainerInfo(any());
  }

  @Test
  void shouldDisplayPausedStatusFromDockerContainerInListView() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER));
    AppInfo appInfo = new AppInfo().setName("crm").setTitle("CRM").setDescription("CRM app");
    when(appManagementService.listApps()).thenReturn(Try.success(Collections.singletonList(
        new AppInfoWithMeta(appInfo, LocalDateTime.now(), LocalDateTime.now())
    )));
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(containerService.getAllImageNames("ouroboros-mothership"))
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v1")));
    Container pausedContainer = new Container();
    pausedContainer.setName("rt-crm-alice");
    pausedContainer.setStatus(ContainerStatus.PAUSED);
    when(containerService.getContainerInfoByName("rt-crm-alice"))
        .thenReturn(io.vavr.control.Either.right(pausedContainer));

    List<AppContainersDTO> result = service.getAll().get();

    assertEquals(ContainerStatus.PAUSED, result.get(0).getChildren().get(0).getStatus());
  }

  @Test
  void shouldExposeRuntimeEndpointsAndCapabilitiesInStatusPayload() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.KUBERNETES);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.KUBERNETES);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    debugSessionStore.save(activeSession(key));
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(containerService.getAllImageNames("ouroboros-mothership"))
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v1")));

    Map<String, Object> payload = service.getStatus("rt-crm-alice").get();

    assertEquals("alice", payload.get("profile"));
    assertEquals("TEST_APP", payload.get("runtimeKind"));
    assertEquals(Long.valueOf(3L), payload.get("runtimeGeneration"));
    assertEquals("KUBERNETES", payload.get("backendType"));
    assertEquals("TEMP_PORT", payload.get("exposureStrategy"));
    assertEquals("service=devrt-test-app-crm-alice-svc", payload.get("backendSummary"));
    assertEquals("devrt-test-app-crm-alice-svc", ((Map<String, String>) payload.get("backendDetails")).get("service"));
    assertEquals("http://127.0.0.1:32080", payload.get("webEndpoint"));
    assertEquals("http://127.0.0.1:32081", payload.get("apiEndpoint"));
    assertEquals("tcp://127.0.0.1:5005", payload.get("debugEndpoint"));
    assertEquals("dbg-001", payload.get("activeDebugSessionId"));
    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("registry.example.com/crm:v1", imageState.getReference());
    assertEquals("v1", imageState.getTag());
    RuntimeAccessViewDTO.DebugCapabilityView debugCapability = (RuntimeAccessViewDTO.DebugCapabilityView) payload.get("debugCapability");
    assertEquals(Boolean.TRUE, debugCapability.getEnabled());
    assertEquals("SESSION_TUNNEL", debugCapability.getStrategy());
    assertEquals(Boolean.TRUE, ((Map<String, Boolean>) payload.get("capabilities")).get("supportsPause"));
    assertEquals(Boolean.TRUE, ((Map<String, Boolean>) payload.get("capabilities")).get("supportsDebugSession"));
    assertEquals(Integer.valueOf(32080), payload.get("webPort"));
  }

  @Test
  void shouldPreferRunningDockerContainerImageForStatusPayload() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER);
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devContainerConfig.getMothershipImageName()).thenReturn("ouroboros-mothership");
    when(containerService.getAllImageNames("ouroboros-mothership"))
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v1")));
    Container runningContainer = new Container();
    runningContainer.setName("rt-crm-alice");
    runningContainer.setStatus(ContainerStatus.RUNNING);
    runningContainer.setImage("sha256:deadbeefcafebabe");
    when(containerService.getContainerInfoByName("rt-crm-alice"))
        .thenReturn(io.vavr.control.Either.right(runningContainer));

    Map<String, Object> payload = service.getStatus("rt-crm-alice").get();

    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payload.get("imageState");
    assertEquals("sha256:deadbeefcafebabe", imageState.getReference());
    assertEquals(Boolean.TRUE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldStopDeleteCreateAndRestartRuntimeWhenUpdatingImageForActiveContainer() {
    RuntimeSpec spec = runtimeSpec("crm", "alice", BackendType.DOCKER);
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance runningInstance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.RUNNING);
    RuntimeInstance stoppedInstance = runtimeInstance("rt-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeInstance createdInstance = runtimeInstance("rt-crm-alice-new", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeSpec recreatedSpec = runtimeSpec("crm", "alice", BackendType.DOCKER, "registry.example.com/crm:v2");
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, runningInstance);
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v2"));
    when(containerService.getAllTaggedImageNames())
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v2")));
    when(specFactory.recreate(spec, "registry.example.com/crm:v2")).thenReturn(recreatedSpec);
    when(devRuntimeService.stop(key)).thenReturn(Try.success(stoppedInstance));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));
    when(devRuntimeService.create(recreatedSpec)).thenReturn(Try.success(createdInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(runningInstance));

    String runtimeId = service.updateImage("rt-crm-alice").get();

    assertEquals("rt-crm-alice", runtimeId);
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/app.json")));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/extend/agent.jar")));
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).stop(key);
    order.verify(devRuntimeService).delete(key);
    order.verify(devRuntimeService).create(recreatedSpec);
    order.verify(devRuntimeService).start(key);
    verify(specFactory).recreate(spec, "registry.example.com/crm:v2");
  }

  @Test
  void shouldRecoverDockerRuntimeStateBeforeUpdatingImage() {
    Container container = existingPortContainer();
    Map<String, String> labels = new LinkedHashMap<String, String>(container.getLabels());
    labels.put("OUROBOROS_APP_NAME", "crm");
    labels.put("OUROBOROS_APP_PROFILE", "alice");
    container.setLabels(labels);
    container.setId("cid-recovered");
    container.setImage("registry.example.com/crm:v1");
    container.setStatus(ContainerStatus.RUNNING);
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    RuntimeInstance runningInstance = runtimeInstance("cid-recovered", "crm", "alice", BackendType.DOCKER, RuntimeStatus.RUNNING);
    RuntimeInstance stoppedInstance = runtimeInstance("cid-recovered", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeInstance createdInstance = runtimeInstance("cid-new", "crm", "alice", BackendType.DOCKER, RuntimeStatus.STOPPED);
    RuntimeSpec recreatedSpec = runtimeSpec("crm", "alice", BackendType.DOCKER, "registry.example.com/crm:v2");
    when(containerService.getContainerInfoById("cid-recovered")).thenReturn(io.vavr.control.Either.right(container));
    when(containerService.getContainerInfoByName("ouroboros-test-app-crm-alice")).thenReturn(io.vavr.control.Either.right(container));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v2"));
    when(containerService.getAllTaggedImageNames())
        .thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v2")));
    when(specFactory.recreate(any(RuntimeSpec.class), eq("registry.example.com/crm:v2"))).thenReturn(recreatedSpec);
    when(devRuntimeService.stop(key)).thenReturn(Try.success(stoppedInstance));
    when(devRuntimeService.delete(key)).thenReturn(Try.success(null));
    when(devRuntimeService.create(recreatedSpec)).thenReturn(Try.success(createdInstance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(runningInstance));

    String runtimeId = service.updateImage("cid-recovered").get();

    assertEquals("cid-recovered", runtimeId);
    assertTrue(runtimeStateStore.findSpec(key).isPresent());
    assertTrue(runtimeStateStore.findInstance(key).isPresent());
    assertEquals("registry.example.com/crm:v1", runtimeStateStore.findSpec(key).get().getImage());
    assertEquals(Integer.valueOf(32080), runtimeStateStore.findSpec(key).get().getRequestedPorts().get("web"));
    assertEquals(Integer.valueOf(32082), runtimeStateStore.findSpec(key).get().getRequestedPorts().get("apiDebug"));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/metadata/app.json")));
    assertTrue(Files.exists(tempDir.resolve("run/crm/alice/extend/agent.jar")));
    var order = inOrder(devRuntimeService);
    order.verify(devRuntimeService).stop(key);
    order.verify(devRuntimeService).delete(key);
    order.verify(devRuntimeService).create(recreatedSpec);
    order.verify(devRuntimeService).start(key);
    ArgumentCaptor<RuntimeSpec> currentSpec = ArgumentCaptor.forClass(RuntimeSpec.class);
    verify(specFactory).recreate(currentSpec.capture(), eq("registry.example.com/crm:v2"));
    assertEquals("registry.example.com/crm:v1", currentSpec.getValue().getImage());
    assertEquals(runtimeStateStore.findSpec(key).get().getRequestedPorts(), currentSpec.getValue().getRequestedPorts());
  }

  @Test
  void shouldReturnNotFoundPayloadWhenRuntimeIdCannotBeResolved() {
    Map<String, Object> payload = service.getStatus("crm").get();

    assertEquals(ContainerStatus.NOT_FOUND, payload.get("status"));
    assertEquals("crm", payload.get("appName"));
  }

  @Test
  void shouldDiscoverDockerRuntimeByAppAndProfileWhenStateIsMissing() {
    Container container = existingPortContainer();
    Map<String, String> labels = new LinkedHashMap<String, String>(container.getLabels());
    labels.put("OUROBOROS_APP_NAME", "crm");
    labels.put("OUROBOROS_APP_PROFILE", "alice");
    container.setLabels(labels);
    container.setStatus(ContainerStatus.RUNNING);
    when(containerService.getContainerInfoByName("ouroboros-test-app-crm-alice")).thenReturn(io.vavr.control.Either.right(container));
    when(containerService.getAllImageNames(any())).thenReturn(io.vavr.control.Either.right(Collections.singletonList("registry.example.com/crm:v1")));
    when(devRuntimeService.getStatus(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)))
        .thenReturn(Try.success(runtimeInstance("ouroboros-test-app-crm-alice", "crm", "alice", BackendType.DOCKER, RuntimeStatus.RUNNING)));

    Map<String, Object> payload = service.getStatus("crm", "alice").get();

    assertEquals(ContainerStatus.RUNNING, payload.get("status"));
    assertEquals("crm", payload.get("appName"));
    assertEquals("alice", payload.get("profile"));
    assertEquals("http://127.0.0.1:32080", payload.get("webEndpoint"));
  }

  private static AppContainerRequest.DataSourceDefine datasource(String type) {
    AppContainerRequest.DataSourceDefine datasource = new AppContainerRequest.DataSourceDefine();
    datasource.setType(type);
    return datasource;
  }

  private static Container existingPortContainer() {
    Container container = new Container();
    container.setName("ouroboros-test-app-crm-alice");
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("ouroboros-test-app", "CRM");
    labels.put("WEB_PORT", "32080");
    labels.put("API_PORT", "32081");
    labels.put("API_DEBUG_PORT", "32082");
    container.setLabels(labels);
    return container;
  }

  private static RuntimeSpec runtimeSpec(String appName, String profile, BackendType backendType) {
    return runtimeSpec(appName, profile, backendType, "registry.example.com/" + appName + ":v1");
  }

  private static RuntimeSpec runtimeSpec(String appName, String profile, BackendType backendType, String image) {
    return RuntimeSpec.builder()
        .appName(appName)
        .profile(profile)
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(backendType)
        .workspaceMode(backendType == BackendType.KUBERNETES ? WorkspaceMode.PVC : WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.TEMP_PORT)
        .image(image)
        .env(Collections.singletonMap("SPRING_APPLICATION_NAME", appName))
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", appName))
        .ttl(Duration.ofHours(1))
        .build();
  }

  private static RuntimeInstance runtimeInstance(String runtimeId, String appName, String profile, BackendType backendType) {
    return runtimeInstance(runtimeId, appName, profile, backendType, RuntimeStatus.RUNNING);
  }

  private static RuntimeInstance runtimeInstance(String runtimeId, String appName, String profile, BackendType backendType, RuntimeStatus status) {
    return RuntimeInstance.builder()
        .runtimeId(runtimeId)
        .appName(appName)
        .profile(profile)
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(backendType)
        .runtimeGeneration(Long.valueOf(3L))
        .status(status)
        .webEndpoint("http://127.0.0.1:32080")
        .apiEndpoint("http://127.0.0.1:32081")
        .capabilities(RuntimeCapability.builder()
            .supportsPause(true)
            .supportsDebugPort(true)
            .supportsDebugSession(true)
            .supportsWorkspaceHotReload(true)
            .supportsNodePortExposure(true)
            .build())
        .backendMetadata(backendMetadata(runtimeId, backendType))
        .build();
  }

  private static Map<String, String> backendMetadata(String runtimeId, BackendType backendType) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    if (backendType == BackendType.KUBERNETES) {
      metadata.put("deployment", "devrt-test-app-crm-alice");
      metadata.put("service", "devrt-test-app-crm-alice-svc");
      metadata.put("serviceType", "NodePort");
      metadata.put("ingress", "devrt-test-app-crm-alice-ing");
      return metadata;
    }
    metadata.put("containerName", runtimeId);
    return metadata;
  }

  private static DebugSession activeSession(RuntimeIdentityKey key) {
    return DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(key)
        .runtimeGeneration(Long.valueOf(1L))
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .openedAt(Instant.parse("2026-03-13T08:00:00Z"))
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(5005))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .clientAccessHint("ssh -L 5005:127.0.0.1:5005 develop.example.com")
            .build())
        .build();
  }

  private static DevelopWorkspaceContext workspaceContext(Path tempDir) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return tempDir.toString();
      }

      @Override
      public String getDevRootDir() {
        return tempDir.resolve("dev").toString();
      }

      @Override
      public String getRunRootDir() {
        return tempDir.resolve("run").toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return tempDir.resolve("debug-support").toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return tempDir.resolve("develop-support").toString();
      }
    };
  }
}
