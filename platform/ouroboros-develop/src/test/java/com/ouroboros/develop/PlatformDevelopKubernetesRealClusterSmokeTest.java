package com.ouroboros.develop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.controller.AppContainerController;
import com.ouroboros.dev.app.controller.AppDevHelperController;
import com.ouroboros.dev.app.initializer.DataSourceInitializer;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.impl.AppContainerServiceImpl;
import com.ouroboros.dev.app.service.impl.AppDevHelperContainerServiceImpl;
import com.ouroboros.dev.app.service.runtime.AppRuntimePreferenceResolver;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.AuthenticationPrincipalTestAppProfileResolver;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.app.service.runtime.DevHelperRuntimeSpecFactory;
import com.ouroboros.dev.app.service.runtime.RuntimeLockOptInInspector;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.app.service.runtime.LogicFlowDebugHelperArtifactMaterializer;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.app.service.support.BaseImageReferenceResolver;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.workspace.PvcWorkspaceProvisioner;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport.MapBackedConfigurationService;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport.MemoryDataSourceInitializer;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport.RealClusterSettings;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport.RecordingDevHelperCommandService;
import com.ouroboros.develop.support.PlatformDevelopKubernetesSmokeSupport.WorkspaceFixture;
import com.ouroboros.model.AppInfo;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;

@SpringBootTest(
    classes = PlatformDevelopKubernetesRealClusterSmokeTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "K8S_PLATFORM_REAL_SMOKE", matches = "(?i:true|1|yes)")
class PlatformDevelopKubernetesRealClusterSmokeTest {
  private static final WorkspaceFixture WORKSPACE = PlatformDevelopKubernetesSmokeSupport.createWorkspaceFixture();

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private KubernetesClient kubernetesClient;

  @Autowired
  private RecordingDevHelperCommandService commandService;

  @LocalServerPort
  private int port;

