package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.IDENTITY;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.MODEL;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.SCHEMA;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.event;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.provider;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.user;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.withPermissions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.store.BusinessEventDeliveryProperties;
import com.ouroboros.businessmodel.event.store.BusinessEventOutbox;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.UserCenter;

class BusinessEventOutboxIntegrationTest {
  @Test
  void joinedSubscriberFinishesBeforeEmitReturnsAndIsNeverReplayedByOutbox() {
    var fixture = new Fixture("first");
    fixture.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    fixture.withModels(() -> {
      fixture.emit();
      assertEquals(1, fixture.effects().count(Map.of()).get());
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals("JOIN_SOURCE_TRANSACTION", fixture.delivery("first").get("executionMode"));
      assertEquals("event-service", fixture.delivery("first").get("user"));
      fixture.worker().sweep();
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void oneFactKeepsAllThreeSubscriptionModesAtTheirOwnCommitBoundary() {
    var fixture = new Fixture("joined", "transactional", "independent");
    fixture.mode("joined", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    var externalCalls = new java.util.concurrent.atomic.AtomicInteger();
    fixture.listeners.set(List.of(fixture.listener("joined", delivery -> { }),
        fixture.listener("transactional", delivery -> { }),
        fixture.independentListener("independent", delivery -> {
          assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
          assertEquals("event-service", delivery.user());
          externalCalls.incrementAndGet();
        })));
    fixture.withModels(() -> {
      new org.springframework.transaction.support.TransactionTemplate(fixture.manager).executeWithoutResult(status -> {
        fixture.emit();
        assertEquals(1, fixture.effects().count(Map.of()).get());
        assertEquals("SUCCESS", fixture.delivery("joined").get("status"));
        assertEquals("PENDING", fixture.delivery("transactional").get("status"));
        assertEquals("PENDING", fixture.delivery("independent").get("status"));
        assertEquals(0, externalCalls.get());
        assertTrue(fixture.wakeups.isEmpty());
      });
      assertEquals(1, fixture.orders().count(Map.of()).get());
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(List.of(fixture.eventId.get()), fixture.wakeups);
      fixture.worker().sweep();
      assertEquals(2, fixture.effects().count(Map.of()).get());
      assertEquals(1, externalCalls.get());
      for (var subscription : List.of("joined", "transactional", "independent")) {
        assertEquals("SUCCESS", fixture.delivery(subscription).get("status"));
        assertEquals(fixture.eventId.get(), fixture.delivery(subscription).get("eventId"));
        assertEquals("event-service", fixture.delivery(subscription).get("user"));
      }
      fixture.worker().sweep();
      assertEquals(2, fixture.effects().count(Map.of()).get());
      assertEquals(1, externalCalls.get());
      return null;
    });
  }

  @Test
  void failureAfterEmitRollsBackJoinedEffectsAndBothAfterCommitResponsibilities() {
    var fixture = new Fixture("joined", "transactional", "independent");
    fixture.mode("joined", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    var joinedCalls = new java.util.concurrent.atomic.AtomicInteger();
    var afterCommitCalls = new java.util.concurrent.atomic.AtomicInteger();
    fixture.listeners.set(List.of(fixture.listener("joined", delivery -> joinedCalls.incrementAndGet()),
        fixture.listener("transactional", delivery -> afterCommitCalls.incrementAndGet()),
        fixture.independentListener("independent", delivery -> afterCommitCalls.incrementAndGet())));
    fixture.withModels(() -> {
      assertThrows(BusinessException.class, () ->
          new org.springframework.transaction.support.TransactionTemplate(fixture.manager).executeWithoutResult(status -> {
            fixture.emit();
            assertEquals(1, fixture.effects().count(Map.of()).get());
            assertEquals(3, fixture.deliveries().count(Map.of()).get());
            throw new BusinessException("source step failed after emit");
          }));
      assertEquals(1, joinedCalls.get());
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.effects().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      fixture.worker().sweep();
      assertEquals(1, joinedCalls.get());
      assertEquals(0, afterCommitCalls.get());
      return null;
    });
  }

  @Test
  void caughtJoinedFailureRollsBackSourceFactAndEveryConsumerEffect() {
    var fixture = new Fixture("first");
    fixture.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> { throw new BusinessException("consumer failure"); })));
    fixture.withModels(() -> {
      assertThrows(org.springframework.transaction.UnexpectedRollbackException.class, () ->
          new org.springframework.transaction.support.TransactionTemplate(fixture.manager).execute(status -> {
            assertThrows(BusinessException.class, fixture::emit);
            return null;
          }));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.effects().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void joinedSubscriberCannotEscapeSourceTransactionWithRequiresNewBusinessWrites() {
    var fixture = new Fixture("first");
    fixture.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> {
      var separate = new org.springframework.transaction.support.TransactionTemplate(fixture.manager);
      separate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      separate.execute(status -> fixture.effects().insert(Map.of("id", "escaped", "state", "invalid")).get());
    })));
    fixture.withModels(() -> {
      assertThrows(RuntimeException.class, fixture::emit);
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.effects().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void changingExecutionModeCannotMoveAnExistingResponsibilityIntoSourceTransaction() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      fixture.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
      fixture.worker().sweep();
      assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
      assertEquals("AFTER_COMMIT_TRANSACTIONAL", fixture.delivery("first").get("executionMode"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void synchronousCascadeReusesSourceTransactionAndExcessDepthRollsEverythingBack() {
    var fixture = new Fixture("first");
    fixture.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var causes = new java.util.ArrayList<String>();
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> {
      causes.add(delivery.causationId());
      if (calls.incrementAndGet() < 3) {
        fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
            BusinessEventEmissionContext.fromProducer("scheduler"));
      }
    })));
    fixture.withModels(() -> {
      fixture.emit();
      assertEquals(3, calls.get());
      assertEquals(3, fixture.effects().count(Map.of()).get());
      assertEquals(3, fixture.facts().count(Map.of()).get());
      var facts = fixture.facts().query().select("occurrenceJson").execute().get().stream()
          .map(row -> com.ouroboros.util.Json.toMap(String.valueOf(row.get("occurrenceJson")))).toList();
      assertEquals(2, facts.stream().filter(fact -> causes.contains(fact.get("causationId"))).count());
      fixture.worker().sweep();
      assertEquals(3, calls.get());
      return null;
    });

    var recursive = new Fixture("first");
    recursive.mode("first", BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
    recursive.listeners.set(List.of(recursive.listener("first", delivery -> recursive.port.emit(IDENTITY,
        BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")))));
    recursive.withModels(() -> {
      assertThrows(BusinessException.class, recursive::emit);
      assertEquals(0, recursive.orders().count(Map.of()).get());
      assertEquals(0, recursive.effects().count(Map.of()).get());
      assertEquals(0, recursive.facts().count(Map.of()).get());
      assertEquals(0, recursive.deliveries().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void disabledSubscriptionsKeepFactsWithoutQueueingOrBackfillingWhenEnabled() {
    var fixture = new Fixture("first", "second");
    fixture.withModels(() -> {
      fixture.enableSubscription("first", false);
      fixture.emit();
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertNull(fixture.delivery("first"));
      assertEquals("PENDING", fixture.delivery("second").get("status"));
      fixture.enableSubscription("first", true);
      fixture.worker().sweep();
      assertNull(fixture.delivery("first"));
      assertEquals("SUCCESS", fixture.delivery("second").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void pendingOrClaimedResponsibilityIsBlockedByDisableAndOnlyExplicitRedriveResumesIt() {
    for (boolean independent : List.of(false, true)) {
      var fixture = new Fixture("first");
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      if (independent) { fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> calls.incrementAndGet()))); }
      fixture.withModels(() -> {
        fixture.emit();
        String id = String.valueOf(fixture.delivery("first").get("id"));
        var claim = fixture.worker().claim(id).orElseThrow();
        fixture.enableSubscription("first", false);
        assertTrue(fixture.runtime.inspectAttempt("inventory.Stock", "first").isEmpty());
        fixture.worker().deliverClaimed(claim);
        assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
        assertEquals(0, calls.get());
        assertEquals(0, fixture.effects().count(Map.of()).get());
        fixture.enableSubscription("first", true);
        fixture.worker().sweep();
        assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
        assertTrue(fixture.worker().redrive(id));
        fixture.worker().sweep();
        assertEquals("SUCCESS", fixture.delivery("first").get("status"));
        assertEquals(id, fixture.delivery("first").get("id"));
        fixture.worker().sweep();
        assertEquals(independent ? 1 : 0, calls.get());
        assertEquals(independent ? 0 : 1, fixture.effects().count(Map.of()).get());
        return null;
      });
    }
  }

  @Test
  void independentEffectsRunWithoutAReceiverTransactionOrLockAndKeepTheCurrentConsumerIdentity() throws Exception {
    var fixture = new Fixture("first");
    var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    var count = new java.util.concurrent.atomic.AtomicInteger();
    fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> {
      assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
      assertTrue(com.ouroboros.data.model.DataModelExecutionContext.isReadOnly());
      assertEquals("event-service", delivery.user());
      assertEquals("event-service", com.ouroboros.security.context.CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      count.incrementAndGet(); entered.countDown(); await(finish);
    })));
    String id = fixture.withModels(() -> { fixture.emit(); return String.valueOf(fixture.delivery("first").get("id")); });
    var claim = fixture.withModels(() -> fixture.worker().claim(id).orElseThrow());
    try (var workers = Executors.newFixedThreadPool(2)) {
      var sending = workers.submit(() -> fixture.withModels(() -> { fixture.worker().deliverClaimed(claim); return null; }));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      try {
        assertTrue(workers.submit(() -> fixture.withModels(() -> fixture.worker().claim(id))).get(2, TimeUnit.SECONDS).isEmpty());
      } finally { finish.countDown(); }
      sending.get(5, TimeUnit.SECONDS);
    }
    fixture.withModels(() -> {
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals("AFTER_COMMIT_INDEPENDENT", fixture.delivery("first").get("executionMode"));
      var diagnostics = withPermissions(List.of(BusinessEventAuthorities.INSPECT_DELIVERIES), () ->
          new com.ouroboros.businessmodel.event.store.BusinessEventOperations(fixture.worker()).inspect(fixture.eventId.get()));
      assertEquals("AFTER_COMMIT_INDEPENDENT", diagnostics.deliveries().getFirst().executionMode());
      assertEquals("event-service", diagnostics.deliveries().getFirst().user());
      fixture.worker().deliver(id); assertEquals(1, count.get()); assertEquals(0, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void anIndependentWorkerThatLosesItsLeaseCannotAcknowledgeTheNewOwner() throws Exception {
    var fixture = new Fixture("first"); var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
    fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> { entered.countDown(); await(finish); })));
    String id = fixture.withModels(() -> { fixture.emit(); return String.valueOf(fixture.delivery("first").get("id")); });
    var firstClaim = fixture.withModels(() -> fixture.worker().claim(id).orElseThrow());
    try (var workers = Executors.newSingleThreadExecutor()) {
      var sending = workers.submit(() -> fixture.withModels(() -> { fixture.worker().deliverClaimed(firstClaim); return null; }));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      fixture.clock.advance(Duration.ofSeconds(61));
      var newClaim = fixture.withModels(() -> fixture.worker().claim(id).orElseThrow());
      finish.countDown(); sending.get(5, TimeUnit.SECONDS);
      fixture.withModels(() -> {
        assertEquals("PROCESSING", fixture.delivery("first").get("status"));
        assertEquals(newClaim.token(), fixture.delivery("first").get("claimToken"));
        assertEquals(2, fixture.delivery("first").get("attempt"));
        fixture.worker().deliverClaimed(newClaim);
        assertEquals("SUCCESS", fixture.delivery("first").get("status"));
        return null;
      });
    } finally { finish.countDown(); }
  }

  @Test
  void anIndependentEffectIsRejectedInsideAnOuterBusinessTransactionAndCannotWriteBusinessData() {
    var fixture = new Fixture("first"); var calls = new java.util.concurrent.atomic.AtomicInteger();
    fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> {
      calls.incrementAndGet();
      assertTrue(fixture.orders().update("order-1", Map.of("state", "unsafe")).isFailure());
    })));
    fixture.withModels(() -> {
      fixture.emit(); String id = String.valueOf(fixture.delivery("first").get("id"));
      var claim = fixture.worker().claim(id).orElseThrow();
      new org.springframework.transaction.support.TransactionTemplate(fixture.manager).execute(status -> {
        fixture.worker().deliverClaimed(claim); return null;
      });
      assertEquals(0, calls.get());
      assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
      assertEquals("independent-listener-requires-transaction-free-entry", fixture.delivery("first").get("lastError"));
      assertTrue(fixture.worker().redrive(id)); fixture.worker().deliver(id);
      assertEquals(1, calls.get()); assertEquals("submitted", fixture.orders().get("order-1").get().get("state"));
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      return null;
    });
  }

  @Test
  void independentAuthorizationFailureStillBlocksBeforeDisclosingTheFact() {
    var fixture = new Fixture("first"); var calls = new java.util.concurrent.atomic.AtomicInteger();
    fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> calls.incrementAndGet())));
    fixture.withModels(() -> {
      fixture.emit(); fixture.authorized.set(false); fixture.worker().sweep();
      assertEquals(0, calls.get()); assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery("first").get("status"));
      fixture.authorized.set(true);
      assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery("first").get("id"))));
      fixture.worker().sweep(); assertEquals(1, calls.get()); assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      return null;
    });
  }

  @Test
  void independentEffectCompletionSurvivesAcknowledgementFailureButOutboxResponsibilityRemains() {
    var fixture = new Fixture("first"); var sends = new java.util.concurrent.atomic.AtomicInteger();
    var completed = new java.util.concurrent.ConcurrentHashMap<String, Boolean>();
    fixture.listeners.set(List.of(fixture.independentListener("first", delivery -> {
      if (completed.putIfAbsent(delivery.deliveryId(), true) == null) { sends.incrementAndGet(); }
    })));
    fixture.withModels(() -> {
      fixture.emit(); fixture.rejectDeliveryStatus("independent_reject_success", "SUCCESS"); fixture.worker().sweep();
      assertEquals("FAILED", fixture.delivery("first").get("status")); assertEquals(1, sends.get());
      fixture.removeDeliveryConstraint("independent_reject_success"); fixture.clock.advance(Duration.ofMinutes(1));
      fixture.worker().sweep(); assertEquals("SUCCESS", fixture.delivery("first").get("status")); assertEquals(1, sends.get());
      return null;
    });
  }

  private static void await(CountDownLatch signal) {
    try { assertTrue(signal.await(10, TimeUnit.SECONDS)); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
  }

  @Test
  void workflowReceiptCursorCallbackAndEventOutboxShareTheReceiverCommit() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> {
      fixture.orders().insert(Map.of("id", "order-1", "state", "draft")).get();
      var store = new WorkflowBusinessFactStore();
      assertThrows(BusinessException.class, () -> store.acquire(MODEL, "order-1", "approval"));
      assertThrows(IllegalStateException.class, () -> fixture.port.inTransaction(() -> {
        receiveWorkflow(fixture, store, workflowFact("source-1", 1), "approved");
        throw new IllegalStateException("receiver failed before commit");
      }));
      assertEquals("draft", fixture.orders().get("order-1").get().get("state"));
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertEquals(0, fixture.models.get("WorkflowBusinessFactCursor").count(Map.of()).get());
      assertEquals(0, fixture.models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());

      String eventId = fixture.port.inTransaction(() -> receiveWorkflow(fixture, store,
          workflowFact("source-1", 1), "approved"));
      assertEquals("approved", fixture.orders().get("order-1").get().get("state"));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      fixture.port.inTransaction(() -> {
        var restarted = new WorkflowBusinessFactStore();
        var cursor = restarted.acquire(MODEL, "order-1", "approval");
        assertEquals(1L, cursor.getStateSequence());
        assertEquals("source-1", cursor.getLastEventFactId());
        assertEquals("instance", cursor.getInstanceId());
        assertEquals("case", cursor.getApprovalCaseId());
        assertEquals(2, cursor.getSubmissionRound());
        var receipt = restarted.findReceipt(cursor, "source-1").orElseThrow();
        assertEquals(eventId, receipt.getBusinessEventId());
        assertEquals("APPLIED", receipt.getDisposition());
        assertEquals(1L, receipt.getStateSequence());
        assertEquals("source-1", receipt.getSourceFactId());
        assertEquals(cursor.getProjectionKey(), receipt.getProjectionKey());
        assertEquals("instance", receipt.getInstanceId());
        assertTrue(receipt.getReceivedAt() != null);
        assertTrue(receipt.getReceiptKey() != null);
        assertEquals("order-1", cursor.getRecordId());
        assertEquals(MODEL, cursor.getModelFullName());
        assertEquals("approval", cursor.getFlowKey());
        return null;
      });
      return null;
    }));
  }

