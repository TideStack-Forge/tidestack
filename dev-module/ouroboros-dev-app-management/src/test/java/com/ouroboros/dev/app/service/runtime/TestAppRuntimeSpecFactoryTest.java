package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.exception.BusinessException;

class TestAppRuntimeSpecFactoryTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldBuildKubernetesTempPortSpecFromRequest() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    configuration.put(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY, "STABLE_DOMAIN");
    configuration.put(ConfigurationConstant.TEST_APP_CONTAINER_IMAGE, "registry.example.com/crm:v1");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );

    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setBackendType("KUBERNETES");
    request.setExposureStrategy("TEMP_PORT");
    request.setEnvironmentName("daily");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice");

    assertEquals(BackendType.KUBERNETES, spec.getBackendType());
    assertEquals(ExposureStrategy.TEMP_PORT, spec.getExposureStrategy());
    assertEquals(RuntimeKind.TEST_APP, spec.getRuntimeKind());
    assertEquals(WorkspaceMode.PVC, spec.getWorkspaceMode());
    assertEquals("registry.example.com/crm:v1", spec.getImage());
    assertEquals("CRM", spec.getLabels().get("ouroboros-test-app"));
    assertEquals("daily", spec.getLabels().get("OUROBOROS_APP_ENVIRONMENT"));
    assertEquals("org.h2.Driver", spec.getEnv().get("SPRING_DATASOURCE_DRIVERCLASSNAME"));
    assertFalse(spec.getEnv().containsKey("CUSTOM_CLASS_PATH"));
  }

  @Test
  void shouldUseConfiguredBackendWhenRequestOmitsBackendType() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "KUBERNETES");
    configuration.put(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY, "STABLE_DOMAIN");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );

    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice");

    assertEquals(BackendType.KUBERNETES, spec.getBackendType());
    assertEquals(ExposureStrategy.STABLE_DOMAIN, spec.getExposureStrategy());
    assertEquals(WorkspaceMode.PVC, spec.getWorkspaceMode());
  }

  @Test
  void shouldCopyWebAndDebugRequestedPortsForDockerTempPortSpec() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY, "STABLE_DOMAIN");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );

    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setBackendType("DOCKER");
    request.setExposureStrategy("TEMP_PORT");
    request.setPortBindings(requestedPorts(32080, 32081, 32082));
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice");

    assertEquals(BackendType.DOCKER, spec.getBackendType());
    assertEquals(ExposureStrategy.TEMP_PORT, spec.getExposureStrategy());
    assertEquals(Integer.valueOf(32080), spec.getRequestedPorts().get("web"));
    assertEquals(Integer.valueOf(32082), spec.getRequestedPorts().get("apiDebug"));
    assertEquals(2, spec.getRequestedPorts().size());
    assertFalse(spec.getRequestedPorts().containsKey("api"));
    assertFalse(spec.getEnv().containsKey("CUSTOM_CLASS_PATH"));
  }

  @Test
  void shouldKeepRequestedCustomClassPathForKubernetesSpec() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setBackendType("KUBERNETES");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));
    Map<String, String> requestEnv = new LinkedHashMap<String, String>();
    requestEnv.put("CUSTOM_CLASS_PATH", "/opt/custom");
    request.setEnv(requestEnv);

    RuntimeSpec spec = factory.create(request, "alice");

    assertThat(spec.getEnv().get("CUSTOM_CLASS_PATH")).isEqualTo("/opt/custom");
  }

  @Test
  void shouldIgnoreRequestedPortsForStableDomainSpec() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY, "TEMP_PORT");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );

    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setBackendType("DOCKER");
    request.setExposureStrategy("STABLE_DOMAIN");
    request.setPortBindings(requestedPorts(32080, 32081, 32082));
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice");

    assertEquals(ExposureStrategy.STABLE_DOMAIN, spec.getExposureStrategy());
    assertEquals(BackendType.DOCKER, spec.getBackendType());
    assertEquals(0, spec.getRequestedPorts().size());
  }

  @Test
  void shouldFailCreateWhenRuntimeBindingOptedInButLatestLockMissing() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        new InMemoryRuntimeLockStore(),
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    assertThrows(BusinessException.class, () -> factory.create(request, "alice/dev"));
  }

  @Test
  void shouldInjectLatestLockArtifactInputWhenOptedIn() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(
        RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP),
        runtimeLock("1.0.0")
    ));
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice/dev");

    assertEquals("com.example:crm-runtime:1.0.0", spec.getEnv().get("OUROBOROS_RUNTIME_LOCK_ARTIFACTS"));
    assertEquals("2026.03", spec.getEnv().get("OUROBOROS_RUNTIME_LOCK_BASE_BOM"));
    assertEquals("true", spec.getEnv().get("OUROBOROS_RUNTIME_LOCK_REQUIRED"));
  }

  @Test
  void shouldAcceptReadyLatestLockWithEmptyTopLevelArtifacts() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.DOCKER);
    InMemoryRuntimeLockStore runtimeLockStore = new InMemoryRuntimeLockStore();
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(
        RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP),
        runtimeLockWithoutTopLevelArtifacts()
    ));
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(configuration),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    request.setDatasource(RuntimeFactoryTestSupport.datasource("memory"));

    RuntimeSpec spec = factory.create(request, "alice/dev");

    assertEquals("", spec.getEnv().get("OUROBOROS_RUNTIME_LOCK_ARTIFACTS"));
    assertEquals("READY", spec.getEnv().get("OUROBOROS_RUNTIME_LOCK_STATUS"));
  }

  @Test
  void shouldRecreateSpecWithUpdatedImageAndPreservedRuntimeSettings() {
    TestAppRuntimeSpecFactory factory = new TestAppRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(new LinkedHashMap<String, Object>()), new DevRuntimeProperties()),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.configurationService(new LinkedHashMap<String, Object>()),
        RuntimeFactoryTestSupport.devContainerConfig(),
        Arrays.asList(RuntimeFactoryTestSupport.dataSourceInitializer("memory", RuntimeFactoryTestSupport.datasourceValue())),
        new InMemoryRuntimeLockStore(),
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
    Map<String, Integer> requestedPorts = requestedPorts(32080, 32081, 32082);
    Map<String, String> env = new LinkedHashMap<String, String>();
    env.put("SPRING_APPLICATION_NAME", "crm");
    env.put("CUSTOM_ENV", "enabled");
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("OUROBOROS_APP_NAME", "crm");
    labels.put("OUROBOROS_APP_PROFILE", "alice");
    Map<String, String> resourceLimits = new LinkedHashMap<String, String>();
    resourceLimits.put("memory", "1024Mi");
    WorkspaceRef workspaceRef = WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .hostPath("/tmp/ouroboros/crm/alice")
        .build();
    RuntimeSpec currentSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.TEMP_PORT)
        .requestedPorts(requestedPorts)
        .image("registry.example.com/crm:v1")
        .env(env)
        .labels(labels)
        .resourceLimits(resourceLimits)
        .ttl(Duration.ofHours(2))
        .workspaceRef(workspaceRef)
        .network("ouroboros-dev")
        .build();

    RuntimeSpec recreated = factory.recreate(currentSpec, " registry.example.com/crm:v2 ");

    assertEquals("registry.example.com/crm:v2", recreated.getImage());
    assertEquals(currentSpec.getAppName(), recreated.getAppName());
    assertEquals(currentSpec.getProfile(), recreated.getProfile());
    assertEquals(currentSpec.getRuntimeKind(), recreated.getRuntimeKind());
    assertEquals(currentSpec.getBackendType(), recreated.getBackendType());
    assertEquals(currentSpec.getWorkspaceMode(), recreated.getWorkspaceMode());
    assertEquals(currentSpec.getExposureStrategy(), recreated.getExposureStrategy());
    assertEquals(currentSpec.getRequestedPorts(), recreated.getRequestedPorts());
    assertEquals("enabled", recreated.getEnv().get("CUSTOM_ENV"));
    assertEquals("crm", recreated.getEnv().get("SPRING_APPLICATION_NAME"));
    assertEquals("true", recreated.getEnv().get("OUROBOROS_DEV_HELPER_TEST_APP"));
    assertNotNull(java.util.UUID.fromString(recreated.getEnv().get("OUROBOROS_APP_INCARNATION_ID")));
    assertEquals(env, currentSpec.getEnv());
    assertNotEquals(recreated.getEnv().get("OUROBOROS_APP_INCARNATION_ID"),
        factory.recreate(recreated, "registry.example.com/crm:v3").getEnv().get("OUROBOROS_APP_INCARNATION_ID"));
    assertEquals(currentSpec.getLabels(), recreated.getLabels());
    assertEquals(currentSpec.getResourceLimits(), recreated.getResourceLimits());
    assertEquals(currentSpec.getTtl(), recreated.getTtl());
    assertEquals(currentSpec.getWorkspaceRef(), recreated.getWorkspaceRef());
    assertEquals(currentSpec.getNetwork(), recreated.getNetwork());
  }

  private static Map<String, Integer> requestedPorts(int web, int api, int apiDebug) {
    Map<String, Integer> ports = new LinkedHashMap<String, Integer>();
    ports.put("web", Integer.valueOf(web));
    ports.put("api", Integer.valueOf(api));
    ports.put("apiDebug", Integer.valueOf(apiDebug));
    return ports;
  }

  private static ResolvedRuntimeLock runtimeLock(String version) {
    AppArtifactBinding artifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version(version)
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .build();
    return ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(Instant.parse("2026-03-24T12:00:00Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Arrays.asList(artifact))
        .build();
  }

  private static ResolvedRuntimeLock runtimeLockWithoutTopLevelArtifacts() {
    return ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(Instant.parse("2026-03-24T12:00:00Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Arrays.asList())
        .build();
  }
}
