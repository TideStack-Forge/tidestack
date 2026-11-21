package com.ouroboros.ai.runtime.provider;

import java.util.List;

import com.ouroboros.ai.descriptor.AiKnowledgeBaseDescriptor;

@FunctionalInterface
public interface AiKnowledgeBaseDescriptorProvider {

  List<AiKnowledgeBaseDescriptor> getKnowledgeBaseDescriptors();

  default int getOrder() {
    return 0;
  }
}
