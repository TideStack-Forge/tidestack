package com.ouroboros.dev.runtime.gateway;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class RuntimeGatewayRoute {
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final String runtimeId;
  private final String webEndpoint;
  private final String apiEndpoint;

  private RuntimeGatewayRoute(Builder builder) {
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.runtimeId = builder.runtimeId;
    this.webEndpoint = builder.webEndpoint;
    this.apiEndpoint = builder.apiEndpoint;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getAppName() {
    return appName;
  }

  public String getProfile() {
    return profile;
  }

  public RuntimeKind getRuntimeKind() {
    return runtimeKind;
  }

  public BackendType getBackendType() {
    return backendType;
  }

  public String getRuntimeId() {
    return runtimeId;
  }

  public String getWebEndpoint() {
    return webEndpoint;
  }

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public static class Builder {
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private String runtimeId;
    private String webEndpoint;
    private String apiEndpoint;

    private Builder() {
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder profile(String profile) {
      this.profile = profile;
      return this;
    }

    public Builder runtimeKind(RuntimeKind runtimeKind) {
      this.runtimeKind = runtimeKind;
      return this;
    }

    public Builder backendType(BackendType backendType) {
      this.backendType = backendType;
      return this;
    }

    public Builder runtimeId(String runtimeId) {
      this.runtimeId = runtimeId;
      return this;
    }

    public Builder webEndpoint(String webEndpoint) {
      this.webEndpoint = webEndpoint;
      return this;
    }

    public Builder apiEndpoint(String apiEndpoint) {
      this.apiEndpoint = apiEndpoint;
      return this;
    }

    public RuntimeGatewayRoute build() {
      return new RuntimeGatewayRoute(this);
    }
  }
}
