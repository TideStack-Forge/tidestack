package com.ouroboros.ai.mcp;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;
import com.ouroboros.ai.exception.AiProviderProtocolException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMcpFailureContractTest {

  @Test
  void discoverTools_wrapsRuntimeFailureWithEndpointIdAndCause() {
    var failure = new IllegalStateException("transport failed");
    var registry = new AiMcpClientRegistry(
        Collections.singletonList(() -> Collections.singletonList(endpoint("docs", "sse"))),
        endpointDescriptor -> {
          throw failure;
        });

    var exception = assertThrows(AiProviderProtocolException.class, registry::discoverTools);

    assertTrue(exception.getMessage().contains("Failed to discover MCP tools from endpoint: docs"));
    assertSame(failure, exception.getCause());
  }

  @Test
  void sdkDiscovery_rejectsUnsupportedTransportWithPreciseProtocolException() {
    var registry = new AiMcpClientRegistry(
        Collections.singletonList(() -> Collections.singletonList(endpoint("docs", "websocket"))),
        new AiMcpClientRegistry.SdkToolDiscovery());

    var exception = assertThrows(AiProviderProtocolException.class, registry::discoverTools);

    assertTrue(exception.getMessage().contains("Unsupported MCP transport: websocket"));
  }

  @Test
  void sdkDiscovery_rejectsStdioWithoutCommandWithPreciseProtocolException() {
    var registry = new AiMcpClientRegistry(
        Collections.singletonList(() -> Collections.singletonList(endpoint("docs", "stdio"))),
        new AiMcpClientRegistry.SdkToolDiscovery());

    var exception = assertThrows(AiProviderProtocolException.class, registry::discoverTools);

    assertTrue(exception.getMessage().contains("Stdio MCP endpoint requires metadata.command: docs"));
  }

  private static AiMcpEndpointDescriptor endpoint(String endpointId, String transport) {
    return AiMcpEndpointDescriptor.builder()
        .endpointId(endpointId)
        .mode("client")
        .transport(transport)
        .url("http://localhost/" + endpointId)
        .enabled(true)
        .build();
  }
}
