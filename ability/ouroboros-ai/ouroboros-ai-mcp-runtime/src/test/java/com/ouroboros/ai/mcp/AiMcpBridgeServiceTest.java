package com.ouroboros.ai.mcp;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMcpBridgeServiceTest {

  @Test
  void mcpBridge_registersDiscoveredToolsIntoThePlatformToolRegistry() {
    var bridge = TestMcpFactory.bridgeWithOneRemoteTool("searchDocs");

    bridge.refresh();

    assertTrue(bridge.getToolRegistry().containsKey("searchDocs"));
  }

  private static final class TestMcpFactory {

    private static AiMcpBridgeService bridgeWithOneRemoteTool(String toolName) {
      var endpoint = AiMcpEndpointDescriptor.builder()
          .endpointId("docs-endpoint")
          .mode("client")
          .transport("sse")
          .url("http://localhost:3000/mcp")
          .enabled(true)
          .build();
      var registry = new AiMcpClientRegistry(
          Collections.singletonList(() -> Collections.singletonList(endpoint)),
          descriptor -> Collections.singletonList(tool(toolName)));
      return new AiMcpBridgeService(registry);
    }

    private static AiToolDescriptor tool(String toolName) {
      return AiToolDescriptor.builder()
          .toolName(toolName)
          .description("Remote MCP tool")
          .allowModelInvocation(true)
          .build();
    }
  }
}
