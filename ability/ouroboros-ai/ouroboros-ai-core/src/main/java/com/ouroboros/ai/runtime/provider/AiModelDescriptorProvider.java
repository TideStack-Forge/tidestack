package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiModelDescriptor;

@FunctionalInterface
public interface AiModelDescriptorProvider {

  List<AiModelDescriptor> getModelDescriptors();

  default int getOrder() {
    return 0;
  }
}
