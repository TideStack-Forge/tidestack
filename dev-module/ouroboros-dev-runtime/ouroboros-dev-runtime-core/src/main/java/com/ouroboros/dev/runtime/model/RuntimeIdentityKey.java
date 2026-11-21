package com.ouroboros.dev.runtime.model;

import java.util.Objects;

public class RuntimeIdentityKey {
  private final String workloadId;
  private final String appName;
  private final String profile;
  private final RuntimeKind runtimeKind;
  private final BackendType backendType;

  private RuntimeIdentityKey(Builder builder) {
    this.workloadId = builder.workloadId;
    this.appName = builder.appName;
    this.profile = builder.profile;
    this.runtimeKind = builder.runtimeKind;
    this.backendType = builder.backendType;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static RuntimeIdentityKey of(String appName, String profile, RuntimeKind runtimeKind, BackendType backendType) {
    return builder()
        .appName(appName)
        .profile(profile)
        .runtimeKind(runtimeKind)
        .backendType(backendType)
        .build();
  }

  public static RuntimeIdentityKey from(RuntimeSpec spec) {
    return builder()
        .appName(spec.getAppName())
        .workloadId(spec.getWorkloadId())
        .profile(spec.getProfile())
        .runtimeKind(spec.getRuntimeKind())
        .backendType(spec.getBackendType())
        .build();
  }

  public static RuntimeIdentityKey from(RuntimeInstance instance) {
    return builder()
        .appName(instance.getAppName())
        .workloadId(instance.getWorkloadId())
        .profile(instance.getProfile())
        .runtimeKind(instance.getRuntimeKind())
        .backendType(instance.getBackendType())
        .build();
  }

  public String getWorkloadId() { return workloadId; }

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

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RuntimeIdentityKey)) {
      return false;
    }
    RuntimeIdentityKey that = (RuntimeIdentityKey) other;
    return Objects.equals(workloadId, that.workloadId) && Objects.equals(appName, that.appName)
        && Objects.equals(profile, that.profile)
        && runtimeKind == that.runtimeKind
        && backendType == that.backendType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(workloadId, appName, profile, runtimeKind, backendType);
  }

  public static class Builder {
    private String workloadId;
    private String appName;
    private String profile;
    private RuntimeKind runtimeKind;
    private BackendType backendType;

    private Builder() {
    }

    public Builder workloadId(String workloadId) { this.workloadId = workloadId; return this; }

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

    public RuntimeIdentityKey build() {
      return new RuntimeIdentityKey(this);
    }
  }
}
