package com.ouroboros.businessmodel.event;

/** Payload-free outcome returned when delivery is paused by an authorization boundary. */
public record BusinessEventDeliveryResult(Status status, String diagnosticCode) {
  public enum Status {
    DELIVERED,
    FILTERED,
    AUTHORIZATION_BLOCKED,
    CONTRACT_BLOCKED,
    PRECONDITION_BLOCKED
  }

  public static BusinessEventDeliveryResult delivered() {
    return new BusinessEventDeliveryResult(Status.DELIVERED, null);
  }

  public static BusinessEventDeliveryResult authorizationBlocked(String diagnosticCode) {
    return new BusinessEventDeliveryResult(Status.AUTHORIZATION_BLOCKED, diagnosticCode);
  }

  public static BusinessEventDeliveryResult preconditionBlocked(String diagnosticCode) {
    return new BusinessEventDeliveryResult(Status.PRECONDITION_BLOCKED, diagnosticCode);
  }

  public static BusinessEventDeliveryResult contractBlocked(String diagnosticCode) {
    return new BusinessEventDeliveryResult(Status.CONTRACT_BLOCKED, diagnosticCode);
  }
}
