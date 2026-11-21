package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimeIdentityKeyTest {

  @Test void languageWorkloadsRemainIndependentWithoutPretendingToBeApplications() {
    var spec = RuntimeSpec.builder().runtimeKind(RuntimeKind.LANGUAGE_SERVICE).workloadId("groovy-env-a").profile("lease")
        .backendType(BackendType.DOCKER).workspaceMode(WorkspaceMode.NONE).build();
    var changed = spec.withEnv(java.util.Map.of("LEASED", "true"));
    assertEquals(RuntimeIdentityKey.from(spec), RuntimeIdentityKey.from(changed));
    org.junit.jupiter.api.Assertions.assertNull(changed.getAppName());
    org.junit.jupiter.api.Assertions.assertNotEquals(RuntimeIdentityKey.from(spec), RuntimeIdentityKey.from(RuntimeSpec.builder()
        .runtimeKind(RuntimeKind.LANGUAGE_SERVICE).workloadId("groovy-env-b").profile("lease").backendType(BackendType.DOCKER).build()));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> RuntimeSpec.builder().runtimeKind(RuntimeKind.LANGUAGE_SERVICE).appName("fake-app").build());
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> RuntimeSpec.builder().runtimeKind(RuntimeKind.TEST_APP).appName("crm").workspaceMode(WorkspaceMode.NONE).build());
  }

  @Test
  void shouldTreatSameAppProfileKindBackendAsSameIdentity() {
    RuntimeSpec first = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .build();

    RuntimeSpec second = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .image("ignored:latest")
        .build();

    assertEquals(RuntimeIdentityKey.from(first), RuntimeIdentityKey.from(second));
  }
}
