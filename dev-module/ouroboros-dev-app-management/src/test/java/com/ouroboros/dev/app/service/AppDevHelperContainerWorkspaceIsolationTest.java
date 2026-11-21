package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
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
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;

class AppDevHelperContainerWorkspaceIsolationTest {

  @TempDir
  Path tempDir;

  @Test
  void createShouldSyncAppMetadataBeforeCreatingHelper() throws Exception {
    Path devRoot = Files.createDirectories(tempDir.resolve("dev"));
    Path runRoot = Files.createDirectories(tempDir.resolve("run"));
    Path debugSupportRoot = Files.createDirectories(tempDir.resolve("debug-support"));
    Path developSupportRoot = Files.createDirectories(tempDir.resolve("develop-support"));
    Files.createDirectories(devRoot.resolve("crm/business-model"));
    Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));

    DevRuntimeService devRuntimeService = mock(DevRuntimeService.class);
    DevHelperRuntimeSpecFactory runtimeSpecFactory = mock(DevHelperRuntimeSpecFactory.class);
    DevHelperCommandService devHelperCommandService = mock(DevHelperCommandService.class);
    ContainerService containerService = mock(ContainerService.class);
    DevContainerConfig devContainerConfig = mock(DevContainerConfig.class);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext(tempDir, devRoot, runRoot, debugSupportRoot, developSupportRoot),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        mock(AppDevHelperLogService.class),
        mock(ApplicationEventPublisher.class)
    );

    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", "crm"))
        .build();
    RuntimeInstance created = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.STOPPED)
        .build();
    when(runtimeSpecFactory.create(request)).thenReturn(spec);
    when(devRuntimeService.create(spec)).thenReturn(Try.success(created));

    Try<String> result = service.create(request);

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/business-model/order.business.json")));
    verify(devRuntimeService).create(spec);
  }

  @Test
  void createAndStartShouldSyncAppMetadataOnlyOnce() throws Exception {
    Path devRoot = Files.createDirectories(tempDir.resolve("dev"));
    Path runRoot = Files.createDirectories(tempDir.resolve("run"));
    Path debugSupportRoot = Files.createDirectories(tempDir.resolve("debug-support"));
    Path developSupportRoot = Files.createDirectories(tempDir.resolve("develop-support"));
    Files.createDirectories(devRoot.resolve("crm/business-model"));
    Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));

    DevRuntimeService devRuntimeService = mock(DevRuntimeService.class);
    DevHelperRuntimeSpecFactory runtimeSpecFactory = mock(DevHelperRuntimeSpecFactory.class);
    DevHelperCommandService devHelperCommandService = mock(DevHelperCommandService.class);
    ContainerService containerService = mock(ContainerService.class);
    DevContainerConfig devContainerConfig = mock(DevContainerConfig.class);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext(tempDir, devRoot, runRoot, debugSupportRoot, developSupportRoot),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        mock(AppDevHelperLogService.class),
        mock(ApplicationEventPublisher.class)
    );

    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", "crm"))
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance created = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.STOPPED)
        .build();
    RuntimeInstance running = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.RUNNING)
        .build();
    when(runtimeSpecFactory.create(request)).thenReturn(spec);
    when(devRuntimeService.create(spec)).thenAnswer(invocation -> {
      Files.write(runRoot.resolve("crm/dev-helper/metadata/create-sentinel.txt"), "created".getBytes(StandardCharsets.UTF_8));
      return Try.success(created);
    });
    when(devRuntimeService.start(key)).thenReturn(Try.success(running));

    Try<String> result = service.createAndStart(request);

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/business-model/order.business.json")));
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/create-sentinel.txt")));
    verify(devRuntimeService).create(spec);
    verify(devRuntimeService).start(key);
  }

  @Test
  void startShouldNotCopyVcsSecretsIntoHelperExtendWorkspace() throws Exception {
    Path devRoot = Files.createDirectories(tempDir.resolve("dev"));
    Path runRoot = Files.createDirectories(tempDir.resolve("run"));
    Path debugSupportRoot = Files.createDirectories(tempDir.resolve("debug-support"));
    Path developSupportRoot = Files.createDirectories(tempDir.resolve("develop-support"));
    Files.createDirectories(devRoot.resolve("crm/business-model"));
    Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(developSupportRoot.resolve("helper.json"), "helper".getBytes(StandardCharsets.UTF_8));
    Files.createDirectories(developSupportRoot.resolve("vcs/crm"));
    Files.write(developSupportRoot.resolve("vcs/crm/credentials.json"), "secret".getBytes(StandardCharsets.UTF_8));

    DevRuntimeService devRuntimeService = mock(DevRuntimeService.class);
    DevHelperRuntimeSpecFactory runtimeSpecFactory = mock(DevHelperRuntimeSpecFactory.class);
    DevHelperCommandService devHelperCommandService = mock(DevHelperCommandService.class);
    ContainerService containerService = mock(ContainerService.class);
    DevContainerConfig devContainerConfig = mock(DevContainerConfig.class);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext(tempDir, devRoot, runRoot, debugSupportRoot, developSupportRoot),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        mock(AppDevHelperLogService.class),
        mock(ApplicationEventPublisher.class)
    );

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", "crm"))
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.RUNNING)
        .build();
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.start(key)).thenReturn(Try.success(instance));

    Try<Void> result = service.start("crm");

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/business-model/order.business.json")));
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/extend/helper.json")));
    assertFalse(Files.exists(runRoot.resolve("crm/dev-helper/extend/vcs/crm/credentials.json")));
  }

  @Test
  void restartShouldSyncAppMetadataBeforeRestartingHelper() throws Exception {
    Path devRoot = Files.createDirectories(tempDir.resolve("dev"));
    Path runRoot = Files.createDirectories(tempDir.resolve("run"));
    Path debugSupportRoot = Files.createDirectories(tempDir.resolve("debug-support"));
    Path developSupportRoot = Files.createDirectories(tempDir.resolve("develop-support"));
    Files.createDirectories(devRoot.resolve("crm/business-model"));
    Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));

    DevRuntimeService devRuntimeService = mock(DevRuntimeService.class);
    DevHelperRuntimeSpecFactory runtimeSpecFactory = mock(DevHelperRuntimeSpecFactory.class);
    DevHelperCommandService devHelperCommandService = mock(DevHelperCommandService.class);
    ContainerService containerService = mock(ContainerService.class);
    DevContainerConfig devContainerConfig = mock(DevContainerConfig.class);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext(tempDir, devRoot, runRoot, debugSupportRoot, developSupportRoot),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        mock(AppDevHelperLogService.class),
        mock(ApplicationEventPublisher.class)
    );

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", "crm"))
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.RUNNING)
        .build();
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.restart(key)).thenReturn(Try.success(instance));
    when(devRuntimeService.getLogs(key)).thenReturn(Try.success(Collections.emptyList()));

    Try<Void> result = service.restart("crm");

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/business-model/order.business.json")));
    verify(devRuntimeService).restart(key);
  }

  @Test
  void reloadShouldSyncAppMetadataBeforeRefreshingHelper() throws Exception {
    Path devRoot = Files.createDirectories(tempDir.resolve("dev"));
    Path runRoot = Files.createDirectories(tempDir.resolve("run"));
    Path debugSupportRoot = Files.createDirectories(tempDir.resolve("debug-support"));
    Path developSupportRoot = Files.createDirectories(tempDir.resolve("develop-support"));
    Files.createDirectories(devRoot.resolve("crm/business-model"));
    Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));

    DevRuntimeService devRuntimeService = mock(DevRuntimeService.class);
    DevHelperRuntimeSpecFactory runtimeSpecFactory = mock(DevHelperRuntimeSpecFactory.class);
    DevHelperCommandService devHelperCommandService = mock(DevHelperCommandService.class);
    ContainerService containerService = mock(ContainerService.class);
    DevContainerConfig devContainerConfig = mock(DevContainerConfig.class);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    Map<String, Consumer<CommandResult>> commandListeners = new ConcurrentHashMap<String, Consumer<CommandResult>>();
    doAnswer(invocation -> {
      commandListeners.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(devHelperCommandService).registerCommandResultListener(any(String.class), any());
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext(tempDir, devRoot, runRoot, debugSupportRoot, developSupportRoot),
        devRuntimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        devHelperCommandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        mock(AppDevHelperLogService.class),
        mock(ApplicationEventPublisher.class)
    );

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .env(Collections.singletonMap(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, "incarnation-a"))
        .labels(Collections.singletonMap("OUROBOROS_APP_NAME", "crm"))
        .build();
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .runtimeGeneration(Long.valueOf(1L))
        .status(RuntimeStatus.RUNNING)
        .build();
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    when(devRuntimeService.getStatus(key)).thenReturn(Try.success(instance));
    Map<String, Object> helperHandshake = new java.util.LinkedHashMap<String, Object>();
    MetadataRefreshProtocol.addHelperIdentity(
        helperHandshake,
        new HelperIdentity("helper-a", 1000L, "incarnation-a")
    );
    commandListeners.get(Commands.KNOCK_KNOCK.getCommand()).accept(new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setSuccess(true)
        .setData(helperHandshake));
    clearInvocations(devHelperCommandService);

    Try<Void> result = service.reload("crm");

    assertTrue(result.isSuccess());
    assertTrue(Files.exists(runRoot.resolve("crm/dev-helper/metadata/business-model/order.business.json")));
    verify(devHelperCommandService).exec(
        eq("crm"),
        eq(Commands.REFRESH_METADATA.getCommand()),
        org.mockito.ArgumentMatchers.argThat(params ->
            Long.valueOf(1L).equals(params.get("runtimeGeneration"))
                && "helper-a".equals(params.get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY))
                && "incarnation-a".equals(params.get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY))
                && params.get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY) != null
        )
    );
  }

  private static DevelopWorkspaceContext workspaceContext(Path rootDir,
                                                          Path devRoot,
                                                          Path runRoot,
                                                          Path debugSupportRoot,
                                                          Path developSupportRoot) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return rootDir.toString();
      }

      @Override
      public String getDevRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getRunRootDir() {
        return runRoot.toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupportRoot.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupportRoot.toString();
      }
    };
  }
}
