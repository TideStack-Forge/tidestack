package com.ouroboros.security.externalauth;

import java.util.Map;
import java.util.Optional;

/**
 * Services that a provider builder may use without coupling to the security runtime internals.
 */
public interface ExternalAuthProviderBuildContext {

  Optional<String> resolveSecret(String reference);

  Optional<Map<String, String>> resolveSecretProjections(Map<String, String> references);

  String publicBaseUrl();
}
