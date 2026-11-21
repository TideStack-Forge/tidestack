package com.ouroboros.ai.runtime.registry;

import java.util.List;
import java.util.Optional;

import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;

public interface AiProviderConnectionRegistry {

  Optional<AiProviderConnectionDescriptor> get(String providerInstanceId);

  List<AiProviderConnectionDescriptor> getAll();
}
