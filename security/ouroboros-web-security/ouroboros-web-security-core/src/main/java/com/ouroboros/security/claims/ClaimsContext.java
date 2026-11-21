package com.ouroboros.security.claims;

import java.util.Optional;

/**
 * Claims 上下文
 * 提供对已聚合 Claims 的访问，解决 Provider 之间的依赖问题
 */
public interface ClaimsContext {

  <T> Optional<T> getClaim(String type, Class<T> valueType);

  default Optional<String> getString(String type) {
    return getClaim(type, String.class);
  }

  boolean hasClaim(String type);
}
