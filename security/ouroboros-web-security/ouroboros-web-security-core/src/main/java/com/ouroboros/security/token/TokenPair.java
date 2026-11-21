package com.ouroboros.security.token;

/**
 * Token 对
 */
public class TokenPair {

  private final String accessToken;
  private final long accessTokenExpiresAt;
  private final String refreshToken;
  private final long refreshTokenExpiresAt;

  public TokenPair(String accessToken, long accessTokenExpiresAt, 
                   String refreshToken, long refreshTokenExpiresAt) {
    this.accessToken = accessToken;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
    this.refreshToken = refreshToken;
    this.refreshTokenExpiresAt = refreshTokenExpiresAt;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public long getAccessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public long getRefreshTokenExpiresAt() {
    return refreshTokenExpiresAt;
  }
}
