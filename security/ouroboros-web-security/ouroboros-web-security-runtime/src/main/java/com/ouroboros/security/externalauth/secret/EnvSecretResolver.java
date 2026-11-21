package com.ouroboros.security.externalauth.secret;

import java.util.Optional;

import org.springframework.core.env.Environment;

/**
 * Resolves secrets from environment variables via Spring Environment.
 * Reference format: "env:VAR_NAME"
 */
public class EnvSecretResolver implements SecretResolver {

  private final Environment environment;

  public EnvSecretResolver(Environment environment) {
    this.environment = environment;
  }

  @Override
  public boolean supports(String scheme) {
    return "env".equals(scheme);
  }

  @Override
  public Optional<String> resolve(String reference) {
    if (reference == null || !reference.startsWith("env:")) {
      return Optional.empty();
    }
    String varName = reference.substring("env:".length());
    return Optional.ofNullable(environment.getProperty(varName));
  }
}
