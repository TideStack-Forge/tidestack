package com.ouroboros.enterprise.runtime.credential;

import java.util.Optional;
import com.ouroboros.security.externalauth.secret.SecretResolver;

/** Contribution to the security layer's existing composite, including its JSON Pointer projection. */
public final class ManagedEnterpriseSecretResolver implements SecretResolver {
  private final EnterpriseManagedCredentials credentials;
  public ManagedEnterpriseSecretResolver(EnterpriseManagedCredentials credentials) { this.credentials = credentials; }
  @Override public boolean supports(String scheme) { return "managed".equals(scheme); }
  @Override public Optional<String> resolve(String reference) { return credentials.resolve(reference); }
}
