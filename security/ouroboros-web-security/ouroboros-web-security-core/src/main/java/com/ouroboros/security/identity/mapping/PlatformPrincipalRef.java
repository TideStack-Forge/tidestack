package com.ouroboros.security.identity.mapping;

import java.io.Serializable;

/**
 * Stable platform principal reference used by cross-domain identity mappings.
 *
 * <p>The value is deliberately independent of RBAC persistence entities so
 * callers do not need to know how a principal or an external binding is
 * stored.
 */
public record PlatformPrincipalRef(String userIdentity) implements Serializable {

  private static final long serialVersionUID = 1L;
}
