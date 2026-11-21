package com.ouroboros.ai.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.runtime.provider.AiToolDescriptorProvider;

final class AiMcpToolDescriptorProvider implements AiToolDescriptorProvider {
  private final AiMcpBridgeService bridgeService;

  AiMcpToolDescriptorProvider(AiMcpBridgeService bridgeService) {
    this.bridgeService = Objects.requireNonNull(bridgeService, "AI MCP bridge service must not be null");
  }

  @Override
  public List<AiToolDescriptor> getToolDescriptors() {
    bridgeService.refresh();
    return new ArrayList<>(bridgeService.getToolRegistry().values());
  }
}
