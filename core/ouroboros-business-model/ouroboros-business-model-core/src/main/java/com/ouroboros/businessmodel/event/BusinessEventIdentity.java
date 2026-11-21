package com.ouroboros.businessmodel.event;

import java.util.Objects;

/** Stable business identity of an Event definition. */
public record BusinessEventIdentity(String modelFullName, String eventName) {
  public BusinessEventIdentity {
    requireText(modelFullName, "modelFullName");
    requireText(eventName, "eventName");
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
