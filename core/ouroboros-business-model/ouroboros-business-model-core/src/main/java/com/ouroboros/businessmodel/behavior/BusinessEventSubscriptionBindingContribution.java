package com.ouroboros.businessmodel.behavior;

import java.util.Objects;

/** High-code listener binding for a consumer-owned dynamic Event subscription. */
public record BusinessEventSubscriptionBindingContribution(String consumerModelFullName,
                                                            String subscriptionName,
                                                            String source,
                                                            String listenerName,
                                                            String formatVersion) {
  public BusinessEventSubscriptionBindingContribution(String consumerModelFullName, String subscriptionName,
                                                       String source, String listenerName) {
    this(consumerModelFullName, subscriptionName, source, listenerName, null);
  }

  public BusinessEventSubscriptionBindingContribution {
    new BehaviorDefinitionSource(source, "eventSubscriptions." + subscriptionName + ".listener", formatVersion);
    requireText(consumerModelFullName, "consumerModelFullName");
    requireText(subscriptionName, "subscriptionName");
    requireText(source, "source");
    requireText(listenerName, "listenerName");
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
