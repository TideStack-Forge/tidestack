package com.ouroboros.ai.runtime.resolution;

import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.policy.AiEffectivePolicy;

public final class AiEffectiveRuntimeSnapshot {
  private final AiModelDescriptor modelDescriptor;
  private final AiEffectivePolicy effectivePolicy;

  private AiEffectiveRuntimeSnapshot(Builder builder) {
    this.modelDescriptor = builder.modelDescriptor;
    this.effectivePolicy = builder.effectivePolicy;
  }

  public static Builder builder() {
    return new Builder();
  }

  public AiModelDescriptor getModelDescriptor() {
    return modelDescriptor;
  }

  public AiEffectivePolicy getEffectivePolicy() {
    return effectivePolicy;
  }

  public static final class Builder {
    private AiModelDescriptor modelDescriptor;
    private AiEffectivePolicy effectivePolicy;

    private Builder() {
    }

    public Builder modelDescriptor(AiModelDescriptor modelDescriptor) {
      this.modelDescriptor = modelDescriptor;
      return this;
    }

    public Builder effectivePolicy(AiEffectivePolicy effectivePolicy) {
      this.effectivePolicy = effectivePolicy;
      return this;
    }

    public AiEffectiveRuntimeSnapshot build() {
      return new AiEffectiveRuntimeSnapshot(this);
    }
  }
}
