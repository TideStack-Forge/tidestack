package com.ouroboros.dev.app.model;

import java.time.Instant;

public class AuthorizeDebugProfileDTO {
  private String profileId;
  private Boolean authorized;
  private String runtimeStrategy;
  private Instant expiresAt;

  public String getProfileId() {
    return profileId;
  }

  public void setProfileId(String profileId) {
    this.profileId = profileId;
  }

  public Boolean getAuthorized() {
    return authorized;
  }

  public void setAuthorized(Boolean authorized) {
    this.authorized = authorized;
  }

  public String getRuntimeStrategy() {
    return runtimeStrategy;
  }

  public void setRuntimeStrategy(String runtimeStrategy) {
    this.runtimeStrategy = runtimeStrategy;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
