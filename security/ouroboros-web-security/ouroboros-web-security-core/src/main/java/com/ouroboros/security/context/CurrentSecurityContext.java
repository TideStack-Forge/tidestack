package com.ouroboros.security.context;

import java.util.Optional;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.identity.SecurityIdentity;

public interface CurrentSecurityContext {

  Optional<SecurityIdentity> findIdentity();

  default Optional<String> findClientType() {
    return Optional.empty();
  }

  default Optional<OuroborosAuthentication> findAuthentication() {
    return Optional.empty();
  }

  default SecurityIdentity getRequiredIdentity() {
    return findIdentity().orElseThrow(() -> new IllegalStateException("No security identity in current context"));
  }
}
