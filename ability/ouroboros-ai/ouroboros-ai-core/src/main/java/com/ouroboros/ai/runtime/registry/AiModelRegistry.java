package com.ouroboros.ai.runtime.registry;

import java.util.List;
import java.util.Optional;

import com.ouroboros.ai.descriptor.AiModelDescriptor;

public interface AiModelRegistry {

  Optional<AiModelDescriptor> get(String modelId);

  List<AiModelDescriptor> getAll();
}
