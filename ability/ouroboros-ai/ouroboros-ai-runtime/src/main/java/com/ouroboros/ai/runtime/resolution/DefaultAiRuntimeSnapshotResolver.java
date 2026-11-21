package com.ouroboros.ai.runtime.resolution;

import java.util.Objects;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.runtime.registry.AiModelRegistry;

public class DefaultAiRuntimeSnapshotResolver implements AiRuntimeSnapshotResolver {
  private final AiModelRegistry modelRegistry;
  private final AiPolicyResolver policyResolver;

  public DefaultAiRuntimeSnapshotResolver(AiModelRegistry modelRegistry,
                                          AiPolicyResolver policyResolver) {
    this.modelRegistry = Objects.requireNonNull(modelRegistry, "AI model registry must not be null");
    this.policyResolver = Objects.requireNonNull(policyResolver, "AI policy resolver must not be null");
  }

  @Override
  public AiEffectiveRuntimeSnapshot resolve(String modelId, AiInvocationContext invocationContext) {
    var modelDescriptor = modelRegistry.get(modelId)
        .orElseThrow(() -> new AiInvocationException("Unknown AI model: " + modelId));
    var resolutionScope = toResolutionScope(invocationContext);
    return AiEffectiveRuntimeSnapshot.builder()
        .modelDescriptor(modelDescriptor)
        .effectivePolicy(policyResolver.resolve(resolutionScope))
        .build();
  }

  private static AiResolutionScope toResolutionScope(AiInvocationContext invocationContext) {
    return invocationContext == null ? AiResolutionScope.builder().build() : invocationContext.toResolutionScope();
  }
}
