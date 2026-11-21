package com.ouroboros.businessmodel.event;

import java.util.Optional;
import java.util.function.Supplier;

/** Causal context only; consumer authority and transaction remain in the existing runtime boundaries. */
final class BusinessEventDeliveryScope {
  private static final ThreadLocal<BusinessEventOccurrence<?>> CURRENT = new ThreadLocal<>();

  private BusinessEventDeliveryScope() { }

  static Optional<BusinessEventOccurrence<?>> current() { return Optional.ofNullable(CURRENT.get()); }

  static <T> T with(BusinessEventOccurrence<?> occurrence, Supplier<T> operation) {
    var previous = CURRENT.get();
    CURRENT.set(occurrence);
    try {
      return operation.get();
    } finally {
      if (previous == null) { CURRENT.remove(); } else { CURRENT.set(previous); }
    }
  }
}
