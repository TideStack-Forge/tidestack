package com.ouroboros.businessmodel.event;

/** High-code consumer invoked by the platform for one published subscription delivery. */
public interface BusinessEventListener {
  String getName();

  /** Independent listeners must persist and deduplicate their own external effects, and cannot write business data. */
  default BusinessEventDeliveryMode getDeliveryMode() { return BusinessEventDeliveryMode.ATOMIC; }

  /** Null means the existing listener does not accept configuration. Values remain literal JSON. */
  default java.util.Map<String, Object> getListenerConfigSchema() {
    return null;
  }

  /** Null preserves the old protocol; an input mapping requires a declared object Schema. */
  default java.util.Map<String, Object> getListenerInputSchema() { return null; }

  /** Configured modes may specialize input without weakening the listener's existing contract. */
  default java.util.Map<String, Object> getListenerInputSchema(java.util.Map<String, Object> listenerConfig) {
    return getListenerInputSchema();
  }

  default String getPayloadSchemaFingerprint() {
    return null;
  }

  default String getImplementationRef() {
    return getClass().getName();
  }

  default String getImplementationVersion() {
    return getClass().getPackage().getImplementationVersion();
  }

  void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery);
}
