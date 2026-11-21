package com.ouroboros.security.externalauth.secret;

import java.util.Optional;

import org.springframework.core.env.Environment;

/**
 * Resolves secrets from Spring configuration properties.
 * Reference format: "config:property.path"
 */
public class ConfigSecretResolver implements SecretResolver {

  private final Environment environment;

  public ConfigSecretResolver(Environment environment) {
    this.environment = environment;
  }

  @Override
  public boolean supports(String scheme) {
    return "config".equals(scheme);
  }

  @Override
  public Optional<String> resolve(String reference) {
    if (reference == null || !reference.startsWith("config:")) {
      return Optional.empty();
    }
    String propertyPath = reference.substring("config:".length());
    return Optional.ofNullable(environment.getProperty(propertyPath));
  }
}
