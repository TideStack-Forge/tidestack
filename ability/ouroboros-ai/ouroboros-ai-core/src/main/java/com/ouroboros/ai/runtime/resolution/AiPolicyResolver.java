package com.ouroboros.ai.runtime.resolution;

import com.ouroboros.ai.context.AiResolutionScope;
import com.ouroboros.ai.policy.AiEffectivePolicy;

public interface AiPolicyResolver {

  AiEffectivePolicy resolve(AiResolutionScope scope);
}
