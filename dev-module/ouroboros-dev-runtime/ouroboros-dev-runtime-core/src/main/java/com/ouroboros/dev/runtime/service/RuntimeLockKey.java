package com.ouroboros.dev.runtime.service;

import java.util.Objects;

import com.ouroboros.dev.runtime.model.RuntimeKind;

public class RuntimeLockKey {
  private final String appId;
  private final String profileId;
  private final RuntimeKind runtimeKind;

  private RuntimeLockKey(String appId, String profileId, RuntimeKind runtimeKind) {
    this.appId = appId;
    this.profileId = profileId;
    this.runtimeKind = runtimeKind;
  }

  public static RuntimeLockKey of(String appId, String profileId, RuntimeKind runtimeKind) {
    return new RuntimeLockKey(appId, profileId, runtimeKind);
  }

  public String getAppId() {
    return appId;
  }

  public String getProfileId() {
    return profileId;
  }

  public RuntimeKind getRuntimeKind() {
    return runtimeKind;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RuntimeLockKey)) {
      return false;
    }
    RuntimeLockKey that = (RuntimeLockKey) other;
    return Objects.equals(appId, that.appId)
        && Objects.equals(profileId, that.profileId)
        && runtimeKind == that.runtimeKind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId, profileId, runtimeKind);
  }
}
