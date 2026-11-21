package com.ouroboros.configuration.management;

/** Component commands implement this interface; generic HTTP only constructs Replace or RestoreDefaults. */
public interface ConfigurationMutation {
  record Replace(Object value) implements ConfigurationMutation { }

  enum RestoreDefaults implements ConfigurationMutation { INSTANCE }
}
