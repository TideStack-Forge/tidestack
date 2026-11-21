package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;

@FunctionalInterface
public interface AiProviderConnectionDescriptorProvider {

  List<AiProviderConnectionDescriptor> getProviderConnectionDescriptors();

  default int getOrder() {
    return 0;
  }
}
