package com.ouroboros.ability.auth.oidc.management;

import java.util.Map;
import com.ouroboros.configuration.management.ConfigurationMutation;

/** A command passed into the original configuration transaction; secret material is never a configuration value. */
public record ClientConfigurationMutation(Action action, String clientId, Map<String, Object> changes,
    String newClientSecret) implements ConfigurationMutation {
  public enum Action { CREATE, UPDATE, DELETE, RESTORE_DEFAULTS }

  @Override
  public String toString() { return "ClientConfigurationMutation[" + action + ", " + clientId + "]"; }
}
