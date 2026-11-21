package com.ouroboros.businessmodel.event;

/** Optional causal metadata supplied by a trusted producer. Actor identity comes from the platform context. */
public record BusinessEventEmissionContext(String producerRef, String correlationId, String causationId,
                                           String expectedSchemaFingerprint, java.time.Instant occurredAt) {
  public BusinessEventEmissionContext(String producerRef, String correlationId, String causationId,
                                     String expectedSchemaFingerprint) {
    this(producerRef, correlationId, causationId, expectedSchemaFingerprint, null);
  }
  public BusinessEventEmissionContext(String producerRef, String correlationId, String causationId) {
    this(producerRef, correlationId, causationId, null);
  }

  public BusinessEventEmissionContext expectingSchema(String fingerprint) {
    return new BusinessEventEmissionContext(producerRef, correlationId, causationId, fingerprint, occurredAt);
  }

  /** Trusted source projections retain the fact's original time, including delayed or out-of-order delivery. */
  public BusinessEventEmissionContext withOccurredAt(java.time.Instant time) {
    return new BusinessEventEmissionContext(producerRef, correlationId, causationId, expectedSchemaFingerprint,
        java.util.Objects.requireNonNull(time));
  }
  public static BusinessEventEmissionContext fromProducer(String producerRef) {
    return new BusinessEventEmissionContext(producerRef, null, null);
  }

  public static BusinessEventEmissionContext fromAction() {
    return new BusinessEventEmissionContext(null, null, null);
  }
}
