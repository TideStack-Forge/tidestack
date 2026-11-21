package com.ouroboros.notification;

import java.util.List;
import java.util.Objects;

/** Rechecks present recipient eligibility, disclosure and ownership of the frozen addresses before each send. */
@FunctionalInterface
public interface NotificationDeliveryPolicy {
  Decision evaluate(String recipientId, String channel, List<String> addresses);

  enum Disposition { ALLOW, SKIP, BLOCK }

  record Decision(Disposition disposition, String diagnostic) {
    public Decision {
      Objects.requireNonNull(disposition, "disposition");
      if (diagnostic == null || diagnostic.isBlank()) { throw new IllegalArgumentException("recipient decision requires diagnostic"); }
    }
  }
}
