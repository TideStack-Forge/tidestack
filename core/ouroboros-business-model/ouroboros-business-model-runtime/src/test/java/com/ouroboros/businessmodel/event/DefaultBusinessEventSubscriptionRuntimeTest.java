package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.IDENTITY;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.MODEL;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.SCHEMA;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.event;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.provider;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.action.BusinessActionAvailability;
import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.model.DefaultDataModelField;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

class DefaultBusinessEventSubscriptionRuntimeTest {
  @Test
  void disabledSubscriptionRejectsDirectDeliveryBeforeResolvingIdentityOrInvokingAnyTarget() {
    var subscription = subscription(); subscription.setIsEnabled(false);
    var users = mock(UserCenter.class);
    var listener = mock(BusinessEventListener.class); when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var actions = mock(BusinessActionRuntime.class);
    var runtime = runtime(subscription, users, listener, actions);
    assertEquals(Optional.empty(), runtime.inspectAttempt("inventory.Stock", "reserve"));
    assertEquals("subscription-disabled", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery-1", 1).diagnosticCode());
    verify(users, never()).getUser(any());
    verify(listener, never()).onEvent(any(), any());
    verify(actions, never()).invoke(any());
  }

  @Test
  void subscriptionUsesThePublishedExecutionAuthorityWithoutRequiringAnActionNameGrant() {
    var subscription = subscription();
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock");
    subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    var original = consumer(subscription);
    var mapped = new BusinessModelBehavior(original.getModelFullName(), original.getActionDefinitions(), List.of(),
        List.of(subscription), List.of(), "mapped", Map.of(), Map.of(), Map.of(),
        Map.of("reserveStock", "inventory.Stock.workflow"));
    var actions = mock(BusinessActionRuntime.class);
    when(actions.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.EXECUTABLE, List.of()));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "inventory.Stock.workflow", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(mapped, source()), actions, List::of,
        identities(users), new BusinessEventTestSupport.Transactions());
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    verify(actions).invoke(any(BusinessActionInvocation.class));
    when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "inventory.Stock.reserveStock", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-2", 1).status());
    verify(actions, times(1)).invoke(any(BusinessActionInvocation.class));
  }
  @Test
  void staleGeneratedPayloadCannotBeAcknowledgedOrExposedToTheListener() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    when(listener.getPayloadSchemaFingerprint()).thenReturn("stale-schema");
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription(), users, listener, mock(BusinessActionRuntime.class));
    var fact = occurrence(BusinessEventSubject.model(MODEL));
    var result = runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1);
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED, result.status());
    verify(listener, never()).onEvent(any(), any());
    when(listener.getPayloadSchemaFingerprint()).thenReturn(
        com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(SCHEMA));
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 2).status());
  }

  @Test
  void explicitListenerInputUsesPublishedElAfterAuthorizationAndIsValidatedBeforeExposure() {
    var subscription = subscription(); subscription.getTarget().setConfig(Map.of("template", "${literal}"));
    subscription.getTarget().setInputMapping(Map.of("order", "${ event.payload.orderId }"));
    var listener = mock(BusinessEventListener.class); when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    when(listener.getListenerConfigSchema()).thenReturn(Map.of("type", "object"));
    when(listener.getListenerInputSchema(any())).thenReturn(Map.of("type", "object", "required", List.of("order"),
        "properties", Map.of("order", Map.of("type", "string")), "additionalProperties", false));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    runtime.validatePublishedBindings(new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this));
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    var captured = org.mockito.ArgumentCaptor.forClass(BusinessEventDeliveryContext.class);
    verify(listener).onEvent(any(), captured.capture());
    assertEquals(Map.of("order", "order-1"), captured.getValue().listenerInput());
    assertEquals(Map.of("template", "${literal}"), captured.getValue().listenerConfig());
    assertThrows(UnsupportedOperationException.class, () -> captured.getValue().listenerInput().clear());
    when(listener.getListenerInputSchema(any())).thenReturn(Map.of("type", "object", "properties", Map.of("order", Map.of("type", "integer"))));
    assertEquals("listener-input-incompatible", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery-1", 2).diagnosticCode());
    verify(listener, times(1)).onEvent(any(), any());
  }

  @Test
  void listenerInputCannotBypassDisclosureOrExecuteItsExpressionWithoutAuthorization() {
    var subscription = subscription(); subscription.getTarget().setInputMapping(Map.of("value", "${ 1 / 0 }"));
    var listener = mock(BusinessEventListener.class); when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    when(listener.getListenerInputSchema(any())).thenReturn(Map.of("type", "object"));
    var users = mock(UserCenter.class); when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of())));
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED, runtime.deliver(
        occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "d", 1).status());
    verify(listener, never()).getListenerInputSchema(any());
    when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    assertEquals("listener-input-mapping-failed", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "d", 2).diagnosticCode());
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void unsupportedListenerSchemasAndActionConfigurationAreRejected() {
    var subscription = subscription(); subscription.getTarget().setInputMapping(Map.of("id", "${ event.payload.orderId }"));
    var listener = mock(BusinessEventListener.class); when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class); when(users.getUser("stock-service")).thenAnswer(ignored -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    for (Map<String, Object> schema : java.util.Arrays.asList(null, Map.<String, Object>of("type", "array"), Map.<String, Object>of("type", 42))) {
      when(listener.getListenerInputSchema(any())).thenReturn(schema);
      var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
      assertThrows(BusinessException.class, () -> runtime.validatePublishedBindings(
          new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
      assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED, runtime.deliver(
          occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "d", 1).status());
    }
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null); subscription.getTarget().setModelFullName("inventory.Stock"); subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    subscription.getTarget().setConfig(Map.of("mode", "listener-only"));
    var runtime = runtime(subscription, users, null, mock(BusinessActionRuntime.class));
    assertThrows(BusinessException.class, () -> runtime.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    assertEquals("listener-config-requires-listener", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "d", 1).diagnosticCode());
  }

  @Test
  void publishedListenerConfigurationIsValidatedAndDeliveredLiterallyAfterAuthorization() {
    var subscription = subscription();
    subscription.getTarget().setConfig(Map.of("template", "${event.payload.orderId}"));
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    when(listener.getListenerConfigSchema()).thenReturn(Map.of("type", "object", "required", List.of("template"),
        "properties", Map.of("template", Map.of("type", "string")), "additionalProperties", false));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    runtime.validatePublishedBindings(new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this));
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    var captured = org.mockito.ArgumentCaptor.forClass(BusinessEventDeliveryContext.class);
    verify(listener).onEvent(any(), captured.capture());
    assertEquals(subscription.getTarget().getConfig(), captured.getValue().listenerConfig());
    assertEquals("event-service", captured.getValue().user());
  }

  @Test
  void missingUnsupportedAndInvalidConfigurationCannotReachAListenerOrPassPublication() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getListenerConfigSchema()).thenReturn(null);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var subscription = subscription(); subscription.getTarget().setConfig(Map.of("template", "message"));
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    var failure = assertThrows(BusinessException.class, () -> runtime.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("eventSubscriptions.reserve.listenerConfig-invalid"));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    when(listener.getListenerConfigSchema()).thenReturn(Map.of("type", 42));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 2).status());
    when(listener.getListenerConfigSchema()).thenReturn(Map.of("type", "object", "required", List.of("template"),
        "properties", Map.of("template", Map.of("type", "string"))));
    subscription.getTarget().setConfig(null);
    var missing = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    assertThrows(BusinessException.class, () -> missing.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        missing.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-2", 1).status());
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void listenerConfigurationDoesNotBypassDisclosureOrAcceptActionInputMappings() {
    var subscription = subscription(); subscription.getTarget().setConfig(Map.of("template", "message"));
    var listener = mock(BusinessEventListener.class); when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of())));
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    verify(listener, never()).getListenerConfigSchema();
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    subscription.getTarget().setInputMapping(Map.of("data", "${event.payload}"));
    var conflicting = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        conflicting.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 2).status());
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void listenerConfigurationCannotBeAttachedToAnActionTargetEvenOutsideTheModelEditor() {
    var subscription = subscription(); subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock"); subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    subscription.getTarget().setConfig(Map.of());
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var actions = mock(BusinessActionRuntime.class);
    var runtime = runtime(subscription, users, null, actions);
    assertThrows(BusinessException.class, () -> runtime.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    verify(actions, never()).invoke(any(BusinessActionInvocation.class));
  }

  @Test
  void publicationRejectsMissingListenerWithConsumerAndSubscriptionDiagnostic() {
    var runtime = runtime(subscription(), mock(UserCenter.class), null, mock(BusinessActionRuntime.class));
    var failure = assertThrows(BusinessException.class, () -> runtime.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(
        "inventory.Stock.eventSubscriptions.reserve.listenerName-unavailable"));
  }
  @Test
  void aSelectedIndependentContractCannotBecomeAnAtomicBindingOutsideTheReceiverLock() {
    var independent = mock(BusinessEventListener.class); when(independent.getName()).thenReturn("reserveListener"); when(independent.getDeliveryMode()).thenCallRealMethod();
    when(independent.getDeliveryMode()).thenReturn(BusinessEventDeliveryMode.INDEPENDENT);
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var bindings = new java.util.concurrent.atomic.AtomicReference<>(List.of(independent));
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(independentSubscription()), source()),
        mock(BusinessActionRuntime.class), bindings::get, identities(users), new BusinessEventTestSupport.Transactions());
    var expected = runtime.inspectAttempt("inventory.Stock", "reserve").orElseThrow();
    var atomic = mock(BusinessEventListener.class); when(atomic.getName()).thenReturn("reserveListener"); when(atomic.getDeliveryMode()).thenCallRealMethod();
    when(atomic.getDeliveryMode()).thenReturn(BusinessEventDeliveryMode.ATOMIC);
    bindings.set(List.of(atomic));
    assertEquals("subscription-attempt-contract-changed", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery", 1, expected).diagnosticCode());
    verify(atomic, never()).onEvent(any(), any()); verify(independent, never()).onEvent(any(), any());
  }

  @Test
  void theSelectedListenerIsRecheckedImmediatelyBeforeExposureIfTheBindingSupplierChanges() {
    var independent = mock(BusinessEventListener.class); when(independent.getName()).thenReturn("reserveListener"); when(independent.getDeliveryMode()).thenCallRealMethod();
    when(independent.getDeliveryMode()).thenReturn(BusinessEventDeliveryMode.INDEPENDENT);
    var atomic = mock(BusinessEventListener.class); when(atomic.getName()).thenReturn("reserveListener"); when(atomic.getDeliveryMode()).thenCallRealMethod();
    when(atomic.getDeliveryMode()).thenReturn(BusinessEventDeliveryMode.ATOMIC);
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var lookups = new java.util.concurrent.atomic.AtomicInteger();
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(independentSubscription()), source()),
        mock(BusinessActionRuntime.class), () -> lookups.incrementAndGet() < 3 ? List.of(independent) : List.of(atomic),
        identities(users), new BusinessEventTestSupport.Transactions());
    var expected = runtime.inspectAttempt("inventory.Stock", "reserve").orElseThrow();
    assertEquals("subscription-attempt-contract-changed", runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery", 1, expected).diagnosticCode());
    verify(atomic, never()).onEvent(any(), any()); verify(independent, never()).onEvent(any(), any());
  }

  @Test
  void aListenerCannotReportDeliveredAfterItsTransactionWasInvalidated() {
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var transactions = new BusinessEventTestSupport.Transactions();
    BusinessEventListener listener = new BusinessEventListener() {
      public String getName() { return "reserveListener"; }
      public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
        DataModelExecutionContext.markRollbackOnly();
      }
    };
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()), source()),
        mock(BusinessActionRuntime.class), () -> List.of(listener), identities(users), transactions);
    assertThrows(BusinessException.class, () -> runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery-1", 1));
    assertEquals(1, transactions.rollbacks);
    assertEquals(0, transactions.commits);
  }

  @Test
  void currentAndOriginalObjectReferenceConstraintsBothRequireAuthorization() {
    var plainSchema = Map.<String, Object>of("type", "object", "properties", Map.of("customer", Map.of("type", "object")));
    var referenceSchema = Map.<String, Object>of("type", "object", "properties", Map.of("customer", Map.of(
        "type", "object", "x-business-object-ref", "sales.Customer")));
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    for (boolean newlyDeclared : List.of(true, false)) {
      var currentEvent = event();
      currentEvent.setPayloadSchema(newlyDeclared ? referenceSchema : plainSchema);
      var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()),
          new BusinessModelBehavior(MODEL, List.of(), List.of(currentEvent), List.of(), List.of(), "revision-2")),
          mock(BusinessActionRuntime.class), () -> List.of(listener), identities(users), new BusinessEventTestSupport.Transactions());
      var fact = BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL),
          newlyDeclared ? plainSchema : referenceSchema, Map.of("customer", Map.of("id", "customer-1")),
          Instant.now(), "producer", "actor", null, null, null);
      assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
          runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1).status());
    }
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void unsupportedReferencePlacementCannotReachTheListener() {
    var schemas = List.of(
        Map.<String, Object>of("allOf", List.of(Map.of("type", "object", "properties", Map.of("customer",
            Map.of("type", "object", "x-business-object-ref", "sales.Customer"))))),
        Map.<String, Object>of("$ref", "#/properties/customer", "properties", Map.of("customer",
            Map.of("type", "object", "x-business-object-ref", "sales.Customer"))));
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    for (var schema : schemas) {
      var currentEvent = event();
      currentEvent.setPayloadSchema(schema);
      var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()),
          new BusinessModelBehavior(MODEL, List.of(), List.of(currentEvent), List.of(), List.of(), "revision-1")),
          mock(BusinessActionRuntime.class), () -> List.of(listener), identities(users), new BusinessEventTestSupport.Transactions());
      var fact = BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL),
          schema, Map.of("id", "customer-1", "customer", Map.of("id", "customer-1")),
          Instant.now(), "producer", "actor", null, null, null);
      assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
          runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1).status());
    }
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void directAsyncListenerCannotReturnAConfirmedDeliveryBeforeRunning() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var queued = new java.util.ArrayList<Runnable>();
    var proxy = new ProxyFactory(listener);
    var async = new AsyncExecutionInterceptor(queued::add);
    proxy.addAdvisor(new org.springframework.aop.support.DefaultPointcutAdvisor(
        new org.springframework.aop.support.StaticMethodMatcherPointcut() {
          public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
            return method.getName().equals("onEvent");
          }
        }, async));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription(), users, (BusinessEventListener) proxy.getProxy(), mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    assertEquals(0, queued.size());
    verify(listener, never()).onEvent(any(), any());
    var outer = new ProxyFactory(proxy.getProxy());
    outer.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
    var nestedRuntime = runtime(subscription(), users, (BusinessEventListener) outer.getProxy(), mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        nestedRuntime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-2", 1).status());
    assertEquals(0, queued.size());
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void synchronousListenerCanShareABeanWithAnUnrelatedAsyncMethod() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var queued = new java.util.ArrayList<Runnable>();
    var proxy = new ProxyFactory(listener);
    proxy.addAdvisor(new org.springframework.aop.support.DefaultPointcutAdvisor(
        new org.springframework.aop.support.StaticMethodMatcherPointcut() {
          public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
            return method.getName().equals("otherMethod");
          }
        }, new AsyncExecutionInterceptor(queued::add)));
    var outer = new ProxyFactory(proxy.getProxy());
    outer.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var fact = occurrence(BusinessEventSubject.model(MODEL));
    var runtime = runtime(subscription(), users, (BusinessEventListener) outer.getProxy(), mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1).status());
    assertEquals(0, queued.size());
    verify(listener).onEvent(fact,
        new BusinessEventDeliveryContext("delivery-1", "inventory.Stock", "reserve", "event-service", 1, "correlation-1", "event-1"));
  }

  @Test
  void cglibImplementationMethodAsyncAdviceCannotConfirmDirectOrNestedDelivery() {
    for (boolean nested : List.of(false, true)) {
      var listener = new CountingListener();
      var queued = new java.util.ArrayList<Runnable>();
      var proxy = new ProxyFactory(listener);
      proxy.setProxyTargetClass(true);
      proxy.addAdvisor(new org.springframework.aop.support.DefaultPointcutAdvisor(
          new org.springframework.aop.support.StaticMethodMatcherPointcut() {
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
              return method.getName().equals("onEvent") && method.getDeclaringClass() == CountingListener.class;
            }
          }, new AsyncExecutionInterceptor(queued::add)));
      BusinessEventListener registered = (BusinessEventListener) proxy.getProxy();
      if (nested) {
        var outer = new ProxyFactory(registered);
        outer.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
        registered = (BusinessEventListener) outer.getProxy();
      }
      var users = mock(UserCenter.class);
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
      var runtime = runtime(subscription(), users, registered, mock(BusinessActionRuntime.class));
      assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
          runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
      assertEquals(0, queued.size());
      assertEquals(0, listener.calls);
    }
  }

  @Test
  void synchronousDeliveryUsesTheProxyInvocationMethodToMatchAdvice() {
    for (boolean classProxy : List.of(false, true)) {
      var listener = new CountingListener();
      var queued = new java.util.ArrayList<Runnable>();
      var proxy = new ProxyFactory(listener);
      proxy.setProxyTargetClass(classProxy);
      proxy.addAdvisor(new org.springframework.aop.support.DefaultPointcutAdvisor(
          new org.springframework.aop.support.StaticMethodMatcherPointcut() {
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
              return method.getName().equals("onEvent")
                  && method.getDeclaringClass() == (classProxy ? BusinessEventListener.class : CountingListener.class);
            }
          }, new AsyncExecutionInterceptor(queued::add)));
      var users = mock(UserCenter.class);
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
      var runtime = runtime(subscription(), users, (BusinessEventListener) proxy.getProxy(), mock(BusinessActionRuntime.class));
      assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
          runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
      assertEquals(0, queued.size());
      assertEquals(1, listener.calls);
    }
  }

  @Test
  void opaqueSpringProxyCannotPromiseSynchronousCompletion() {
    for (boolean classProxy : List.of(false, true)) {
      for (boolean nested : List.of(false, true)) {
        var listener = new CountingListener();
        var queued = new java.util.ArrayList<Runnable>();
        var proxy = new ProxyFactory(listener);
        proxy.setProxyTargetClass(classProxy);
        proxy.setOpaque(true);
        proxy.addAdvisor(new org.springframework.aop.support.DefaultPointcutAdvisor(
            new org.springframework.aop.support.StaticMethodMatcherPointcut() {
              public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return method.getName().equals("onEvent");
              }
            }, new AsyncExecutionInterceptor(queued::add)));
        BusinessEventListener registered = (BusinessEventListener) proxy.getProxy();
        if (nested) {
          var outer = new ProxyFactory(registered);
          outer.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
          registered = (BusinessEventListener) outer.getProxy();
        }
        var users = mock(UserCenter.class);
        when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
            BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
        var runtime = runtime(subscription(), users, registered, mock(BusinessActionRuntime.class));
        assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
            runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
        assertEquals(0, queued.size());
        assertEquals(0, listener.calls);
      }
    }
  }

  @Test
  void eventAndModelRowFiltersCannotReplaceTheSubjectIdOrEachOther() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    var runtime = runtime(subscription(), users, listener, mock(BusinessActionRuntime.class));
    var model = objectModel(List.of(Map.of("id", "order-1"), Map.of("id", "order-2")));
    try (var models = mockStatic(DataModelCenter.class)) {
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(model));
      for (var filters : List.of(List.of("order-1", "order-2"), List.of("order-2", "order-1"),
          List.of("order-2", "order-2"))) {
        when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
            BusinessEventAuthorities.read(IDENTITY), rowParams(filters.get(0)),
            MODEL + ".search", rowParams(filters.get(1))))));
        assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
            runtime.deliver(occurrence(BusinessEventSubject.instance(MODEL, "order-1")),
                "inventory.Stock", "reserve", "delivery-1", 1).status());
      }
      verify(listener, never()).onEvent(any(), any());
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), rowParams("order-1"), MODEL + ".search", rowParams("order-1")))));
      assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
          runtime.deliver(occurrence(BusinessEventSubject.instance(MODEL, "order-1")),
              "inventory.Stock", "reserve", "delivery-1", 2).status());
    }
  }

  @Test
  void missingOrRevokedConsumerAuthorityNeverExposesThePayloadAndCanBeExplicitlyRedriven() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of())));
    var runtime = runtime(subscription(), users, listener, mock(BusinessActionRuntime.class));
    var occurrence = occurrence(BusinessEventSubject.model(MODEL));
    var result = runtime.deliver(occurrence, "inventory.Stock", "reserve", "delivery-1", 1);
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED, result.status());
    verify(listener, never()).onEvent(any(), any());

    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(occurrence, "inventory.Stock", "reserve", "delivery-1", 2).status());
    verify(listener).onEvent(occurrence,
        new BusinessEventDeliveryContext("delivery-1", "inventory.Stock", "reserve", "event-service", 2, "correlation-1", "event-1"));
    assertEquals(Map.of("orderId", "order-1"), occurrence.payloadFields());
  }

  @Test
  void restrictedNestedPayloadOrObjectCannotBeDeliveredPartially() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var denied = mock(AccessControlParams.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    when(denied.getAllowedFields()).thenReturn(List.of("*"));
    when(denied.getDisallowedFields()).thenReturn(List.of("orderId"));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(BusinessEventAuthorities.read(IDENTITY), denied))));
    var runtime = runtime(subscription(), users, listener, mock(BusinessActionRuntime.class));
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), MODEL + ".search", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var model = objectModel(List.of());
    try (var models = mockStatic(DataModelCenter.class)) {
      models.when(() -> DataModelCenter.getDataModel(MODEL)).thenReturn(Optional.of(model));
      assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
          runtime.deliver(occurrence(BusinessEventSubject.instance(MODEL, "order-1")),
              "inventory.Stock", "reserve", "delivery-1", 2).status());
    }
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void declaredPayloadObjectReferenceRequiresItsOwnReadAuthority() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("customer", Map.of(
        "type", "object", "x-business-object-ref", "sales.Customer", "properties", Map.of("id", Map.of("type", "string")))));
    var sourceEvent = event();
    sourceEvent.setPayloadSchema(schema);
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()),
        new BusinessModelBehavior(MODEL, List.of(), List.of(sourceEvent), List.of(), List.of(), "revision-1")),
        mock(BusinessActionRuntime.class), () -> List.of(listener), identities(users), new BusinessEventTestSupport.Transactions());
    var fact = BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL),
        schema, Map.of("customer", Map.of("id", "customer-1")), Instant.now(), "producer", "actor", null, null, null);
    assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
        runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1).status());
    verify(listener, never()).onEvent(any(), any());

    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "sales.Customer.search", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var customer = objectModel(List.of(Map.of("id", "customer-1"), Map.of("id", "customer-2")));
    try (var models = mockStatic(DataModelCenter.class)) {
      models.when(() -> DataModelCenter.getDataModel("sales.Customer")).thenReturn(Optional.of(customer));
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "sales.Customer.search", rowParams("customer-2")))));
      assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
          runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 2).status());
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "sales.Customer.search", rowParams("customer-1")))));
      assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
          runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 3).status());
    }
  }

  @Test
  void listenerRunsAsTheCurrentConsumerAndFailureRollsBackItsTransaction() {
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    AtomicReference<String> principal = new AtomicReference<>();
    var transactions = new BusinessEventTestSupport.Transactions();
    BusinessEventListener failing = new BusinessEventListener() {
      public String getName() {
        return "reserveListener";
      }

      public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
        principal.set(CurrentSecurityContextHolder.getRequiredIdentity().getPrincipal());
        throw new IllegalStateException("reservation failed");
      }
    };
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()), source()),
        mock(BusinessActionRuntime.class), () -> List.of(failing), identities(users), transactions);
    assertThrows(IllegalStateException.class, () -> runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)),
        "inventory.Stock", "reserve", "delivery-1", 1));
    assertEquals("event-service", principal.get());
    assertEquals(1, transactions.rollbacks);
    assertEquals(Optional.empty(), CurrentSecurityContextHolder.findIdentity());
  }

  @Test
  void actionTargetReceivesMappedInputThroughTheUnifiedActionRuntime() {
    var subscription = subscription();
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock");
    subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    subscription.getTarget().setInputMapping(Map.of("data.order", "${ event.payload.orderId }", "data.causedBy", "${ event.eventId }"));
    var actions = mock(BusinessActionRuntime.class);
    when(actions.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.EXECUTABLE, List.of()));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(),
        "inventory.Stock.reserveStock", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription, users, null, actions);
    assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "delivery-1", 1).status());
    verify(actions).invoke(new BusinessActionInvocation("inventory.Stock", "reserveStock", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("order", "order-1", "causedBy", "event-1"))));
  }

  @Test
  void fileActionIsBlockedBeforeAvailabilityOrExecutionWhenAPublishedContractChanges() {
    var subscription = subscription(); subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock"); subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    var action = new ActionDefinition(); action.setName("reserveStock"); action.setIsEnabled(true);
    action.setOutputSchema(Map.of("type", "string", "format", "binary"));
    var consumer = new BusinessModelBehavior("inventory.Stock", List.of(action), List.of(), List.of(subscription));
    var actions = mock(BusinessActionRuntime.class);
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(),
        "inventory.Stock.reserveStock", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer, source()), actions,
        List::of, identities(users), new BusinessEventTestSupport.Transactions());
    var result = runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "file-delivery", 1);
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED, result.status());
    assertEquals("target-action-file-output-not-consumable", result.diagnosticCode());
    verify(actions, never()).checkAvailability(any()); verify(actions, never()).invoke(any());
  }

  @Test
  void incompatibleActionInputAndUnavailableBindingPauseAsContractFailures() {
    var subscription = subscription();
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock");
    subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    var actions = mock(BusinessActionRuntime.class);
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(),
        "inventory.Stock.reserveStock", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription, users, null, actions);
    when(actions.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.NEEDS_INPUT, List.of("invalid input")));
    var inputBlocked = runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "d1", 1);
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED, inputBlocked.status());
    assertEquals("target-action-input-incompatible", inputBlocked.diagnosticCode());
    when(actions.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.BLOCKED, List.of("unavailable binding"), BusinessActionAvailability.Blockage.CONTRACT));
    var bindingBlocked = runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "d1", 2);
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED, bindingBlocked.status());
    assertEquals("target-action-contract-unavailable", bindingBlocked.diagnosticCode());
    verify(actions, never()).invoke(any());
  }

  @Test
  void unsatisfiedTargetPreconditionHasItsOwnBusinessBlockage() {
    var subscription = subscription();
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(null);
    subscription.getTarget().setModelFullName("inventory.Stock");
    subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserveStock");
    var actions = mock(BusinessActionRuntime.class);
    when(actions.checkAvailability(any())).thenReturn(new BusinessActionAvailability(
        BusinessActionAvailability.Status.BLOCKED, List.of("需要补货"), BusinessActionAvailability.Blockage.PRECONDITION));
    var users = mock(UserCenter.class);
    when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
        BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), "inventory.Stock.reserveStock", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
    var runtime = runtime(subscription, users, null, actions);
    assertEquals(BusinessEventDeliveryResult.Status.PRECONDITION_BLOCKED,
        runtime.deliver(occurrence(BusinessEventSubject.model(MODEL)), "inventory.Stock", "reserve", "d1", 1).status());
    verify(actions, never()).invoke(any());
  }

  @Test
  void removedCurrentContractPausesDeliveryWithoutRewritingTheOriginalFact() {
    var subscription = subscription();
    subscription.getSourceEvent().setEventName("anotherEvent");
    var listener = mock(BusinessEventListener.class);
    var users = mock(UserCenter.class);
    var runtime = runtime(subscription, users, listener, mock(BusinessActionRuntime.class));
    var fact = occurrence(BusinessEventSubject.model(MODEL));
    assertEquals(BusinessEventDeliveryResult.Status.CONTRACT_BLOCKED,
        runtime.deliver(fact, "inventory.Stock", "reserve", "delivery-1", 1).status());
    assertEquals("event-1", fact.eventId());
    verify(users, never()).getUser(any());
    verify(listener, never()).onEvent(any(), any());
  }

  @Test
  void defaultModelFactNeedsCurrentUnfilteredIdReadAndHistoricalRestrictionCannotBeRelaxed() {
    var listener = mock(BusinessEventListener.class);
    when(listener.getName()).thenReturn("reserveListener"); when(listener.getDeliveryMode()).thenCallRealMethod();
    var users = mock(UserCenter.class);
    var fact = occurrence(BusinessEventSubject.model(MODEL));
    for (boolean historical : List.of(false, true)) {
      var definition = event();
      definition.setRequireUnfilteredModelRead(!historical);
      var runtime = new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription()),
          new BusinessModelBehavior(MODEL, List.of(), List.of(definition), List.of())), mock(BusinessActionRuntime.class),
          () -> List.of(listener), identities(users), new BusinessEventTestSupport.Transactions());
      var protectedFact = fact.withRequireUnfilteredModelRead(historical);
      var hiddenId = mock(AccessControlParams.class, org.mockito.Mockito.CALLS_REAL_METHODS);
      when(hiddenId.getAllowedFields()).thenReturn(List.of("*"));
      when(hiddenId.getDisallowedFields()).thenReturn(List.of("id"));
      for (var grants : List.of(
          Map.of(BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()),
          Map.of(BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), MODEL + ".search", rowParams("order-1")),
          Map.of(BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), MODEL + ".search", hiddenId))) {
        when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(grants)));
        assertEquals(BusinessEventDeliveryResult.Status.AUTHORIZATION_BLOCKED,
            runtime.deliver(protectedFact, "inventory.Stock", "reserve", "delivery-1", 1).status());
      }
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(Map.of(
          BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), MODEL + ".search", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()))));
      // Model-scoped deletion facts require no read of a row which no longer exists.
      assertEquals(BusinessEventDeliveryResult.Status.DELIVERED,
          runtime.deliver(protectedFact, "inventory.Stock", "reserve", "delivery-1", 2).status());
    }
    verify(listener, times(2)).onEvent(any(), any());
  }

  private DefaultBusinessEventSubscriptionRuntime runtime(EventSubscriptionDefinition subscription,
      UserCenter users, BusinessEventListener listener, BusinessActionRuntime actions) {
    if (listener != null && listener.getDeliveryMode() == BusinessEventDeliveryMode.INDEPENDENT) {
      subscription.setExecutionMode(BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
    }
    return new DefaultBusinessEventSubscriptionRuntime(provider(consumer(subscription), source()), actions,
        () -> listener == null ? List.of() : List.of(listener), identities(users), new BusinessEventTestSupport.Transactions());
  }

  private BusinessModelBehavior source() {
    return new BusinessModelBehavior(MODEL, List.of(), List.of(event()), List.of(), List.of(), "revision-1");
  }

  private BusinessModelBehavior consumer(EventSubscriptionDefinition subscription) {
    var action = new ActionDefinition();
    action.setName("reserveStock");
    action.setIsEnabled(true);

    return new BusinessModelBehavior("inventory.Stock", List.of(action), List.of(), List.of(subscription), List.of(), "revision-2");
  }

  private EventSubscriptionDefinition independentSubscription() {
    var subscription = subscription();
    subscription.setExecutionMode(BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
    return subscription;
  }

  private EventSubscriptionDefinition subscription() {
    var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
    subscription.setName("reserve");
    subscription.getSourceEvent().setModelFullName(MODEL);
    subscription.getSourceEvent().setEventName("submitted");
    subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("reserveListener");
    return subscription;
  }

  private BusinessEventOccurrence<Map<String, Object>> occurrence(BusinessEventSubject subject) {
    return BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", subject, SCHEMA,
        Map.of("orderId", "order-1"), Instant.now(), "producer", "original-actor", "correlation-1", null, null);
  }

  private AccessControlParams rowParams(String id) {
    var params = mock(AccessControlParams.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    when(params.getAllowedFields()).thenReturn(List.of("*"));
    when(params.getFilters()).thenReturn(Map.of("id", id));
    return params;
  }

  private DataModel objectModel(List<Map<String, Object>> records) {
    var model = mock(DataModel.class);
    var idMeta = new DataModelFieldMeta();
    idMeta.setName("id");
    idMeta.setType("String");
    var id = new DefaultDataModelField(model, idMeta);
    when(model.getPrimaryKeys()).thenReturn(List.of(id));
    when(model.getFields()).thenReturn(List.of(id));
    when(model.query()).thenCallRealMethod();
    // Simulate the data boundary, evaluating every condition in the actual QueryDSL request.
    when(model.query(anyMap())).thenAnswer(invocation -> {
      Map<String, Object> statement = invocation.getArgument(0);
      return Try.success(RecordList.of(records.stream().filter(record -> matches(statement.get("WHERE"), record)).toList()));
    });
    when(model.get(any())).thenAnswer(invocation -> Try.success(records.stream()
        .filter(record -> java.util.Objects.equals(record.get("id"), invocation.getArgument(0)))
        .findFirst().map(Record::of).orElse(null)));
    return model;
  }

  private boolean matches(Object condition, Map<String, Object> record) {
    if (condition instanceof Map<?, ?> map) {
      return map.entrySet().stream().allMatch(entry -> "$and".equalsIgnoreCase(String.valueOf(entry.getKey()))
          ? matches(entry.getValue(), record) : java.util.Objects.equals(record.get(entry.getKey()), entry.getValue()));
    }
    if (condition instanceof List<?> conditions) {
      return conditions.stream().allMatch(nested -> matches(nested, record));
    }
    return condition == null;
  }

  public static class CountingListener implements BusinessEventListener {
    private int calls;

    public String getName() {
      return "reserveListener";
    }

    public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
      calls++;
    }
  }
  private static com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver identities(UserCenter users) {
    var settings = new com.ouroboros.businessmodel.event.BusinessEventConsumerProperties();
    settings.setPrincipals(Map.of("inventory.Stock", "stock-service"));
    return new com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver(settings, users);
  }
}
