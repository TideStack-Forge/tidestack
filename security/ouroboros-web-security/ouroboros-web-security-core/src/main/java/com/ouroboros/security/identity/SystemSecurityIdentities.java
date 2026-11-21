package com.ouroboros.security.identity;

public final class SystemSecurityIdentities {

  public static final String SYSTEM_USER_ID = "system";

  private static final SecurityIdentity SYSTEM = new DefaultSecurityIdentity(() -> SYSTEM_USER_ID);

  private SystemSecurityIdentities() {
  }

  public static SecurityIdentity system() {
    return SYSTEM;
  }
}
