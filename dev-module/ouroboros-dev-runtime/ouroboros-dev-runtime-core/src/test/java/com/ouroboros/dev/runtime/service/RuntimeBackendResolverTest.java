package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.FakeRuntimeBackend;

class RuntimeBackendResolverTest {

  @Test
  void shouldUseConfiguredDefaultBackendWhenRequestDoesNotSpecifyBackend() {
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);

    RuntimeBackendResolver resolver = new RuntimeBackendResolver(
        Arrays.asList(new FakeRuntimeBackend(BackendType.DOCKER), new FakeRuntimeBackend(BackendType.KUBERNETES)),
        properties
    );

    RuntimeBackend backend = resolver.resolve(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .build());

    assertEquals(BackendType.KUBERNETES, backend.getBackendType());
  }

  @Test
  void shouldResolveBackendByBackendTypeAndRuntimeKind() {
    RuntimeBackend dockerTestApp = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.TEST_APP);
    RuntimeBackend dockerHelper = new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.DEV_HELPER);
    RuntimeBackendResolver resolver = new RuntimeBackendResolver(
        Arrays.asList(dockerTestApp, dockerHelper),
        new DevRuntimeProperties()
    );

    RuntimeBackend selected = resolver.resolve(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .build());

    assertEquals(RuntimeKind.DEV_HELPER, selected.getRuntimeKind());
  }

  @Test
  void shouldFailWhenNoBackendSupportsRequestedSpec() {
    RuntimeBackendResolver resolver = new RuntimeBackendResolver(
        Arrays.asList(new FakeRuntimeBackend(BackendType.DOCKER, RuntimeKind.TEST_APP)),
        new DevRuntimeProperties()
    );

    assertThrows(IllegalStateException.class, () -> resolver.resolve(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .build()));
  }

  @Test
  void shouldFillWorkspaceModeFromResolvedBackend() {
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    RuntimeBackendResolver resolver = new RuntimeBackendResolver(
        Arrays.asList(new FakeRuntimeBackend(BackendType.KUBERNETES)),
        properties
    );

    RuntimeSpec kubernetesSpec = resolver.normalizeBackend(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .build());
    RuntimeSpec dockerSpec = resolver.normalizeBackend(RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .build());

    assertEquals(WorkspaceMode.PVC, kubernetesSpec.getWorkspaceMode());
    assertEquals(WorkspaceMode.HOST_PATH, dockerSpec.getWorkspaceMode());
  }
}
