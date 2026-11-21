package com.ouroboros.ai.context;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class AiInvocationContext {
  private final String tenantId;
  private final String appName;
  private final String environment;
  private final String workspace;
  private final String userId;
  private final String traceId;
  private final String correlationId;
  private final Integer timeoutMillis;
  private final Integer retryCount;
  private final Map<String, Object> tags;
  private final Map<String, Object> metadata;

  private AiInvocationContext(Builder builder) {
    this.tenantId = builder.tenantId;
    this.appName = builder.appName;
    this.environment = builder.environment;
    this.workspace = builder.workspace;
    this.userId = builder.userId;
    this.traceId = builder.traceId;
    this.correlationId = builder.correlationId;
    this.timeoutMillis = builder.timeoutMillis;
    this.retryCount = builder.retryCount;
    this.tags = AiModelCopies.copyMap(builder.tags);
    this.metadata = AiModelCopies.copyMap(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getAppName() {
    return appName;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getWorkspace() {
    return workspace;
  }

  public String getUserId() {
    return userId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public Integer getTimeoutMillis() {
    return timeoutMillis;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public Map<String, Object> getTags() {
    return tags;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public AiResolutionScope toResolutionScope() {
    return AiResolutionScope.builder()
        .tenantId(tenantId)
        .appName(appName)
        .environment(environment)
        .workspace(workspace)
        .userId(userId)
        .build();
  }

  public static final class Builder {
    private String tenantId;
    private String appName;
    private String environment;
    private String workspace;
    private String userId;
    private String traceId;
    private String correlationId;
    private Integer timeoutMillis;
    private Integer retryCount;
    private Map<String, Object> tags;
    private Map<String, Object> metadata;

    private Builder() {
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    public Builder workspace(String workspace) {
      this.workspace = workspace;
      return this;
    }

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public Builder correlationId(String correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    public Builder timeoutMillis(Integer timeoutMillis) {
      this.timeoutMillis = timeoutMillis;
      return this;
    }

    public Builder retryCount(Integer retryCount) {
      this.retryCount = retryCount;
      return this;
    }

    public Builder tags(Map<String, Object> tags) {
      this.tags = tags;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public AiInvocationContext build() {
      return new AiInvocationContext(this);
    }
  }
}
