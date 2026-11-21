package com.ouroboros.dev.runtime.model;

import java.util.Locale;
import java.util.Map;

public enum TransportMode {
  TCP,
  HTTP;

  public static TransportMode fromMetadata(Map<String, String> metadata, String key) {
    return fromName(metadata == null ? null : metadata.get(key));
  }

  public static TransportMode fromName(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
