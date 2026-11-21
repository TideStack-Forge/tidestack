package com.ouroboros.ai.runtime.resolution;

import com.ouroboros.ai.context.AiInvocationContext;

public interface AiRuntimeSnapshotResolver {

  AiEffectiveRuntimeSnapshot resolve(String modelId, AiInvocationContext invocationContext);
}
