package com.ouroboros.ai.runtime.resolution;

import java.util.Objects;
import java.util.Optional;

import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;
import com.ouroboros.ai.runtime.registry.AiProviderConnectionRegistry;

public class DefaultAiProviderConnectionResolver implements AiProviderConnectionResolver {
  private final AiProviderConnectionRegistry providerConnectionRegistry;

  public DefaultAiProviderConnectionResolver(AiProviderConnectionRegistry providerConnectionRegistry) {
    this.providerConnectionRegistry = Objects.requireNonNull(providerConnectionRegistry, "AI provider connection registry must not be null");
  }

  @Override
  public Optional<AiProviderConnectionDescriptor> resolve(AiModelDescriptor modelDescriptor, AiResolutionScope scope) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    if (hasText(modelDescriptor.getProviderInstanceId())) {
      return providerConnectionRegistry.get(modelDescriptor.getProviderInstanceId());
    }
    return Optional.empty();
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