  private RealClusterSettings realCluster;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("ouroboros.dev.runtime.default-backend", () -> "KUBERNETES");
    registry.add("ouroboros.dev.runtime.kubernetes.namespace",
        () -> PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_NAMESPACE", PlatformDevelopKubernetesSmokeSupport.NAMESPACE));
    registry.add("ouroboros.dev.runtime.kubernetes.shared-pvc-name",
        () -> PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_PVC", "dev-runtime-pvc"));
    registry.add("ouroboros.dev.runtime.kubernetes.mounted-workspace-root", WORKSPACE::mountedWorkspaceRoot);
    registry.add("ouroboros.dev.runtime.kubernetes.helper-ready-timeout", () -> "30s");
    registry.add("ouroboros.dev.runtime.kubernetes.ingress-host-suffix",
        () -> PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_INGRESS_SUFFIX", "develop.example.com"));
    registry.add("ouroboros-dev.container.default-network-name", () -> "ouroboros-dev");
  }

  @BeforeEach
  void setUp() {
    realCluster = PlatformDevelopKubernetesSmokeSupport.realClusterSettings();
    WORKSPACE.reset();
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    PlatformDevelopKubernetesSmokeSupport.cleanupRuntimeResources(
        kubernetesClient,
        realCluster.getNamespace(),
        PlatformDevelopKubernetesSmokeSupport.testAppIdentityKey()
    );
    PlatformDevelopKubernetesSmokeSupport.cleanupRuntimeResources(
        kubernetesClient,
        realCluster.getNamespace(),
        PlatformDevelopKubernetesSmokeSupport.devHelperIdentityKey()
    );
  }

  @AfterEach
  void cleanupCluster() {
    CurrentSecurityContextHolder.clearDelegate();
    if (realCluster == null) {
      return;
    }
    PlatformDevelopKubernetesSmokeSupport.cleanupRuntimeResources(
        kubernetesClient,
        realCluster.getNamespace(),
        PlatformDevelopKubernetesSmokeSupport.testAppIdentityKey()
    );
    PlatformDevelopKubernetesSmokeSupport.cleanupRuntimeResources(
        kubernetesClient,
        realCluster.getNamespace(),
        PlatformDevelopKubernetesSmokeSupport.devHelperIdentityKey()
    );
  }

  @AfterAll
  void cleanupWorkspace() {
    WORKSPACE.close();
  }

  @Test
  void shouldExposeKubernetesRuntimeThroughRealHttpAndCluster() throws Exception {
    JsonNode createResponse = exchange(
        "/api/app/container/create",
        HttpMethod.POST,
        body(
            "appName", PlatformDevelopKubernetesSmokeSupport.APP_NAME,
            "image", realCluster.getTestAppImage(),
            "backendType", "KUBERNETES",
            "exposureStrategy", "TEMP_PORT",
            "datasource", body("type", "memory", "reinitialize", Boolean.FALSE),
            "configFile", body("isReset", Boolean.FALSE)
        )
    );

    assertEquals(0, createResponse.path("code").asInt(), createResponse.toString());
    String runtimeId = createResponse.path("data").asText();
    assertFalse(runtimeId.isEmpty());

    JsonNode launchingStatus = exchange(statusPath(), HttpMethod.GET, null).path("data");
    assertEquals(ContainerStatus.LAUNCHING.getValue(), launchingStatus.path("status").asText());
    assertEquals("KUBERNETES", launchingStatus.path("backendType").asText());

    PlatformDevelopKubernetesSmokeSupport.waitForTestAppReady(kubernetesClient, realCluster);

    JsonNode runningStatus = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "platform test-app status=RUNNING",
        realCluster.getTimeout(),
        () -> {
          JsonNode payload = exchange(statusPath(), HttpMethod.GET, null).path("data");
          return ContainerStatus.RUNNING.getValue().equals(payload.path("status").asText()) ? payload : null;
        }
    );

    assertEquals(ContainerStatus.RUNNING.getValue(), runningStatus.path("status").asText());
    assertTrue(runningStatus.path("webEndpoint").asText().startsWith("http://127.0.0.1:"));
    assertTrue(runningStatus.path("webPort").asInt() > 0);
    assertTrue(runningStatus.path("runtimeAccess").path("webAccess").path("endpoint").asText().startsWith("http://127.0.0.1:"));
    assertTrue(runningStatus.path("backendApiCapability").path("enabled").asBoolean());
    assertEquals("SESSION_TUNNEL", runningStatus.path("backendApiCapability").path("strategy").asText());
    assertTrue(runningStatus.path("backendApiCapability").path("sessionOpenable").asBoolean());

    WORKSPACE.writeAppMetadata("app.json", "{\"app\":\"crm\",\"version\":\"real-v2\"}");

    JsonNode reloadResponse = exchange("/api/app/container/" + runtimeId + "/reload", HttpMethod.POST, null);
    assertEquals(0, reloadResponse.path("code").asInt());

    PlatformDevelopKubernetesSmokeSupport.waitForCondition(
        "mounted metadata refresh",
        realCluster.getTimeout(),
        () -> WORKSPACE.mountedMetadataMatches(PlatformDevelopKubernetesSmokeSupport.TEST_PROFILE, "app.json", "{\"app\":\"crm\",\"version\":\"real-v2\"}")
    );

    JsonNode helperCreateResponse = exchange(
        "/api/dev/" + PlatformDevelopKubernetesSmokeSupport.APP_NAME + "/helper",
        HttpMethod.POST,
        body("image", realCluster.getHelperImage(), "backendType", "KUBERNETES")
    );

    assertEquals(0, helperCreateResponse.path("code").asInt());
    assertFalse(helperCreateResponse.path("data").asText().isEmpty());

    JsonNode helperLaunchingStatus = exchange(helperStatusPath(), HttpMethod.GET, null).path("data");
    assertEquals(ContainerStatus.LAUNCHING.getValue(), helperLaunchingStatus.path("status").asText());

    Long firstGeneration = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "first helper runtime generation",
        realCluster.getTimeout(),
        () -> commandService.latestRuntimeGeneration(PlatformDevelopKubernetesSmokeSupport.APP_NAME)
    );
    assertEquals(Long.valueOf(1L), firstGeneration);

    PlatformDevelopKubernetesSmokeSupport.waitForHelperDeployment(
        kubernetesClient,
        realCluster.getNamespace(),
        realCluster.getTimeout()
    );
    PlatformDevelopKubernetesSmokeSupport.emitRefreshSuccess(commandService, firstGeneration);

    JsonNode helperRunningStatus = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "platform helper status=RUNNING",
        realCluster.getTimeout(),
        () -> {
          JsonNode payload = exchange(helperStatusPath(), HttpMethod.GET, null).path("data");
          return ContainerStatus.RUNNING.getValue().equals(payload.path("status").asText()) ? payload : null;
        }
    );

    assertEquals(imageVersionOf(realCluster.getHelperImage()), helperRunningStatus.path("imageVersion").asText());
    assertNotNull(helperRunningStatus.path("lastLoadTime").textValue());

