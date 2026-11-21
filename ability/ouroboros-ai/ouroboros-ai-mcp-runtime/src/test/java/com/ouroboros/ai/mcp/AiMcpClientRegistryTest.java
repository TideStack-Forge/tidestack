package com.ouroboros.ai.mcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMcpClientRegistryTest {

  @Test
  void discoverTools_filtersExposedToolsAndKeepsFirstNormalizedName() {
    var docsEndpoint = endpoint("docs", Arrays.asList("SearchDocs"));
    var adminEndpoint = endpoint("admin", Collections.emptyList());
    var registry = new AiMcpClientRegistry(
        Collections.singletonList(() -> Arrays.asList(docsEndpoint, adminEndpoint)),
        endpointDescriptor -> {
          if ("docs".equals(endpointDescriptor.getEndpointId())) {
            return Arrays.asList(tool("SearchDocs"), tool("HiddenTool"));
          }
          return Arrays.asList(tool("searchdocs"), tool("AdminTool"));
        });

    Map<String, AiToolDescriptor> tools = registry.discoverTools();

    assertEquals(2, tools.size());
    assertTrue(tools.containsKey("SearchDocs"));
    assertTrue(tools.containsKey("AdminTool"));
    assertFalse(tools.containsKey("HiddenTool"));
    assertFalse(tools.containsKey("searchdocs"));
  }

  private static AiMcpEndpointDescriptor endpoint(String endpointId, List<String> exposedTools) {
    return AiMcpEndpointDescriptor.builder()
        .endpointId(endpointId)
        .mode("client")
        .transport("sse")
        .url("http://localhost/" + endpointId)
        .exposedTools(exposedTools)
        .enabled(true)
        .build();
  }

  private static AiToolDescriptor tool(String toolName) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("source", "test");
    return AiToolDescriptor.builder()
        .toolName(toolName)
        .description("Remote MCP tool")
        .inputSchema(Collections.singletonMap("type", "object"))
        .allowModelInvocation(true)
        .metadata(metadata)
        .build();
  }
}
