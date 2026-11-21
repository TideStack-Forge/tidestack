package com.ouroboros.app.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ouroboros.platform.cache.token")
public class PlatformCacheTokenProperties {
  private String secret;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String requireSecret() {
    String trimmed = secret == null ? null : secret.trim();
    if (trimmed == null || trimmed.isEmpty()) {
      throw new IllegalStateException("Platform cache token secret is required");
    }
    return trimmed;
  }
}