  @Test
  void staleWorkflowFactKeepsItsReceiptAndSequenceConflictRollsBackTheNewEvent() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> {
      fixture.orders().insert(Map.of("id", "order-1", "state", "draft")).get();
      var store = new WorkflowBusinessFactStore();
      fixture.port.inTransaction(() -> receiveWorkflow(fixture, store, workflowFact("source-1", 1), "processing"));
      fixture.port.inTransaction(() -> receiveWorkflow(fixture, store, workflowFact("source-3", 3), "approved"));
      fixture.port.inTransaction(() -> {
        var cursor = store.acquire(MODEL, "order-1", "approval");
        assertThrows(BusinessException.class, () -> store.advance(cursor, workflowFact("source-2", 2)));
        store.record(cursor, "source-2", "instance", 2L, "historical-event", "STALE");
        store.record(cursor, "task-1", "instance", null, "task-event-1", "TASK");
        store.record(cursor, "task-2", "instance", null, "task-event-2", "TASK");
        return null;
      });
      assertThrows(BusinessException.class, () -> fixture.port.inTransaction(() -> {
        var cursor = store.acquire(MODEL, "order-1", "approval");
        var fact = fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
            BusinessEventEmissionContext.fromProducer("scheduler"));
        store.record(cursor, "conflicting-source-2", "instance", 2L, fact.eventId(), "STALE");
        return null;
      }));
      fixture.port.inTransaction(() -> {
        var cursor = store.acquire(MODEL, "order-1", "approval");
        assertEquals(3L, cursor.getStateSequence());
        assertEquals("STALE", store.findReceipt(cursor, "source-2").orElseThrow().getDisposition());
        assertTrue(store.findReceipt(cursor, "conflicting-source-2").isEmpty());
        return null;
      });
      assertEquals("approved", fixture.orders().get("order-1").get().get("state"));
      assertEquals(2, fixture.facts().count(Map.of()).get());
      assertEquals(2, fixture.deliveries().count(Map.of()).get());
      assertEquals(5, fixture.models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
      return null;
    }));
  }

  @Test
  void concurrentWorkflowRetriesObserveTheSameCommittedReceiptBeforeRunningTheCallback() throws Exception {
    var fixture = new Fixture("first");
    var store = new WorkflowBusinessFactStore();
    fixture.withModels(() -> fixture.port.inTransaction(() -> {
      fixture.orders().insert(Map.of("id", "order-1", "state", "draft")).get();
      store.acquire(MODEL, "order-1", "approval");
      return null;
    }));
    var start = new CountDownLatch(1);
    var executed = new java.util.concurrent.atomic.AtomicInteger();
    try (var workers = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<String> receive = () -> {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return fixture.withModels(() -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () ->
            fixture.port.inTransaction(() -> {
              var cursor = store.acquire(MODEL, "order-1", "approval");
              var receipt = store.findReceipt(cursor, "source-concurrent");
              if (receipt.isPresent()) { return receipt.get().getBusinessEventId(); }
              executed.incrementAndGet();
              return receiveWorkflow(fixture, store, workflowFact("source-concurrent", 1), "approved");
            })));
      };
      var first = workers.submit(receive);
      var second = workers.submit(receive);
      start.countDown();
      assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }
    assertEquals(1, executed.get());
    fixture.withModels(() -> {
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      assertEquals(1, fixture.models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
      return null;
    });
  }

  private static String receiveWorkflow(Fixture fixture, WorkflowBusinessFactStore store, FlowInstanceEvent event, String state) {
    var cursor = store.acquire(MODEL, "order-1", "approval");
    fixture.orders().update("order-1", Map.of("state", state)).get();
    var fact = fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
        BusinessEventEmissionContext.fromProducer("scheduler"));
    store.advance(cursor, event);
    store.record(cursor, event.getEventFactId(), event.getInstanceId(), event.getStateSequence(), fact.eventId(), "APPLIED");
    return fact.eventId();
  }

  private static FlowInstanceEvent workflowFact(String id, long sequence) {
    var event = new FlowInstanceEvent();
    event.setEventFactId(id);
    event.setInstanceId("instance");
    event.setFlowKey("approval");
    event.setApprovalCaseId("case");
    event.setSubmissionRound(2);
    event.setStateSequence(sequence);
    return event;
  }

  @Test
  void legacyOccurrenceJsonNeedsNoDdlAndRetainsItsOriginalPendingDelivery() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var fact = fixture.emit();
      String deliveryId = String.valueOf(fixture.delivery("first").get("id"));
      var stored = fixture.facts().get(fact.eventId()).get();
      var legacy = new java.util.LinkedHashMap<>(com.ouroboros.util.Json.toMap(String.valueOf(stored.get("occurrenceJson"))));
      assertTrue(legacy.containsKey("requireUnfilteredModelRead"));
      legacy.remove("requireUnfilteredModelRead");
      String legacyJson = com.ouroboros.util.Json.toJsonString(legacy);
      fixture.facts().update(fact.eventId(), Map.of("occurrenceJson", legacyJson)).get();

      var upgradeSql = fixture.station.generateMigrationSql(fixture.station.getDataModelList()).get();
      assertTrue(upgradeSql.isEmpty(), upgradeSql.toString());
      assertEquals("PENDING", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(deliveryId, fixture.delivery("first").get("id"));
      assertEquals(legacyJson, fixture.facts().get(fact.eventId()).get().get("occurrenceJson"));
      fixture.worker().sweep();
      assertEquals(1, fixture.effects().count(Map.of()).get());
      assertEquals(1, fixture.delivery("first").get("attempt"));
      return null;
    });
  }

  @Test
  void subscriptionFilterCannotExecuteExternalSideEffectsInsideTheSourceTransaction() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var filtered = fixture.subscription("first");
      String property = "ouroboros.item5.filter-test";
      System.clearProperty(property);
      filtered.setFilter("${T(java.lang.System).setProperty('" + property + "','changed') == null}");
      fixture.contracts.getBusinessModelBehaviorMap().put("inventory.Stock", new BusinessModelBehavior(
          "inventory.Stock", List.of(), List.of(), List.of(filtered), List.of(), "revision-1"));
      try {
        assertThrows(BusinessException.class, fixture::emit);
        assertNull(System.getProperty(property));
        assertEquals(0, fixture.orders().count(Map.of()).get());
        assertEquals(0, fixture.facts().count(Map.of()).get());
        assertEquals(0, fixture.deliveries().count(Map.of()).get());
      } finally {
        System.clearProperty(property);
      }
      return null;
    });
  }

  @Test
  void matchingRevisionAndCurrentAttemptAreDurableAndFilterChangesDoNotEraseResponsibility() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      assertEquals("revision-1", fixture.delivery("first").get("matchedRevision"));
      var changed = fixture.subscription("first");
      changed.setFilter(false);
      fixture.contracts.getBusinessModelBehaviorMap().put("inventory.Stock", new BusinessModelBehavior(
          "inventory.Stock", List.of(), List.of(), List.of(changed), List.of(), "revision-2"));
      fixture.worker().sweep();
      var row = fixture.delivery("first");
      assertEquals("SUCCESS", row.get("status"));
      assertEquals("revision-1", row.get("matchedRevision"));
      assertEquals("revision-2", row.get("attemptRevision"));
      assertTrue(String.valueOf(row.get("implementationRef")).startsWith("listener:first:"));
      assertEquals("integration-build-1", row.get("implementationVersion"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void subscriptionFilterIsEvaluatedWhenTheFactIsEnqueued() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var filtered = fixture.subscription("first");
      filtered.setFilter(false);
      fixture.contracts.getBusinessModelBehaviorMap().put("inventory.Stock", new BusinessModelBehavior(
          "inventory.Stock", List.of(), List.of(), List.of(filtered), List.of(), "revision-1"));
      fixture.emit();
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void restrictedEventPredicateMatchesImmutablePayloadWhenEnqueuing() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var filtered = fixture.subscription("first");
      filtered.setFilter("${event.payload.orderId == 'order-1'}");
      fixture.contracts.getBusinessModelBehaviorMap().put("inventory.Stock", new BusinessModelBehavior(
          "inventory.Stock", List.of(), List.of(), List.of(filtered), List.of(), "revision-1"));
      fixture.emit();
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void operationsSeparateInspectAndRedrivePermissionsAndPreserveFacts() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var fact = fixture.emit();
      fixture.authorized.set(false);
      var worker = fixture.worker();
      worker.deliverEvent(fact.eventId());
      var operations = new com.ouroboros.businessmodel.event.store.BusinessEventOperations(worker);
      String id = String.valueOf(fixture.delivery("first").get("id"));
      var before = fixture.facts().get(fact.eventId()).get();
      assertThrows(BusinessException.class, () -> operations.inspect(fact.eventId()));
      assertThrows(BusinessException.class, () -> operations.redrive(id));
      var diagnostics = withPermissions(List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.INSPECT_DELIVERIES),
          () -> operations.inspect(fact.eventId()));
      assertEquals(fact.eventId(), diagnostics.fact().orElseThrow().eventId());
      assertEquals("AUTHORIZATION_BLOCKED", diagnostics.deliveries().get(0).status());
      assertEquals("event-disclosure-denied", diagnostics.deliveries().get(0).diagnosticCode());
      assertFalse(diagnostics.canRedrive());
      assertTrue(withPermissions(List.of(BusinessEventAuthorities.INSPECT_DELIVERIES, BusinessEventAuthorities.REDRIVE_DELIVERIES),
          () -> operations.inspect(fact.eventId())).canRedrive());
      assertEquals("revision-1", diagnostics.deliveries().get(0).matchedRevision());
      assertEquals("revision-1", diagnostics.deliveries().get(0).attemptRevision());
      assertThrows(BusinessException.class, () -> withPermissions(
          List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.INSPECT_DELIVERIES), () -> operations.redrive(id)));
      assertTrue(withPermissions(List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES),
          () -> operations.redrive(id)));
      assertEquals(new java.util.LinkedHashMap<>(before), new java.util.LinkedHashMap<>(fixture.facts().get(fact.eventId()).get()));
      worker.deliverEvent(fact.eventId());
      assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery("first").get("status"));
      fixture.authorized.set(true);
      assertTrue(withPermissions(List.of(com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES),
          () -> operations.redrive(id)));
      worker.deliverEvent(fact.eventId());
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().query().execute().get().size());
      return null;
    });
  }
  @Test
  void aCaughtEmissionFailureStillRollsBackAllSourceWritesAndFacts() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.rejectDeliveryStatus("reject_pending", "PENDING");
      assertThrows(BusinessException.class, () -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> fixture.port.inTransaction(() -> {
        fixture.orders().insert(Map.of("id", "order-1", "state", "submitted")).get();
        assertThrows(BusinessException.class, () -> fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
            Map.of("orderId", "order-1"), BusinessEventEmissionContext.fromProducer("scheduler")));
        return null;
      })));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      return null;
    });
  }

  @Test
  void aCaughtDirectRecorderFailureAlsoInvalidatesTheOwningSourceTransaction() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var fact = BusinessEventOccurrence.create("direct-fact", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL),
          SCHEMA, Map.of("orderId", "order-1"), Instant.now(), "scheduler", "actor", null, null, null);
      fixture.rejectDeliveryStatus("reject_pending", "PENDING");
      assertThrows(BusinessException.class, () -> fixture.port.inTransaction(() -> {
        fixture.orders().insert(Map.of("id", "order-1", "state", "submitted")).get();
        assertThrows(BusinessException.class, () -> fixture.recorder.record(fact));
        return null;
      }));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      return null;
    });
  }

  @Test
  void aCaughtPayloadValidationFailureStillRollsBackTheSourceBusinessWrite() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      assertThrows(BusinessException.class, () -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> fixture.port.inTransaction(() -> {
        fixture.orders().insert(Map.of("id", "order-1", "state", "submitted")).get();
        assertThrows(BusinessException.class, () -> fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL),
            Map.of("orderId", 42), BusinessEventEmissionContext.fromProducer("scheduler")));
        return null;
      })));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      return null;
    });
  }

  @Test
  void missingCurrentBindingPausesUntilExplicitRedriveInsteadOfRestoringTheOldListener() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      var original = Map.copyOf(fixture.facts().get(fixture.eventId.get()).get());
      var registered = fixture.listeners.get();
      fixture.listeners.set(List.of());
      fixture.worker().sweep();
      assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
      assertEquals("listener-unavailable", fixture.delivery("first").get("lastError"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      fixture.listeners.set(registered);
      fixture.worker().sweep();
      assertEquals("CONTRACT_BLOCKED", fixture.delivery("first").get("status"));
      assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery("first").get("id"))));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(original, Map.copyOf(fixture.facts().get(fixture.eventId.get()).get()));
      return null;
    });
  }

  @Test
  void aFailedCommitWakeupIsRecoveredByANewWorkerWithoutChangingTheFact() {
    var fixture = new Fixture("first");
    fixture.wakeupFails.set(true);
    fixture.withModels(() -> {
      fixture.emit();
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals("PENDING", fixture.delivery("first").get("status"));
      assertTrue(fixture.wakeups.isEmpty());
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void retryExhaustionPreservesTheFactAndExplicitRedriveResetsOnlyDeliveryFailure() {
    var fixture = new Fixture("first");
    fixture.properties.setMaxRetryCount(0);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> {
      throw new IllegalStateException("secret payload must not be persisted");
    })));
    fixture.withModels(() -> {
      fixture.emit();
      fixture.worker().sweep();
      assertEquals("DEAD", fixture.delivery("first").get("status"));
      assertEquals(IllegalStateException.class.getName(), fixture.delivery("first").get("lastError"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      fixture.listeners.set(List.of(fixture.listener("first", delivery -> { })));
      fixture.clock.advance(Duration.ofDays(1));
      fixture.worker().sweep();
      assertEquals("DEAD", fixture.delivery("first").get("status"));
      assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery("first").get("id"))));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(2, fixture.delivery("first").get("attempt"));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void recordingAnIdenticalFactIsIdempotentAndAConflictingIdentityIsRejected() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      var fact = fixture.emit();
      fixture.port.inTransaction(() -> {
        fixture.recorder.record(fact);
        return null;
      });
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      assertEquals(1, fixture.wakeups.size());
      var conflict = BusinessEventOccurrence.create(fact.eventId(), fact.identity(), fact.modelRevision(),
          fact.subject(), fact.payloadSchema(), Map.of("orderId", "another"), fact.occurredAt(), fact.producerRef(),
          fact.actorRef(), fact.correlationId(), fact.causationId(), fact.executionId());
      assertThrows(BusinessException.class, () -> fixture.port.inTransaction(() -> {
        fixture.recorder.record(conflict);
        return null;
      }));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void sourceRollbackOrCommitIncludesBusinessStateFactAndEverySubscription() {
    var fixture = new Fixture("first", "second");
    fixture.withModels(() -> {
      assertThrows(IllegalStateException.class, () -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)),
          () -> fixture.port.inTransaction(() -> {
            fixture.orders().insert(Map.of("id", "order-1", "state", "submitted")).get();
            fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
                BusinessEventEmissionContext.fromProducer("scheduler"));
            throw new IllegalStateException("source failed");
          })));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      fixture.emit();
      assertEquals(1, fixture.orders().count(Map.of()).get());
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(2, fixture.deliveries().count(Map.of()).get());
      assertEquals(1, fixture.wakeups.size());
      return null;
    });
  }

  @Test
  void aFailedConsumerRollsBackOnlyItsEffectsAndDoesNotReplayTheSuccessfulConsumer() {
    var fixture = new Fixture("first", "second");
    var fail = new AtomicBoolean(true);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> { }), fixture.listener("second", delivery -> {
      if (fail.get()) {
        throw new IllegalStateException("consumer failed");
      }
    })));
    fixture.withModels(() -> {
      fixture.emit();
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals("FAILED", fixture.delivery("second").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      fail.set(false);
      fixture.clock.advance(Duration.ofMinutes(1));
      // A new worker recovers durable pending work without any source callback or in-memory queue.
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("second").get("status"));
      assertEquals(1, fixture.delivery("first").get("attempt"));
      assertEquals(2, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void anExpiredClaimCanBeRecoveredAndItsOldTokenCannotProduceEffectsOrAcknowledge() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      String id = String.valueOf(fixture.delivery("first").get("id"));
      var original = fixture.worker().claim(id).orElseThrow();
      assertTrue(fixture.worker().claim(id).isEmpty());
      fixture.clock.advance(Duration.ofSeconds(61));
      var replacement = fixture.worker().claim(id).orElseThrow();
      fixture.worker().deliverClaimed(original);
      assertEquals(0, fixture.effects().count(Map.of()).get());
      fixture.worker().deliverClaimed(replacement);
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(2, fixture.delivery("first").get("attempt"));
      fixture.worker().deliver(id);
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void revokedAuthorizationPausesUntilExplicitRedriveAndKeepsTheOriginalFactAndIdentity() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      var original = Map.copyOf(fixture.facts().get(fixture.eventId.get()).get());
      fixture.authorized.set(false);
      fixture.worker().sweep();
      assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery("first").get("status"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      fixture.authorized.set(true);
      fixture.worker().sweep();
      assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery("first").get("status"));
      String id = String.valueOf(fixture.delivery("first").get("id"));
      assertTrue(fixture.worker().redrive(id));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(id, fixture.delivery("first").get("id"));
      assertEquals(original, Map.copyOf(fixture.facts().get(fixture.eventId.get()).get()));
      assertFalse(fixture.worker().redrive(id));
      return null;
    });
  }

  @Test
  void aPreconditionRaceRollsBackConsumerEffectsThenPausesWithoutSpendingRetryBudget() {
    var fixture = new Fixture("first");
    var unsatisfied = new AtomicBoolean(true);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> {
      if (unsatisfied.get()) {
        throw new com.ouroboros.businessmodel.action.BusinessActionPreconditionException("业务条件发生变化");
      }
    })));
    fixture.withModels(() -> {
      fixture.emit();
      String id = String.valueOf(fixture.delivery("first").get("id"));
      fixture.worker().sweep();
      assertEquals("PRECONDITION_BLOCKED", fixture.delivery("first").get("status"));
      assertEquals("target-action-precondition-unsatisfied", fixture.delivery("first").get("lastError"));
      assertEquals(0, fixture.delivery("first").get("retryCount"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      assertEquals(1, fixture.facts().count(Map.of()).get());
      unsatisfied.set(false);
      fixture.worker().sweep();
      assertEquals("PRECONDITION_BLOCKED", fixture.delivery("first").get("status"));
      assertTrue(fixture.worker().redrive(id));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      assertEquals(2, fixture.delivery("first").get("attempt"));
      return null;
    });
  }

  @Test
  void failureToAcknowledgeRollsBackLocalEffectsBeforeRetrying() {
    var fixture = new Fixture("first");
    fixture.withModels(() -> {
      fixture.emit();
      fixture.rejectDeliveryStatus("reject_success", "SUCCESS");
      fixture.worker().sweep();
      assertEquals("FAILED", fixture.delivery("first").get("status"));
      assertEquals("revision-1", fixture.delivery("first").get("attemptRevision"));
      assertEquals(0, fixture.effects().count(Map.of()).get());
      fixture.removeDeliveryConstraint("reject_success");
      fixture.clock.advance(Duration.ofMinutes(1));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void aSecondWorkerCannotConsumeAnExpiredLeaseWhileTheFirstConsumerHoldsTheCommitLock() throws Exception {
    var fixture = new Fixture("first");
    var entered = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    fixture.listeners.set(List.of(fixture.listener("first", delivery -> {
      entered.countDown();
      try {
        if (!finish.await(3, TimeUnit.SECONDS)) {
          throw new IllegalStateException("test listener timed out");
        }
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(error);
      }
    })));
    String id = fixture.withModels(() -> {
      fixture.emit();
      return String.valueOf(fixture.delivery("first").get("id"));
    });
    var claim = fixture.withModels(() -> fixture.worker().claim(id).orElseThrow());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> fixture.withModels(() -> {
        fixture.worker().deliverClaimed(claim);
        return null;
      }));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      fixture.clock.advance(Duration.ofSeconds(61));
      var second = executor.submit(() -> fixture.withModels(() -> fixture.worker().claim(id)));
      try {
        assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
      } finally {
        finish.countDown();
      }
      first.get(2, TimeUnit.SECONDS);
      assertTrue(second.get(2, TimeUnit.SECONDS).isEmpty());
    }
    fixture.withModels(() -> {
      assertEquals("SUCCESS", fixture.delivery("first").get("status"));
      assertEquals(1, fixture.effects().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void outboxOnAnUnenlistedSourceCannotCommitTheBusinessWriteOrFact() {
    var fixture = new Fixture("first");
    var other = new Fixture("first");
    fixture.models.put(BusinessEventOutbox.MODEL_FULL_NAME, other.deliveries());
    fixture.withModels(() -> {
      assertThrows(BusinessException.class, fixture::emit);
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertTrue(fixture.wakeups.isEmpty());
      return null;
    });
  }

  private static class Fixture {
    private final DataSourceTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final SqlDataStation station;
    private final boolean mysql;
    private final String tableSuffix;
    private final Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final MutableClock clock = new MutableClock();
    private final AtomicBoolean authorized = new AtomicBoolean(true);
    private final AtomicBoolean wakeupFails = new AtomicBoolean();
    private final AtomicReference<String> eventId = new AtomicReference<>();
    private final AtomicReference<List<BusinessEventListener>> listeners = new AtomicReference<>();
    private final List<String> wakeups = new ArrayList<>();
    private final BusinessEventSubscriptionRuntime runtime;
    private final BusinessModelBehaviorProvider contracts;
    private final BusinessEventPort port;
    private final DataModelBusinessEventOccurrenceRecorder recorder;
    private final BusinessEventDeliveryProperties properties = new BusinessEventDeliveryProperties();

    Fixture(String... subscriptions) {
      String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      mysql = url != null && !url.isBlank();
      tableSuffix = mysql ? "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : "";
      javax.sql.DataSource source;
      if (mysql) {
        source = new org.springframework.jdbc.datasource.DriverManagerDataSource(url,
            System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      } else {
        var h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:business_event_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa");
        source = h2;
      }
      manager = new DataSourceTransactionManager(source);
      jdbc = new JdbcTemplate(source);
      var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.event.store").values());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.workflow.store").values());
      metas.add(businessModel("Order", "orders"));
      metas.add(businessModel("Effect", "effects"));
      metas.forEach(meta -> meta.setRawName(meta.getRawName() + tableSuffix));
      station = new SqlDataStation("event-integration", source, metas, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
      station.getDataModelList().forEach(model -> models.put(model.getFullName(), model));
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      contracts = provider(new BusinessModelBehavior(MODEL, List.of(), List.of(event()), List.of(), List.of(), "revision-1"),
          new BusinessModelBehavior("inventory.Stock", List.of(), List.of(),
              java.util.Arrays.stream(subscriptions).map(this::subscription).toList(), List.of(), "revision-1"));
      var users = mock(UserCenter.class);
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(authorized.get()
          ? Map.of(BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(),
              BusinessEventAuthorities.emit(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()) : Map.of())));
      listeners.set(java.util.Arrays.stream(subscriptions).map(name -> listener(name, delivery -> { })).toList());
      runtime = new DefaultBusinessEventSubscriptionRuntime(contracts, mock(BusinessActionRuntime.class), listeners::get, identities(users), manager);
      recorder = new DataModelBusinessEventOccurrenceRecorder(contracts, () -> runtime, id -> {
        if (wakeupFails.get()) {
          throw new IllegalStateException("scheduler unavailable");
        }
        wakeups.add(id);
      });
      port = new DefaultBusinessEventPort(contracts, () -> List.of((BusinessEventProducer) () -> "scheduler"), recorder, manager);
    }

    void rejectDeliveryStatus(String constraint, String status) {
      String quote = mysql ? "`" : "\"";
      jdbc.execute("ALTER TABLE " + quote + "t_business_event_delivery" + tableSuffix + quote
          + " ADD CONSTRAINT " + constraint + tableSuffix + " CHECK (" + quote + "status" + quote + " <> '" + status + "')");
    }

    void removeDeliveryConstraint(String constraint) {
      String quote = mysql ? "`" : "\"";
      jdbc.execute("ALTER TABLE " + quote + "t_business_event_delivery" + tableSuffix + quote
          + (mysql ? " DROP CHECK " : " DROP CONSTRAINT ") + constraint + tableSuffix);
    }

    BusinessEventOccurrence<?> emit() {
      return withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> port.inTransaction(() -> {
        orders().insert(Map.of("id", "order-1", "state", "submitted")).get();
        var fact = port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
            BusinessEventEmissionContext.fromProducer("scheduler"));
        eventId.set(fact.eventId());
        return fact;
      }));
    }

    BusinessEventOutbox worker() {
      return new BusinessEventOutbox(manager, () -> runtime, properties, clock);
    }

    void mode(String name, BusinessEventExecutionMode mode) {
      var behavior = contracts.getBusinessModelBehavior("inventory.Stock").orElseThrow();
      var definitions = behavior.getEventSubscriptionDefinitions();
      definitions.stream().filter(subscription -> name.equals(subscription.getName())).forEach(subscription -> subscription.setExecutionMode(mode));
      contracts.getBusinessModelBehaviorMap().put("inventory.Stock", new BusinessModelBehavior(behavior.getModelFullName(),
          behavior.getActionDefinitions(), behavior.getEventDefinitions(), definitions, List.of(), behavior.getModelRevision()));
    }

    void enableSubscription(String name, boolean enabled) {
      var behavior = contracts.getBusinessModelBehavior("inventory.Stock").orElseThrow();
      var definitions = behavior.getEventSubscriptionDefinitions();
      definitions.stream().filter(subscription -> name.equals(subscription.getName())).forEach(subscription -> subscription.setIsEnabled(enabled));
      contracts.getBusinessModelBehaviorMap().put("inventory.Stock",
          new BusinessModelBehavior("inventory.Stock", List.of(), List.of(), definitions));
    }

    BusinessEventListener independentListener(String name, java.util.function.Consumer<BusinessEventDeliveryContext> completion) {
      mode(name, BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT);
      return new BusinessEventListener() {
        public String getName() { return name; }
        public BusinessEventDeliveryMode getDeliveryMode() { return BusinessEventDeliveryMode.INDEPENDENT; }
        public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) { completion.accept(delivery); }
      };
    }

    BusinessEventListener listener(String name, java.util.function.Consumer<BusinessEventDeliveryContext> completion) {
      return new BusinessEventListener() {
        public String getName() {
          return name;
        }

        public String getImplementationVersion() {
          return "integration-build-1";
        }

        public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
          effects().insert(Map.of("id", delivery.deliveryId(), "state", occurrence.eventId())).get();
          completion.accept(delivery);
        }
      };
    }

    EventSubscriptionDefinition subscription(String name) {
      var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
      subscription.setName(name);
      subscription.getSourceEvent().setModelFullName(MODEL);
      subscription.getSourceEvent().setEventName("submitted");
      subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName(name);
      return subscription;
    }

    Record delivery(String subscription) {
      String id = DataModelBusinessEventOccurrenceRecorder.deliveryId(eventId.get(), "inventory.Stock", subscription);
      return deliveries().get(id).get();
    }

    DataModel orders() { return models.get(MODEL); }
    DataModel effects() { return models.get("sales.Effect"); }
    DataModel facts() { return models.get(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME); }
    DataModel deliveries() { return models.get(BusinessEventOutbox.MODEL_FULL_NAME); }

    <T> T withModels(Supplier<T> operation) {
      try (var center = mockStatic(DataModelCenter.class)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(lookup -> Optional.ofNullable(models.get(lookup.getArgument(0))));
        center.when(DataModelCenter::getDataModelMap).thenReturn(models);
        center.when(DataModelCenter::hasSnapshot).thenReturn(true);
        return operation.get();
      }
    }

    private DataModelMeta businessModel(String name, String rawName) {
      var meta = new DataModelMeta();
      meta.setName(name);
      meta.setNamespace("sales");
      meta.setRawName(rawName);
      meta.setPrimaryKeys(List.of("id"));
      meta.setFields(List.of(field("id"), field("state")));
      return meta;
    }

    private DataModelFieldMeta field(String name) {
      var field = new DataModelFieldMeta();
      field.setName(name);
      field.setRawName(name);
      field.setType("String");
      return field;
    }
  }

  private static class MutableClock extends Clock {
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-09T00:00:00Z"));
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return Clock.fixed(time.get(), zone); }
    public Instant instant() { return time.get(); }
    void advance(Duration duration) { time.updateAndGet(current -> current.plus(duration)); }
  }
  private static com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver identities(UserCenter users) {
    var settings = new com.ouroboros.businessmodel.event.BusinessEventConsumerProperties();
    settings.setPrincipals(Map.of("inventory.Stock", "stock-service"));
    return new com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver(settings, users);
  }
}
