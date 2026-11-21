package com.ouroboros.security.profile;

/** Delivery cannot modify account state; notification adapters report the actual result. */
public interface ContactVerificationDelivery {
  boolean supports(String kind);
  void deliver(Message message);

  record Message(String kind, String address, String requestId, String proof) {
    @Override public String toString() { return "ContactVerificationMessage[redacted]"; }
  }
}
