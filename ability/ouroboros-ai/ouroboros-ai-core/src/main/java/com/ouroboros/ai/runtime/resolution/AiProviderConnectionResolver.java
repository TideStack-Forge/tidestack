package com.ouroboros.ai.runtime.resolution;

import java.util.Optional;

import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.descriptor.AiProviderConnectionDescriptor;

public interface AiProviderConnectionResolver {

  Optional<AiProviderConnectionDescriptor> resolve(AiModelDescriptor modelDescriptor, AiResolutionScope scope);
}
