package com.ouroboros.security.claims;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Claims（声明）接口
 * 表示关于主体的一组断言/属性
 */
public interface Claims {

  <T> Optional<T> get(String type, Class<T> valueType);

  default Optional<String> getString(String type) {
    return get(type, String.class);
  }

  default Optional<Integer> getInteger(String type) {
    return get(type, Integer.class);
  }

  default Optional<Boolean> getBoolean(String type) {
    return get(type, Boolean.class);
  }

  boolean has(String type);

  Set<String> getTypes();

  Map<String, Object> asMap();

  static Claims empty() {
    return ImmutableClaims.EMPTY;
  }

  static ClaimsBuilder builder() {
    return new ClaimsBuilder();
  }
}
