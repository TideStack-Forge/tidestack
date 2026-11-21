package com.ouroboros.rpc.dev.service.testing;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import com.ouroboros.configuration.ConfigurationService;

/** Fixes configuration reads for one preparation/execution, including dependency discovery. */
final class RpcModelTestConfiguration {
  private record Default(String value, String source) { }
  private final Map<String, String> overrides;
  private final Map<String, Default> defaults = new HashMap<>();
  private final ConfigurationService configuration;
  private final Environment environment;
  private final RpcModelTestRedactor redactor;

  RpcModelTestConfiguration(Map<String, String> overrides, ConfigurationService configuration,
      Environment environment, RpcModelTestRedactor redactor) {
    this.overrides = Map.copyOf(overrides);
    this.configuration = configuration;
    this.environment = environment;
    this.redactor = redactor;
  }

  String get(String name) {
    String value = overrides.containsKey(name) ? overrides.get(name) : defaults(name).value();
    redactor.addSecret(value);
    return value;
  }

  String defaultSource(String name) { return defaults(name).source(); }

  private Default defaults(String name) {
    return defaults.computeIfAbsent(name, key -> {
      var configured = configuration.get(String.class, key);
      if (configured.isPresent()) return new Default(configured.get(), "configuration");
      String value = environment.getProperty(key);
      return new Default(value, value == null ? null : "environment");
    });
  }
}
