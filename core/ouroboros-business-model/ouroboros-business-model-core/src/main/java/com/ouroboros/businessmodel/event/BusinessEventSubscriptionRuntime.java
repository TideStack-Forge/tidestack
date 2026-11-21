package com.ouroboros.businessmodel.event;

/** Executes one subscription using its current published definition and consumer identity. */
public interface BusinessEventSubscriptionRuntime {
  /** Resolves the consuming account's User.userIdentity independently of the source actor. */
  default java.util.Optional<String> resolveUser(String consumerModelFullName) {
    return java.util.Optional.empty();
  }

  default java.util.Optional<BusinessEventDeliveryAttempt> inspectAttempt(String consumerModelFullName, String subscriptionName) {
    return java.util.Optional.empty();
  }

  BusinessEventDeliveryResult deliver(BusinessEventOccurrence<?> occurrence,
                                      String consumerModelFullName,
                                      String subscriptionName,
                                      String deliveryId,
                                      int attempt);

  /** Pins the selected contract and execution mode across a lock-free external-effect boundary. */
  default BusinessEventDeliveryResult deliver(BusinessEventOccurrence<?> occurrence, String consumerModelFullName,
      String subscriptionName, String deliveryId, int attempt, BusinessEventDeliveryAttempt expected) {
    if (expected != null && expected.executionMode() == BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT) {
      return BusinessEventDeliveryResult.contractBlocked("independent-listener-runtime-unavailable");
    }
    return deliver(occurrence, consumerModelFullName, subscriptionName, deliveryId, attempt);
  }
}
