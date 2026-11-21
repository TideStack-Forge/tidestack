package com.ouroboros.enterprise.auth;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.ProviderSpec;

/**
 * Provider-owned authentication facet for a published enterprise connection.
 *
 * <p>Each implementation owns its fixed endpoints, credential shape, and stable identity field.
 * A provider may return an OAuth2 profile or a protocol-specific provider spec; callers must not
 * infer a profile from a workflow credential.</p>
 */
public interface EnterpriseConnectionAuthProfileProvider {

  String provider();

  Optional<ProviderSpec> resolve(EnterpriseConnectionAuthProfileRequest request);
}
