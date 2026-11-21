package com.ouroboros.ai.descriptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.policy.AiEffectivePolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiDescriptorCoverageTest {

  @Test
  void providerConnectionAndToolBindingPreserveDescriptorFields() {
    Map<String, Object> connectionOptions = new LinkedHashMap<>();
    connectionOptions.put("timeout", 30);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("region", "us-east-1");

    AiProviderConnectionDescriptor connection = AiProviderConnectionDescriptor.builder()
        .providerInstanceId("openai-prod")
        .providerFamily(AiProviderFamily.OPENAI)
        .transportKind("HTTP")
        .endpointRef("https://api.openai.com/v1")
        .regionOrProject("us-east-1")
        .credentialsRef("secret://ai/openai/prod")
        .connectionOptions(connectionOptions)
        .metadata(metadata)
        .enabled(false)
        .build();

    Map<String, Object> timeoutPolicy = new LinkedHashMap<>();
    timeoutPolicy.put("hardLimitMs", 4000);
    Map<String, Object> retryPolicy = new LinkedHashMap<>();
    retryPolicy.put("maxAttempts", 3);
    Map<String, Object> isolationPolicy = new LinkedHashMap<>();
    isolationPolicy.put("level", "tenant");

    AiToolBinding binding = AiToolBinding.builder()
        .bindingRef("logicflow:query-order")
        .executionMode("SYNC")
        .targetRef("tool-registry:query-order")
        .timeoutPolicy(timeoutPolicy)
        .retryPolicy(retryPolicy)
        .isolationPolicy(isolationPolicy)
        .credentialsRef("secret://tools/query-order")
        .metadata(metadata)
        .build();

    assertEquals(AiProviderFamily.OPENAI, connection.getProviderFamily());
    assertEquals("https://api.openai.com/v1", connection.getEndpointRef());
    assertFalse(connection.isEnabled());
    assertEquals(30, connection.getConnectionOptions().get("timeout"));
    assertEquals("logicflow:query-order", binding.getBindingRef());
    assertEquals("SYNC", binding.getExecutionMode());
    assertEquals("tool-registry:query-order", binding.getTargetRef());
    assertEquals(3, binding.getRetryPolicy().get("maxAttempts"));
    assertThrows(UnsupportedOperationException.class, () -> connection.getMetadata().put("x", "y"));
  }

  @Test
  void effectivePolicyCopiesAllowListsAndPolicyMaps() {
    Map<String, Object> mcpPolicy = new LinkedHashMap<>();
    mcpPolicy.put("enabled", true);
    Map<String, Object> rateLimitPolicy = new LinkedHashMap<>();
    rateLimitPolicy.put("rpm", 120);
    Map<String, Object> auditPolicy = new LinkedHashMap<>();
    auditPolicy.put("mode", "full");
    Map<String, Object> redactionPolicy = new LinkedHashMap<>();
    redactionPolicy.put("maskSecrets", true);

    AiEffectivePolicy policy = AiEffectivePolicy.builder()
        .policyId("tenant-policy")
        .tenantId("tenant-a")
        .appName("develop")
        .environment("prod")
        .workspace("workspace-1")
        .modelAllowList(Arrays.asList("gpt-4o", "claude-3-7-sonnet"))
        .toolAllowList(Collections.singletonList("query-order"))
        .mcpPolicy(mcpPolicy)
        .rateLimitPolicy(rateLimitPolicy)
        .auditPolicy(auditPolicy)
        .redactionPolicy(redactionPolicy)
        .build();

    assertEquals("tenant-policy", policy.getPolicyId());
    assertEquals(Arrays.asList("gpt-4o", "claude-3-7-sonnet"), policy.getModelAllowList());
    assertEquals(Integer.valueOf(120), policy.getRateLimitPolicy().get("rpm"));
    assertEquals("full", policy.getAuditPolicy().get("mode"));
    assertThrows(UnsupportedOperationException.class, () -> policy.getToolAllowList().add("other"));
  }
}
