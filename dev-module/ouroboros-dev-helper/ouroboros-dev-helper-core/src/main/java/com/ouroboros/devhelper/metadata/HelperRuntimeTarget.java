package com.ouroboros.devhelper.metadata;

import java.util.Objects;

/**
 * Identifies the concrete runtime that owns a helper lifecycle tuple.
 */
public final class HelperRuntimeTarget {
  private final String backendType;
  private final String profile;
  private final String runtimeId;

  public HelperRuntimeTarget(String backendType, String profile, String runtimeId) {
    this.backendType = backendType;
    this.profile = profile;
    this.runtimeId = runtimeId;
  }

  public String getBackendType() {
    return backendType;
  }

  public String getProfile() {
    return profile;
  }

  public String getRuntimeId() {
    return runtimeId;
  }

  public boolean matches(String candidateBackendType, String candidateProfile, String candidateRuntimeId) {
    return Objects.equals(backendType, candidateBackendType)
        && Objects.equals(profile, candidateProfile)
        && Objects.equals(runtimeId, candidateRuntimeId);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof HelperRuntimeTarget)) {
      return false;
    }
    HelperRuntimeTarget that = (HelperRuntimeTarget) other;
    return matches(that.backendType, that.profile, that.runtimeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(backendType, profile, runtimeId);
  }
}
