package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;

@FunctionalInterface
public interface AiMcpEndpointDescriptorProvider {

  List<AiMcpEndpointDescriptor> getMcpEndpointDescriptors();

  default int getOrder() {
    return 0;
  }
}
