package com.ouroboros.ai.context;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiInvocationContextTest {

  @Test
  void invocationContext_convertsToResolutionScopeAndCopiesMetadata() {
    Map<String, Object> tags = new LinkedHashMap<>();
    tags.put("channel", "internal");
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("requestId", "req-1");

    AiInvocationContext context = AiInvocationContext.builder()
        .tenantId("tenant-a")
        .appName("develop")
        .environment("prod")
        .workspace("workspace-1")
        .userId("user-1")
        .traceId("trace-1")
        .correlationId("corr-1")
        .timeoutMillis(1500)
        .retryCount(2)
        .tags(tags)
        .metadata(metadata)
        .build();

    AiResolutionScope scope = context.toResolutionScope();

    assertEquals("tenant-a", scope.getTenantId());
    assertEquals("develop", scope.getAppName());
    assertEquals("prod", scope.getEnvironment());
    assertEquals("workspace-1", scope.getWorkspace());
    assertEquals("user-1", scope.getUserId());
    assertEquals("internal", context.getTags().get("channel"));
    assertEquals("req-1", context.getMetadata().get("requestId"));
  }
}