    WORKSPACE.writeDevelopSupportFile("helper.txt", "helper-support-real-v2");

    JsonNode helperReloadResponse = exchange(helperReloadPath(), HttpMethod.POST, null);
    assertEquals(0, helperReloadResponse.path("code").asInt());

    JsonNode helperReloadingStatus = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "platform helper status=LAUNCHING",
        realCluster.getTimeout(),
        () -> {
          JsonNode payload = exchange(helperStatusPath(), HttpMethod.GET, null).path("data");
          return ContainerStatus.LAUNCHING.getValue().equals(payload.path("status").asText()) ? payload : null;
        }
    );
    assertEquals(ContainerStatus.LAUNCHING.getValue(), helperReloadingStatus.path("status").asText());

    Long secondGeneration = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "second helper runtime generation",
        realCluster.getTimeout(),
        () -> {
          Long runtimeGeneration = commandService.latestRuntimeGeneration(PlatformDevelopKubernetesSmokeSupport.APP_NAME);
          return runtimeGeneration != null && runtimeGeneration.longValue() > firstGeneration.longValue() ? runtimeGeneration : null;
        }
    );
    assertEquals(Long.valueOf(2L), secondGeneration);

    PlatformDevelopKubernetesSmokeSupport.emitRefreshSuccess(commandService, secondGeneration);

    JsonNode helperRunningAgainStatus = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "platform helper status=RUNNING after reload",
        realCluster.getTimeout(),
        () -> {
          JsonNode payload = exchange(helperStatusPath(), HttpMethod.GET, null).path("data");
          return ContainerStatus.RUNNING.getValue().equals(payload.path("status").asText()) ? payload : null;
        }
    );
    assertEquals(ContainerStatus.RUNNING.getValue(), helperRunningAgainStatus.path("status").asText());

    PlatformDevelopKubernetesSmokeSupport.waitForCondition(
        "mounted helper support refresh",
        realCluster.getTimeout(),
        () -> WORKSPACE.mountedDevelopSupportMatches("helper.txt", "helper-support-real-v2")
    );

    JsonNode deleteResponse = exchange("/api/app/container/" + runtimeId, HttpMethod.DELETE, null);
    assertEquals(0, deleteResponse.path("code").asInt());

    JsonNode deletedStatus = PlatformDevelopKubernetesSmokeSupport.waitForValue(
        "platform test-app status=NOT_FOUND",
        realCluster.getTimeout(),
        () -> {
          JsonNode payload = exchange(statusPath(), HttpMethod.GET, null).path("data");
          return ContainerStatus.NOT_FOUND.getValue().equals(payload.path("status").asText()) ? payload : null;
        }
    );
    assertEquals(ContainerStatus.NOT_FOUND.getValue(), deletedStatus.path("status").asText());

    JsonNode helperDeleteResponse = exchange(helperDeletePath(), HttpMethod.DELETE, null);
    assertEquals(0, helperDeleteResponse.path("code").asInt());
  }

  private JsonNode exchange(String path, HttpMethod method, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response = restTemplate.exchange(
        "http://127.0.0.1:" + port + path,
        method,
        new HttpEntity<Object>(body, headers),
        String.class
    );
    return Try.of(() -> objectMapper.readTree(response.getBody())).getOrElseThrow(throwable -> new IllegalStateException(throwable));
  }

  private String statusPath() {
    return "/api/app/container/" + PlatformDevelopKubernetesSmokeSupport.APP_NAME + "/" + PlatformDevelopKubernetesSmokeSupport.TEST_PROFILE + "/status";
  }

  private String helperStatusPath() {
    return "/api/dev/" + PlatformDevelopKubernetesSmokeSupport.APP_NAME + "/helper/status";
  }

  private String helperReloadPath() {
    return "/api/dev/" + PlatformDevelopKubernetesSmokeSupport.APP_NAME + "/helper/reload";
  }

  private String helperDeletePath() {
    return "/api/dev/" + PlatformDevelopKubernetesSmokeSupport.APP_NAME + "/helper";
  }

  private static Map<String, Object> body(Object... entries) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    for (int index = 0; index < entries.length; index += 2) {
      values.put(String.valueOf(entries[index]), entries[index + 1]);
    }
    return values;
  }

  private static String imageVersionOf(String image) {
    if (image == null || !image.contains(":")) {
      return null;
    }
    return image.substring(image.lastIndexOf(':') + 1);
  }

  @org.springframework.boot.SpringBootConfiguration
  @ImportAutoConfiguration({
      ConfigurationPropertiesAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      ServletWebServerFactoryAutoConfiguration.class,
      DispatcherServletAutoConfiguration.class,
      ErrorMvcAutoConfiguration.class,
      WebMvcAutoConfiguration.class
  })
  @Import({
      DevRuntimeCoreAutoConfiguration.class,
      AppContainerController.class,
      AppDevHelperController.class,
      com.ouroboros.dev.app.service.impl.AppConfigService.class,
      AppContainerServiceImpl.class,
      AppDevHelperContainerServiceImpl.class,
      com.ouroboros.dev.app.service.impl.AppEndpointService.class,
      com.ouroboros.dev.app.service.impl.AppImageService.class,
      com.ouroboros.dev.app.service.impl.AppLifecycleService.class,
      com.ouroboros.dev.app.service.impl.AppRuntimeQueryService.class,
      AppRuntimePreferenceResolver.class,
      AppRuntimeInitService.class,
      AuthenticationPrincipalTestAppProfileResolver.class,
      DebugSessionViewAssembler.class,
      AppRuntimeViewAssembler.class,
      TestAppRuntimeSpecFactory.class,
      DevHelperRuntimeSpecFactory.class,
      LogicFlowDebugHelperArtifactMaterializer.class,
      DevContainerConfig.class,
      TestBeans.class
  })
  static class TestApplication {
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class TestBeans {
    @Bean
    KubernetesClient kubernetesClient() {
      return PlatformDevelopKubernetesSmokeSupport.createRealClusterClient();
    }

    @Bean
    KubernetesRuntimeProperties kubernetesRuntimeProperties() {
      KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
      properties.setNamespace(PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_NAMESPACE", PlatformDevelopKubernetesSmokeSupport.NAMESPACE));
      properties.setSharedPvcName(PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_PVC", "dev-runtime-pvc"));
      properties.setMountedWorkspaceRoot(WORKSPACE.mountedWorkspaceRoot());
      properties.setHelperReadyTimeout(java.time.Duration.ofSeconds(30));
      properties.setIngressHostSuffix(PlatformDevelopKubernetesSmokeSupport.realClusterEnvOrDefault("K8S_SMOKE_INGRESS_SUFFIX", "develop.example.com"));
      return properties;
    }

    @Bean
    PvcWorkspaceProvisioner pvcWorkspaceProvisioner(DevelopWorkspaceContext developWorkspaceContext,
                                                    KubernetesRuntimeProperties kubernetesRuntimeProperties) {
      return new PvcWorkspaceProvisioner(developWorkspaceContext, kubernetesRuntimeProperties);
    }

    @Bean
    KubernetesRuntimeResourceFactory kubernetesRuntimeResourceFactory(KubernetesRuntimeProperties kubernetesRuntimeProperties) {
      return new KubernetesRuntimeResourceFactory(kubernetesRuntimeProperties);
    }

    @Bean
    KubernetesRuntimeStatusSynchronizer kubernetesRuntimeStatusSynchronizer(KubernetesClient kubernetesClient,
                                                                            KubernetesRuntimeProperties kubernetesRuntimeProperties,
                                                                            DevRuntimeEventPublisher devRuntimeEventPublisher) {
      return new KubernetesRuntimeStatusSynchronizer(kubernetesClient, kubernetesRuntimeProperties, devRuntimeEventPublisher);
    }

    @Bean
    KubernetesTestAppRuntimeBackend kubernetesTestAppRuntimeBackend(KubernetesClient kubernetesClient,
                                                                    KubernetesRuntimeResourceFactory kubernetesRuntimeResourceFactory,
                                                                    KubernetesRuntimeStatusSynchronizer kubernetesRuntimeStatusSynchronizer,
                                                                    KubernetesRuntimeProperties kubernetesRuntimeProperties) {
      return new KubernetesTestAppRuntimeBackend(
          kubernetesClient,
          kubernetesRuntimeResourceFactory,
          kubernetesRuntimeStatusSynchronizer,
          kubernetesRuntimeProperties
      );
    }

    @Bean
    KubernetesDevHelperRuntimeBackend kubernetesDevHelperRuntimeBackend(KubernetesClient kubernetesClient,
                                                                        KubernetesRuntimeResourceFactory kubernetesRuntimeResourceFactory,
                                                                        KubernetesRuntimeProperties kubernetesRuntimeProperties,
                                                                        RecordingDevHelperCommandService devHelperCommandService,
                                                                        KubernetesRuntimeStatusSynchronizer kubernetesRuntimeStatusSynchronizer) {
      return new KubernetesDevHelperRuntimeBackend(
          kubernetesClient,
          kubernetesRuntimeResourceFactory,
          kubernetesRuntimeProperties,
          devHelperCommandService,
          kubernetesRuntimeStatusSynchronizer
      );
    }

    @Bean
    RuntimeLockOptInInspector runtimeLockOptInInspector(DevelopWorkspaceContext developWorkspaceContext) {
      return new RuntimeLockOptInInspector(developWorkspaceContext);
    }

    @Bean
    DevelopWorkspaceContext developWorkspaceContext() {
      return WORKSPACE.workspaceContext();
    }

    @Bean
    ConfigurationService configurationService() {
      return new MapBackedConfigurationService(PlatformDevelopKubernetesSmokeSupport.defaultConfigurationValues());
    }

    @Bean
    AppLogCacheService appLogCacheService(ConfigurationService configurationService) {
      return new AppLogCacheService(configurationService);
    }

    @Bean
    AppDevHelperLogService appDevHelperLogService(AppLogCacheService appLogCacheService) {
      return new AppDevHelperLogService(appLogCacheService);
    }

    @Bean
    AppManagementService appManagementService() {
      AppManagementService appManagementService = Mockito.mock(AppManagementService.class);
      Mockito.when(appManagementService.getAppInfo(PlatformDevelopKubernetesSmokeSupport.APP_NAME))
          .thenReturn(Try.success(new AppInfo()
              .setName(PlatformDevelopKubernetesSmokeSupport.APP_NAME)
              .setTitle(PlatformDevelopKubernetesSmokeSupport.APP_TITLE)
              .setDescription("platform real-cluster smoke app")));
      Mockito.when(appManagementService.listApps()).thenReturn(Try.success(Collections.emptyList()));
      return appManagementService;
    }

    @Bean
    AppDataSourceService appDataSourceService() {
      AppDataSourceService service = Mockito.mock(AppDataSourceService.class);
      Mockito.when(service.getByAppName(PlatformDevelopKubernetesSmokeSupport.APP_NAME)).thenReturn(Collections.emptyList());
      return service;
    }

    @Bean
    PlatformDataSourceService platformDataSourceService() {
      PlatformDataSourceService service = Mockito.mock(PlatformDataSourceService.class);
      Mockito.when(service.getByNames(Mockito.anyList())).thenReturn(Collections.emptyList());
      return service;
    }

    @Bean
    ContainerService containerService() {
      ContainerService containerService = Mockito.mock(ContainerService.class);
      Mockito.when(containerService.getContainerInfoByName(Mockito.anyString()))
          .thenReturn(io.vavr.control.Either.left(new IllegalStateException("Docker container not found in Kubernetes smoke test")));
      Mockito.when(containerService.searchContainerInfo(Mockito.anyMap()))
          .thenReturn(io.vavr.control.Either.right(Collections.emptyList()));
      Mockito.when(containerService.getAllImageNames(Mockito.anyString()))
          .thenReturn(io.vavr.control.Either.right(Collections.emptyList()));
      Mockito.when(containerService.pullImageAsync(Mockito.anyString()))
          .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
      return containerService;
    }

    @Bean
    RecordingDevHelperCommandService devHelperCommandService() {
      return new RecordingDevHelperCommandService();
    }

    @Bean
    RuntimeImageViewService runtimeImageViewService() {
      return new RuntimeImageViewService();
    }

    @Bean
    BaseImageReferenceResolver baseImageReferenceResolver() {
      return new BaseImageReferenceResolver();
    }

    @Bean
    BaseImageQuerySupport baseImageQuerySupport(BaseImageReferenceResolver baseImageReferenceResolver) {
      return new BaseImageQuerySupport(baseImageReferenceResolver);
    }

    @Bean
    DataSourceInitializer memoryDataSourceInitializer() {
      return new MemoryDataSourceInitializer();
    }

    @Bean
    OncePerRequestFilter testAuthenticationFilter() {
      return new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  PlatformDevelopKubernetesSmokeSupport.TEST_PROFILE,
                  "n/a",
                  Collections.emptyList()
              )
          );
          try {
            filterChain.doFilter(request, response);
          } finally {
            SecurityContextHolder.clearContext();
          }
        }
      };
    }
  }
}
