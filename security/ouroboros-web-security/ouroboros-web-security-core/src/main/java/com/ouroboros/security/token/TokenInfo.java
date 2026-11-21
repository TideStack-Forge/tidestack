package com.ouroboros.security.token;

import java.util.Map;

/**
 * Token 验证结果
 */
public class TokenInfo {

  private final String userIdentity;
  private final String sessionId;
  private final Map<String, Object> claims;
  private final long expiresAt;

  public TokenInfo(String userIdentity, String sessionId, Map<String, Object> claims, long expiresAt) {
    this.userIdentity = userIdentity;
    this.sessionId = sessionId;
    this.claims = claims;
    this.expiresAt = expiresAt;
  }

  public String getUserIdentity() {
    return userIdentity;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Map<String, Object> getClaims() {
    return claims;
  }

  public long getExpiresAt() {
    return expiresAt;
  }
}
