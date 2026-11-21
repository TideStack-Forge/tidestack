package com.ouroboros.ai.descriptor;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiToolDescriptor {
  private final String toolName;
  private final String description;
  private final Map<String, Object> inputSchema;
  private final Map<String, Object> outputSchema;
  private final String bindingRef;
  private final Map<String, Object> permissionPolicy;
  private final Integer timeoutMillis;
  private final boolean allowModelInvocation;
  private final Map<String, Object> metadata;

  private AiToolDescriptor(Builder builder) {
    this.toolName = builder.toolName;
    this.description = builder.description;
    this.inputSchema = AiModelCopies.copyMap(builder.inputSchema);
    this.outputSchema = AiModelCopies.copyMap(builder.outputSchema);
    this.bindingRef = builder.bindingRef;
    this.permissionPolicy = AiModelCopies.copyMap(builder.permissionPolicy);
    this.timeoutMillis = builder.timeoutMillis;
    this.allowModelInvocation = builder.allowModelInvocation;
    this.metadata = AiModelCopies.copyMap(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getToolName() {
    return toolName;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getInputSchema() {
    return inputSchema;
  }

  public Map<String, Object> getOutputSchema() {
    return outputSchema;
  }

  public String getBindingRef() {
    return bindingRef;
  }

  public Map<String, Object> getPermissionPolicy() {
    return permissionPolicy;
  }

  public Integer getTimeoutMillis() {
    return timeoutMillis;
  }

  public boolean isAllowModelInvocation() {
    return allowModelInvocation;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public static final class Builder {
    private String toolName;
    private String description;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String bindingRef;
    private Map<String, Object> permissionPolicy;
    private Integer timeoutMillis;
    private boolean allowModelInvocation = true;
    private Map<String, Object> metadata;

    private Builder() {
    }

    public Builder toolName(String toolName) {
      this.toolName = toolName;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder inputSchema(Map<String, Object> inputSchema) {
      this.inputSchema = inputSchema;
      return this;
    }

    public Builder outputSchema(Map<String, Object> outputSchema) {
      this.outputSchema = outputSchema;
      return this;
    }

    public Builder bindingRef(String bindingRef) {
      this.bindingRef = bindingRef;
      return this;
    }

    public Builder permissionPolicy(Map<String, Object> permissionPolicy) {
      this.permissionPolicy = permissionPolicy;
      return this;
    }

    public Builder timeoutMillis(Integer timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
      return this;
    }

    public Builder allowModelInvocation(boolean allowModelInvocation) {
      this.allowModelInvocation = allowModelInvocation;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public AiToolDescriptor build() {
      return new AiToolDescriptor(this);
    }
  }
}
