package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiPolicyDescriptor;

@FunctionalInterface
public interface AiPolicyDescriptorProvider {

  List<AiPolicyDescriptor> getPolicyDescriptors();

  default int getOrder() {
    return 0;
  }
}
