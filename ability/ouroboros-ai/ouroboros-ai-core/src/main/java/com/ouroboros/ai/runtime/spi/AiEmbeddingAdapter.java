package com.ouroboros.ai.runtime.spi;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.embedding.EmbeddingResponse;

public interface AiEmbeddingAdapter {

  AiProviderFamily getProviderFamily();

  EmbeddingResponse embed(AiModelDescriptor modelDescriptor, EmbeddingRequest request);
}
