package com.ouroboros.businessmodel.event;

/** The implementation owns this capability; it is not a dynamic subscription switch. */
public enum BusinessEventDeliveryMode {
  /** Database effects and Outbox acknowledgement share the receiver transaction and lock. */
  ATOMIC,
  /** Independently durable external effects run synchronously without a receiver transaction or lock. */
  INDEPENDENT
}
