package com.ouroboros.storage.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bound inside each consumer's own properties namespace. */
public final class StorageConfiguration {
  private String type;
  private Map<String, String> options = new LinkedHashMap<>();
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public Map<String, String> getOptions() { return Map.copyOf(options); }
  public void setOptions(Map<String, String> options) { this.options = new LinkedHashMap<>(options); }
  public static StorageConfiguration filesystem(String directory) {
    var configuration = new StorageConfiguration();
    configuration.setType("filesystem");
    configuration.setOptions(Map.of("root-directory", directory));
    return configuration;
  }
}
