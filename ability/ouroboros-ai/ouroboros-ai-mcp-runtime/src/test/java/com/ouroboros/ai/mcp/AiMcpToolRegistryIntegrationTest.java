package com.ouroboros.ai.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.runtime.AiRuntimeAutoConfiguration;
import com.ouroboros.ai.runtime.registry.AiToolRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMcpToolRegistryIntegrationTest {
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(AiRuntimeAutoConfiguration.class, AiMcpRuntimeAutoConfiguration.class);

  @Test
  void mcpToolDescriptorProviderExposesRemoteToolsToDefaultAiToolRegistry() {
    contextRunner
        .withBean(AiMcpClientRegistry.class, () -> new AiMcpClientRegistry(
            Collections.singletonList(() -> Collections.singletonList(endpoint())),
            endpointDescriptor -> Collections.singletonList(tool(endpointDescriptor.getEndpointId()))))
        .run(context -> {
          var registry = context.getBean(AiToolRegistry.class);
          var tool = registry.get("REMOTESEARCH");

          assertTrue(tool.isPresent());
          assertEquals("Remote MCP search", tool.get().getDescription());
          assertEquals("docs-endpoint", tool.get().getMetadata().get("endpointId"));
          assertTrue(context.getBean(AiMcpBridgeService.class).getToolRegistry().containsKey("remoteSearch"));
        });
  }

  private static AiMcpEndpointDescriptor endpoint() {
    return AiMcpEndpointDescriptor.builder()
        .endpointId("docs-endpoint")
        .mode("client")
        .transport("sse")
        .url("http://localhost:3000/mcp")
        .enabled(true)
        .build();
  }

  private static AiToolDescriptor tool(String endpointId) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("endpointId", endpointId);
    metadata.put("transport", "sse");
    return AiToolDescriptor.builder()
        .toolName("remoteSearch")
        .description("Remote MCP search")
        .inputSchema(Collections.singletonMap("type", "object"))
        .allowModelInvocation(true)
        .metadata(metadata)
        .build();
  }
}
