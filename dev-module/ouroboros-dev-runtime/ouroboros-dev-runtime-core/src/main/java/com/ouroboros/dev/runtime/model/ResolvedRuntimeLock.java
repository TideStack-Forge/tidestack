package com.ouroboros.dev.runtime.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResolvedRuntimeLock {
  private final String appId;
  private final String profileId;
  private final RuntimeKind runtimeKind;
  private final RuntimeLockStatus status;
  private final Instant resolvedAt;
  private final String baseBomVersion;
  private final List<AppArtifactBinding> topLevelArtifacts;
  private final List<ResolvedRuntimeArtifact> resolvedArtifacts;
  private final List<ResolvedRuntimeArtifact> suppressedArtifacts;
  private final List<String> diagnostics;

  private ResolvedRuntimeLock(Builder builder) {
    this.appId = builder.appId;
    this.profileId = builder.profileId;
    this.runtimeKind = builder.runtimeKind;
    this.status = builder.status;
    this.resolvedAt = builder.resolvedAt;
    this.baseBomVersion = builder.baseBomVersion;
    this.topLevelArtifacts = immutableCopy(builder.topLevelArtifacts);
    this.resolvedArtifacts = immutableCopy(builder.resolvedArtifacts);
    this.suppressedArtifacts = immutableCopy(builder.suppressedArtifacts);
    this.diagnostics = immutableCopy(builder.diagnostics);
  }

  public static Builder builder() {
    return new Builder();
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

  public RuntimeLockStatus getStatus() {
    return status;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getBaseBomVersion() {
    return baseBomVersion;
  }

  public List<AppArtifactBinding> getTopLevelArtifacts() {
    return topLevelArtifacts;
  }

  public List<ResolvedRuntimeArtifact> getResolvedArtifacts() {
    return resolvedArtifacts;
  }

  public List<ResolvedRuntimeArtifact> getSuppressedArtifacts() {
    return suppressedArtifacts;
  }

  public List<String> getDiagnostics() {
    return diagnostics;
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }

  public static class Builder {
    private String appId;
    private String profileId;
    private RuntimeKind runtimeKind;
    private RuntimeLockStatus status;
    private Instant resolvedAt;
    private String baseBomVersion;
    private List<AppArtifactBinding> topLevelArtifacts;
    private List<ResolvedRuntimeArtifact> resolvedArtifacts;
    private List<ResolvedRuntimeArtifact> suppressedArtifacts;
    private List<String> diagnostics;

    private Builder() {
    }

    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    public Builder runtimeKind(RuntimeKind runtimeKind) {
      this.runtimeKind = runtimeKind;
      return this;
    }

    public Builder status(RuntimeLockStatus status) {
      this.status = status;
      return this;
    }

    public Builder resolvedAt(Instant resolvedAt) {
      this.resolvedAt = resolvedAt;
      return this;
    }

    public Builder baseBomVersion(String baseBomVersion) {
      this.baseBomVersion = baseBomVersion;
      return this;
    }

    public Builder topLevelArtifacts(List<AppArtifactBinding> topLevelArtifacts) {
      this.topLevelArtifacts = topLevelArtifacts;
      return this;
    }

    public Builder resolvedArtifacts(List<ResolvedRuntimeArtifact> resolvedArtifacts) {
      this.resolvedArtifacts = resolvedArtifacts;
      return this;
    }

    public Builder suppressedArtifacts(List<ResolvedRuntimeArtifact> suppressedArtifacts) {
      this.suppressedArtifacts = suppressedArtifacts;
      return this;
    }

    public Builder diagnostics(List<String> diagnostics) {
      this.diagnostics = diagnostics;
      return this;
    }

    public ResolvedRuntimeLock build() {
      return new ResolvedRuntimeLock(this);
    }
  }
}
