package com.ouroboros.security.context;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.User;

public class SpringSecurityCurrentSecurityContext implements CurrentSecurityContext {

  @Override
  public java.util.Optional<String> findClientType() {
    return java.util.Optional.of(com.ouroboros.security.session.client.ClientTypeHolder.getClientType());
  }

  @Override
  public Optional<OuroborosAuthentication> findAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    return Optional.of(OuroborosAuthenticationHolder.adapt(authentication));
  }

  @Override
  public Optional<SecurityIdentity> findIdentity() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }

    OuroborosAuthentication adapted = OuroborosAuthenticationHolder.adapt(authentication);
    if (adapted.getIdentity() != null) { return Optional.of(adapted.getIdentity()); }
    User adaptedUser = adapted.getUserDetail();
    if (adaptedUser != null) {
      return Optional.of(new DefaultSecurityIdentity(adaptedUser));
    }

    if (authentication.getDetails() instanceof User) {
      return Optional.of(new DefaultSecurityIdentity((User) authentication.getDetails()));
    }

    if (authentication.getPrincipal() instanceof User) {
      return Optional.of(new DefaultSecurityIdentity((User) authentication.getPrincipal()));
    }

    String name = authentication.getName();
    if (!(authentication.getPrincipal() instanceof String) || name == null || name.trim().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new DefaultSecurityIdentity(new AuthenticationBackedUser(name)));
  }

  private static final class AuthenticationBackedUser implements User {

    private final String userIdentity;

    private AuthenticationBackedUser(String userIdentity) {
      this.userIdentity = userIdentity;
    }

    @Override
    public String getUserIdentity() {
      return userIdentity;
    }
  }
}
