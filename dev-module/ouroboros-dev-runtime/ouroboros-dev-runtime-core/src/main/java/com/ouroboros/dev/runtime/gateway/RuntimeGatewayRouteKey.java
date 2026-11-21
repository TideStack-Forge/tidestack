package com.ouroboros.dev.runtime.gateway;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class RuntimeGatewayRouteKey {
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;

  private RuntimeGatewayRouteKey(Builder builder) {
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
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

  public static class Builder {
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;

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

    public RuntimeGatewayRouteKey build() {
      return new RuntimeGatewayRouteKey(this);
    }
  }
}
