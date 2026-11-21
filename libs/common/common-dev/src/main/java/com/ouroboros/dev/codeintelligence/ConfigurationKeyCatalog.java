package com.ouroboros.dev.codeintelligence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;

/** Editor-safe configuration names. Never exposes defaults, values, or protected sources. */
public final class ConfigurationKeyCatalog {
  private ConfigurationKeyCatalog() {}

  public static List<Map<String, Object>> describe(ApplicationMetadataSnapshot snapshot) {
    var entries = snapshot.getModule("configuration");
    var sensitiveNames = new java.util.HashSet<String>();
    for (var entry : entries) {
      if (entry == null || entry.getDetails() == null) continue;
      var names = entry.getDetails().get("sensitiveSourceNames");
      if (names instanceof Iterable<?> iterable) iterable.forEach(name -> sensitiveNames.add(String.valueOf(name)));
    }
    var result = new LinkedHashMap<String, Map<String, Object>>();
    for (var entry : entries) {
      if (entry == null || entry.getDetails() == null) continue;
      var details = entry.getDetails();
      var key = entry.getName();
      if (key == null || key.isBlank() || sensitiveNames.contains(key)
          || Boolean.TRUE.equals(details.get("managementAccessPolicyRequired"))) continue;
      var safe = new LinkedHashMap<String, Object>();
      safe.put("key", key);
      for (var field : List.of("label", "description", "valueType")) {
        if (details.get(field) instanceof String text) safe.put(field, text);
      }
      result.putIfAbsent(key, Map.copyOf(safe));
    }
    return List.copyOf(result.values());
  }
}
