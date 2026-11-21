package com.ouroboros.dev.runtime.model;

import java.time.Instant;

public class DebugKeyMaterial {
  private final String profileId;
  private final String debugKey;
  private final RuntimeCapability runtimeCapability;
  private final DebugAccessStrategy runtimeStrategy;
  private final Instant expiresAt;

  private DebugKeyMaterial(Builder builder) {
    this.profileId = builder.profileId;
    this.debugKey = builder.debugKey;
    this.runtimeCapability = builder.runtimeCapability == null ? RuntimeCapability.builder().build() : builder.runtimeCapability;
    this.runtimeStrategy = builder.runtimeStrategy;
    this.expiresAt = builder.expiresAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getProfileId() {
    return profileId;
  }

  public String getDebugKey() {
    return debugKey;
  }

  public RuntimeCapability getRuntimeCapability() {
    return runtimeCapability;
  }

  public DebugAccessStrategy getRuntimeStrategy() {
    return runtimeStrategy;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public static class Builder {
    private String profileId;
    private String debugKey;
    private RuntimeCapability runtimeCapability;
    private DebugAccessStrategy runtimeStrategy;
    private Instant expiresAt;

    private Builder() {
    }

    public Builder profileId(String profileId) {
      this.profileId = profileId;
      return this;
    }

    public Builder debugKey(String debugKey) {
      this.debugKey = debugKey;
      return this;
    }

    public Builder runtimeCapability(RuntimeCapability runtimeCapability) {
      this.runtimeCapability = runtimeCapability;
      return this;
    }

    public Builder runtimeStrategy(DebugAccessStrategy runtimeStrategy) {
      this.runtimeStrategy = runtimeStrategy;
      return this;
    }

    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public DebugKeyMaterial build() {
      return new DebugKeyMaterial(this);
    }
  }
}
