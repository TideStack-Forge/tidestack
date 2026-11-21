package com.ouroboros.configuration.management;

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import com.ouroboros.configuration.ConfigurationSnapshot;

/** Registered as a Spring bean by the component owning a protected configuration key. */
public interface ConfigurationManagementAccessPolicy {
  boolean supports(String key);

  /** The snapshot is an independent copy. Empty means invisible, including in list statistics. */
  Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot);

  /** Other configuration rows whose authoritative snapshots are required by this command. */
  Set<String> writeDependencies(String key, ConfigurationMutation mutation);

  /** Runs inside the original transaction. The policy authenticates the command and supplies its audit actor. */
  ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot oldSnapshot,
                      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies);
}
