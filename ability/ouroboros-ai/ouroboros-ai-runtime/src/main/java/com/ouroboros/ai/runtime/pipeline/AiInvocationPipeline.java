package com.ouroboros.ai.runtime.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;

public class AiInvocationPipeline {
  private final List<AiInvocationStage> stages;

  public AiInvocationPipeline(List<AiInvocationStage> stages) {
    this.stages = Collections.unmodifiableList(new ArrayList<>(Optional.ofNullable(stages)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiInvocationStage::getOrder))
        .collect(Collectors.toList())));
  }

  public ChatRequest applyBeforeChat(AiModelDescriptor modelDescriptor, ChatRequest request) {
    var currentRequest = request;
    for (AiInvocationStage stage : stages) {
      currentRequest = Objects.requireNonNull(
          stage.beforeChat(modelDescriptor, currentRequest),
          "AI invocation stage must not return null chat request"
      );
    }
    return currentRequest;
  }

  public EmbeddingRequest applyBeforeEmbedding(AiModelDescriptor modelDescriptor, EmbeddingRequest request) {
    var currentRequest = request;
    for (AiInvocationStage stage : stages) {
      currentRequest = Objects.requireNonNull(
          stage.beforeEmbedding(modelDescriptor, currentRequest),
          "AI invocation stage must not return null embedding request"
      );
    }
    return currentRequest;
  }
}
