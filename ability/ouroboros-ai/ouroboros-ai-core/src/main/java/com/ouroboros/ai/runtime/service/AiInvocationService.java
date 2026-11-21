package com.ouroboros.ai.runtime.service;

import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.ChatResponse;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.embedding.EmbeddingResponse;

public interface AiInvocationService {

  ChatResponse chat(ChatRequest request);

  EmbeddingResponse embed(EmbeddingRequest request);
}
