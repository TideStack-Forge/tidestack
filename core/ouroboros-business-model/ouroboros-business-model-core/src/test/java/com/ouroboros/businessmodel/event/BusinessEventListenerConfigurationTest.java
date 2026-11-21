package com.ouroboros.businessmodel.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessEventListenerConfigurationTest {
  @Test
  void enablementPreservesAbsentMetadataAndHistoricalEffectiveFingerprints() {
    var subscription = Json.toBean(EventSubscriptionDefinition.class,
        "{\"name\":\"observe\",\"executionMode\":\"AFTER_COMMIT_TRANSACTIONAL\",\"sourceEvent\":{\"modelFullName\":\"sales.Order\",\"eventName\":\"submitted\"},\"target\":{\"kind\":\"listener\",\"listenerName\":\"listener\"}}");
    assertNull(subscription.getIsEnabled());
    var original = Json.toMap(Json.toJsonString(subscription));
    assertFalse(original.containsKey("isEnabled"));
    var historical = com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(original);
    assertEquals(historical, com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.subscriptionFingerprint(subscription));
    subscription.setIsEnabled(true);
    assertEquals(historical, com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.subscriptionFingerprint(subscription));
    subscription.setIsEnabled(false);
    assertNotEquals(historical, com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.subscriptionFingerprint(subscription));
    var behavior = new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order",
        List.of(), List.of(), List.of(subscription));
    subscription.setIsEnabled(true);
    assertEquals(false, behavior.getEventSubscriptions().get("observe").getIsEnabled());
    assertEquals(false, Json.toBean(EventSubscriptionDefinition.class,
        Json.toJsonString(behavior.getEventSubscriptions().get("observe"))).getIsEnabled());
  }

  @Test
  void listenerTargetRoundTripsWithoutMaterializingOptionalConfiguration() {
    var subscription = Json.toBean(EventSubscriptionDefinition.class, "{\"name\":\"observe\",\"executionMode\":\"AFTER_COMMIT_TRANSACTIONAL\",\"sourceEvent\":{\"modelFullName\":\"sales.Order\",\"eventName\":\"submitted\"},\"target\":{\"kind\":\"listener\",\"listenerName\":\"listener\"}}");
    assertNull(subscription.getTarget().getConfig());
    assertNull(subscription.getTarget().getInputMapping());
    assertFalse(Json.toJsonString(subscription).contains("listenerInputMapping"));
    assertFalse(Json.toJsonString(subscription).contains("listenerConfig"));
    var delivery = new BusinessEventDeliveryContext("delivery", "sales.Order", "observe", "service", 1, null, null);
    var serializedDelivery = Json.toMap(Json.toJsonString(delivery));
    assertEquals("service", serializedDelivery.get("user"));
    assertFalse(serializedDelivery.containsKey("executorRef"));
    assertFalse(serializedDelivery.containsKey("executionPrincipalRef"));
    assertEquals(delivery, Json.toBean(BusinessEventDeliveryContext.class, Json.toJsonString(delivery)));
    assertTrue(delivery.listenerConfig().isEmpty());
    assertTrue(delivery.listenerInput().isEmpty());
    BusinessEventListener legacy = new BusinessEventListener() {
      public String getName() { return "legacy"; }
      public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext context) { fail("not dispatched"); }
    };
    assertNull(legacy.getListenerConfigSchema());
    assertNull(legacy.getListenerInputSchema());
    assertNull(legacy.getListenerInputSchema(Map.of("mode", "legacy")));
    assertEquals(BusinessEventDeliveryMode.ATOMIC, legacy.getDeliveryMode());
    var oldAttempt = new BusinessEventDeliveryAttempt("revision", null, "listener", null);
    assertEquals(BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, oldAttempt.executionMode());
    BusinessEventSubscriptionRuntime oldRuntime = (occurrence, model, subscriptionName, deliveryId, attempt) -> BusinessEventDeliveryResult.delivered();
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        oldRuntime.deliver(null, "model", "subscription", "delivery", 1, oldAttempt).status());
    assertEquals("independent-listener-runtime-unavailable", oldRuntime.deliver(null, "model", "subscription", "delivery", 1,
        new BusinessEventDeliveryAttempt("revision", null, "listener", null, com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT)).diagnosticCode());
  }

  @Test
  void existingInputSchemaRemainsTheDefaultForConfiguredListeners() {
    var schema = Map.<String, Object>of("type", "object", "required", List.of("payload"));
    BusinessEventListener listener = new BusinessEventListener() {
      public String getName() { return "configured"; }
      public Map<String, Object> getListenerInputSchema() { return schema; }
      public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext context) { fail("not dispatched"); }
    };
    assertSame(schema, listener.getListenerInputSchema(Map.of("template", "existing")));
  }

  @Test
  void deliveryFreezesNestedConfigurationAndDoesNotShareTheCallerContainers() {
    var destinations = new ArrayList<>(List.of("email"));
    var options = new LinkedHashMap<String, Object>(); options.put("channels", destinations); options.put("optional", null);
    var config = new LinkedHashMap<String, Object>(); config.put("options", options);
    var delivery = new BusinessEventDeliveryContext("delivery", "sales.Order", "observe", "service", 1, null, null, config);
    destinations.add("sms"); options.clear(); config.clear();
    var frozen = (Map<?, ?>) delivery.listenerConfig().get("options");
    assertEquals(List.of("email"), frozen.get("channels")); assertTrue(frozen.containsKey("optional"));
    assertThrows(UnsupportedOperationException.class, frozen::clear);
    assertThrows(UnsupportedOperationException.class, () -> ((List<?>) frozen.get("channels")).clear());
    assertThrows(UnsupportedOperationException.class, () -> delivery.listenerConfig().clear());
  }

  @Test
  void aConfigurationCannotSmuggleNativeResourcesThroughTheEventDeliveryContext() {
    assertThrows(IllegalArgumentException.class, () -> new BusinessEventDeliveryContext(
        "delivery", "sales.Order", "observe", "service", 1, null, null, Map.of("resource", new Object())));
  }
}
