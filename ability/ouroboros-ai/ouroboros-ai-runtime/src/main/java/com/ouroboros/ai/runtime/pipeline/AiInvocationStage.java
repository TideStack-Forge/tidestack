package com.ouroboros.ai.runtime.pipeline;

import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;

public interface AiInvocationStage {

  default int getOrder() {
    return 0;
  }

  default ChatRequest beforeChat(AiModelDescriptor modelDescriptor, ChatRequest request) {
    return request;
  }

  default EmbeddingRequest beforeEmbedding(AiModelDescriptor modelDescriptor, EmbeddingRequest request) {
    return request;
  }
}
