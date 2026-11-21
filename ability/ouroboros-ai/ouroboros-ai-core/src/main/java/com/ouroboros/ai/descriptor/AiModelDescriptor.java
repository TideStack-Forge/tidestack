package com.ouroboros.ai.descriptor;

import java.util.Map;
import java.util.Set;

import com.ouroboros.ai.AiCapability;
import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.support.AiModelCopies;

public final class AiModelDescriptor {
  private final String modelId;
  private final AiProviderFamily providerFamily;
  private final String providerKey;
  private final String modelName;
  private final String providerInstanceId;
  private final Set<AiCapability> capabilities;
  private final AiOptions defaultOptions;
  private final Map<String, Object> metadata;
  private final Map<String, Object> providerOptions;
  private final boolean enabled;

  private AiModelDescriptor(Builder builder) {
    this.modelId = builder.modelId;
    this.providerFamily = builder.providerFamily;
    this.providerKey = builder.providerKey;
    this.modelName = builder.modelName;
    this.providerInstanceId = builder.providerInstanceId;
    this.capabilities = AiModelCopies.copySet(builder.capabilities);
    this.defaultOptions = builder.defaultOptions;
    this.metadata = AiModelCopies.copyMap(builder.metadata);
    this.providerOptions = AiModelCopies.copyMap(builder.providerOptions);
    this.enabled = builder.enabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getModelId() {
    return modelId;
  }

  public AiProviderFamily getProviderFamily() {
    return providerFamily;
  }

  public String getProviderKey() {
    return providerKey;
  }

  public String getModelName() {
    return modelName;
  }

  public String getProviderInstanceId() {
    return providerInstanceId;
  }

  public Set<AiCapability> getCapabilities() {
    return capabilities;
  }

  public AiOptions getDefaultOptions() {
    return defaultOptions;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Map<String, Object> getProviderOptions() {
    return providerOptions;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static final class Builder {
    private String modelId;
    private AiProviderFamily providerFamily = AiProviderFamily.CUSTOM;
    private String providerKey;
    private String modelName;
    private String providerInstanceId;
    private Set<AiCapability> capabilities;
    private AiOptions defaultOptions;
    private Map<String, Object> metadata;
    private Map<String, Object> providerOptions;
    private boolean enabled = true;

    private Builder() {
    }

    public Builder modelId(String modelId) {
      this.modelId = modelId;
      return this;
    }

    public Builder providerFamily(AiProviderFamily providerFamily) {
      this.providerFamily = providerFamily;
      return this;
    }

    public Builder providerKey(String providerKey) {
      this.providerKey = providerKey;
      return this;
    }

    public Builder modelName(String modelName) {
      this.modelName = modelName;
      return this;
    }

    public Builder providerInstanceId(String providerInstanceId) {
      this.providerInstanceId = providerInstanceId;
      return this;
    }

    public Builder capabilities(Set<AiCapability> capabilities) {
      this.capabilities = capabilities;
      return this;
    }

    public Builder capability(AiCapability capability) {
      if (this.capabilities == null) {
        this.capabilities = new java.util.LinkedHashSet<>();
      }
      this.capabilities.add(capability);
      return this;
    }

    public Builder defaultOptions(AiOptions defaultOptions) {
      this.defaultOptions = defaultOptions;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
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

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public AiModelDescriptor build() {
      return new AiModelDescriptor(this);
    }
  }
}
