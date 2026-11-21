package com.ouroboros.ai.embedding;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class EmbeddingResponse {
  private final String modelId;
  private final List<List<Double>> vectors;
  private final Map<String, Object> usage;

  private EmbeddingResponse(Builder builder) {
    this.modelId = builder.modelId;
    this.vectors = AiModelCopies.copyList(builder.vectors);
    this.usage = AiModelCopies.copyMap(builder.usage);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getModelId() {
    return modelId;
  }

  public List<List<Double>> getVectors() {
    return vectors;
  }

  public Map<String, Object> getUsage() {
    return usage;
  }

  public static final class Builder {
    private String modelId;
    private List<List<Double>> vectors;
    private Map<String, Object> usage;

    private Builder() {
    }

    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    public Builder vectors(List<List<Double>> vectors) {
      this.vectors = vectors;
      return this;
    }

    public Builder usage(Map<String, Object> usage) {
      this.usage = usage;
      return this;
    }

    public EmbeddingResponse build() {
      return new EmbeddingResponse(this);
    }
  }
}
