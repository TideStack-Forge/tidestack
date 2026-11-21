package com.ouroboros.security.identity;

import java.util.List;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

public interface SecurityIdentity {

  String getPrincipal();

  String getUserIdentity();

  User getUserDetail();

  Claims getClaims();

  Subject getSubject();

  List<Role> getRoles();

  default AuthorizationBoundary getAuthorizationBoundary() { return AuthorizationBoundary.unrestricted(); }

  default CredentialContext getCredentialContext() { return CredentialContext.platform(); }
}
