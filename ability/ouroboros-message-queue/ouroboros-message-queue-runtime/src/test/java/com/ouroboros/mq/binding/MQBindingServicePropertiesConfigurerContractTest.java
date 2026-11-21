package com.ouroboros.mq.binding;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MQBindingServicePropertiesConfigurerContractTest {

  @Test
  void configBindingShouldNoOpWhenBindingServicePropertiesIsMissing() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDestination("orders-topic")
        .setGroup("default-group")
        .setContentType("application/json")
        .setBinder("mq");

    assertDoesNotThrow(() -> configurer.configBinding(properties));
  }

  @Test
  void configBindingShouldCreateNewBindingWhenAbsent() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
    configurer.bindingServiceProperties = bindingServiceProperties;

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDestination("orders-topic")
        .setGroup("consumer-group")
        .setContentType("application/json")
        .setBinder("mq");

    configurer.configBinding(properties);

    BindingProperties binding = bindingServiceProperties.getBindings().get("orders");
    assertNotNull(binding);
    assertEquals("orders-topic", binding.getDestination());
    assertEquals("consumer-group", binding.getGroup());
    assertEquals("application/json", binding.getContentType());
    assertEquals("mq", binding.getBinder());
  }

  @Test
  void configBindingShouldCreateHydratedConsumerSectionWhenBindingIsAbsent() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
    configurer.bindingServiceProperties = bindingServiceProperties;

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDestination("orders-topic")
        .setGroup("consumer-group")
        .setContentType("application/json")
        .setBinder("mq")
        .setExtraProp("maxAttempts", "7");

    configurer.configBinding(properties);

    BindingProperties binding = bindingServiceProperties.getBindings().get("orders");
    assertNotNull(binding);
    assertNotNull(binding.getConsumer());
    assertEquals(7, binding.getConsumer().getMaxAttempts());
  }

  @Test
  void configBindingShouldOnlyFillNullFieldsWhenBindingExists() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
    configurer.bindingServiceProperties = bindingServiceProperties;

    BindingProperties existing = new BindingProperties();
    existing.setDestination("existing-destination");
    existing.setGroup(null);
    existing.setContentType(null);
    existing.setBinder("existing-binder");
    bindingServiceProperties.getBindings().put("orders", existing);

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDestination("new-destination")
        .setGroup("new-group")
        .setContentType("application/json")
        .setBinder("new-binder");

    configurer.configBinding(properties);

    BindingProperties merged = bindingServiceProperties.getBindings().get("orders");
    assertEquals("existing-destination", merged.getDestination());
    assertEquals("new-group", merged.getGroup());
    assertEquals("application/json", merged.getContentType());
    assertEquals("existing-binder", merged.getBinder());
  }

  @Test
  void configBindingShouldKeepExistingConsumerSectionWhenAlreadyPresent() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
    configurer.bindingServiceProperties = bindingServiceProperties;

    BindingProperties existing = new BindingProperties();
    ConsumerProperties existingConsumer = new ConsumerProperties();
    existingConsumer.setMaxAttempts(9);
    existing.setConsumer(existingConsumer);
    bindingServiceProperties.getBindings().put("orders", existing);

    MQConsumerProperties properties = new MQConsumerProperties()
        .setBindingName("orders")
        .setDestination("orders-topic")
        .setExtraProp("maxAttempts", "3");

    configurer.configBinding(properties);

    BindingProperties merged = bindingServiceProperties.getBindings().get("orders");
    assertSame(existingConsumer, merged.getConsumer());
    assertEquals(9, merged.getConsumer().getMaxAttempts());
    assertEquals("orders-topic", merged.getDestination());
  }

  @Test
  void configBindingShouldCreateHydratedProducerSectionWhenBindingIsAbsent() {
    MQBindingServicePropertiesConfigurer configurer = new MQBindingServicePropertiesConfigurer();
    BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
    configurer.bindingServiceProperties = bindingServiceProperties;

    MQProducerProperties properties = new MQProducerProperties()
        .setBindingName("orders-out")
        .setDestination("orders-topic")
        .setContentType("application/json")
        .setBinder("mq")
        .setExtraProp("partitionCount", "3");

    configurer.configBinding(properties);

    BindingProperties binding = bindingServiceProperties.getBindings().get("orders-out");
    assertNotNull(binding);
    assertNotNull(binding.getProducer());
    assertEquals(3, binding.getProducer().getPartitionCount());
  }
  @Test
  void broadcastConsumersUseIndependentAnonymousSubscriptionsEvenWhenAnOldGroupExists() {
    var configurer = new MQBindingServicePropertiesConfigurer();
    var service = new BindingServiceProperties();
    configurer.bindingServiceProperties = service;
    var previous = new BindingProperties();
    previous.setGroup("old-competing-group");
    service.getBindings().put("commands", previous);
    configurer.configBinding(new MQConsumerProperties().setBindingName("commands")
        .setDestination("helper.commands").setGroup("also-competing").setBroadcast(true));
    org.junit.jupiter.api.Assertions.assertNull(service.getBindings().get("commands").getGroup());
    configurer.configBinding(new MQConsumerProperties().setBindingName("events")
        .setDestination("debug.events").setGroup("shared-ingest").setBroadcast(false));
    assertEquals("shared-ingest", service.getBindings().get("events").getGroup());
  }

}
