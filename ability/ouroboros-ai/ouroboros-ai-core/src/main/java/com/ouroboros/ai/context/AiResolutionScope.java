package com.ouroboros.ai.context;

public final class AiResolutionScope {
  private final String tenantId;
  private final String appName;
  private final String environment;
  private final String workspace;
  private final String userId;

  private AiResolutionScope(Builder builder) {
    this.tenantId = builder.tenantId;
    this.appName = builder.appName;
    this.environment = builder.environment;
    this.workspace = builder.workspace;
    this.userId = builder.userId;
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

  public static final class Builder {
    private String tenantId;
    private String appName;
    private String environment;
    private String workspace;
    private String userId;

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

    public AiResolutionScope build() {
      return new AiResolutionScope(this);
    }
  }
}
