package com.ouroboros.configuration.management;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.configuration.ConfigurationSnapshot;

/** Explicit management boundary for component editors; never exposes internal configuration values. */
public interface ConfigurationManagementService {
  Optional<ConfigurationSnapshot> readManagementSnapshot(String key);

  /** May also guard declared write dependencies by revision; those rows are locked and checked without being modified. */
  Map<String, ConfigurationSnapshot> mutateManagementConfigurations(Map<String, ConfigurationMutation> mutations,
      Map<String, String> expectedRevisions);

  /** Update values through the same managed write path, retaining actor and compare-and-set semantics. */
  default void update(Map<String, Object> values, String updateBy) {
    update(values, updateBy, java.util.Collections.emptyMap());
  }

  void update(Map<String, Object> values, String updateBy, Map<String, String> expectedRevisions);
}
