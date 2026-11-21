package com.ouroboros.ai.runtime.resolution;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiPolicyDescriptor;
import com.ouroboros.ai.runtime.provider.AiModelDescriptorProvider;
import com.ouroboros.ai.runtime.provider.AiPolicyDescriptorProvider;
import com.ouroboros.ai.runtime.registry.DefaultAiModelRegistry;
import com.ouroboros.ai.runtime.registry.DefaultAiPolicyRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAiRuntimeSnapshotResolverTest {

  @Test
  void runtimeSnapshotResolver_buildsImmutableSnapshotFromModelAndPolicyScope() {
    var resolver = createResolver(modelProvider(openAiModel()),
        policyProvider(globalPolicy(), tenantPolicy(), appPolicy()));

    var snapshot = resolver.resolve("openai-chat", AiInvocationContext.builder()
        .tenantId("tenant-a")
        .appName("develop")
        .build());

    assertEquals("openai-chat", snapshot.getModelDescriptor().getModelId());
    assertEquals("openai-prod", snapshot.getModelDescriptor().getProviderInstanceId());
    assertEquals("app-develop", snapshot.getEffectivePolicy().getPolicyId());
    assertEquals("tenant-a", snapshot.getEffectivePolicy().getTenantId());
    assertEquals("develop", snapshot.getEffectivePolicy().getAppName());
    assertEquals(Collections.singletonList("openai-chat"), snapshot.getEffectivePolicy().getModelAllowList());
    assertEquals("proxy", snapshot.getEffectivePolicy().getMcpPolicy().get("mode"));
    assertEquals(100, snapshot.getEffectivePolicy().getMcpPolicy().get("timeout"));
    assertEquals(30, snapshot.getEffectivePolicy().getRateLimitPolicy().get("rpm"));
    assertThrows(UnsupportedOperationException.class,
        () -> snapshot.getEffectivePolicy().getMcpPolicy().put("new-key", "new-value"));
  }

  @Test
  void runtimeSnapshotResolver_usesEmptyScopeWhenInvocationContextIsMissing() {
    var resolver = createResolver(modelProvider(openAiModel()), policyProvider(globalPolicy()));

    var snapshot = resolver.resolve("openai-chat", null);

    assertEquals("openai-chat", snapshot.getModelDescriptor().getModelId());
    assertEquals("global-default", snapshot.getEffectivePolicy().getPolicyId());
  }

  private static DefaultAiRuntimeSnapshotResolver createResolver(AiModelDescriptorProvider modelProvider,
                                                                 AiPolicyDescriptorProvider policyProvider) {
    return new DefaultAiRuntimeSnapshotResolver(
        new DefaultAiModelRegistry(Collections.singletonList(modelProvider)),
        new DefaultAiPolicyResolver(new DefaultAiPolicyRegistry(Collections.singletonList(policyProvider)))
    );
  }

  private static AiModelDescriptorProvider modelProvider(AiModelDescriptor... descriptors) {
    return () -> Arrays.asList(descriptors);
  }

  private static AiPolicyDescriptorProvider policyProvider(AiPolicyDescriptor... descriptors) {
    return () -> Arrays.asList(descriptors);
  }

  private static AiModelDescriptor openAiModel() {
    return AiModelDescriptor.builder()
        .modelId("openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .providerInstanceId("openai-prod")
        .build();
  }

  private static AiPolicyDescriptor globalPolicy() {
    return AiPolicyDescriptor.builder()
        .policyId("global-default")
        .modelAllowList(Collections.singletonList("openai-chat"))
        .mcpPolicy(mapOf("mode", "proxy", "timeout", 30))
        .build();
  }

  private static AiPolicyDescriptor tenantPolicy() {
    return AiPolicyDescriptor.builder()
        .policyId("tenant-a-policy")
        .tenantScope("tenant-a")
        .rateLimitPolicy(mapOf("rpm", 30))
        .build();
  }

  private static AiPolicyDescriptor appPolicy() {
    return AiPolicyDescriptor.builder()
        .policyId("app-develop")
        .appScope("develop")
        .mcpPolicy(mapOf("timeout", 100))
        .build();
  }

  private static Map<String, Object> mapOf(Object... values) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return map;
  }
}
