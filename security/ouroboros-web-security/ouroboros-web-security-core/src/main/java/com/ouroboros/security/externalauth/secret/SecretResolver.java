package com.ouroboros.security.externalauth.secret;

import java.util.Optional;

/**
 * Resolves secret references (e.g. "env:VAR_NAME", "config:path", "file:/path").
 */
public interface SecretResolver {

  /**
   * Whether this resolver handles the given scheme.
   */
  boolean supports(String scheme);

  /**
   * Resolve the reference to its secret value.
   *
   * @param reference full reference string including scheme prefix (e.g. "env:MY_SECRET")
   * @return the resolved secret value, or empty if not found
   */
  Optional<String> resolve(String reference);
}
