package com.ouroboros.ai.runtime.registry;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.descriptor.AiPolicyDescriptor;
import com.ouroboros.ai.runtime.provider.AiPolicyDescriptorProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAiPolicyRegistryTest {

  @Test
  void policyRegistry_returnsScopedPoliciesByNormalizedId() {
    var registry = new DefaultAiPolicyRegistry(Collections.singletonList(policyProvider()));

    assertEquals("tenant-a", registry.get(" TENANT-POLICY ").get().getTenantScope());
    assertEquals(1, registry.getAll().size());
  }

  private static AiPolicyDescriptorProvider policyProvider() {
    return () -> Collections.singletonList(
        AiPolicyDescriptor.builder()
            .policyId("tenant-policy")
            .tenantScope("tenant-a")
            .appScope("develop")
            .build()
    );
  }
}
