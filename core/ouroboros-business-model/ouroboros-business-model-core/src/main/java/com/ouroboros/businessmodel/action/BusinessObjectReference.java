package com.ouroboros.businessmodel.action;

import java.util.Objects;

/** Stable reference to one instance of a business model. */
public record BusinessObjectReference(String modelFullName, Object id) {
  public BusinessObjectReference {
    if (modelFullName == null || modelFullName.isBlank()) {
      throw new IllegalArgumentException("modelFullName must not be blank");
    }
    Objects.requireNonNull(id, "id must not be null");
  }
}
