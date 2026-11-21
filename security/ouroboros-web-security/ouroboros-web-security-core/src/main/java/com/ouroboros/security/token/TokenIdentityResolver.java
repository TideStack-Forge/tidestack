package com.ouroboros.security.token;

import java.util.Optional;
import com.ouroboros.security.identity.SecurityIdentity;

/** Credential owners verify their token and supply its immutable identity restrictions. */
public interface TokenIdentityResolver {
  Optional<SecurityIdentity> resolve(String token);
}
