package com.ouroboros.businessmodel.event;

import java.util.Objects;

/** The business object or model to which an EventOccurrence refers. */
public record BusinessEventSubject(String modelFullName, Object id) {
  public BusinessEventSubject {
    Objects.requireNonNull(modelFullName, "modelFullName must not be null");
    if (modelFullName.isBlank()) {
      throw new IllegalArgumentException("modelFullName must not be blank");
    }
    if (id != null && !(id instanceof String || id instanceof Byte || id instanceof Short
        || id instanceof Integer || id instanceof Long || id instanceof Float || id instanceof Double
        || id instanceof java.math.BigInteger || id instanceof java.math.BigDecimal || id instanceof java.util.UUID)) {
      throw new IllegalArgumentException("Business Event subject must use a scalar object id");
    }
  }

  public static BusinessEventSubject model(String modelFullName) {
    return new BusinessEventSubject(modelFullName, null);
  }

  public static BusinessEventSubject instance(String modelFullName, Object id) {
    Objects.requireNonNull(id, "id must not be null for an instance subject");
    return new BusinessEventSubject(modelFullName, id);
  }

  public boolean isInstance() {
    return id != null;
  }
}
