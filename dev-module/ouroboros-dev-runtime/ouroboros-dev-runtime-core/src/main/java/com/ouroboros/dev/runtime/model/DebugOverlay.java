package com.ouroboros.dev.runtime.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugOverlay {
  private final String sessionId;
  private final String uploadBatchId;
  private final String profileId;
  private final List<TemporaryDebugArtifact> artifacts;
  private final Instant createdAt;

  private DebugOverlay(Builder builder) {
    this.sessionId = builder.sessionId;
    this.uploadBatchId = builder.uploadBatchId;
    this.profileId = builder.profileId;
    this.artifacts = immutableCopy(builder.artifacts);
    this.createdAt = builder.createdAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public String getProfileId() {
    return profileId;
  }

  public List<TemporaryDebugArtifact> getArtifacts() {
    return artifacts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }

  public static class Builder {
    private String sessionId;
    private String uploadBatchId;
    private String profileId;
    private List<TemporaryDebugArtifact> artifacts;
    private Instant createdAt;

    private Builder() {
    }

    public Builder sessionId(String sessionId) {
      this.sessionId = sessionId;
      return this;
    }

    public Builder uploadBatchId(String uploadBatchId) {
      this.uploadBatchId = uploadBatchId;
      return this;
    }

    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    public Builder artifacts(List<TemporaryDebugArtifact> artifacts) {
      this.artifacts = artifacts;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public DebugOverlay build() {
      return new DebugOverlay(this);
    }
  }
}
