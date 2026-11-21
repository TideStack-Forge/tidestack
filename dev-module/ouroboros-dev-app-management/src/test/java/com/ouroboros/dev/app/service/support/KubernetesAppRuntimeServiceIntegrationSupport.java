package com.ouroboros.dev.app.service.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.initializer.DataSourceInitializer;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.impl.AppDevHelperContainerServiceImpl;
import com.ouroboros.dev.app.service.impl.AppContainerServiceImpl;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.app.service.support.BaseImageReferenceResolver;
import com.ouroboros.dev.app.service.runtime.AppRuntimePreferenceResolver;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.DevHelperRuntimeSpecFactory;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.app.service.runtime.RuntimeLockOptInInspector;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.workspace.PvcWorkspaceProvisioner;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.service.DefaultDevRuntimeService;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.dev.metadata.provider.HelperMetadataProvider;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.model.AppInfo;

public final class KubernetesAppRuntimeServiceIntegrationSupport {
  private static final String APP_NAME = "crm";
  private static final String PROFILE = "alice";
  private static final String NAMESPACE = "dev-space";
  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC);

  private KubernetesAppRuntimeServiceIntegrationSupport() {
  }

  public static TestAppEnvironment testAppEnvironment(Path tempDir) {
    return new TestAppEnvironment(tempDir);
  }

  public static DevHelperEnvironment devHelperEnvironment(Path tempDir) {
    return new DevHelperEnvironment(tempDir);
  }

  public static final class TestAppEnvironment implements AutoCloseable {
    private final WorkspacePaths workspacePaths;
    private final TestKubernetesMockServer server;
    private final AppContainerServiceImpl service;
    private final RuntimeIdentityKey runtimeKey;

    private TestAppEnvironment(Path tempDir) {
      try {
        this.workspacePaths = WorkspacePaths.create(tempDir);
      } catch (IOException exception) {
        throw new IllegalStateException("failed to initialize workspace", exception);
      }
      this.runtimeKey = RuntimeIdentityKey.of(APP_NAME, PROFILE, RuntimeKind.TEST_APP, BackendType.KUBERNETES);
      this.server = TestKubernetesMockServer.crudHttps();
      this.server.start();
      this.service = createService(workspacePaths, server);
    }

    public AppContainerServiceImpl service() {
      return service;
    }

    public AppContainerRequest request() {
      AppContainerRequest request = new AppContainerRequest();
      request.setAppName(APP_NAME);
      request.setImage("ouroboros/test-app:latest");
      request.setBackendType(BackendType.KUBERNETES.name());
      request.setExposureStrategy(ExposureStrategy.TEMP_PORT.name());
      request.setDatasource(datasource("memory"));
      return request;
    }

    public void markReady() {
      server.getClient().pods()
          .inNamespace(NAMESPACE)
          .resource(new PodBuilder()
              .withNewMetadata()
              .withName(KubernetesResourceNameFactory.deploymentName(runtimeKey) + "-pod")
              .withNamespace(NAMESPACE)
              .withLabels(KubernetesResourceNameFactory.labels(runtimeKey))
              .endMetadata()
              .withNewStatus()
              .withPhase("Running")
              .addNewCondition()
              .withType("Ready")
              .withStatus("True")
              .endCondition()
              .endStatus()
              .build())
          .createOrReplace();
    }

    public void deleteReadyPod() {
      server.getClient().pods()
          .inNamespace(NAMESPACE)
          .withName(KubernetesResourceNameFactory.deploymentName(runtimeKey) + "-pod")
          .delete();
    }

    public String readMountedMetadata(String fileName) {
      try {
        return new String(
            Files.readAllBytes(workspacePaths.mountedMetadataDir().resolve(fileName)),
            StandardCharsets.UTF_8
        );
      } catch (IOException exception) {
        throw new IllegalStateException("failed to read mounted metadata", exception);
      }
    }

    public void writeDevMetadata(String fileName, String content) {
      try {
        Files.write(
            workspacePaths.devMetadataDir().resolve(fileName),
            content.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
      } catch (IOException exception) {
        throw new IllegalStateException("failed to write dev metadata", exception);
      }
    }

    @Override
    public void close() {
      server.close();
    }
  }

  public static final class DevHelperEnvironment implements AutoCloseable {
    private final WorkspacePaths workspacePaths;
    private final RecordingDevHelperCommandService commandService;
    private final TestKubernetesMockServer server;
    private final AnnotationConfigApplicationContext applicationContext;
    private final InMemoryRuntimeStateStore runtimeStateStore;
    private final AppDevHelperContainerServiceImpl service;

    private DevHelperEnvironment(Path tempDir) {
      try {
        this.workspacePaths = WorkspacePaths.create(tempDir);
      } catch (IOException exception) {
        throw new IllegalStateException("failed to initialize workspace", exception);
      }
      this.commandService = new RecordingDevHelperCommandService();
      this.applicationContext = new AnnotationConfigApplicationContext();
      this.runtimeStateStore = new InMemoryRuntimeStateStore();
      this.server = TestKubernetesMockServer.crudHttps();
      this.server.start();
      CacheManager.getCacheManager().clear();
      this.service = createDevHelperService(
          workspacePaths,
          server,
          commandService,
          runtimeStateStore,
          applicationContext
      );
    }

    public AppDevHelperContainerServiceImpl service() {
      return service;
    }

    public AppDevHelperRequest request() {
      AppDevHelperRequest request = new AppDevHelperRequest();
      request.setAppName(APP_NAME);
      request.setImage("ouroboros/dev-helper:latest");
      request.setBackendType(BackendType.KUBERNETES.name());
      return request;
    }

    public Long latestRequestGeneration() {
      return commandService.latestRequestGeneration();
    }

    public int refreshRequestCount() {
      return commandService.refreshRequestCount();
    }

    public void failNextRefreshSend(RuntimeException failure) {
      commandService.failNextExec(failure);
    }

    public void emitHelperOnline(String helperInstanceId, long startedAtEpochMillis) {
      RuntimeIdentityKey key = RuntimeIdentityKey.of(
          APP_NAME,
          "dev-helper",
          RuntimeKind.DEV_HELPER,
          BackendType.KUBERNETES
      );
      String appIncarnationId = runtimeStateStore.findSpec(key)
          .map(spec -> spec.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV))
          .orElseThrow(() -> new IllegalStateException("dev helper runtime spec not found"));
      Map<String, Object> data = new LinkedHashMap<String, Object>();
      MetadataRefreshProtocol.addHelperIdentity(
          data,
          new HelperIdentity(helperInstanceId, startedAtEpochMillis, appIncarnationId)
      );
      commandService.emit(new CommandResult()
          .setAppName(APP_NAME)
          .setCommand(Commands.KNOCK_KNOCK.getCommand())
          .setSuccess(true)
          .setData(data));
    }

    public void emitRefreshSuccess(long runtimeGeneration) {
      CommandRequest request = commandService.latestRequest();
      if (request == null || !Long.valueOf(runtimeGeneration).equals(request.getRuntimeGeneration())) {
        throw new IllegalStateException("metadata refresh request generation mismatch");
      }
      MetadataRefreshSnapshot snapshot = new MetadataRefreshSnapshot()
          .setSchemaVersion(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION)
          .setAppName(request.getAppName())
          .setRuntimeGeneration(request.getRuntimeGeneration())
          .setHelperInstanceId(request.getHelperInstanceId())
          .setHelperStartedAtEpochMillis(request.getHelperStartedAtEpochMillis().longValue())
          .setAppIncarnationId(request.getAppIncarnationId())
          .setRefreshOperationId(request.getRefreshOperationId())
          .setRefreshRevision(1L)
          .setCollectedAtEpochMillis(3000L)
          .setModules(Collections.emptyMap());
      commandService.emit(new CommandResult(request, snapshot));
    }

    public String readMountedDevelopSupport(String fileName) {
      try {
        return new String(
            Files.readAllBytes(workspacePaths.mountedDevelopSupportDir().resolve(fileName)),
            StandardCharsets.UTF_8
        );
      } catch (IOException exception) {
        throw new IllegalStateException("failed to read mounted develop-support file", exception);
      }
    }

    public boolean mountedDevelopSupportExists(String fileName) {
      return Files.exists(workspacePaths.mountedDevelopSupportDir().resolve(fileName));
    }

    public void writeDevelopSupportFile(String fileName, String content) {
      try {
        Path target = workspacePaths.developSupportRoot().resolve(fileName);
        if (target.getParent() != null) {
          Files.createDirectories(target.getParent());
        }
        Files.write(
            target,
            content.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
      } catch (IOException exception) {
        throw new IllegalStateException("failed to write develop-support file", exception);
      }
    }

    @Override
    public void close() {
      applicationContext.close();
      CacheManager.getCacheManager().clear();
      server.close();
    }
  }

  private static AppContainerServiceImpl createService(WorkspacePaths workspacePaths, TestKubernetesMockServer server) {
    ConfigurationService configurationService = configurationService(Collections.<String, Object>emptyMap());
    DevelopWorkspaceContext workspaceContext = workspaceContext(workspacePaths);
    AppManagementService appManagementService = appManagementService(appInfo(APP_NAME, "CRM"));
    ContainerService containerService = mockContainerService();
    AppDataSourceService appDataSourceService = appDataSourceService();
    PlatformDataSourceService platformDataSourceService = platformDataSourceService();
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    InMemoryDebugSessionStore debugSessionStore = new InMemoryDebugSessionStore();
    DevContainerConfig devContainerConfig = new FakeDevContainerConfig(
        configurationService,
        workspaceContext,
        mqConfig(),
        "org.h2.Driver",
        "jdbc:h2:mem:helper"
    );
    KubernetesRuntimeProperties kubernetesProperties = kubernetesProperties(workspacePaths.mountedWorkspaceRoot());
    KubernetesRuntimeResourceFactory resourceFactory = new KubernetesRuntimeResourceFactory(kubernetesProperties, FIXED_CLOCK);
    DevRuntimeService runtimeService = new DefaultDevRuntimeService(
        Collections.singletonList(new KubernetesTestAppRuntimeBackend(
            server.getClient(),
            resourceFactory,
            new KubernetesRuntimeStatusSynchronizer(server.getClient(), kubernetesProperties, DevRuntimeEventPublisher.noop()),
            kubernetesProperties
        )),
        Collections.singletonList(new PvcWorkspaceProvisioner(workspaceContext, kubernetesProperties)),
        runtimeStateStore
    );
    AppRuntimePreferenceResolver preferenceResolver = new AppRuntimePreferenceResolver(configurationService, devRuntimeProperties());
    TestAppRuntimeSpecFactory runtimeSpecFactory = new TestAppRuntimeSpecFactory(
        preferenceResolver,
        appManagementService,
        configurationService,
        devContainerConfig,
        Collections.singletonList(dataSourceInitializer("memory", datasourceValue())),
        new InMemoryRuntimeLockStore(),
        new RuntimeLockOptInInspector(workspaceContext)
    );
    return new AppContainerServiceImpl(
        appManagementService,
        configurationService,
        workspaceContext,
        containerService,
        devContainerConfig,
        appDataSourceService,
        platformDataSourceService,
        runtimeService,
        runtimeStateStore,
        debugSessionStore,
        runtimeSpecFactory,
        new AppRuntimeViewAssembler(new DebugSessionViewAssembler(), new RuntimeImageViewService()),
        new RuntimeImageViewService(),
        new BaseImageQuerySupport(new BaseImageReferenceResolver())
    );
  }

  private static AppDevHelperContainerServiceImpl createDevHelperService(WorkspacePaths workspacePaths,
                                                                         TestKubernetesMockServer server,
                                                                         RecordingDevHelperCommandService commandService,
                                                                         InMemoryRuntimeStateStore runtimeStateStore,
                                                                         AnnotationConfigApplicationContext applicationContext) {
    ConfigurationService configurationService = configurationService(Collections.<String, Object>emptyMap());
    DevelopWorkspaceContext workspaceContext = workspaceContext(workspacePaths);
    AppManagementService appManagementService = appManagementService(appInfo(APP_NAME, "CRM"));
    DevContainerConfig devContainerConfig = new FakeDevContainerConfig(
        configurationService,
        workspaceContext,
        mqConfig(),
        "org.h2.Driver",
        "jdbc:h2:mem:helper"
    );
    KubernetesRuntimeProperties kubernetesProperties = kubernetesProperties(workspacePaths.mountedWorkspaceRoot());
    KubernetesDevHelperRuntimeBackend runtimeBackend = new KubernetesDevHelperRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(kubernetesProperties, FIXED_CLOCK),
        kubernetesProperties,
        commandService,
        new KubernetesRuntimeStatusSynchronizer(server.getClient(), kubernetesProperties, DevRuntimeEventPublisher.noop()),
        DevRuntimeEventPublisher.noop(),
        null,
        applicationContext
    );
    DevRuntimeService runtimeService = new DefaultDevRuntimeService(
        Collections.singletonList(runtimeBackend),
        Collections.singletonList(new PvcWorkspaceProvisioner(workspaceContext, kubernetesProperties)),
        runtimeStateStore
    );
    DevHelperRuntimeSpecFactory runtimeSpecFactory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(configurationService, devRuntimeProperties()),
        appManagementService,
        devContainerConfig
    );
    ContainerService containerService = mockContainerService();
    AppDevHelperContainerServiceImpl service = new AppDevHelperContainerServiceImpl(
        workspaceContext,
        runtimeService,
        runtimeStateStore,
        runtimeSpecFactory,
        commandService,
        devContainerConfig,
        new RuntimeImageViewService(),
        new AppDevHelperLogService(new AppLogCacheService(configurationService)),
        applicationContext
    );
    HelperMetadataProvider metadataProvider = new HelperMetadataProvider(commandService, applicationContext);
    applicationContext.registerBean(KubernetesDevHelperRuntimeBackend.class, () -> runtimeBackend);
    applicationContext.registerBean(AppDevHelperContainerServiceImpl.class, () -> service);
    applicationContext.registerBean(HelperMetadataProvider.class, () -> metadataProvider);
    applicationContext.refresh();
    return service;
  }

  private static ContainerService mockContainerService() {
    ContainerService containerService = org.mockito.Mockito.mock(ContainerService.class);
    org.mockito.Mockito.when(containerService.getAllImageNames(org.mockito.Mockito.anyString()))
        .thenReturn(Either.right(Collections.<String>emptyList()));
    return containerService;
  }

  private static KubernetesRuntimeProperties kubernetesProperties(Path mountedWorkspaceRoot) {
    KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
    properties.setNamespace(NAMESPACE);
    properties.setSharedPvcName("dev-runtime-pvc");
    properties.setMountedWorkspaceRoot(mountedWorkspaceRoot.toString());
    properties.setIngressHostSuffix("develop.example.com");
    return properties;
  }

  private static DevRuntimeProperties devRuntimeProperties() {
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    return properties;
  }

  private static AppInfo appInfo(String appName, String title) {
    return new AppInfo()
        .setName(appName)
        .setTitle(title)
        .setDescription(title + " description");
  }

  private static AppManagementService appManagementService(AppInfo appInfo) {
    return new AppManagementService() {
      @Override
      public Try<Void> createApp(AppInfo app) {
        return unsupported();
      }

      @Override
      public Try<Void> updateApp(String appName, AppInfo app) {
        return unsupported();
      }

      @Override
      public Try<Void> deleteApp(String appName) {
        return unsupported();
      }

      @Override
      public Try<Void> renameApp(String appName, String newName) {
        return unsupported();
      }

      @Override
      public Try<java.util.List<AppInfoWithMeta>> listApps(String category) {
        return Try.success(Collections.<AppInfoWithMeta>emptyList());
      }

      @Override
      public Try<java.util.List<AppInfoWithMeta>> listApps() {
        return Try.success(Collections.<AppInfoWithMeta>emptyList());
      }

      @Override
      public boolean appExists(String appName) {
        return appInfo != null && java.util.Objects.equals(appInfo.getName(), appName);
      }

      @Override
      public Try<AppInfo> getAppInfo(String appName) {
        return Try.success(appInfo);
      }

      @Override
      public Try<java.io.InputStream> exportApp(String appName, boolean includeUncommitted, boolean includeGitRepo) {
        return unsupported();
      }

      @Override
      public Try<Void> importApp(java.io.InputStream zipInputStream) {
        return unsupported();
      }
    };
  }

  private static ConfigurationService configurationService(Map<String, Object> values) {
    return new ConfigurationService() {
      @Override
      public <T> java.util.Optional<T> get(String key) {
        return java.util.Optional.ofNullable((T) values.get(key));
      }

      @Override
      public Map<String, Object> getValues(ConfigScope scope) {
        return Collections.emptyMap();
      }
    };
  }

  private static DataSourceInitializer dataSourceInitializer(String type, ContainerDatasource datasource) {
    return new DataSourceInitializer() {
      @Override
      public String getTypeName() {
        return type;
      }

      @Override
      public Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                           AppContainerRequest.DataSourceDefine dataSourceDefine) {
        return Try.success(new ContainerDatasource()
            .setDriverClassName(datasource.getDriverClassName())
            .setUrl(datasource.getUrl())
            .setUsername(datasource.getUsername())
            .setPassword(datasource.getPassword()));
      }
    };
  }

  private static ContainerDatasource datasourceValue() {
    return new ContainerDatasource()
        .setDriverClassName("org.h2.Driver")
        .setUrl("jdbc:h2:mem:crm")
        .setUsername("sa")
        .setPassword("pw");
  }

  private static AppContainerRequest.DataSourceDefine datasource(String type) {
    AppContainerRequest.DataSourceDefine datasource = new AppContainerRequest.DataSourceDefine();
    datasource.setType(type);
    return datasource;
  }

  private static Map<String, String> mqConfig() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("SPRING_RABBITMQ_HOST", "mq.example.internal");
    values.put("SPRING_RABBITMQ_PORT", "5672");
    values.put("SPRING_RABBITMQ_USERNAME", "dev");
    values.put("SPRING_RABBITMQ_PASSWORD", "secret");
    return values;
  }

  private static DevelopWorkspaceContext workspaceContext(WorkspacePaths workspacePaths) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return workspacePaths.root().toString();
      }

      @Override
      public String getDevRootDir() {
        return workspacePaths.devRoot().toString();
      }

      @Override
      public String getRunRootDir() {
        return workspacePaths.runRoot().toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return workspacePaths.debugSupportRoot().toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return workspacePaths.developSupportRoot().toString();
      }
    };
  }

  private static AppDataSourceService appDataSourceService() {
    return new AppDataSourceService() {
      @Override
      public Try<AppDataSourceModel> add(AppDataSourceModel model) {
        return unsupported();
      }

      @Override
      public Try<Boolean> delete(String primaryKey) {
        return unsupported();
      }

      @Override
      public Try<Boolean> update(String primaryKey, AppDataSourceModel model) {
        return unsupported();
      }

      @Override
      public java.util.Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.List<AppDataSourceModel> getByAppName(String appName) {
        return Collections.emptyList();
      }

      @Override
      public java.util.Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String appDataSourceName) {
        return java.util.Optional.empty();
      }

      @Override
      public Long count(Map<String, Object> where) {
        return Long.valueOf(0L);
      }

      @Override
      public java.util.List<AppDataSourceModel> query(Map<String, Object> statement) {
        return Collections.emptyList();
      }

      @Override
      public java.util.List<AppDataSourceModel> getAll() {
        return Collections.emptyList();
      }

      @Override
      public java.util.List<TableInfo> getTableInfo(String appName, String dataStationName, String envName) {
        return Collections.emptyList();
      }
    };
  }

  private static PlatformDataSourceService platformDataSourceService() {
    return new PlatformDataSourceService() {
      @Override
      public Try<PlatformDataSourceModel> add(PlatformDataSourceModel model) {
        return unsupported();
      }

      @Override
      public Try<Boolean> delete(String primaryKey) {
        return unsupported();
      }

      @Override
      public Try<Boolean> update(String primaryKey, PlatformDataSourceModel model) {
        return unsupported();
      }

      @Override
      public java.util.Optional<PlatformDataSourceModel> getByPrimaryKey(String primaryKey) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Optional<PlatformDataSourceModel> getByName(String name) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.List<PlatformDataSourceModel> getByNames(java.util.List<String> names) {
        return Collections.emptyList();
      }

      @Override
      public Long count(Map<String, Object> where) {
        return Long.valueOf(0L);
      }

      @Override
      public java.util.List<PlatformDataSourceModel> query(Map<String, Object> statement) {
        return Collections.emptyList();
      }

      @Override
      public java.util.List<PlatformDataSourceModel> getAll() {
        return Collections.emptyList();
      }

      @Override
      public java.util.List<TableInfo> getTables(String platformDataSourceName, String envName) {
        return Collections.emptyList();
      }
    };
  }

  private static <T> Try<T> unsupported() {
    return Try.failure(new BusinessException("not used in test"));
  }

  private static final class FakeDevContainerConfig extends DevContainerConfig {
    private final String mothershipImageName;
    private final String networkName;
    private final Map<String, String> mqConfig;
    private final String memoryDatasourceDriverClass;
    private final String memoryDatasourceUrl;

    private FakeDevContainerConfig(ConfigurationService configurationService,
                                   DevelopWorkspaceContext workspaceContext,
                                   Map<String, String> mqConfig,
                                   String memoryDatasourceDriverClass,
                                   String memoryDatasourceUrl) {
      super(configurationService, org.mockito.Mockito.mock(ContainerService.class), workspaceContext, new StandardEnvironment());
      this.mothershipImageName = "ouroboros-mothership";
      this.networkName = "ouroboros-dev";
      this.mqConfig = new LinkedHashMap<String, String>(mqConfig);
      this.memoryDatasourceDriverClass = memoryDatasourceDriverClass;
      this.memoryDatasourceUrl = memoryDatasourceUrl;
    }

    @Override
    public String getMothershipImageName() {
      return mothershipImageName;
    }

    @Override
    public Map<String, String> getMQConfig() {
      return Collections.unmodifiableMap(mqConfig);
    }

    @Override
    public String getNetworkName() {
      return networkName;
    }

    @Override
    public String getTestAppMemoryDatasourceDriverClass() {
      return memoryDatasourceDriverClass;
    }

    @Override
    public String getTestAppMemoryDatasourceUrl() {
      return memoryDatasourceUrl;
    }
  }

  private static final class WorkspacePaths {
    private final Path root;
    private final Path devRoot;
    private final Path runRoot;
    private final Path debugSupportRoot;
    private final Path developSupportRoot;
    private final Path mountedWorkspaceRoot;

    private WorkspacePaths(Path root,
                           Path devRoot,
                           Path runRoot,
                           Path debugSupportRoot,
                           Path developSupportRoot,
                           Path mountedWorkspaceRoot) {
      this.root = root;
      this.devRoot = devRoot;
      this.runRoot = runRoot;
      this.debugSupportRoot = debugSupportRoot;
      this.developSupportRoot = developSupportRoot;
      this.mountedWorkspaceRoot = mountedWorkspaceRoot;
    }

    static WorkspacePaths create(Path tempDir) throws IOException {
      Path root = tempDir.resolve("workspace");
      Path devRoot = root.resolve("dev");
      Path runRoot = root.resolve("run");
      Path debugSupportRoot = root.resolve("debug-support");
      Path developSupportRoot = root.resolve("develop-support");
      Path mountedWorkspaceRoot = tempDir.resolve("mounted-workspace");

      Files.createDirectories(devRoot.resolve(APP_NAME));
      Files.createDirectories(runRoot);
      Files.createDirectories(debugSupportRoot);
      Files.createDirectories(developSupportRoot);
      Files.createDirectories(mountedWorkspaceRoot);

      Files.write(devRoot.resolve(APP_NAME).resolve("app.json"), "{\"version\":1}".getBytes(StandardCharsets.UTF_8));
      Files.write(debugSupportRoot.resolve("agent.jar"), "debug-agent".getBytes(StandardCharsets.UTF_8));
      Files.write(developSupportRoot.resolve("helper.json"), "seed-v1".getBytes(StandardCharsets.UTF_8));

      return new WorkspacePaths(root, devRoot, runRoot, debugSupportRoot, developSupportRoot, mountedWorkspaceRoot);
    }

    Path root() {
      return root;
    }

    Path devRoot() {
      return devRoot;
    }

    Path runRoot() {
      return runRoot;
    }

    Path debugSupportRoot() {
      return debugSupportRoot;
    }

    Path developSupportRoot() {
      return developSupportRoot;
    }

    Path mountedWorkspaceRoot() {
      return mountedWorkspaceRoot;
    }

    Path devMetadataDir() {
      return devRoot.resolve(APP_NAME);
    }

    Path mountedMetadataDir() {
      return mountedWorkspaceRoot.resolve("workspace/run/" + APP_NAME + "/" + PROFILE + "/metadata");
    }

    Path mountedDevelopSupportDir() {
      return mountedWorkspaceRoot.resolve("workspace/run/" + APP_NAME + "/dev-helper/extend");
    }
  }

  private static final class RecordingDevHelperCommandService implements DevHelperCommandService {
    private final List<CommandRequest> requests = new ArrayList<CommandRequest>();
    private final List<Consumer<CommandResult>> globalListeners = new CopyOnWriteArrayList<Consumer<CommandResult>>();
    private final Map<String, List<Consumer<CommandResult>>> commandListeners = new LinkedHashMap<String, List<Consumer<CommandResult>>>();
    private final List<RuntimeException> execFailures = new ArrayList<RuntimeException>();

    void failNextExec(RuntimeException failure) {
      execFailures.add(failure);
    }

    int refreshRequestCount() {
      return (int) requests.stream()
          .filter(request -> Commands.REFRESH_METADATA.getCommand().equals(request.getCommand()))
          .count();
    }

    Long latestRequestGeneration() {
      CommandRequest request = latestRequest();
      return request == null ? null : request.getRuntimeGeneration();
    }

    CommandRequest latestRequest() {
      return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    void emit(CommandResult result) {
      for (Consumer<CommandResult> listener : globalListeners) {
        listener.accept(result);
      }
      List<Consumer<CommandResult>> listeners = commandListeners.get(result.getCommand());
      if (listeners == null) {
        return;
      }
      for (Consumer<CommandResult> listener : listeners) {
        listener.accept(result);
      }
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params) {
      return execRough(APP_NAME, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params, long timeout) {
      return execRough(APP_NAME, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params) {
      return execRough(appName, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params, long timeout) {
      return record(appName, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params) {
      return exec(APP_NAME, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params, long timeout) {
      return exec(APP_NAME, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params) {
      return exec(appName, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params, long timeout) {
      return record(appName, command, params, timeout);
    }

    @Override
    public void registerGlobalResultListener(Consumer<CommandResult> listener) {
      globalListeners.add(listener);
    }

    @Override
    public void unregisterGlobalResultListener(Consumer<CommandResult> listener) {
      globalListeners.remove(listener);
    }

    @Override
    public void registerCommandResultListener(String command, Consumer<CommandResult> listener) {
      listenersFor(command).add(listener);
    }

    @Override
    public void unregisterCommandResultListener(String command, Consumer<CommandResult> listener) {
      listenersFor(command).remove(listener);
    }

    @Override
    public StreamChannel createStreamChannel(String channelName, Boolean strictInOrder) {
      return new StreamChannel() {
        @Override
        public void send(String command, Map<String, Object> params) {
        }

        @Override
        public void send(String command, Map<String, Object> params, long timeout) {
        }

        @Override
        public void send(String appName, String command, Map<String, Object> params) {
        }

        @Override
        public void send(String appName, String command, Map<String, Object> params, long timeout) {
        }

        @Override
        public void subscribe(Consumer<CommandResult> handler) {
        }

        @Override
        public void unsubscribe(Consumer<CommandResult> handler) {
        }
      };
    }

    @Override
    public void destroyStreamChannel(String channelName) {
    }

    private CompletableFuture<CommandResult> record(String appName, String command, Map<String, Object> params, long timeout) {
      CommandRequest request = new CommandRequest()
          .setAppName(appName)
          .setCommand(command)
          .setParams(params)
          .setTimeout(timeout)
          .setRuntimeGeneration(runtimeGeneration(params))
          .setHelperInstanceId(textParameter(params, MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY))
          .setHelperStartedAtEpochMillis(longParameter(
              params,
              MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY
          ))
          .setAppIncarnationId(textParameter(params, MetadataRefreshProtocol.APP_INCARNATION_ID_KEY))
          .setRefreshOperationId(textParameter(params, MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY));
      requests.add(request);
      if (!execFailures.isEmpty()) {
        throw execFailures.remove(0);
      }
      return CompletableFuture.completedFuture(new CommandResult(request));
    }

    private Long runtimeGeneration(Map<String, Object> params) {
      return longParameter(params, "runtimeGeneration");
    }

    private Long longParameter(Map<String, Object> params, String key) {
      if (params == null) {
        return null;
      }
      Object value = params.get(key);
      if (value instanceof Number) {
        return Long.valueOf(((Number) value).longValue());
      }
      return null;
    }

    private String textParameter(Map<String, Object> params, String key) {
      if (params == null || params.get(key) == null) {
        return null;
      }
      return params.get(key).toString();
    }

    private List<Consumer<CommandResult>> listenersFor(String command) {
      if (!commandListeners.containsKey(command)) {
        commandListeners.put(command, new CopyOnWriteArrayList<Consumer<CommandResult>>());
      }
      return commandListeners.get(command);
    }
  }

  private static final class TestKubernetesMockServer implements AutoCloseable {
    private final KubernetesMockServer server;
    private NamespacedKubernetesClient client;

    private TestKubernetesMockServer(KubernetesMockServer server) {
      this.server = server;
    }

    private static TestKubernetesMockServer crudHttps() {
      Map<ServerRequest, Queue<ServerResponse>> responses = new LinkedHashMap<ServerRequest, Queue<ServerResponse>>();
      KubernetesMockServer server = new KubernetesMockServer(
          new Context(Serialization.jsonMapper()),
          new MockWebServer(),
          responses,
          new KubernetesMixedDispatcher(responses),
          true
      );
      return new TestKubernetesMockServer(server);
    }

    private void start() {
      server.init();
      client = server.createClient();
    }

    private NamespacedKubernetesClient getClient() {
      return client;
    }

    @Override
    public void close() {
      try {
        server.destroy();
      } finally {
        if (client != null) {
          client.close();
        }
      }
    }
  }
}
