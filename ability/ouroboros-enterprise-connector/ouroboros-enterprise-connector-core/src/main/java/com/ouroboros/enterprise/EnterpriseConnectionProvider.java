package com.ouroboros.enterprise;

import java.util.Map;
import java.util.Set;

/** Installed provider's non-sensitive connection contract; contains no domain adapter factory. */
public interface EnterpriseConnectionProvider {
  String provider();
  default String displayName() { return provider(); }

  /** Deployment-wide cardinality; generic protocols can retain multiple identity sources. */
  default int maxConnections() { return Integer.MAX_VALUE; }

  /** Non-secret enterprise identity fields which cannot be changed by editing an existing connection. */
  default Set<String> connectionIdentityFields() { return Set.of(); }

  default Set<String> connectionConfigFields() { return Set.of(); }

  default void validateConnectionConfig(Map<String, Object> config) {
    // Providers without additional connection fields have no further field rules.
  }

  default Map<String, Object> connectionSchema() {
    return Map.of("provider", provider(), "fields", connectionConfigFields().stream().sorted().toList());
  }

  /** Secret input contract owned by the provider; values never enter the connection schema. */
  default java.util.List<Map<String, Object>> credentialFields() { return java.util.List.of(); }
}
