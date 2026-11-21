package com.ouroboros.ai.runtime.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.ChatResponse;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.embedding.EmbeddingResponse;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.policy.AiEffectivePolicy;
import com.ouroboros.ai.runtime.pipeline.AiInvocationPipeline;
import com.ouroboros.ai.runtime.resolution.AiRuntimeSnapshotResolver;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

public class DefaultAiInvocationService implements AiInvocationService {
  private final AiRuntimeSnapshotResolver runtimeSnapshotResolver;
  private final AiInvocationPipeline pipeline;
  private final Map<AiProviderFamily, AiChatAdapter> chatAdapters;
  private final Map<AiProviderFamily, AiEmbeddingAdapter> embeddingAdapters;

  public DefaultAiInvocationService(AiRuntimeSnapshotResolver runtimeSnapshotResolver,
                                    AiInvocationPipeline pipeline,
                                    List<AiChatAdapter> chatAdapters,
                                    List<AiEmbeddingAdapter> embeddingAdapters) {
    this.runtimeSnapshotResolver = Objects.requireNonNull(runtimeSnapshotResolver, "AI runtime snapshot resolver must not be null");
    this.pipeline = Objects.requireNonNull(pipeline, "AI invocation pipeline must not be null");
    this.chatAdapters = Collections.unmodifiableMap(indexChatAdapters(chatAdapters));
    this.embeddingAdapters = Collections.unmodifiableMap(indexEmbeddingAdapters(embeddingAdapters));
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    Objects.requireNonNull(request, "Chat request must not be null");
    var snapshot = runtimeSnapshotResolver.resolve(request.getModelId(), request.getInvocationContext());
    var modelDescriptor = snapshot.getModelDescriptor();
    validatePolicy(snapshot.getEffectivePolicy(), modelDescriptor.getModelId(), request.getToolNames());
    var effectiveRequest = pipeline.applyBeforeChat(modelDescriptor, mergeOptions(modelDescriptor, request));
    var adapter = Optional.ofNullable(chatAdapters.get(modelDescriptor.getProviderFamily()))
        .orElseThrow(() -> new AiInvocationException("No chat adapter registered for provider family: " + modelDescriptor.getProviderFamily()));
    return adapter.chat(modelDescriptor, effectiveRequest);
  }

  @Override
  public EmbeddingResponse embed(EmbeddingRequest request) {
    Objects.requireNonNull(request, "Embedding request must not be null");
    var snapshot = runtimeSnapshotResolver.resolve(request.getModelId(), request.getInvocationContext());
    var modelDescriptor = snapshot.getModelDescriptor();
    validatePolicy(snapshot.getEffectivePolicy(), modelDescriptor.getModelId(), Collections.emptyList());
    var effectiveRequest = pipeline.applyBeforeEmbedding(modelDescriptor, mergeOptions(modelDescriptor, request));
    var adapter = Optional.ofNullable(embeddingAdapters.get(modelDescriptor.getProviderFamily()))
        .orElseThrow(() -> new AiInvocationException("No embedding adapter registered for provider family: " + modelDescriptor.getProviderFamily()));
    return adapter.embed(modelDescriptor, effectiveRequest);
  }

  private ChatRequest mergeOptions(AiModelDescriptor modelDescriptor, ChatRequest request) {
    if (modelDescriptor.getDefaultOptions() == null) {
      return request;
    }
    var builder = ChatRequest.builder()
        .modelId(request.getModelId())
        .options(mergeOptions(modelDescriptor.getDefaultOptions(), request.getOptions()))
        .toolNames(request.getToolNames())
        .knowledgeBaseIds(request.getKnowledgeBaseIds())
        .mcpEndpointIds(request.getMcpEndpointIds())
        .stream(request.isStream())
        .invocationContext(request.getInvocationContext());
    for (var message : request.getMessageList()) {
      builder.message(message);
    }
    return builder.build();
  }

  private EmbeddingRequest mergeOptions(AiModelDescriptor modelDescriptor, EmbeddingRequest request) {
    if (modelDescriptor.getDefaultOptions() == null) {
      return request;
    }
    return EmbeddingRequest.builder()
        .modelId(request.getModelId())
        .inputs(request.getInputs())
        .options(mergeOptions(modelDescriptor.getDefaultOptions(), request.getOptions()))
        .invocationContext(request.getInvocationContext())
        .build();
  }

  private static AiOptions mergeOptions(AiOptions defaultOptions, AiOptions requestOptions) {
    return requestOptions == null ? defaultOptions : defaultOptions.merge(requestOptions);
  }

  private static void validatePolicy(AiEffectivePolicy effectivePolicy, String modelId, List<String> toolNames) {
    if (effectivePolicy == null) {
      return;
    }
    validateAllowed("model", modelId, effectivePolicy.getModelAllowList());
    if (toolNames == null || toolNames.isEmpty()) {
      return;
    }
    for (String toolName : toolNames) {
      validateAllowed("tool", toolName, effectivePolicy.getToolAllowList());
    }
  }

  private static void validateAllowed(String resourceType, String resourceName, List<String> allowList) {
    if (allowList == null || allowList.isEmpty()) {
      return;
    }
    var normalizedResourceName = normalize(resourceName);
    var allowed = allowList.stream()
        .map(DefaultAiInvocationService::normalize)
        .anyMatch(normalizedResourceName::equals);
    if (!allowed) {
      throw new AiPolicyViolationException("AI " + resourceType + " is not allowed by current policy: " + resourceName);
    }
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<AiProviderFamily, AiChatAdapter> indexChatAdapters(List<AiChatAdapter> adapters) {
    var adapterMap = new EnumMap<AiProviderFamily, AiChatAdapter>(AiProviderFamily.class);
    if (adapters == null) {
      return adapterMap;
    }
    for (AiChatAdapter adapter : adapters) {
      if (adapter != null) {
        adapterMap.putIfAbsent(adapter.getProviderFamily(), adapter);
      }
    }
    return adapterMap;
  }

  private static Map<AiProviderFamily, AiEmbeddingAdapter> indexEmbeddingAdapters(List<AiEmbeddingAdapter> adapters) {
    var adapterMap = new EnumMap<AiProviderFamily, AiEmbeddingAdapter>(AiProviderFamily.class);
    if (adapters == null) {
      return adapterMap;
    }
    for (AiEmbeddingAdapter adapter : adapters) {
      if (adapter != null) {
        adapterMap.putIfAbsent(adapter.getProviderFamily(), adapter);
      }
    }
    return adapterMap;
  }
}
