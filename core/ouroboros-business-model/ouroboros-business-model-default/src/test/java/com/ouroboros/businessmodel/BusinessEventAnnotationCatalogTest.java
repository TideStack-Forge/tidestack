package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.ouroboros.businessmodel.annotation.BusinessEvent;
import com.ouroboros.businessmodel.annotation.BusinessEventPayload;
import com.ouroboros.businessmodel.annotation.BusinessEventSubscription;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessEventAuthorities;
import com.ouroboros.businessmodel.event.BusinessEventDeliveryContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.security.authority.Authority;

class BusinessEventAnnotationCatalogTest {
  @AfterEach
  void resetCatalog() {
    try (GenericApplicationContext empty = annotationContext()) {
      empty.refresh();
      registerBusinessEventAnnotationCatalog(empty).afterSingletonsInstantiated();
    }
  }

  @Test
  void codeFirstEventAndTypedListenerMaterializeInTheNormalModelSnapshot() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(EventContract.class);
      context.registerBean(TypedConsumer.class);
      context.refresh();
      var catalog = registerBusinessEventAnnotationCatalog(context);
      catalog.afterSingletonsInstantiated();
      var model = model();
      var behavior = model.getBehavior();
      assertEquals("model", behavior.getEvents().get("submitted").getSubject());
      assertTrue(behavior.getEvents().get("submitted").isRequireUnfilteredModelRead());
      assertEquals(List.of("orderId"), behavior.getEvents().get("submitted").getPayloadSchema().get("required"));
      assertEquals("stockListener", behavior.getEventSubscriptions().get("reserve").getTarget().getListenerName());
      assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
      var read = model.buildModelBundle().getModel(Authority.class,
          BusinessEventAuthorities.read(new BusinessEventIdentity("sales.Order", "submitted"))).orElseThrow();
      assertEquals("orderId", read.getAccessControlDefine().getControlledFields().get(0).getName());
      var occurrence = occurrence();
      catalog.getListeners().get(0).onEvent(occurrence,
          new BusinessEventDeliveryContext("delivery-1", "sales.Order", "reserve", "stock-service", 1, null, null));
      assertEquals(new SubmittedPayload("order-1"), context.getBean(TypedConsumer.class).received);
      assertEquals("event-1", occurrence.eventId());
    }
  }

  @Test
  void methodListenerConfigurationAndItsSchemaUseTheExistingAnnotationCatalog() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(ConfiguredConsumer.class); context.refresh();
      var catalog = registerBusinessEventAnnotationCatalog(context); catalog.afterSingletonsInstantiated();
      var definition = BusinessEventAnnotationCatalog.getDefinitionContributions().getFirst().getEventSubscriptions().getFirst();
      assertEquals(Map.of("template", "orderSubmitted"), definition.getTarget().getConfig());
      var listener = catalog.getListeners().getFirst();
      assertEquals("object", listener.getListenerConfigSchema().get("type"));
      assertEquals("object", listener.getListenerInputSchema().get("type"));
      var published = new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order", List.of(), List.of(), List.of(definition));
      var input = published.subscriptionInputPlan("reserve").map(Map.of("event", Map.of("payload", Map.of("orderId", "order-1"))), value -> value);
      assertEquals(com.ouroboros.businessmodel.event.BusinessEventDeliveryMode.INDEPENDENT, listener.getDeliveryMode());
      listener.onEvent(occurrence(), new BusinessEventDeliveryContext("delivery", "sales.Order", "reserve", "service", 1,
          null, null, definition.getTarget().getConfig(), input));
      assertEquals("orderSubmitted", context.getBean(ConfiguredConsumer.class).template);
      assertEquals(Map.of("order", "order-1"), context.getBean(ConfiguredConsumer.class).input);
    }
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(InvalidConfiguredConsumer.class); context.refresh();
      assertThrows(IllegalStateException.class, () ->
          registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated());
    }
  }

  @Test
  void nullConfigurationAndBindingOverridesAreRejectedAtTheOriginalCatalogBoundary() {
    for (Class<?> type : List.of(NullConfiguredConsumer.class, ConfiguredBindingConsumer.class)) {
      try (GenericApplicationContext context = annotationContext()) {
        context.registerBean(type); context.refresh();
        assertThrows(IllegalStateException.class, () ->
            registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated());
      }
    }
  }

  @Test
  void listenerBindingPreservesTheDynamicSubscriptionContract() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(BindingConsumer.class);
      context.refresh();
      var catalog = registerBusinessEventAnnotationCatalog(context);
      catalog.afterSingletonsInstantiated();
      var meta = meta();
      EventSubscriptionDefinition subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
      subscription.setName("reserve");
      subscription.setIsEnabled(false);
      subscription.getSourceEvent().setModelFullName("sales.Order");
      subscription.getSourceEvent().setEventName("submitted");
      meta.setEventSubscriptions(List.of(subscription));
      var model = new DefaultBusinessModel(meta, emptyProvider());
      var published = model.getBehavior().getEventSubscriptions().get("reserve");
      assertEquals("stockListener", published.getTarget().getListenerName());
      assertEquals(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, published.getExecutionMode());
      assertEquals(false, published.getIsEnabled());
      assertEquals(null, subscription.getTarget().getListenerName());
      assertTrue(BusinessEventAnnotationCatalog.getDefinitionContributions().isEmpty());
    }
  }

  @Test
  void asyncListenerCannotBeAcknowledgedBeforeItsWorkCompletes() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(AsyncConsumer.class);
      context.refresh();
      assertThrows(IllegalStateException.class,
          () -> registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated());
    }
  }

  @Test
  void springAsyncVoidListenerCannotBeAcknowledgedBeforeItsWorkCompletes() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(SpringAsyncConsumer.class);
      context.refresh();
      assertThrows(IllegalStateException.class,
          () -> registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated());
    }
  }

  @Test
  void annotatedSynchronousListenerCanUseASpringProxyWithAnotherAsyncMethod() {
    var target = new MixedConsumer();
    var queued = new java.util.ArrayList<Runnable>();
    var proxy = new org.springframework.aop.framework.ProxyFactory(target);
    proxy.setProxyTargetClass(true);
    proxy.addAdvisor(new org.springframework.scheduling.annotation.AsyncAnnotationAdvisor(queued::add,
        (error, method, parameters) -> { throw new IllegalStateException(error); }));
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(MixedConsumer.class, () -> (MixedConsumer) proxy.getProxy());
      context.refresh();
      var catalog = registerBusinessEventAnnotationCatalog(context);
      catalog.afterSingletonsInstantiated();
      catalog.getListeners().get(0).onEvent(occurrence(),
          new BusinessEventDeliveryContext("delivery-1", "sales.Order", "reserve", "stock-service", 1, null, null));
      assertEquals(new SubmittedPayload("order-1"), target.received);
      assertTrue(queued.isEmpty());
    }
  }

  public static class ConfiguredConsumer {
    String template;
    Map<String, Object> input;
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        sourceEventModelFullName = "sales.Order", sourceEventName = "submitted", listenerName = "configured",  listenerConfig = "{\"template\":\"orderSubmitted\"}",
        inputMapping = "{\"order\":\"${ event.payload.orderId }\"}",
        listenerInputSchema = "{\"type\":\"object\",\"required\":[\"order\"],\"properties\":{\"order\":{\"type\":\"string\"}}}",
        listenerConfigSchema = "{\"type\":\"object\",\"required\":[\"template\"],\"properties\":{\"template\":{\"type\":\"string\"}}}")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      template = (String) delivery.listenerConfig().get("template");
      input = delivery.listenerInput();
    }
  }

  public static class NullConfiguredConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        sourceEventModelFullName = "sales.Order", sourceEventName = "submitted", listenerName = "invalid", listenerConfig = "null")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      throw new IllegalStateException("invalid config must not run");
    }
  }

  public static class ConfiguredBindingConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve", listenerName = "invalid",
        listenerConfig = "{\"template\":\"override\"}")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      throw new IllegalStateException("binding cannot override dynamic config");
    }
  }

  public static class InvalidConfiguredConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve", listenerName = "invalid",
        listenerConfigSchema = "{\"type\":42}")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      throw new IllegalStateException("invalid config must not run");
    }
  }

  public static class MixedConsumer extends TypedConsumer {
    @org.springframework.scheduling.annotation.Async
    public void other() {
      throw new IllegalStateException("The unrelated asynchronous method must never be invoked");
    }
  }

  public static class SpringAsyncConsumer {
    @org.springframework.scheduling.annotation.Async
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        sourceEventModelFullName = "sales.Order", sourceEventName = "submitted",
        listenerName = "stockListener")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      throw new IllegalStateException("The asynchronous listener must never be invoked");
    }
  }

  @Test
  void recursiveOrOpaquePayloadTypesCannotCreateAnUnverifiableSchema() {
    assertThrows(IllegalArgumentException.class, () -> BusinessEventPayloadSchemaGenerator.schema(OpaquePayload.class));
    assertThrows(IllegalArgumentException.class, () -> BusinessEventPayloadSchemaGenerator.schema(RecursivePayload.class));
    assertEquals(List.of("OPEN", "CLOSED"), ((Map<?, ?>) ((Map<?, ?>)
        BusinessEventPayloadSchemaGenerator.schema(EnumPayload.class).get("properties")).get("status")).get("enum"));
  }

  private DefaultBusinessModel model() {
    return new DefaultBusinessModel(meta(), emptyProvider());
  }

  private DefaultBusinessModelMeta meta() {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sales");
    meta.setName("Order");
    meta.setTitle("Order");
    meta.setIsEnabled(true);
    return meta;
  }

  private BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) {
        return Optional.empty();
      }

      public Map<String, BusinessModel> getBusinessModelMap() {
        return Map.of();
      }
    };
  }

  private BusinessEventOccurrence<Map<String, Object>> occurrence() {
    return BusinessEventOccurrence.create("event-1", new BusinessEventIdentity("sales.Order", "submitted"), "revision-1",
        BusinessEventSubject.model("sales.Order"), BusinessEventPayloadSchemaGenerator.schema(SubmittedPayload.class),
        Map.of("orderId", "order-1"), Instant.now(), "producer", "actor", null, null, null);
  }

  @BusinessEvent(modelFullName = "sales.Order", eventName = "submitted", title = "Submitted",
      description = "Order was submitted", occurrenceCondition = "Submission committed", subject = "model", requireUnfilteredModelRead = true,
      payloadSchema = "com.ouroboros.businessmodel.BusinessEventAnnotationCatalogTest$SubmittedPayload")
  public static class EventContract {
  }

  @BusinessEventPayload(modelFullName = "sales.Order", eventName = "submitted")
  public record SubmittedPayload(String orderId) {
  }

  public static class TypedConsumer {
    SubmittedPayload received;

    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        sourceEventModelFullName = "sales.Order", sourceEventName = "submitted",
        listenerName = "stockListener")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      received = occurrence.payload();
    }
  }

  public static class BindingConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        listenerName = "stockListener")
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) {
      assertEquals("event-1", occurrence.eventId());
    }
  }

  @Test
  void annotationsCanDisableOwnedDefinitionsButCannotDisableAnExistingDynamicBinding() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(DisabledConsumer.class); context.refresh();
      registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated();
      assertEquals(false, BusinessEventAnnotationCatalog.getDefinitionContributions().getFirst()
          .getEventSubscriptions().getFirst().getIsEnabled());
    }
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(DisabledBinding.class); context.refresh();
      assertThrows(IllegalStateException.class, () ->
          registerBusinessEventAnnotationCatalog(context).afterSingletonsInstantiated());
    }
  }

  public static class DisabledConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "disabled",
        sourceEventModelFullName = "sales.Order", sourceEventName = "submitted",
        listenerName = "disabledListener",  isEnabled = false)
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) { }
  }

  public static class DisabledBinding {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "disabled",
        listenerName = "disabledListener", isEnabled = false)
    public void receive(BusinessEventOccurrence<SubmittedPayload> occurrence, BusinessEventDeliveryContext delivery) { }
  }

  public static class AsyncConsumer {
    @BusinessEventSubscription(executionMode = com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL, consumerModelFullName = "sales.Order", subscriptionName = "reserve",
        listenerName = "stockListener")
    public java.util.concurrent.CompletableFuture<Void> receive(BusinessEventOccurrence<SubmittedPayload> occurrence,
                                                                BusinessEventDeliveryContext delivery) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
  }

  static class OpaquePayload {
  }

  record RecursivePayload(RecursivePayload child) {
  }

  record EnumPayload(State status) {
  }

  enum State { OPEN, CLOSED }

  private static com.ouroboros.businessmodel.BusinessEventAnnotationCatalog registerBusinessEventAnnotationCatalog(org.springframework.context.support.GenericApplicationContext context) {
    var catalog = new com.ouroboros.businessmodel.BusinessEventAnnotationCatalog(context.getBeanFactory());
    context.getBeanFactory().registerSingleton(BusinessEventAnnotationCatalog.class.getName(), catalog);
    return catalog;
  }
  private static org.springframework.context.support.GenericApplicationContext annotationContext() {
    var context = new org.springframework.context.support.GenericApplicationContext();
    context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    return context;
  }
}
