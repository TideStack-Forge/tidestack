package com.ouroboros.ai.descriptor;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiToolBinding {
  private final String bindingRef;
  private final String executionMode;
  private final String targetRef;
  private final Map<String, Object> timeoutPolicy;
  private final Map<String, Object> retryPolicy;
  private final Map<String, Object> isolationPolicy;
  private final String credentialsRef;
  private final Map<String, Object> metadata;

  private AiToolBinding(Builder builder) {
    this.bindingRef = builder.bindingRef;
    this.executionMode = builder.executionMode;
    this.targetRef = builder.targetRef;
    this.timeoutPolicy = AiModelCopies.copyMap(builder.timeoutPolicy);
    this.retryPolicy = AiModelCopies.copyMap(builder.retryPolicy);
    this.isolationPolicy = AiModelCopies.copyMap(builder.isolationPolicy);
    this.credentialsRef = builder.credentialsRef;
    this.metadata = AiModelCopies.copyMap(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getBindingRef() {
    return bindingRef;
  }

  public String getExecutionMode() {
    return executionMode;
  }

  public String getTargetRef() {
    return targetRef;
  }

  public Map<String, Object> getTimeoutPolicy() {
    return timeoutPolicy;
  }

  public Map<String, Object> getRetryPolicy() {
    return retryPolicy;
  }

  public Map<String, Object> getIsolationPolicy() {
    return isolationPolicy;
  }

  public String getCredentialsRef() {
    return credentialsRef;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public static final class Builder {
    private String bindingRef;
    private String executionMode;
    private String targetRef;
    private Map<String, Object> timeoutPolicy;
    private Map<String, Object> retryPolicy;
    private Map<String, Object> isolationPolicy;
    private String credentialsRef;
    private Map<String, Object> metadata;

    private Builder() {
    }

    public Builder bindingRef(String bindingRef) {
      this.bindingRef = bindingRef;
      return this;
    }

    public Builder executionMode(String executionMode) {
      this.executionMode = executionMode;
      return this;
    }

    public Builder targetRef(String targetRef) {
      this.targetRef = targetRef;
      return this;
    }

    public Builder timeoutPolicy(Map<String, Object> timeoutPolicy) {
      this.timeoutPolicy = timeoutPolicy;
      return this;
    }

    public Builder retryPolicy(Map<String, Object> retryPolicy) {
      this.retryPolicy = retryPolicy;
      return this;
    }

    public Builder isolationPolicy(Map<String, Object> isolationPolicy) {
      this.isolationPolicy = isolationPolicy;
      return this;
    }

    public Builder credentialsRef(String credentialsRef) {
      this.credentialsRef = credentialsRef;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public AiToolBinding build() {
      return new AiToolBinding(this);
    }
  }
}
