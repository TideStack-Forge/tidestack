package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;

class DevHelperRuntimeSpecFactoryTest {

  @Test
  void shouldBuildInternalOnlyHelperSpecWithMqEnv() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    DevHelperRuntimeSpecFactory factory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        RuntimeFactoryTestSupport.devContainerConfig()
    );

    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName("crm");
    request.setBackendType("DOCKER");
    request.setImage("registry.example.com/helper:v1");

    RuntimeSpec spec = factory.create(request);

    assertEquals(RuntimeKind.DEV_HELPER, spec.getRuntimeKind());
    assertEquals(ExposureStrategy.INTERNAL_ONLY, spec.getExposureStrategy());
    assertEquals(BackendType.DOCKER, spec.getBackendType());
    assertEquals(WorkspaceMode.HOST_PATH, spec.getWorkspaceMode());
    assertEquals("explicit", spec.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL));
    assertEquals("registry.example.com/helper:v1", spec.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL));
    assertEquals("crm", spec.getEnv().get("SPRING_APPLICATION_NAME"));
    assertEquals("true", spec.getEnv().get("ENABLE_DEPLOY_EXTEND"));
    assertEquals("/app/deploy/metadata/", spec.getEnv().get("OUROBOROS_METADATA_PATH"));
    assertEquals("mq.example.internal", spec.getEnv().get("SPRING_RABBITMQ_HOST"));
    assertEquals("CRM", spec.getLabels().get("ouroboros-dev-helper"));
    assertEquals("crm", spec.getLabels().get("OUROBOROS_APP_NAME"));
    assertNotNull(spec.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV));
  }

  @Test
  void shouldRecreateDefaultSourcedHelperWithCurrentConfiguredImage() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    RuntimeFactoryTestSupport.FakeDevContainerConfig config = RuntimeFactoryTestSupport.devContainerConfig();
    config.setMothershipImageName("registry.example.com/helper:v2");
    DevHelperRuntimeSpecFactory factory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        config
    );

    RuntimeSpec currentSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/helper:v1")
        .labels(java.util.Collections.singletonMap(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL, "default"))
        .build();

    RuntimeSpec firstRecreated = factory.recreate(currentSpec);
    RuntimeSpec secondRecreated = factory.recreate(currentSpec);

    assertEquals("registry.example.com/helper:v2", firstRecreated.getImage());
    assertEquals("default", firstRecreated.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL));
    assertNotNull(firstRecreated.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV));
    assertNotEquals(
        firstRecreated.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV),
        secondRecreated.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV)
    );
  }

  @Test
  void shouldRecreateExplicitHelperWithOriginalImage() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    RuntimeFactoryTestSupport.FakeDevContainerConfig config = RuntimeFactoryTestSupport.devContainerConfig();
    config.setMothershipImageName("registry.example.com/helper:v2");
    DevHelperRuntimeSpecFactory factory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        config
    );

    RuntimeSpec currentSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/custom-helper:v9")
        .labels(java.util.Collections.singletonMap(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL, "explicit"))
        .build();

    RuntimeSpec recreated = factory.recreate(currentSpec);

    assertEquals("registry.example.com/custom-helper:v9", recreated.getImage());
    assertEquals("explicit", recreated.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL));
    assertEquals("registry.example.com/custom-helper:v9", recreated.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL));
  }

  @Test
  void shouldRecreateExplicitHelperFromCanonicalSourceReferenceWhenCurrentImageIsRuntimeResolved() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    RuntimeFactoryTestSupport.FakeDevContainerConfig config = RuntimeFactoryTestSupport.devContainerConfig();
    config.setMothershipImageName("registry.example.com/helper:v2");
    DevHelperRuntimeSpecFactory factory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        config
    );

    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL, "explicit");
    labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL, "registry.example.com/custom-helper:v9");
    RuntimeSpec currentSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("sha256:deadbeefcafebabe")
        .labels(labels)
        .build();

    RuntimeSpec recreated = factory.recreate(currentSpec);

    assertEquals("registry.example.com/custom-helper:v9", recreated.getImage());
    assertEquals("registry.example.com/custom-helper:v9", recreated.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL));
  }

  @Test
  void shouldRecreateHistoricalDefaultDerivedExplicitHelperWithCurrentConfiguredImage() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, "DOCKER");
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    RuntimeFactoryTestSupport.FakeDevContainerConfig config = RuntimeFactoryTestSupport.devContainerConfig();
    config.setMothershipImageName("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.3");
    DevHelperRuntimeSpecFactory factory = new DevHelperRuntimeSpecFactory(
        new AppRuntimePreferenceResolver(RuntimeFactoryTestSupport.configurationService(configuration), properties),
        RuntimeFactoryTestSupport.appManagementService(RuntimeFactoryTestSupport.appInfo("crm", "CRM")),
        config
    );

    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL, "explicit");
    labels.put(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_REFERENCE_LABEL,
        "registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2");
    RuntimeSpec currentSpec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.2")
        .labels(labels)
        .build();

    RuntimeSpec recreated = factory.recreate(currentSpec);

    assertEquals("registry.example.com/ouroboros/ouroboros-mothership:1.0.0-beta.3", recreated.getImage());
    assertEquals("default", recreated.getLabels().get(DevHelperRuntimeSpecFactory.HELPER_IMAGE_SOURCE_LABEL));
  }
}
