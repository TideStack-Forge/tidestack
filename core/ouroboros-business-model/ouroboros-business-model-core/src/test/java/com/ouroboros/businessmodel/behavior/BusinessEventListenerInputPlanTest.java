package com.ouroboros.businessmodel.behavior;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;

import static org.junit.jupiter.api.Assertions.*;

class BusinessEventListenerInputPlanTest {
  @Test
  void publishedExplicitInputIsIsolatedFromLiteralConfigurationAndOriginalContainers() {
    var subscription = subscription();
    var mapping = new LinkedHashMap<String, Object>(); mapping.put("payload.id", "${ event.payload.id }");
    mapping.put("missing", "${ event.payload.absent }"); mapping.put("nil", "${ event.payload.nil }");
    mapping.put("label", "$${ literal }");
    subscription.getTarget().setInputMapping(mapping);
    subscription.getTarget().setConfig(Map.of("template", "${ notAnExpression( }"));
    var behavior = behavior(subscription);
    mapping.clear(); subscription.getTarget().setInputMapping(null);
    var payload = new LinkedHashMap<String, Object>(); payload.put("id", "one"); payload.put("nil", null);
    var input = behavior.subscriptionInputPlan("notify").map(Map.of("event", Map.of("payload", payload)), value -> value);
    assertEquals("one", ((Map<?, ?>) input.get("payload")).get("id"));
    assertFalse(input.containsKey("missing")); assertTrue(input.containsKey("nil")); assertNull(input.get("nil"));
    assertEquals("${ literal }", input.get("label"));
    assertEquals("${ notAnExpression( }", behavior.getEventSubscriptions().get("notify").getTarget().getConfig().get("template"));
    var exposed = behavior.getEventSubscriptions().get("notify"); exposed.getTarget().setInputMapping(Map.of("other", 42));
    assertFalse(behavior.subscriptionInputPlan("notify").map(Map.of("event", Map.of("payload", payload)), value -> value).containsKey("other"));
    assertEquals("two", ((Map<?, ?>) behavior.subscriptionInputPlan("notify").map(
        Map.of("event", Map.of("payload", Map.of("id", "two"))), value -> value).get("payload")).get("id"));
  }

  @Test
  void newMappingHasOnlyOfficialSyntaxRegardlessOfTheConsumerMetadataVersion() {
    for (String expression : List.of("$.event.payload.id", "${ event.payload[ }")) {
      var subscription = subscription(); subscription.getTarget().setInputMapping(Map.of("id", expression));
      var failure = assertThrows(IllegalArgumentException.class, () -> behavior(subscription));
      assertTrue(failure.getMessage().contains("eventSubscriptions.notify.target"));
    }
    var subscription = subscription();
    assertTrue(behavior(subscription).subscriptionInputPlan("notify").map(Map.of(), value -> value).isEmpty());
  }

  @Test
  void deliveryFreezesEvaluatedInputAndRetainsEightArgumentCompatibility() {
    var nested = new LinkedHashMap<String, Object>(); nested.put("id", "one");
    var delivery = new BusinessEventDeliveryContext("id", "Order", "notify", "service", 1, null, null,
        Map.of("template", "order"), Map.of("payload", nested));
    nested.clear();
    var frozen = (Map<?, ?>) delivery.listenerInput().get("payload");
    assertEquals("one", frozen.get("id")); assertThrows(UnsupportedOperationException.class, frozen::clear);
    assertTrue(new BusinessEventDeliveryContext("id", "Order", "notify", "service", 1, null, null, Map.of()).listenerInput().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> new BusinessEventDeliveryContext("id", "Order", "notify", "service",
        1, null, null, Map.of(), Map.of("request", new Object())));
  }

  private EventSubscriptionDefinition subscription() {
    var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL); subscription.setName("notify");
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("notification"); return subscription;
  }

  private BusinessModelBehavior behavior(EventSubscriptionDefinition subscription) {
    return new BusinessModelBehavior("Order", List.of(), List.of(), List.of(subscription));
  }
}
