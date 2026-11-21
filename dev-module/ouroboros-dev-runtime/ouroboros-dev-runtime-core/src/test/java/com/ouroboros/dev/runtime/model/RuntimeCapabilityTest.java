package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeCapabilityTest {

  @Test
  void shouldRetainDeclaredCapabilitiesOnRuntimeInstance() {
    RuntimeCapability capabilities = RuntimeCapability.builder()
        .supportsPause(true)
        .supportsDebugPort(false)
        .supportsDebugSession(true)
        .supportsNodePortExposure(true)
        .supportsIngressExposure(false)
        .supportsWorkspaceHotReload(true)
        .build();

    RuntimeInstance instance = RuntimeInstance.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .runtimeGeneration(3L)
        .capabilities(capabilities)
        .build();

    assertTrue(instance.getCapabilities().isSupportsPause());
    assertFalse(instance.getCapabilities().isSupportsDebugPort());
    assertTrue(instance.getCapabilities().isSupportsDebugSession());
    assertTrue(instance.getCapabilities().isSupportsNodePortExposure());
    assertFalse(instance.getCapabilities().isSupportsIngressExposure());
    assertEquals(Long.valueOf(3L), instance.getRuntimeGeneration());
  }

  @Test
  void shouldDefaultCapabilitiesWhenRuntimeInstanceDoesNotDeclareThem() {
    RuntimeInstance instance = RuntimeInstance.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .build();

    assertNotNull(instance.getCapabilities());
    assertFalse(instance.getCapabilities().isSupportsPause());
    assertFalse(instance.getCapabilities().isSupportsDebugPort());
    assertFalse(instance.getCapabilities().isSupportsDebugSession());
    assertFalse(instance.getCapabilities().isSupportsWorkspaceHotReload());
    assertFalse(instance.getCapabilities().isSupportsNodePortExposure());
    assertFalse(instance.getCapabilities().isSupportsIngressExposure());
  }
}
