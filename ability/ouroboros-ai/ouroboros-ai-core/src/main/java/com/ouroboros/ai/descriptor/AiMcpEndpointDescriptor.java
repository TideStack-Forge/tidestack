package com.ouroboros.ai.descriptor;

import java.util.List;
import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiMcpEndpointDescriptor {
  private final String endpointId;
  private final String mode;
  private final String transport;
  private final String url;
  private final String credentialsRef;
  private final List<String> exposedTools;
  private final List<String> exposedResources;
  private final List<String> exposedPrompts;
  private final Map<String, Object> metadata;
  private final boolean enabled;

  private AiMcpEndpointDescriptor(Builder builder) {
    this.endpointId = builder.endpointId;
    this.mode = builder.mode;
    this.transport = builder.transport;
    this.url = builder.url;
    this.credentialsRef = builder.credentialsRef;
    this.exposedTools = AiModelCopies.copyStringList(builder.exposedTools);
    this.exposedResources = AiModelCopies.copyStringList(builder.exposedResources);
    this.exposedPrompts = AiModelCopies.copyStringList(builder.exposedPrompts);
    this.metadata = AiModelCopies.copyMap(builder.metadata);
    this.enabled = builder.enabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getEndpointId() {
    return endpointId;
  }

  public String getMode() {
    return mode;
  }

  public String getTransport() {
    return transport;
  }

  public String getUrl() {
    return url;
  }

  public String getCredentialsRef() {
    return credentialsRef;
  }

  public List<String> getExposedTools() {
    return exposedTools;
  }

  public List<String> getExposedResources() {
    return exposedResources;
  }

  public List<String> getExposedPrompts() {
    return exposedPrompts;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static final class Builder {
    private String endpointId;
    private String mode;
    private String transport;
    private String url;
    private String credentialsRef;
    private List<String> exposedTools;
    private List<String> exposedResources;
    private List<String> exposedPrompts;
    private Map<String, Object> metadata;
    private boolean enabled = true;

    private Builder() {
    }

    public Builder endpointId(String endpointId) {
      this.endpointId = endpointId;
      return this;
    }

    public Builder mode(String mode) {
      this.mode = mode;
      return this;
    }

    public Builder transport(String transport) {
      this.transport = transport;
      return this;
    }

    public Builder url(String url) {
      this.url = url;
      return this;
    }

    public Builder credentialsRef(String credentialsRef) {
      this.credentialsRef = credentialsRef;
      return this;
    }

    public Builder exposedTools(List<String> exposedTools) {
      this.exposedTools = exposedTools;
      return this;
    }

    public Builder exposedResources(List<String> exposedResources) {
      this.exposedResources = exposedResources;
      return this;
    }

    public Builder exposedPrompts(List<String> exposedPrompts) {
      this.exposedPrompts = exposedPrompts;
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

    public AiMcpEndpointDescriptor build() {
      return new AiMcpEndpointDescriptor(this);
    }
  }
}
