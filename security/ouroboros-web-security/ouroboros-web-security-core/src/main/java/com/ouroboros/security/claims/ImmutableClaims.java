package com.ouroboros.security.claims;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 不可变的 Claims 实现
 */
public class ImmutableClaims implements Claims {

  public static final Claims EMPTY = new ImmutableClaims(Collections.emptyMap());

  private final Map<String, Object> claims;

  public ImmutableClaims(Map<String, Object> claims) {
    this.claims = Collections.unmodifiableMap(new HashMap<>(claims));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(String type, Class<T> valueType) {
    Object value = claims.get(type);
    if (value == null) {
      return Optional.empty();
    }

    if (valueType.isInstance(value)) {
      return Optional.of((T) value);
    }

    if (valueType == String.class) {
      return Optional.of((T) value.toString());
    }

    return Optional.empty();
  }

  @Override
  public boolean has(String type) {
    return claims.containsKey(type);
  }

  @Override
  public Set<String> getTypes() {
    return claims.keySet();
  }

  @Override
  public Map<String, Object> asMap() {
    return claims;
  }

  @Override
  public String toString() {
    return "Claims" + claims;
  }
}
