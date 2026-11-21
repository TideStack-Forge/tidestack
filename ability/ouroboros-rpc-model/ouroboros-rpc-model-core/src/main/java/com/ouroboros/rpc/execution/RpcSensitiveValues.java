package com.ouroboros.rpc.execution;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Values actually used for authentication, independent of optional logging masks. */
public final class RpcSensitiveValues {
  private final Set<String> values = ConcurrentHashMap.newKeySet();

  public void add(String value) {
    if (value == null || value.isEmpty()) { return; }
    values.add(value);
    values.add(URLEncoder.encode(value, StandardCharsets.UTF_8));
    values.add(java.util.Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
  }

  public Object redact(Object value) {
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> result.put(String.valueOf(redact(String.valueOf(key))), redact(item)));
      return result;
    }
    if (value instanceof Collection<?> collection) { return collection.stream().map(this::redact).toList(); }
    if (value instanceof String text) {
      for (var secret : values.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
        text = text.replace(secret, "***");
      }
      return text;
    }
    return value;
  }
}
