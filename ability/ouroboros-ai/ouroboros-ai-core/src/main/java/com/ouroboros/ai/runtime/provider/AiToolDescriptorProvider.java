package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiToolDescriptor;

@FunctionalInterface
public interface AiToolDescriptorProvider {

  List<AiToolDescriptor> getToolDescriptors();

  default int getOrder() {
    return 0;
  }
}
