package com.ouroboros.security.claims;

import java.util.HashMap;
import java.util.Map;

/**
 * Claims 构建器
 */
public class ClaimsBuilder {

  private final Map<String, Object> claims = new HashMap<>();

  /**
   * 添加 Claim（忽略 null 值）
   */
  public ClaimsBuilder claim(String type, Object value) {
    if (value != null) {
      claims.put(type, value);
    }
    return this;
  }

  /**
   * 强制添加 Claim（包括 null 值）
   * 用于明确清空某个 Claim
   */
  public ClaimsBuilder claimOrNull(String type, Object value) {
    claims.put(type, value);
    return this;
  }

  /**
   * 条件添加：只在不存在时添加
   */
  public ClaimsBuilder claimIfAbsent(String type, Object value) {
    if (value != null && !claims.containsKey(type)) {
      claims.put(type, value);
    }
    return this;
  }

  /**
   * 批量添加 Claims（忽略 null 值）
   */
  public ClaimsBuilder claims(Map<String, Object> claims) {
    if (claims != null) {
      claims.forEach(this::claim);
    }
    return this;
  }

  public Claims build() {
    return new ImmutableClaims(claims);
  }
}
