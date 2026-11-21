package com.ouroboros.ai.options;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiOptions {
  private final Double temperature;
  private final Double topP;
  private final Integer maxTokens;
  private final List<String> stop;
  private final Map<String, Object> jsonSchema;
  private final String toolChoice;
  private final Map<String, Object> providerOptions;

  private AiOptions(Builder builder) {
    this.temperature = builder.temperature;
    this.topP = builder.topP;
    this.maxTokens = builder.maxTokens;
    this.stop = AiModelCopies.copyStringList(builder.stop);
    this.jsonSchema = AiModelCopies.copyMap(builder.jsonSchema);
    this.toolChoice = builder.toolChoice;
    this.providerOptions = AiModelCopies.copyMap(builder.providerOptions);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Double getTemperature() {
    return temperature;
  }

  public Double getTopP() {
    return topP;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public List<String> getStop() {
    return stop;
  }

  public Map<String, Object> getJsonSchema() {
    return jsonSchema;
  }

  public String getToolChoice() {
    return toolChoice;
  }

  public Map<String, Object> getProviderOptions() {
    return providerOptions;
  }

  public AiOptions merge(AiOptions override) {
    if (override == null) {
      return this;
    }
    var mergedProviderOptions = new java.util.LinkedHashMap<String, Object>(providerOptions);
    mergedProviderOptions.putAll(override.providerOptions);
    return AiOptions.builder()
        .temperature(override.temperature != null ? override.temperature : temperature)
        .topP(override.topP != null ? override.topP : topP)
        .maxTokens(override.maxTokens != null ? override.maxTokens : maxTokens)
        .stop(!override.stop.isEmpty() ? override.stop : stop)
        .jsonSchema(!override.jsonSchema.isEmpty() ? override.jsonSchema : jsonSchema)
        .toolChoice(override.toolChoice != null ? override.toolChoice : toolChoice)
        .providerOptions(mergedProviderOptions)
        .build();
  }

  public static final class Builder {
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private List<String> stop;
    private Map<String, Object> jsonSchema;
    private String toolChoice;
    private Map<String, Object> providerOptions;

    private Builder() {
    }

    public Builder temperature(Double temperature) {
      this.temperature = temperature;
      return this;
    }

    public Builder topP(Double topP) {
      this.topP = topP;
      return this;
    }

    public Builder maxTokens(Integer maxTokens) {
      this.maxTokens = maxTokens;
      return this;
    }

    public Builder stop(List<String> stop) {
      this.stop = stop;
      return this;
    }

    public Builder jsonSchema(Map<String, Object> jsonSchema) {
      this.jsonSchema = jsonSchema;
      return this;
    }

    public Builder toolChoice(String toolChoice) {
      this.toolChoice = toolChoice;
      return this;
    }

    public Builder providerOptions(Map<String, Object> providerOptions) {
      this.providerOptions = providerOptions;
      return this;
    }

    public Builder providerOption(String key, Object value) {
      if (this.providerOptions == null) {
        this.providerOptions = new java.util.LinkedHashMap<>();
      }
      this.providerOptions.put(key, value);
      return this;
    }

    public AiOptions build() {
      return new AiOptions(this);
    }
  }
}
