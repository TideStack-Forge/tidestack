package com.ouroboros.businessmodel.event;

/** Authority names for producing and disclosing a declared business Event. */
public final class BusinessEventAuthorities {
  public static final String INSPECT_DELIVERIES = "businessModel.events.inspect";
  public static final String REDRIVE_DELIVERIES = "businessModel.events.redrive";

  private BusinessEventAuthorities() {
  }

  public static String emit(BusinessEventIdentity identity) {
    return prefix(identity) + ".emit";
  }

  public static String read(BusinessEventIdentity identity) {
    return prefix(identity) + ".read";
  }

  private static String prefix(BusinessEventIdentity identity) {
    return identity.modelFullName() + ".events." + identity.eventName();
  }
}
