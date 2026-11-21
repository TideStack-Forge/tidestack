package com.ouroboros.configuration.management;

/** Authorized value and server-derived audit identity, returned inside the configuration transaction. */
public record ConfigurationPreparedWrite(Object value, String auditActor) {
  @Override
  public String toString() { return "ConfigurationPreparedWrite[value=REDACTED]"; }
}
