package com.ouroboros.dev.runtime.gateway.command;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class GatewayRouteSyncCommand {
  private final GatewayRouteSyncCommandType type;
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;
  private final String runtimeId;
  private final String stableDomainHost;
  private final String webEndpoint;
  private final String apiEndpoint;

  private GatewayRouteSyncCommand(Builder builder) {
    this.type = builder.type;
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
    this.runtimeId = builder.runtimeId;
    this.stableDomainHost = builder.stableDomainHost;
    this.webEndpoint = builder.webEndpoint;
    this.apiEndpoint = builder.apiEndpoint;
  }

  public static Builder builder() {
    return new Builder();
  }

  public GatewayRouteSyncCommandType getType() {
    return type;
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

  public String getStableDomainHost() {
    return stableDomainHost;
  }

  public String getWebEndpoint() {
    return webEndpoint;
  }

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public static class Builder {
    private GatewayRouteSyncCommandType type;
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;
    private String runtimeId;
    private String stableDomainHost;
    private String webEndpoint;
    private String apiEndpoint;

    private Builder() {
    }

    public Builder type(GatewayRouteSyncCommandType type) {
      this.type = type;
      return this;
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

    public Builder stableDomainHost(String stableDomainHost) {
      this.stableDomainHost = stableDomainHost;
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

    public GatewayRouteSyncCommand build() {
      return new GatewayRouteSyncCommand(this);
    }
  }
}
