package com.ouroboros.businessmodel.event;

/** Runtime identity and attempt facts; user stores the consuming account's User.userIdentity. */
public record BusinessEventDeliveryContext(String deliveryId,
                                           String consumerModelFullName,
                                           String subscriptionName,
                                           String user,
                                           int attempt,
                                           String correlationId,
                                           String causationId,
                                           java.util.Map<String, Object> listenerConfig,
                                           java.util.Map<String, Object> listenerInput) {
  public BusinessEventDeliveryContext {
    listenerInput = listenerInput == null || listenerInput.isEmpty() ? java.util.Map.of()
        : BusinessEventOccurrence.immutableMap(listenerInput);
    listenerConfig = listenerConfig == null || listenerConfig.isEmpty() ? java.util.Map.of()
        : BusinessEventOccurrence.immutableMap(listenerConfig);
  }

  public BusinessEventDeliveryContext(String deliveryId, String consumerModelFullName, String subscriptionName,
                                     String user, int attempt, String correlationId, String causationId,
                                     java.util.Map<String, Object> listenerConfig) {
    this(deliveryId, consumerModelFullName, subscriptionName, user, attempt, correlationId, causationId,
        listenerConfig, java.util.Map.of());
  }

  public BusinessEventDeliveryContext(String deliveryId, String consumerModelFullName, String subscriptionName,
                                     String user, int attempt, String correlationId, String causationId) {
    this(deliveryId, consumerModelFullName, subscriptionName, user, attempt, correlationId, causationId,
        java.util.Map.of());
  }
}
