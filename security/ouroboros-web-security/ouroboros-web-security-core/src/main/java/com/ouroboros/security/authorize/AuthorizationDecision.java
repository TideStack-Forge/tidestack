package com.ouroboros.security.authorize;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.exception.AuthorizationDeniedException;

public sealed interface AuthorizationDecision {
  record Permit(AccessControlParams params) implements AuthorizationDecision {
    public Permit { java.util.Objects.requireNonNull(params); }
  }

  record Deny(Reason reason) implements AuthorizationDecision { }

  enum Reason {
    MISSING_IDENTITY, UNKNOWN_AUTHORITY, NOT_GRANTED, BOUNDARY_EXCLUDED, RESOURCE_MISMATCH,
    NOT_DELEGABLE, MISSING_DECISION_TARGET, AMBIGUOUS_DECISION_TARGET
  }

  default AccessControlParams requirePermit() {
    if (this instanceof Permit permit) { return permit.params(); }
    throw new AuthorizationDeniedException(((Deny) this).reason().name());
  }
}
