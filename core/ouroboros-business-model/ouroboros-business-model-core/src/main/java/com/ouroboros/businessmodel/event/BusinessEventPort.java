package com.ouroboros.businessmodel.event;

import java.util.Map;
import java.util.function.Supplier;

/** Emits a declared business fact through the platform event contract. */
public interface BusinessEventPort {
  /** Executes a producer's business writes and event emission in one governed source transaction. */
  default <T> T inTransaction(Supplier<T> producer) {
    throw new IllegalStateException("Business Event source transactions are unavailable");
  }

  BusinessEventOccurrence<Map<String, Object>> emit(BusinessEventIdentity identity,
                                                     BusinessEventSubject subject,
                                                     Object payload,
                                                     BusinessEventEmissionContext context);
}
