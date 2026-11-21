package com.ouroboros.security.externalauth;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/**
 * Security extension point for a provider protocol that is not built into the platform.
 */
public interface ExternalAuthProviderBuilder {

  /** Unique ProviderSpec.type claimed by this builder. */
  String type();

  /**
   * Builds a provider from the supplied spec. An empty result marks the provider unavailable.
   */
  Optional<ExternalAuthProvider> build(ProviderSpec spec, ExternalAuthProviderBuildContext context);
}
