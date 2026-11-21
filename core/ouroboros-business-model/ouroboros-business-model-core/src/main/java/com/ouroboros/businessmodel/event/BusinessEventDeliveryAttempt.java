package com.ouroboros.businessmodel.event;

/** Published contract and selected implementation used by one delivery attempt. */
public record BusinessEventDeliveryAttempt(String subscriptionRevision, String targetRevision,
                                           String implementationRef, String implementationVersion,
                                           BusinessEventExecutionMode executionMode, String user) {
  public BusinessEventDeliveryAttempt {
    java.util.Objects.requireNonNull(executionMode);
  }

  public BusinessEventDeliveryAttempt(String subscriptionRevision, String targetRevision,
      String implementationRef, String implementationVersion, BusinessEventExecutionMode executionMode) {
    this(subscriptionRevision, targetRevision, implementationRef, implementationVersion, executionMode, null);
  }

  public BusinessEventDeliveryAttempt(String subscriptionRevision, String targetRevision,
                                      String implementationRef, String implementationVersion) {
    this(subscriptionRevision, targetRevision, implementationRef, implementationVersion, BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
  }
}
