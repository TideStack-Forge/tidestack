package com.ouroboros.ai.runtime.registry;

import java.util.List;
import java.util.Optional;

import com.ouroboros.ai.descriptor.AiPolicyDescriptor;

public interface AiPolicyRegistry {

  Optional<AiPolicyDescriptor> get(String policyId);

  List<AiPolicyDescriptor> getAll();
}
