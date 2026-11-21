package com.ouroboros.security.externalauth.config;

import java.util.List;

/**
 * Pluggable source of provider specifications.
 * Runtime sources expose a revision so the factory can rebuild changed specifications.
 */
public interface ExternalAuthProviderSpecSource {

  /**
   * Returns all configured provider specs.
   */
  List<ProviderSpec> getProviderSpecs();

  default java.util.Optional<String> getRevision() {
    return java.util.Optional.empty();
  }
}
