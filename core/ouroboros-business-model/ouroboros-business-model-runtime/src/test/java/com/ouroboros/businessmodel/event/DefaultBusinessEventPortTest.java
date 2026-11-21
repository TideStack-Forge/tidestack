package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.IDENTITY;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.MODEL;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.event;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.provider;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.withPermissions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.action.BusinessActionHandler;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.DefaultBusinessActionRuntime;
import com.ouroboros.businessmodel.annotation.BusinessEventPayload;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventReference;
import com.ouroboros.exception.BusinessException;

class DefaultBusinessEventPortTest {
  @Test
  void delayedProducerRetainsTheOriginalOccurrenceTimeAndSchemaExpectation() {
    var facts = new ArrayList<BusinessEventOccurrence<?>>();
    var port = port(facts::add);
    var time = java.time.Instant.parse("2026-01-01T02:03:04Z");
    var fingerprint = com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.schemaFingerprint(event().getPayloadSchema());
    var context = BusinessEventEmissionContext.fromProducer("scheduler").withOccurredAt(time).expectingSchema(fingerprint);
    var occurrence = withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> port.inTransaction(() ->
        port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"), context)));
    assertEquals(time, occurrence.occurredAt());
    assertEquals(time, context.occurredAt());
    assertEquals(fingerprint, context.expectedSchemaFingerprint());
    assertEquals(time, context.expectingSchema(fingerprint).withOccurredAt(time).occurredAt());
  }

  @Test
  void nonActionProducerEmitsADeclaredImmutableFactThroughItsAuthority() {
    var facts = new ArrayList<BusinessEventOccurrence<?>>();
    var port = port(facts::add);
    var occurrence = withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> port.inTransaction(() ->
        port.emit(new BusinessEventIdentity("SALES.ORDER", "SUBMITTED"), BusinessEventSubject.model(MODEL),
            new Submitted("order-1"), BusinessEventEmissionContext.fromProducer("scheduler"))));
    assertEquals(1, facts.size());
    assertEquals(IDENTITY, occurrence.identity());
    assertEquals("event-service", occurrence.actorRef());
    assertEquals("scheduler", occurrence.producerRef());
    assertEquals(Map.of("orderId", "order-1"), occurrence.payload());
    assertThrows(UnsupportedOperationException.class, () -> occurrence.payload().put("orderId", "changed"));
  }

  @Test
  void producerCannotBypassIdentitySchemaSubjectOrWritableTransaction() {
    var facts = new ArrayList<BusinessEventOccurrence<?>>();
    var port = port(facts::add);
    assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
        Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")));
    var transaction = new TransactionTemplate(new BusinessEventTestSupport.Transactions());
    withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> transaction.execute(status -> {
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
          Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromAction()));
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
          Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("unknown")));
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
          Map.of("orderId", 42), BusinessEventEmissionContext.fromProducer("scheduler")));
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.instance(MODEL, "order-1"),
          Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")));
      return null;
    }));
    withPermissions(List.of(), () -> transaction.execute(status -> {
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
          Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")));
      return null;
    }));
    transaction.setReadOnly(true);
    withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> transaction.execute(status -> {
      assertThrows(BusinessException.class, () -> port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
          Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")));
      return null;
    }));
    assertTrue(facts.isEmpty());
  }

  @Test
  void actionEmissionRequiresDeclarationAndJoinsTheActionAuditAndRollback() {
    var transactions = new BusinessEventTestSupport.Transactions();
    var committedFacts = new ArrayList<BusinessEventOccurrence<?>>();
    var audits = new ArrayList<com.ouroboros.businessmodel.action.BusinessActionExecutionAuditRecord>();
    BusinessEventOccurrenceRecorder recorder = fact -> TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          public void afterCommit() {
            committedFacts.add(fact);
          }
        });
    var port = port(recorder);
    var action = action();
    var reference = new EventReference();
    reference.setModelFullName(MODEL.toUpperCase());
    reference.setEventName("SUBMITTED");
    action.setEmits(List.of(reference));
    BusinessActionHandler handler = new BusinessActionHandler() {
      public String getName() {
        return "submitHandler";
      }

      public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
        var fact = port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
            BusinessEventEmissionContext.fromAction());
        return Map.of("eventId", fact.eventId());
      }
    };
    var runtime = new DefaultBusinessActionRuntime(provider(new BusinessModelBehavior(MODEL, List.of(action),
        List.of(event()), List.of(), List.of(), "revision-1")), (name, inputs) -> Try.success(null),
        List.of(handler), transactions, audits::add);
    withPermissions(List.of(MODEL + ".submit"), () -> runtime.invoke(new BusinessActionInvocation(MODEL, "submit", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of()))));
    assertEquals(1, committedFacts.size());
    assertEquals(List.of(committedFacts.get(0).eventId()), audits.get(0).eventIds());
    action.setEmits(List.of());
    var undeclared = new DefaultBusinessActionRuntime(provider(new BusinessModelBehavior(MODEL, List.of(action),
        List.of(event()), List.of(), List.of(), "revision-1")), (name, inputs) -> Try.success(null),
        List.of(handler), transactions, audits::add);
    assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".submit"),
        () -> undeclared.invoke(new BusinessActionInvocation(MODEL, "submit", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of())))));
    assertEquals(1, committedFacts.size());
    assertEquals(1, transactions.rollbacks);
  }

  private DefaultBusinessEventPort port(BusinessEventOccurrenceRecorder recorder) {
    return new DefaultBusinessEventPort(provider(new BusinessModelBehavior(MODEL, List.of(), List.of(event()),
        List.of(), List.of(), "revision-1")), () -> List.of((BusinessEventProducer) () -> "scheduler"), recorder,
        new BusinessEventTestSupport.Transactions());
  }

  private ActionDefinition action() {
    ActionDefinition action = new ActionDefinition();
    action.setName("submit");
    action.setIsEnabled(true);

    action.setInputSchema(Map.of("type", "object"));
    action.setOutputSchema(Map.of("type", "object"));
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName("submitHandler");
    action.setBinding(binding);
    return action;
  }

  @BusinessEventPayload(modelFullName = MODEL, eventName = "submitted")
  public record Submitted(String orderId) {
  }
}
