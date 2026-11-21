package com.ouroboros.security.externalauth;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/**
 * Top-level abstraction for external authentication providers.
 * Password login is NOT part of this hierarchy.
 */
public interface ExternalAuthProvider {

  /**
   * Returns the unique provider key matching ProviderSpec.key.
   */
  String getProviderKey();

  /**
   * Returns the provider specification.
   */
  ProviderSpec getSpec();
}
