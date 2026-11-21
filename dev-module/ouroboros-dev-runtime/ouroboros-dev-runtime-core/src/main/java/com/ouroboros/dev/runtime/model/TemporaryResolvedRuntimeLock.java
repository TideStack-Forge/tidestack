package com.ouroboros.dev.runtime.model;

import java.time.Instant;

public class TemporaryResolvedRuntimeLock {
  private final String sessionId;
  private final String uploadBatchId;
  private final DebugOverlay debugOverlay;
  private final ResolvedRuntimeLock baseLock;
  private final ResolvedRuntimeLock resolvedLock;
  private final Instant preparedAt;

  private TemporaryResolvedRuntimeLock(Builder builder) {
    this.sessionId = builder.sessionId;
    this.uploadBatchId = builder.uploadBatchId;
    this.debugOverlay = builder.debugOverlay;
    this.baseLock = builder.baseLock;
    this.resolvedLock = builder.resolvedLock;
    this.preparedAt = builder.preparedAt;
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

  public DebugOverlay getDebugOverlay() {
    return debugOverlay;
  }

  public ResolvedRuntimeLock getBaseLock() {
    return baseLock;
  }

  public ResolvedRuntimeLock getResolvedLock() {
    return resolvedLock;
  }

  public Instant getPreparedAt() {
    return preparedAt;
  }

  public static class Builder {
    private String sessionId;
    private String uploadBatchId;
    private DebugOverlay debugOverlay;
    private ResolvedRuntimeLock baseLock;
    private ResolvedRuntimeLock resolvedLock;
    private Instant preparedAt;

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

    public Builder debugOverlay(DebugOverlay debugOverlay) {
      this.debugOverlay = debugOverlay;
      return this;
    }

    public Builder baseLock(ResolvedRuntimeLock baseLock) {
      this.baseLock = baseLock;
      return this;
    }

    public Builder resolvedLock(ResolvedRuntimeLock resolvedLock) {
      this.resolvedLock = resolvedLock;
      return this;
    }

    public Builder preparedAt(Instant preparedAt) {
      this.preparedAt = preparedAt;
      return this;
    }

    public TemporaryResolvedRuntimeLock build() {
      return new TemporaryResolvedRuntimeLock(this);
    }
  }
}
