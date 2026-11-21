package com.ouroboros.notification.adapter;

import java.util.Objects;

/**
 * A receipt for exactly the address collection supplied to one channel invocation.
 * Diagnostics are non-sensitive operational codes, not provider response bodies or notification content.
 * RETRYABLE means the provider confirms this unit was not accepted; ambiguous effects must be UNKNOWN.
 */
public record NotificationChannelResult(Outcome outcome, String diagnostic) {
  public enum Outcome { ACCEPTED, RETRYABLE, BLOCKED, UNKNOWN }

  public NotificationChannelResult {
    Objects.requireNonNull(outcome, "outcome");
    if (diagnostic == null || diagnostic.isBlank()) { throw new IllegalArgumentException("channel receipt requires diagnostic"); }
  }
}
