package com.ouroboros.ai.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.ai.descriptor.AiToolDescriptor;

public class AiMcpBridgeService {
  private final AiMcpClientRegistry clientRegistry;
  private volatile Map<String, AiToolDescriptor> toolRegistry = Collections.emptyMap();

  public AiMcpBridgeService(AiMcpClientRegistry clientRegistry) {
    this.clientRegistry = Objects.requireNonNull(clientRegistry, "AI MCP client registry must not be null");
  }

  public void refresh() {
    this.toolRegistry = Collections.unmodifiableMap(new LinkedHashMap<>(clientRegistry.discoverTools()));
  }

  public Map<String, AiToolDescriptor> getToolRegistry() {
    return toolRegistry;
  }
}
