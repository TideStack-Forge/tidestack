package com.ouroboros.configuration.management;

/** Constructed by the runtime from the current server security context, never from request data. */
public record ConfigurationManagementContext(String userIdentity, Purpose purpose) {
  public enum Purpose { MANAGEMENT, SCRIPT, DEVELOPMENT }

  public boolean authenticated() {
    return userIdentity != null && !userIdentity.isBlank();
  }
}
