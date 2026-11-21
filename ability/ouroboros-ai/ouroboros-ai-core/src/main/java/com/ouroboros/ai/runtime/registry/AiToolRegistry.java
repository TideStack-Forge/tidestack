package com.ouroboros.ai.runtime.registry;

import java.util.List;
import java.util.Optional;

import com.ouroboros.ai.descriptor.AiToolDescriptor;

public interface AiToolRegistry {

  Optional<AiToolDescriptor> get(String toolName);

  List<AiToolDescriptor> getAll();
}
