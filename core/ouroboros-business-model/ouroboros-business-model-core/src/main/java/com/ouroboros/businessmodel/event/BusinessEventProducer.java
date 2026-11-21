package com.ouroboros.businessmodel.event;

/** Registered high-code capability allowed to produce non-Action business Events. */
public interface BusinessEventProducer {
  String getName();

  default String getKind() {
    return "producer";
  }

  /** Source-backed producers may require a verified fact scope in addition to emit Authority. */
  default boolean isEmissionAllowed(BusinessEventIdentity identity, BusinessEventEmissionContext context) {
    return true;
  }
}
