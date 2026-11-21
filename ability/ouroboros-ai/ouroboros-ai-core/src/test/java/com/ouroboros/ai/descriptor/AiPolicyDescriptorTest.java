package com.ouroboros.ai.descriptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiPolicyDescriptorTest {

  @Test
  void policyDescriptor_supportsTenantAppEnvironmentAndWorkspaceScopes() {
    var descriptor = AiPolicyDescriptor.builder()
        .policyId("tenant-policy")
        .tenantScope("tenant-a")
        .appScope("develop")
        .environmentScope("prod")
        .workspaceScope("default")
        .build();

    assertEquals("tenant-a", descriptor.getTenantScope());
    assertEquals("develop", descriptor.getAppScope());
    assertEquals("prod", descriptor.getEnvironmentScope());
    assertEquals("default", descriptor.getWorkspaceScope());
  }
}
