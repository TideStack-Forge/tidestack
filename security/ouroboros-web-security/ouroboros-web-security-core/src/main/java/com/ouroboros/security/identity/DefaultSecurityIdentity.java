package com.ouroboros.security.identity;

import java.util.Collections;
import java.util.List;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

public class DefaultSecurityIdentity implements SecurityIdentity {

  private final User user;
  private final AuthorizationBoundary boundary;
  private final CredentialContext credential;

  public DefaultSecurityIdentity(User user) {
    this(user, AuthorizationBoundary.unrestricted(), CredentialContext.platform());
  }

  public DefaultSecurityIdentity(User user, AuthorizationBoundary boundary, CredentialContext credential) {
    this.user = java.util.Objects.requireNonNull(user);
    this.boundary = java.util.Objects.requireNonNull(boundary);
    this.credential = java.util.Objects.requireNonNull(credential);
    if ((credential.kind() == CredentialContext.Kind.DELEGATED)
        != (boundary.mode() == AuthorizationBoundary.Mode.RESTRICTED)
        || !java.util.Objects.equals(credential.resource(), boundary.resource())) {
      throw new IllegalArgumentException("凭据来源与授权边界不一致");
    }
  }

  @Override
  public AuthorizationBoundary getAuthorizationBoundary() { return boundary; }

  @Override
  public CredentialContext getCredentialContext() { return credential; }

  @Override
  public String getPrincipal() {
    return getUserIdentity();
  }

  @Override
  public String getUserIdentity() {
    return user.getUserIdentity();
  }

  @Override
  public User getUserDetail() {
    return user;
  }

  @Override
  public Claims getClaims() {
    return user.getClaims();
  }

  @Override
  public Subject getSubject() {
    return user.getSubject();
  }

  @Override
  public List<Role> getRoles() {
    List<Role> roles = user.getRoles();
    return roles == null ? Collections.emptyList() : roles;
  }
}
