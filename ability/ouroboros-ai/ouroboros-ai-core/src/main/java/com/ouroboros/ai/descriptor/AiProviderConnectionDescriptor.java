package com.ouroboros.ai.descriptor;

import java.util.Map;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.support.AiModelCopies;

public final class AiProviderConnectionDescriptor {
  private final String providerInstanceId;
  private final AiProviderFamily providerFamily;
  private final String transportKind;
  private final String endpointRef;
  private final String regionOrProject;
  private final String credentialsRef;
  private final Map<String, Object> connectionOptions;
  private final Map<String, Object> metadata;
  private final boolean enabled;

  private AiProviderConnectionDescriptor(Builder builder) {
    this.providerInstanceId = builder.providerInstanceId;
    this.providerFamily = builder.providerFamily;
    this.transportKind = builder.transportKind;
    this.endpointRef = builder.endpointRef;
    this.regionOrProject = builder.regionOrProject;
    this.credentialsRef = builder.credentialsRef;
    this.connectionOptions = AiModelCopies.copyMap(builder.connectionOptions);
    this.metadata = AiModelCopies.copyMap(builder.metadata);
    this.enabled = builder.enabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getProviderInstanceId() {
    return providerInstanceId;
  }

  public AiProviderFamily getProviderFamily() {
    return providerFamily;
  }

  public String getTransportKind() {
    return transportKind;
  }

  public String getEndpointRef() {
    return endpointRef;
  }

  public String getRegionOrProject() {
    return regionOrProject;
  }

  public String getCredentialsRef() {
    return credentialsRef;
  }

  public Map<String, Object> getConnectionOptions() {
    return connectionOptions;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static final class Builder {
    private String providerInstanceId;
    private AiProviderFamily providerFamily = AiProviderFamily.CUSTOM;
    private String transportKind;
    private String endpointRef;
    private String regionOrProject;
    private String credentialsRef;
    private Map<String, Object> connectionOptions;
    private Map<String, Object> metadata;
    private boolean enabled = true;

    private Builder() {
    }

    public Builder providerInstanceId(String providerInstanceId) {
      this.providerInstanceId = providerInstanceId;
      return this;
    }

    public Builder providerFamily(AiProviderFamily providerFamily) {
      this.providerFamily = providerFamily;
      return this;
    }

    public Builder transportKind(String transportKind) {
      this.transportKind = transportKind;
      return this;
    }

    public Builder endpointRef(String endpointRef) {
      this.endpointRef = endpointRef;
      return this;
    }

    public Builder regionOrProject(String regionOrProject) {
      this.regionOrProject = regionOrProject;
      return this;
    }

    public Builder credentialsRef(String credentialsRef) {
      this.credentialsRef = credentialsRef;
      return this;
    }

    public Builder connectionOptions(Map<String, Object> connectionOptions) {
      this.connectionOptions = connectionOptions;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public AiProviderConnectionDescriptor build() {
      return new AiProviderConnectionDescriptor(this);
    }
  }
}
