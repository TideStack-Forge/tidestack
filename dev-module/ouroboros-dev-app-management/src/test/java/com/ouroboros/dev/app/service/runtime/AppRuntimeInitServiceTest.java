package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.ResolvedRuntimeLockDTO;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AppRuntimeInitServiceTest {

  @TempDir
  Path tempDir;

  @Mock
  private AppContainerService appContainerService;

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private DevContainerConfig devContainerConfig;

  private InMemoryRuntimeLockStore runtimeLockStore;

  private AppRuntimeInitService service;

  @BeforeEach
  void setUp() {
    DevRuntimeProperties properties = new DevRuntimeProperties();
    AppRuntimePreferenceResolver resolver = new AppRuntimePreferenceResolver(configurationService, properties);
    StableDomainHostResolver hostResolver = (appName, profile) -> appName + "-" + profile + ".develop.example.com";
    runtimeLockStore = new InMemoryRuntimeLockStore();
    service = new AppRuntimeInitService(
        appContainerService,
        configurationService,
        resolver,
        hostResolver,
        devContainerConfig,
        runtimeLockStore,
        new RuntimeLockOptInInspector(RuntimeFactoryTestSupport.workspaceContext(tempDir))
    );
  }

  @Test
  void shouldBuildDockerTempPortCreateInitForTestAppWithWebAndDebugDirectExposure() {
    Map<String, Integer> portBindings = new LinkedHashMap<String, Integer>();
    portBindings.put("web", Integer.valueOf(32080));
    portBindings.put("apiDebug", Integer.valueOf(32081));
    when(appContainerService.generateAvailablePortMap()).thenReturn(Try.success(portBindings));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList(
        "registry.example.com/crm:v1",
        "registry.example.com/crm:v2"
    )));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v1"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of("DOCKER"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));

    RuntimeCreateInitDTO result = service.buildTestAppCreateInit("crm", "alice");

    assertEquals(Collections.singletonList("DOCKER"), optionValues(result.getAvailableBackends()));
    assertEquals(Collections.singletonList("TEMP_PORT"), optionValues(result.getAvailableExposureStrategies()));
    assertEquals("DOCKER", result.getDefaultBackendType());
    assertEquals("TEMP_PORT", result.getDefaultExposureStrategy());
    assertEquals(Boolean.TRUE, result.getFieldVisibilityRules().get("backendType"));
    assertEquals(Boolean.TRUE, result.getFieldVisibilityRules().get("exposureStrategy"));
    assertEquals(Boolean.TRUE, result.getFieldVisibilityRules().get("portBindings"));
    assertEquals("http://127.0.0.1:32080", endpointOf(result, "web"));
    assertEquals("tcp://127.0.0.1:32081", endpointOf(result, "debug"));
    assertNull(endpointOf(result, "api"));
    assertEquals(Boolean.FALSE, result.getActionCapabilities().get("supportsExposureStrategySelection"));
    assertEquals(Boolean.TRUE, result.getActionCapabilities().get("autoStartAfterCreate"));
    assertEquals("SELECT_LOCAL_IMAGE", result.getImageInputMode());
    assertEquals("registry.example.com/crm:v1", result.getImage());
    assertEquals(Integer.valueOf(32080), result.getPortBindings().get("web"));
    assertEquals(Integer.valueOf(32081), result.getPortBindings().get("apiDebug"));
    assertEquals(2, result.getPortBindings().size());
    assertFalse(result.getPortBindings().containsKey("api"));
    assertNotNull(result.getConfigFile());
    assertFalse(result.getConfigFile().isReset());
  }

  @Test
  void shouldBuildKubernetesCreateInitForDevHelper() {
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of("KUBERNETES"));
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/mothership:latest");

    RuntimeCreateInitDTO result = service.buildDevHelperCreateInit("crm");

    assertEquals(Collections.singletonList("KUBERNETES"), optionValues(result.getAvailableBackends()));
    assertNull(result.getAvailableExposureStrategies());
    assertEquals("KUBERNETES", result.getDefaultBackendType());
    assertNull(result.getDefaultExposureStrategy());
    assertEquals("INPUT_IMAGE_REFERENCE", result.getImageInputMode());
    assertEquals("registry.example.com/mothership:latest", result.getImage());
    assertEquals(Boolean.FALSE, result.getFieldVisibilityRules().get("backendType"));
    assertEquals(Boolean.FALSE, result.getFieldVisibilityRules().get("portBindings"));
    assertEquals(Boolean.FALSE, result.getActionCapabilities().get("supportsExposureStrategySelection"));
    assertEquals(Boolean.TRUE, result.getActionCapabilities().get("autoStartAfterCreate"));
    assertTrue(result.getImageOptions() == null || result.getImageOptions().isEmpty());
    verify(appContainerService, never()).getImages();
  }

  @Test
  void shouldPreferDedicatedHelperConfiguredImageForDockerDevHelper() {
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList(
        "registry.example.com/ouroboros-mothership:latest",
        "registry.example.com/ouroboros-develop:latest"
    )));
    when(devContainerConfig.getMothershipImageName()).thenReturn("registry.example.com/ouroboros-mothership:latest");

    RuntimeCreateInitDTO result = service.buildDevHelperCreateInit("crm");

    assertEquals("registry.example.com/ouroboros-mothership:latest", result.getImage());
    assertEquals(Arrays.asList(
        "registry.example.com/ouroboros-mothership:latest",
        "registry.example.com/ouroboros-develop:latest"
    ), optionValues(result.getImageOptions()));
  }

  @Test
  void shouldFailCreateInitWhenRuntimeBindingOptedInButLatestLockMissing() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    when(appContainerService.generateAvailablePortMap()).thenReturn(Try.success(new LinkedHashMap<String, Integer>()));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList("registry.example.com/crm:v1")));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v1"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));

    assertThrows(BusinessException.class, () -> service.buildTestAppCreateInit("crm", "alice/dev"));
  }

  @Test
  void shouldExposeResolvedRuntimeLockPreviewWhenLatestLockExistsForOptedInRuntime() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(
        RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP),
        runtimeLock("1.0.0")
    ));
    when(appContainerService.generateAvailablePortMap()).thenReturn(Try.success(new LinkedHashMap<String, Integer>()));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList("registry.example.com/crm:v1")));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v1"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));

    RuntimeCreateInitDTO dto = service.buildTestAppCreateInit("crm", "alice/dev");

    ResolvedRuntimeLockDTO lockPreview = (ResolvedRuntimeLockDTO) dto.getRuntimeAccessPreview().get("resolvedRuntimeLock");
    assertNotNull(lockPreview);
    assertEquals("READY", lockPreview.getStatus());
    assertEquals("2026.03", lockPreview.getBaseBomVersion());
  }

  @Test
  void shouldAcceptReadyLatestLockEvenWhenTopLevelArtifactsEmpty() throws Exception {
    Path bindingsPath = tempDir.resolve(Paths.get("dev", "crm", "runtime-lock", "app-artifact-bindings.json"));
    Files.createDirectories(bindingsPath.getParent());
    Files.write(bindingsPath,
        "[ { \"groupId\" : \"com.example\", \"artifactId\" : \"crm-runtime\", \"version\" : \"1.0.0\", \"enabled\" : true } ]".getBytes(StandardCharsets.UTF_8));
    runtimeLockStore.saveLatestSuccessful(RuntimeLockRecord.of(
        RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP),
        runtimeLockWithoutTopLevelArtifacts()
    ));
    when(appContainerService.generateAvailablePortMap()).thenReturn(Try.success(new LinkedHashMap<String, Integer>()));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList("registry.example.com/crm:v1")));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v1"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));

    RuntimeCreateInitDTO dto = service.buildTestAppCreateInit("crm", "alice/dev");

    ResolvedRuntimeLockDTO lockPreview = (ResolvedRuntimeLockDTO) dto.getRuntimeAccessPreview().get("resolvedRuntimeLock");
    assertNotNull(lockPreview);
    assertEquals("READY", lockPreview.getStatus());
    assertTrue(lockPreview.getArtifacts() == null || lockPreview.getArtifacts().isEmpty());
  }

  @Test
  void shouldNotRequireLatestLockForRuntimeWithoutBindingInputs() {
    when(appContainerService.generateAvailablePortMap()).thenReturn(Try.success(new LinkedHashMap<String, Integer>()));
    when(appContainerService.getImages()).thenReturn(Try.success(Arrays.asList("registry.example.com/crm:v1")));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.of("registry.example.com/crm:v1"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of(BackendType.DOCKER.name()));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("TEMP_PORT"));

    RuntimeCreateInitDTO dto = service.buildTestAppCreateInit("crm", "alice");

    assertTrue(dto.getRuntimeAccessPreview() != null);
    assertTrue(!dto.getRuntimeAccessPreview().containsKey("resolvedRuntimeLock"));
  }

  private static java.util.List<String> optionValues(java.util.List<com.ouroboros.web.SelectOption> options) {
    return options.stream().map(com.ouroboros.web.SelectOption::getValue).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private static String endpointOf(RuntimeCreateInitDTO result, String key) {
    Map<String, Object> endpoint = (Map<String, Object>) result.getRuntimeAccessPreview().get(key);
    return endpoint == null ? null : endpoint.get("endpoint").toString();
  }

  private ResolvedRuntimeLock runtimeLock(String version) {
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

  private ResolvedRuntimeLock runtimeLockWithoutTopLevelArtifacts() {
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
