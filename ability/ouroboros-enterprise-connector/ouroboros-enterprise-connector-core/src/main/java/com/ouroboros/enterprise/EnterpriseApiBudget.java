package com.ouroboros.enterprise;

/** Admission before a remote request; local rejection has no external side effect. */
@FunctionalInterface
public interface EnterpriseApiBudget {
  void acquire(String provider);
}
