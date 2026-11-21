package com.ouroboros.mq.binding;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MQBindingPropertiesMergeContractTest {

  @Test
  void toHydratedMapShouldIncludeBaseAndConsumerFields() {
    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDescription("Order events")
        .setDestination("orders-topic")
        .setBinder("mq")
        .setGroup("order-group")
        .setContentType("application/json")
        .setBroadcast(Boolean.TRUE)
        .setRoutingKey("orders.created")
        .setFlowName("order-flow")
        .setExtraProp("concurrency", "5");

    Map<String, Object> hydrated = invokeToHydratedMap(properties);

    assertEquals("orders", hydrated.get("bindingName"));
    assertEquals("Order events", hydrated.get("description"));
    assertEquals("orders-topic", hydrated.get("destination"));
    assertEquals("mq", hydrated.get("binder"));
    assertEquals("order-group", hydrated.get("group"));
    assertEquals("application/json", hydrated.get("contentType"));
    assertEquals(Boolean.TRUE, hydrated.get("broadcast"));
    assertEquals("orders.created", hydrated.get("routingKey"));
    assertEquals("order-flow", hydrated.get("flowName"));
    assertEquals("5", hydrated.get("concurrency"));
  }

  @Test
  void fillNullsFromShouldPreserveExistingBaseAndConsumerValues() {
    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setGroup("existing-group")
        .setFlowName("existing-flow")
        .setExtraProp("concurrency", "3");

    MQConsumerProperties fallback = new MQConsumerProperties()
        .setBindingName("fallback-orders")
        .setDescription("Order events")
        .setDestination("orders-topic")
        .setBinder("mq")
        .setGroup("fallback-group")
        .setContentType("application/json")
        .setBroadcast(Boolean.TRUE)
        .setRoutingKey("orders.created")
        .setFlowName("fallback-flow")
        .setExtraProp("concurrency", "5")
        .setExtraProp("batchMode", Boolean.TRUE);

    invokeFillNullsFrom(properties, fallback);

    assertEquals("orders", properties.getBindingName());
    assertEquals("Order events", properties.getDescription());
    assertEquals("orders-topic", properties.getDestination());
    assertEquals("mq", properties.getBinder());
    assertEquals("existing-group", properties.getGroup());
    assertEquals("application/json", properties.getContentType());
    assertEquals(Boolean.TRUE, properties.getBroadcast());
    assertEquals("orders.created", properties.getRoutingKey());
    assertEquals("existing-flow", properties.getFlowName());
    assertEquals("3", properties.getExtraProp("concurrency").orElse(null));
    assertEquals(Boolean.TRUE, properties.getExtraProp("batchMode").orElse(null));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> invokeToHydratedMap(MQBindingProperties<?> properties) {
    try {
      return (Map<String, Object>) properties.getClass().getMethod("toHydratedMap").invoke(properties);
    } catch (NoSuchMethodException e) {
      return properties.getExtraProps();
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static void invokeFillNullsFrom(MQBindingProperties<?> target, MQBindingProperties<?> source) {
    try {
      target.getClass().getMethod("fillNullsFrom", MQBindingProperties.class).invoke(target, source);
    } catch (NoSuchMethodException e) {
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
