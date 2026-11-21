package com.ouroboros.ai.embedding;

import java.util.List;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.support.AiModelCopies;

public final class EmbeddingRequest {
  private final String modelId;
  private final List<String> inputs;
  private final AiOptions options;
  private final AiInvocationContext invocationContext;

  private EmbeddingRequest(Builder builder) {
    this.modelId = builder.modelId;
    this.inputs = AiModelCopies.copyStringList(builder.inputs);
    this.options = builder.options;
    this.invocationContext = builder.invocationContext;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getModelId() {
    return modelId;
  }

  public List<String> getInputs() {
    return inputs;
  }

  public AiOptions getOptions() {
    return options;
  }

  public AiInvocationContext getInvocationContext() {
    return invocationContext;
  }

  public static final class Builder {
    private String modelId;
    private List<String> inputs;
    private AiOptions options;
    private AiInvocationContext invocationContext;

    private Builder() {
    }

    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    public Builder inputs(List<String> inputs) {
      this.inputs = inputs;
      return this;
    }

    public Builder options(AiOptions options) {
      this.options = options;
      return this;
    }

    public Builder invocationContext(AiInvocationContext invocationContext) {
      this.invocationContext = invocationContext;
      return this;
    }

    public EmbeddingRequest build() {
      return new EmbeddingRequest(this);
    }
  }
}
