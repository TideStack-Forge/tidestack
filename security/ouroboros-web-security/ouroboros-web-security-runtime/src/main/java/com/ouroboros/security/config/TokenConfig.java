package com.ouroboros.security.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenConfig {

  /**
   * accessToken过期时间，默认3小时，精确到秒
   */
  private long accessTokenExpire = 3 * 60 * 60;

  /**
   * refreshToken过期时间，默认7天，精确到秒
   */
  private long refreshTokenExpire = 7 * 24 * 60 * 60;

  /**
   * AccessToken 中包含的 Claims 类型
   */
  private List<String> accessTokenClaims = Arrays.asList("sub", "subject_type", "subject_id");

  /**
   * 额外属性
   */
  private Map<String, Object> extraProperties = new HashMap<>();

  public long getAccessTokenExpire() {
    return accessTokenExpire;
  }

  public void setAccessTokenExpire(long accessTokenExpire) {
    this.accessTokenExpire = accessTokenExpire;
  }

  public long getRefreshTokenExpire() {
    return refreshTokenExpire;
  }

  public void setRefreshTokenExpire(long refreshTokenExpire) {
    this.refreshTokenExpire = refreshTokenExpire;
  }

  public List<String> getAccessTokenClaims() {
    return accessTokenClaims;
  }

  public void setAccessTokenClaims(List<String> accessTokenClaims) {
    this.accessTokenClaims = accessTokenClaims;
  }

  public Map<String, Object> getExtraProperties() {
    return extraProperties;
  }

  public void setExtraProperties(Map<String, Object> extraProperties) {
    this.extraProperties = extraProperties;
  }
}
