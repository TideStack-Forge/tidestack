package com.ouroboros.businessmodel.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.event.DefaultBusinessEventPort;
import com.ouroboros.businessmodel.event.store.BusinessEventOutbox;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactCursor;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactReceipt;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkflowBusinessFactProjectionIntegrationTest {
  private static final String MODEL = "cases.Document";
  private static final String FLOW = "approval";
  private static final Instant TIME = Instant.parse("2026-01-01T02:03:04Z");
  private static final CallbackBinding BINDING = new CallbackBinding(FLOW, MODEL + ".processCallback", MODEL, "status", Map.of());

  @Test
  void realReceiverRollsBackTogetherThenCommitsAndDeduplicatesAfterRestart() {
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var calls = new AtomicInteger();
      assertThrows(IllegalStateException.class, () -> fixture.receiver().projectInstance(started, BINDING, () -> {
        fixture.status("processing");
        throw new IllegalStateException("callback rollback");
      }));
      fixture.assertCounts(0, 0, 0);
      assertEquals("draft", fixture.status());
      assertTrue(fixture.receiver().projectInstance(started, BINDING, () -> {
        calls.incrementAndGet();
        fixture.status("processing");
        assertEquals("workflow-projection:" + MODEL, CurrentSecurityContextHolder.getRequiredIdentity().getPrincipal());
        assertTrue(BusinessBehaviorSecurity.hasAuthority(MODEL + ".workflow"));
        assertFalse(BusinessBehaviorSecurity.hasAuthority("other.Model.workflow"));
        assertEquals(Map.of("id", Map.of("=", 42L)), CurrentSecurityContextHolder.findAuthentication().orElseThrow()
            .findScopedAccessControlParams(MODEL + ".update").orElseThrow().getFilters());
      }));
      fixture.assertCounts(1, 1, 1);
      assertEquals("processing", fixture.status());
      fixture.receiver().projectInstance(started, BINDING, calls::incrementAndGet);
      assertEquals(1, calls.get());
      var fact = fixture.facts().query().execute().get().getFirst();
      var occurrence = Json.toMap(fact.get("occurrenceJson").toString());
      assertEquals(TIME.toString(), occurrence.get("occurredAt"));
      assertEquals(started.getEventFactId(), occurrence.get("causationId"));
      assertEquals(42, ((Number) ((Map<?, ?>) occurrence.get("subject")).get("id")).intValue());
      assertEquals(WorkflowBusinessEventProducer.NAME, occurrence.get("producerRef"));
      assertNull(occurrence.get("actionExecutionId"));
      assertNull(DataModelExecutionContext.getWriteGuard());
    }
  }

  @Test
  void receiptFailureRollsBackCallbackFactAndDeliveryAndRetryUsesTheSameSource() {
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var broken = new WorkflowBusinessFactStore() {
        @Override
        public WorkflowBusinessFactReceipt record(WorkflowBusinessFactCursor cursor, String sourceFactId, String instanceId,
            Long sequence, String eventId, String disposition) {
          super.record(cursor, sourceFactId, instanceId, sequence, eventId, disposition);
          throw new IllegalStateException("receipt commit failed");
        }
      };
      assertThrows(IllegalStateException.class, () -> fixture.receiver(broken, fixture.outbox)
          .projectInstance(started, BINDING, () -> fixture.status("processing")));
      fixture.assertCounts(0, 0, 0);
      assertEquals("draft", fixture.status());
      fixture.receiver().projectInstance(started, BINDING, () -> fixture.status("processing"));
      fixture.assertCounts(1, 1, 1);
    }
  }

  @Test
  void concurrentReceiversReadTheCommittedReceiptAfterWaitingEvenWithAnOlderSourceSnapshot() throws Exception {
    try (var fixture = new Fixture(); var executor = Executors.newFixedThreadPool(2)) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var proofs = new CountDownLatch(2);
      var release = new CountDownLatch(1);
      var source = spy(fixture.outbox);
      doAnswer(call -> {
        call.callRealMethod();
        proofs.countDown();
        return null;
      }).when(source).requirePersistedFact(any(FlowInstanceEvent.class));
      var calls = new AtomicInteger();
      Runnable callback = () -> {
        calls.incrementAndGet();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test receiver not released"); }
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(failure);
        }
        fixture.status("processing");
      };
      try {
        var first = executor.submit(() -> fixture.inThread(() -> fixture.receiver(new WorkflowBusinessFactStore(), source)
            .projectInstance(started, BINDING, callback)));
        var second = executor.submit(() -> fixture.inThread(() -> fixture.receiver(new WorkflowBusinessFactStore(), source)
            .projectInstance(started, BINDING, callback)));
        assertTrue(proofs.await(5, TimeUnit.SECONDS));
        release.countDown();
        assertTrue(first.get(10, TimeUnit.SECONDS));
        assertTrue(second.get(10, TimeUnit.SECONDS));
      } finally { release.countDown(); }
      assertEquals(1, calls.get());
      fixture.assertCounts(1, 1, 1);
    }
  }

  @Test
  void newerSourceStateAndNewRoundsPreventLateCallbacksButKeepHistoricalEvents() {
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var approved = fixture.source(FlowInstanceEvent.EventType.APPROVED);
      fixture.transaction.executeWithoutResult(tx -> {
        fixture.cases.bindExecution(fixture.submission.approvalCaseId(), fixture.submission.submissionRound(), fixture.instance);
        assertNull(fixture.cases.findById(fixture.submission.approvalCaseId()).orElseThrow().getActiveExecutionId());
        assertThrows(BusinessException.class, () -> fixture.cases.bindExecution(fixture.submission.approvalCaseId(),
            fixture.submission.submissionRound(), "other-instance"));
      });
      fixture.receiver().projectInstance(approved, BINDING, () -> fixture.status("approved"));
      fixture.receiver().projectInstance(started, BINDING, () -> fail("stale STARTED must not run"));
      assertEquals("approved", fixture.status());
      fixture.assertCounts(2, 2, 2);
      fixture.transaction.executeWithoutResult(status -> {
        var cursor = new WorkflowBusinessFactStore().acquire(MODEL, 42L, FLOW);
        assertEquals(2L, cursor.getStateSequence());
        assertEquals("STALE_STATE", new WorkflowBusinessFactStore().findReceipt(cursor, started.getEventFactId())
            .orElseThrow().getDisposition());
      });
    }
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var withdrawn = fixture.source(FlowInstanceEvent.EventType.CANCELLED, "withdrawn");
      fixture.submission = fixture.transaction.execute(status -> fixture.cases.reserveSubmission(MODEL, "42", FLOW));
      fixture.instance = "new-instance";
      var next = fixture.source(FlowInstanceEvent.EventType.STARTED);
      fixture.receiver().projectInstance(next, BINDING, () -> fixture.status("new-processing"));
      fixture.receiver().projectInstance(withdrawn, BINDING, () -> fail("previous round must not run"));
      fixture.receiver().projectInstance(started, BINDING, () -> fail("previous instance must not run"));
      assertEquals("new-processing", fixture.status());
      fixture.assertCounts(3, 3, 3);
      assertTrue(fixture.facts().query().execute().get().stream().anyMatch(row -> "workflowWithdrawn".equals(row.get("eventName"))));
    }
  }

  @Test
  void taskFactsAreIndependentAndDoNotAdvanceLifecycleCursorOrDuplicateCommands() {
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      fixture.receiver().projectInstance(started, BINDING, () -> fixture.status("processing"));
      var created = fixture.task(TaskState.CREATED, null);
      var reminder1 = fixture.task(TaskState.TIMEOUT_REMIND, null);
      var reminder2 = fixture.task(TaskState.TIMEOUT_REMIND, null);
      var transferred = fixture.task(TaskState.PROCESSING, "TRANSFER");
      var handled = fixture.task(TaskState.HANDLED, null);
      for (var task : List.of(created, reminder1, reminder2, transferred, handled)) {
        assertTrue(fixture.receiver().projectTask(task));
        fixture.receiver().projectTask(task);
      }
      fixture.assertCounts(5, 5, 6);
      fixture.transaction.executeWithoutResult(status -> assertEquals(1L,
          new WorkflowBusinessFactStore().acquire(MODEL, 42L, FLOW).getStateSequence()));
      assertEquals("processing", fixture.status());
    }
  }

  @Test
  void sourceProofBindingAndScopedProducerRejectForgeryAndDoNotLeakIdentity() {
    try (var fixture = new Fixture()) {
      var source = fixture.source(FlowInstanceEvent.EventType.STARTED);
      var original = new DefaultSecurityIdentity((User) () -> "caller");
      CurrentSecurityContextHolder.withIdentity(original, () -> {
        fixture.port.inTransaction(() -> {
          var guard = DataModelExecutionContext.getWriteGuard();
          fixture.receiver().projectInstance(source, BINDING, () -> fixture.status("processing"));
          assertSame(guard, DataModelExecutionContext.getWriteGuard());
          assertSame(original, CurrentSecurityContextHolder.getRequiredIdentity());
          return null;
        });
        return null;
      });
      var identity = new BusinessEventIdentity(MODEL, "workflowStarted");
      assertThrows(BusinessException.class, () -> WorkflowProjectionSecurity.withFact(identity, 42L, () -> fixture.port.inTransaction(() ->
          fixture.port.emit(identity, BusinessEventSubject.instance(MODEL, 42L), Map.of(),
              new BusinessEventEmissionContext(WorkflowBusinessEventProducer.NAME, "instance", source.getEventFactId())))));
      source.setOutcome("forged");
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(source, BINDING, () -> fail("forged")));
      source.setOutcome(null);
      var wrong = new CallbackBinding("wrong", BINDING.callbackFlow(), MODEL, "status", Map.of());
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(source, wrong, () -> fail("wrong binding")));
      fixture.assertCounts(1, 1, 1);
      assertNull(DataModelExecutionContext.getWriteGuard());
    }
  }

  @Test
  void receiverCommitIsIndependentOfEnclosingTransactionAndLegacyFactsAreNotBackfilled() {
    try (var fixture = new Fixture()) {
      var started = fixture.source(FlowInstanceEvent.EventType.STARTED);
      assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status -> {
        fixture.receiver().projectInstance(started, BINDING, () -> fixture.status("processing"));
        throw new IllegalStateException("outer delivery acknowledgement failed");
      }));
      fixture.assertCounts(1, 1, 1);
      var legacy = fixture.event(FlowInstanceEvent.EventType.STARTED, null);
      fixture.transaction.executeWithoutResult(status -> fixture.outbox.enqueue(legacy));
      fixture.receiver().projectInstance(legacy, BINDING, () -> fail("old fact must not rewind a versioned instance"));
      fixture.assertCounts(1, 1, 2);
      var generic = new FlowInstanceEvent();
      assertFalse(fixture.receiver().projectInstance(generic, null, () -> fail("generic")));
      assertFalse(fixture.receiver().projectTask(new FlowTaskEvent()));
    }
  }

  @Test
  void legacyCallbackAndAuthoritativeBaselineCommitTogetherWithoutBackfillingBusinessEvents() {
    try (var fixture = new Fixture()) {
      fixture.startLegacyEngine();
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      assertThrows(IllegalStateException.class, () -> fixture.receiver().projectInstance(legacy, BINDING, () -> {
        fixture.status("processing");
        throw new IllegalStateException("legacy callback rollback");
      }));
      fixture.transaction.executeWithoutResult(tx -> assertTrue(fixture.states.lock(fixture.instance).isEmpty()));
      assertNull(fixture.cases.findById(fixture.submission.approvalCaseId()).orElseThrow().getLastStateSequence());
      assertEquals("draft", fixture.status());
      fixture.assertCounts(0, 0, 0);
      var broken = new WorkflowBusinessFactStore() {
        @Override
        public WorkflowBusinessFactReceipt record(WorkflowBusinessFactCursor cursor, String factId, String instanceId,
            Long sequence, String eventId, String disposition) {
          super.record(cursor, factId, instanceId, sequence, eventId, disposition);
          throw new IllegalStateException("legacy receipt rollback");
        }
      };
      assertThrows(IllegalStateException.class, () -> fixture.receiver(broken, fixture.outbox)
          .projectInstance(legacy, BINDING, () -> fixture.status("processing")));
      fixture.transaction.executeWithoutResult(tx -> assertTrue(fixture.states.lock(fixture.instance).isEmpty()));
      assertEquals("draft", fixture.status());
      fixture.assertCounts(0, 0, 0);
      var calls = new AtomicInteger();
      fixture.receiver().projectInstance(legacy, BINDING, () -> { calls.incrementAndGet(); fixture.status("processing"); });
      fixture.receiver().projectInstance(legacy, BINDING, calls::incrementAndGet);
      assertEquals(1, calls.get());
      fixture.assertCounts(0, 0, 1);
      fixture.transaction.executeWithoutResult(tx -> {
        var baseline = fixture.states.lock(fixture.instance).orElseThrow();
        assertEquals(0L, baseline.getStateSequence());
        assertEquals("", baseline.getLastEventFactId());
        assertEquals(fixture.submission.approvalCaseId(), baseline.getApprovalCaseId());
      });
      assertEquals(0L, fixture.cases.findById(fixture.submission.approvalCaseId()).orElseThrow().getLastStateSequence());
      var suspended = fixture.source(FlowInstanceEvent.EventType.SUSPENDED);
      assertEquals(1L, suspended.getStateSequence());
      fixture.receiver().projectInstance(suspended, BINDING, () -> fixture.status("suspended"));
      fixture.receiver().projectInstance(fixture.legacy(FlowInstanceEvent.EventType.STARTED, null), BINDING,
          () -> fail("old fact must not pass the new source version"));
      assertEquals("suspended", fixture.status());
      fixture.assertCounts(1, 1, 3);
    }
  }

  @Test
  void externalInspectionPreparesWithoutATransactionThenChecksAndCommitsUnderTheCaseLock() {
    try (var fixture = new Fixture()) {
      var checks = new AtomicInteger();
      doAnswer(call -> {
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        return (Supplier<com.ouroboros.workflow.approval.ApprovalInstanceState>) () -> {
          assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
          var locked = fixture.cases.lockByBusinessRecord(MODEL, "42", FLOW).orElseThrow();
          assertEquals(fixture.submission.approvalCaseId(), locked.getApprovalCaseId()); checks.incrementAndGet();
          return new com.ouroboros.workflow.approval.ApprovalInstanceState(FLOW, fixture.instance,
              new FlowInstanceEvent.BusinessContext(MODEL, "42"), locked.getApprovalCaseId(), locked.getSubmissionRound(),
              BINDING.callbackFlow(), FlowInstanceEvent.EventType.STARTED, null);
        };
      }).when(fixture.bindings).prepareInstanceInspection(FLOW, fixture.instance);
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      fixture.transaction.executeWithoutResult(status -> {
        fixture.receiver().projectInstance(legacy, BINDING, () -> fixture.status("processing"));
        status.setRollbackOnly();
      });
      assertEquals("processing", fixture.status());
      fixture.assertCounts(0, 0, 1);
      assertEquals(1, checks.get());
      doThrow(new BusinessException("remote unavailable")).when(fixture.bindings).prepareInstanceInspection(FLOW, fixture.instance);
      assertTrue(fixture.receiver().projectInstance(legacy, BINDING, () -> fail("already received")));
      var newer = fixture.source(FlowInstanceEvent.EventType.SUSPENDED);
      fixture.receiver().projectInstance(newer, BINDING, () -> fixture.status("suspended"));
      assertTrue(fixture.receiver().projectInstance(fixture.legacy(FlowInstanceEvent.EventType.STARTED, null), BINDING,
          () -> fail("superseded")));
      assertEquals("suspended", fixture.status());
    }
  }

  @Test
  void anUnverifiablePreparedInspectionLeavesTheOriginalLegacyResponsibilityAndNoBaseline() {
    try (var fixture = new Fixture()) {
      doThrow(new BusinessException("remote unavailable")).when(fixture.bindings).prepareInstanceInspection(FLOW, fixture.instance);
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(legacy, BINDING, () -> fail("unverified")));
      fixture.transaction.executeWithoutResult(status -> assertTrue(fixture.states.lock(fixture.instance).isEmpty()));
      assertEquals("PENDING", fixture.outbox.findById(legacy.getEventFactId()).orElseThrow().getStatus());
      fixture.assertCounts(0, 0, 0);
    }
  }

  @Test
  void anUnpersistedOrAlteredLegacyPayloadCannotTriggerRemoteInspection() {
    try (var fixture = new Fixture()) {
      var event = fixture.event(FlowInstanceEvent.EventType.STARTED, null);
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(event, BINDING, () -> fail("forged")));
      var persisted = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      persisted.setOutcome("altered");
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(persisted, BINDING, () -> fail("altered")));
      verifyNoInteractions(fixture.bindings);
      fixture.assertCounts(0, 0, 0);
    }
  }

  @Test
  void legacyStateUsesActualFlowableHistoryAndDoesNotReplayStartedAfterApproval() {
    try (var fixture = new Fixture()) {
      fixture.startLegacyEngine();
      var started = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      fixture.completeLegacy("approved");
      var approved = fixture.legacy(FlowInstanceEvent.EventType.APPROVED, "approved");
      fixture.receiver().projectInstance(started, BINDING, () -> fail("engine already ended"));
      assertEquals("APPROVED", fixture.cases.findById(fixture.submission.approvalCaseId()).orElseThrow().getBusinessStatus());
      var calls = new AtomicInteger();
      fixture.receiver().projectInstance(approved, BINDING, () -> { calls.incrementAndGet(); fixture.status("approved"); });
      fixture.receiver().projectInstance(approved, BINDING, calls::incrementAndGet);
      assertEquals(1, calls.get());
      assertEquals("approved", fixture.status());
      fixture.assertCounts(0, 0, 2);
      fixture.transaction.executeWithoutResult(tx -> assertEquals("approved", fixture.states.lock(fixture.instance).orElseThrow().getOutcome()));
    }
  }

  @Test
  void legacyPauseAndResumeAreCheckedAgainstPersistedEngineStateRatherThanDeliveryTime() {
    for (boolean resumed : List.of(false, true)) {
      try (var fixture = new Fixture()) {
        fixture.startLegacyEngine();
        fixture.transaction.executeWithoutResult(tx -> {
          fixture.engine.getRuntimeService().suspendProcessInstanceById(fixture.instance);
          if (resumed) {
            fixture.engine.getRuntimeService().activateProcessInstanceById(fixture.instance);
            fixture.engine.getRuntimeService().setVariable(fixture.instance,
                com.ouroboros.workflow.approval.flowable.transpiler.Constants.PROCESS_RESUME_TIME_VARIABLE_NAME, java.time.LocalDateTime.now());
          }
        });
        fixture.receiver().projectInstance(fixture.legacy(FlowInstanceEvent.EventType.STARTED, null), BINDING, () -> fail("late STARTED"));
        var expected = resumed ? FlowInstanceEvent.EventType.RESUMED : FlowInstanceEvent.EventType.SUSPENDED;
        var other = resumed ? FlowInstanceEvent.EventType.SUSPENDED : FlowInstanceEvent.EventType.RESUMED;
        fixture.receiver().projectInstance(fixture.legacy(other, null), BINDING, () -> fail("wrong current state"));
        fixture.receiver().projectInstance(fixture.legacy(expected, null), BINDING, () -> fixture.status(expected.name()));
        assertEquals(expected.name(), fixture.status());
        fixture.assertCounts(0, 0, 3);
      }
    }
  }

  @Test
  void legacyUnknownTerminalOutcomeOrForeignEngineOwnershipLeavesTheOriginalOutboxPending() {
    try (var fixture = new Fixture()) {
      fixture.startLegacyEngine();
      fixture.transaction.executeWithoutResult(tx -> fixture.engine.getRuntimeService().deleteProcessInstance(fixture.instance, "ambiguous old deletion"));
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.CANCELLED, null);
      var error = assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(legacy, BINDING, () -> fail("unknown outcome")));
      assertTrue(error.getMessage().contains("可核验的业务结果"));
      fixture.assertCounts(0, 0, 0);
      assertEquals("PENDING", fixture.outbox.findById(legacy.getEventFactId()).orElseThrow().getStatus());
    }
    try (var fixture = new Fixture()) {
      fixture.startLegacyEngine();
      fixture.transaction.executeWithoutResult(tx -> fixture.engine.getRuntimeService().setVariable(fixture.instance, "businessRecordId", "foreign"));
      assertThrows(BusinessException.class, () -> fixture.receiver()
          .projectInstance(fixture.legacy(FlowInstanceEvent.EventType.STARTED, null), BINDING, () -> fail("foreign engine record")));
      fixture.transaction.executeWithoutResult(tx -> assertTrue(fixture.states.lock(fixture.instance).isEmpty()));
      fixture.assertCounts(0, 0, 0);
    }
  }

  @Test
  void legacyEngineStateIsReadAfterTheCaseLockEvenWhenTheSourceProofEstablishedAnEarlierSnapshot() throws Exception {
    try (var fixture = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
      fixture.startLegacyEngine();
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      var proofRead = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var source = spy(fixture.outbox);
      doAnswer(call -> {
        call.callRealMethod();
        proofRead.countDown();
        if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("engine update not released"); }
        return null;
      }).when(source).requirePersistedFact(any(FlowInstanceEvent.class));
      try {
        var receiver = executor.submit(() -> fixture.inThread(() -> fixture.receiver(new WorkflowBusinessFactStore(), source)
            .projectInstance(legacy, BINDING, () -> fail("stale source snapshot must not hide committed engine history"))));
        assertTrue(proofRead.await(10, TimeUnit.SECONDS));
        fixture.completeLegacy("approved");
        release.countDown();
        assertTrue(receiver.get(15, TimeUnit.SECONDS));
      } finally { release.countDown(); }
      fixture.transaction.executeWithoutResult(tx -> assertEquals("APPROVED", fixture.states.lock(fixture.instance).orElseThrow().getEventType()));
      fixture.assertCounts(0, 0, 1);
    }
  }

  @Test
  void firstVersionedSourceWaitsForLegacyBaselineAndReadsTheCaseCommittedByTheReceiver() throws Exception {
    try (var fixture = new Fixture(); var executor = Executors.newFixedThreadPool(2)) {
      fixture.startLegacyEngine();
      var legacy = fixture.legacy(FlowInstanceEvent.EventType.STARTED, null);
      var inCallback = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var sourceWaiting = new CountDownLatch(1);
      var suspended = fixture.event(FlowInstanceEvent.EventType.SUSPENDED, null);
      try {
        var receiver = executor.submit(() -> fixture.inThread(() -> fixture.receiver().projectInstance(legacy, BINDING, () -> {
          inCallback.countDown();
          try {
            if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("baseline callback not released"); }
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
          }
          fixture.status("processing");
        })));
        assertTrue(inCallback.await(10, TimeUnit.SECONDS));
        var command = executor.submit(() -> fixture.inThread(() -> fixture.transaction.execute(tx -> {
          fixture.states.restoreIdentity(suspended);
          sourceWaiting.countDown();
          fixture.cases.lockForSource(suspended);
          fixture.engine.getRuntimeService().suspendProcessInstanceById(fixture.instance);
          fixture.states.advance(suspended);
          assertTrue(fixture.cases.applyInstanceEvent(suspended));
          fixture.outbox.enqueue(suspended);
          return true;
        })));
        assertTrue(sourceWaiting.await(10, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.TimeoutException.class, () -> command.get(150, TimeUnit.MILLISECONDS));
        release.countDown();
        assertTrue(receiver.get(15, TimeUnit.SECONDS));
        assertTrue(command.get(15, TimeUnit.SECONDS));
      } finally { release.countDown(); }
      assertEquals(1L, suspended.getStateSequence());
      assertEquals("SUSPENDED", fixture.cases.findById(fixture.submission.approvalCaseId()).orElseThrow().getBusinessStatus());
      fixture.receiver().projectInstance(suspended, BINDING, () -> fixture.status("suspended"));
      assertEquals("suspended", fixture.status());
      fixture.assertCounts(1, 1, 2);
    }
  }

  @Test
  void newFactFromLegacyInstanceWithoutCaseVariablesUsesOnlyItsExistingCanonicalExecutionBinding() {
    try (var fixture = new Fixture()) {
      fixture.startLegacyEngine();
      var suspended = fixture.event(FlowInstanceEvent.EventType.SUSPENDED, null);
      suspended.setApprovalCaseId(null); suspended.setSubmissionRound(null);
      fixture.transaction.executeWithoutResult(tx -> {
        fixture.cases.lockForSource(suspended);
        fixture.engine.getRuntimeService().suspendProcessInstanceById(fixture.instance);
        fixture.states.advance(suspended);
        assertTrue(fixture.cases.applyInstanceEvent(suspended));
        fixture.outbox.enqueue(suspended);
      });
      assertEquals(fixture.submission.approvalCaseId(), suspended.getApprovalCaseId());
      assertEquals(1L, suspended.getStateSequence());
      fixture.receiver().projectInstance(suspended, BINDING, () -> fixture.status("suspended"));
      assertEquals("suspended", fixture.status());
      var foreign = fixture.event(FlowInstanceEvent.EventType.APPROVED, "approved");
      foreign.setApprovalCaseId(null); foreign.setSubmissionRound(null); foreign.setInstanceId("foreign");
      assertThrows(BusinessException.class, () -> fixture.transaction.executeWithoutResult(tx -> fixture.cases.lockForSource(foreign)));
      fixture.assertCounts(1, 1, 1);
    }
  }

  @Test
  void unverifiableLegacyStateAndVersionedFactsWithoutSequenceRetainFailureResponsibility() {
    try (var fixture = new Fixture()) {
      var legacy = fixture.event(FlowInstanceEvent.EventType.STARTED, null);
      fixture.transaction.executeWithoutResult(tx -> fixture.outbox.enqueue(legacy));
      var failure = assertThrows(BusinessException.class, () -> fixture.receiver()
          .projectInstance(legacy, BINDING, () -> fail("unverified legacy callback")));
      assertTrue(failure.getMessage().contains("状态基线"));
      assertTrue(fixture.receiver().ownsInstance(legacy));
      var malformed = fixture.event(FlowInstanceEvent.EventType.STARTED, null);
      malformed.setProjectionVersion(1);
      fixture.transaction.executeWithoutResult(tx -> fixture.outbox.enqueue(malformed));
      failure = assertThrows(BusinessException.class, () -> fixture.receiver()
          .projectInstance(malformed, BINDING, () -> fail("missing sequence is not legacy")));
      assertTrue(failure.getMessage().contains("状态序号"));
      assertFalse(fixture.receiver().ownsInstance(null));
      assertFalse(fixture.receiver().projectInstance(null, null, () -> fail("null")));
      assertEquals("draft", fixture.status());
      fixture.assertCounts(0, 0, 0);
    }
  }

  @Test
  void sourceFixesNotificationOwnershipBeforePersistenceAndRestartHandoffNeverFallsBackOrRepeatsTheCallback() {
    try (var fixture = new Fixture(true)) {
      var event = fixture.source(FlowInstanceEvent.EventType.APPROVED, "approved", fixture::notification);
      assertNotNull(event.getBusinessNotificationOwner());
      var stored = fixture.outbox.findById(event.getEventFactId()).orElseThrow();
      var serializedOwner = (Map<?, ?>) Json.toMap(stored.getEventJson()).get("businessNotificationOwner");
      assertEquals("notification-service", serializedOwner.get("user"));
      assertFalse(serializedOwner.containsKey("executorRef"));
      assertFalse(serializedOwner.containsKey("executionPrincipalRef"));
      var reloaded = fixture.outbox.fromInstanceJson(stored).orElseThrow();
      assertEquals(event.getBusinessNotificationOwner(), reloaded.getBusinessNotificationOwner());
      var original = fixture.publishedBehavior;
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), List.of());
      var callbacks = new AtomicInteger();
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(reloaded, BINDING, callbacks::incrementAndGet));
      assertEquals(0, callbacks.get()); fixture.assertCounts(0, 0, 0);
      fixture.publishedBehavior = original;
      when(fixture.notificationRuntime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.empty());
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(reloaded, BINDING, callbacks::incrementAndGet));
      assertEquals(0, callbacks.get()); fixture.assertCounts(0, 0, 0);
      when(fixture.notificationRuntime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.of(
          new com.ouroboros.businessmodel.event.BusinessEventDeliveryAttempt("revision", null, "listener", "1",
              com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT)));
      fixture.receiver().projectInstance(reloaded, BINDING, callbacks::incrementAndGet);
      assertEquals(1, callbacks.get()); fixture.assertCounts(1, 2, 1);
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), List.of());
      fixture.receiver().projectInstance(reloaded, BINDING, callbacks::incrementAndGet);
      assertEquals(1, callbacks.get()); fixture.assertCounts(1, 2, 1);
    }
  }

  @Test
  void disablingTheFixedNotificationOwnerKeepsSourceResponsibilityUntilRestoredAndBlocksCanonicalReads() {
    try (var fixture = new Fixture(true)) {
      var event = fixture.source(FlowInstanceEvent.EventType.APPROVED, "approved", fixture::notification);
      var original = fixture.publishedBehavior;
      var subscriptions = original.getEventSubscriptionDefinitions();
      subscriptions.forEach(subscription -> subscription.setIsEnabled(false));
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), subscriptions, List.of(), original.getModelRevision());
      var callbacks = new AtomicInteger();
      assertThrows(BusinessException.class, () -> fixture.receiver().projectInstance(event, BINDING, callbacks::incrementAndGet));
      assertEquals(0, callbacks.get()); fixture.assertCounts(0, 0, 0);
      assertNotNull(fixture.outbox.findById(event.getEventFactId()).orElseThrow().getEventJson());
      subscriptions.forEach(subscription -> subscription.setIsEnabled(true));
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), subscriptions, List.of(), original.getModelRevision());
      fixture.receiver().projectInstance(event, BINDING, callbacks::incrementAndGet);
      assertEquals(1, callbacks.get()); fixture.assertCounts(1, 2, 1);
      var occurrence = fixture.lastOccurrence;
      var delivery = fixture.delivery("workflowApproved");
      var source = fixture.notificationSource(ignored -> true);
      assertTrue(fixture.asConsumer(() -> source.read(occurrence, delivery)).isPresent());
      subscriptions.forEach(subscription -> subscription.setIsEnabled(false));
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), subscriptions, List.of(), original.getModelRevision());
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.read(occurrence, delivery)));
    }
  }

  @Test
  void taskNotificationOwnershipIsRequiredOnlyUntilTheReceiptHasAcceptedResponsibility() {
    try (var fixture = new Fixture(true)) {
      var task = fixture.task(TaskState.CREATED, null, fixture::notification);
      assertNotNull(task.getBusinessNotificationOwner());
      var original = fixture.publishedBehavior;
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), List.of());
      assertThrows(BusinessException.class, () -> fixture.receiver().projectTask(task)); fixture.assertCounts(0, 0, 0);
      fixture.publishedBehavior = original;
      assertTrue(fixture.receiver().projectTask(task)); fixture.assertCounts(1, 2, 1);
      fixture.publishedBehavior = new BusinessModelBehavior(MODEL, List.of(), original.getEventDefinitions(), List.of());
      assertTrue(fixture.receiver().projectTask(task)); fixture.assertCounts(1, 2, 1);
    }
  }

  @Test
  void originalSourceRulesWithoutANewPublishedOwnerRemainLegacyAndSourceRollbackLeavesNoFact() {
    try (var fixture = new Fixture()) {
      var task = fixture.task(TaskState.CREATED, null, fixture::notification);
      assertNull(task.getBusinessNotificationOwner());
      assertNull(fixture.outbox.fromTaskJson(fixture.outbox.findById(task.getEventFactId()).orElseThrow()).orElseThrow().getBusinessNotificationOwner());
    }
    try (var fixture = new Fixture(true)) {
      var disabled = fixture.task(TaskState.CREATED, null, event -> {
        fixture.notification(event);
        var node = new com.ouroboros.workflow.approval.definition.nodes.HandleNode(); node.setNotify(false); event.setFlowNode(node);
      });
      assertNull(disabled.getBusinessNotificationOwner());
      var event = fixture.event(FlowInstanceEvent.EventType.APPROVED, null); fixture.notification(event);
      fixture.transaction.executeWithoutResult(tx -> {
        fixture.states.restoreIdentity(event); fixture.cases.lockForSource(event); fixture.states.advance(event);
        fixture.cases.applyInstanceEvent(event); fixture.outbox.enqueue(event);
        assertNotNull(event.getBusinessNotificationOwner()); tx.setRollbackOnly();
      });
      assertTrue(fixture.outbox.findById(event.getEventFactId()).isEmpty());
      fixture.assertCounts(0, 0, 0);
    }
  }

  @Test
  void notificationSourceReadsCanonicalReceiptedFactsWithOriginalTemplateValuesAndNoReceiverTransaction() {
    try (var fixture = new Fixture(true)) {
      var task = fixture.task(TaskState.CREATED, null, fixture::notification);
      fixture.receiver().projectTask(task);
      var occurrence = fixture.lastOccurrence;
      var delivery = fixture.delivery("workflowTaskCreated");
      var notice = fixture.asConsumer(() -> fixture.notificationSource(ignored -> true).read(occurrence, delivery)).orElseThrow();
      assertEquals("task-template", notice.template());
      assertEquals(List.of("alice", "bob"), notice.employeeIds());
      assertEquals(task.getEventTime(), notice.payload().get("eventTime"));
      assertInstanceOf(java.time.LocalDateTime.class, notice.payload().get("eventTime"));
      assertTrue(notice.payload().containsKey("actionName"));
      assertNull(notice.payload().get("actionName"));
      assertEquals("Review", notice.payload().get("taskName"));
      assertThrows(UnsupportedOperationException.class, () -> notice.payload().put("secret", "changed"));
      assertFalse(occurrence.payloadFields().containsKey("candidateUsers"));
      assertFalse(occurrence.payloadFields().containsKey("taskName"));
      assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
    }
  }

  @Test
  void notificationSourceRetainsLegacyOwnershipAndRejectsUnauthorizedOrForgedDisclosureBeforeInternalFactReads() {
    try (var fixture = new Fixture(true)) {
      var task = fixture.task(TaskState.TIMEOUT_REMIND, null);
      fixture.receiver().projectTask(task);
      var occurrence = fixture.lastOccurrence;
      var delivery = fixture.delivery("workflowTaskTimeoutReminded");
      assertTrue(fixture.asConsumer(() -> fixture.notificationSource(ignored -> true).read(occurrence, delivery)).isEmpty());
      var receipts = mock(WorkflowBusinessFactStore.class);
      var source = mock(FlowEventOutboxService.class);
      var restricted = new DefaultBusinessWorkflowNotificationSource(fixture.owner, fixture.behaviors, ignored -> false, receipts, source, fixture.tasks, fixture.definitions);
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> restricted.read(occurrence, delivery)));
      verifyNoInteractions(receipts, source);
      var fake = com.ouroboros.businessmodel.event.BusinessEventOccurrence.create(occurrence.eventId(), occurrence.identity(), occurrence.modelRevision(),
          occurrence.subject(), occurrence.payloadSchema(), Map.of("eventFactId", task.getEventFactId(), "flowKey", FLOW, "instanceId", fixture.instance),
          occurrence.occurredAt(), occurrence.producerRef(), occurrence.actorRef(), occurrence.correlationId(), occurrence.causationId(), occurrence.executionId());
      var guarded = new DefaultBusinessWorkflowNotificationSource(fixture.owner, fixture.behaviors, ignored -> true, receipts, source, fixture.tasks, fixture.definitions);
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> guarded.read(fake, delivery)));
      verifyNoInteractions(receipts, source);
    }
  }

  @Test
  void notificationSourceRequiresTheExactReceiptRuleAndConsumerIdentity() {
    try (var fixture = new Fixture(true)) {
      var task = fixture.task(TaskState.CREATED, null, fixture::notification);
      fixture.receiver().projectTask(task);
      var occurrence = fixture.lastOccurrence;
      var delivery = fixture.delivery("workflowTaskCreated");
      var source = fixture.notificationSource(ignored -> true);
      assertThrows(RuntimeException.class, () -> source.read(occurrence, delivery));
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.read(occurrence, fixture.delivery("workflowApproved"))));
      var receipt = fixture.models.get("WorkflowBusinessFactReceipt").query(Map.of()).get().getFirst();
      fixture.models.get("WorkflowBusinessFactReceipt").update(receipt.get("receiptKey"), Map.of("businessEventId", "foreign")).get();
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.read(occurrence, delivery)));
    }
  }

  @Test
  void delayedTaskNotificationsSkipCompletedTransferredAndDisabledRecipientsAndPropagateUnverifiableState() {
    try (var fixture = new Fixture(true)) {
      var task = fixture.task(TaskState.TIMEOUT_REMIND, null, fixture::notification);
      fixture.receiver().projectTask(task);
      var occurrence = fixture.lastOccurrence;
      var delivery = fixture.delivery("workflowTaskTimeoutReminded");
      var source = fixture.notificationSource(ignored -> true);
      var current = new com.ouroboros.workflow.approval.model.FlowTaskDetail();
      current.setId("task"); current.setFlowKey(FLOW); current.setInstanceId(fixture.instance); current.setTaskDefinitionKey("review");
      current.setActive(true); current.setCandidateUsers(List.of("alice"));
      when(fixture.tasks.getTaskDetail("task")).thenAnswer(call -> {
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        return current;
      });
      var definition = new com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition();
      var node = new com.ouroboros.workflow.approval.definition.nodes.HandleNode(); node.setId("review");
      var start = new com.ouroboros.workflow.approval.definition.nodes.StartNode(); start.setId("start"); start.setNext("review");
      definition.setNodes(List.of(start, node));
      when(fixture.definitions.getStructuredFlowDefinition(FLOW)).thenReturn(io.vavr.control.Try.success(definition));
      assertTrue(fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "alice")).applicable());
      assertEquals("workflow-recipient-no-longer-eligible", fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")).diagnostic());
      current.setAssignee("bob");
      assertFalse(fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "alice")).applicable());
      assertTrue(fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")).applicable());
      node.setNotify(false);
      assertEquals("workflow-node-notify-disabled", fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")).diagnostic());
      current.setActive(false);
      assertEquals("workflow-task-no-longer-active", fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")).diagnostic());
      current.setActive(null);
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")));
      current.setActive(true); current.setAssignee(null); current.setCandidateUsers(null); node.setNotify(true);
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")));
      current.setInstanceId("foreign");
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")));
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "stranger")));
      when(fixture.tasks.getTaskDetail("task")).thenThrow(new BusinessException("external engine unavailable"));
      assertThrows(BusinessException.class, () -> fixture.asConsumer(() -> source.eligibility(occurrence, delivery, "bob")));
    }
  }

  @Test
  void resultNotificationsRetainStarterOutcomeAndTimeForBothOriginalTemplates() {
    for (var type : List.of(FlowInstanceEvent.EventType.APPROVED, FlowInstanceEvent.EventType.REJECTED)) {
      try (var fixture = new Fixture(true)) {
        var instance = fixture.source(type, "business-result", fixture::notification);
        fixture.receiver().projectInstance(instance, BINDING, () -> { });
        String name = type == FlowInstanceEvent.EventType.APPROVED ? "workflowApproved" : "workflowRejected";
        var occurrence = fixture.lastOccurrence;
        var source = fixture.notificationSource(ignored -> true);
        var notice = fixture.asConsumer(() -> source.read(occurrence, fixture.delivery(name))).orElseThrow();
        assertEquals(List.of("starter"), notice.employeeIds());
        assertEquals(instance.getEventTime(), notice.payload().get("eventTime"));
        assertEquals(type.name(), notice.payload().get("eventType"));
        assertEquals("business-result", notice.payload().get("outcome"));
        assertTrue(fixture.asConsumer(() -> source.eligibility(occurrence, fixture.delivery(name), "starter")).applicable());
        verifyNoInteractions(fixture.tasks, fixture.definitions);
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final MockedStatic<DataModelCenter> center;
    private final DataSourceTransactionManager manager;
    private final TransactionTemplate transaction;
    private final FlowEventOutboxService outbox = new FlowEventOutboxService();
    private final FlowInstanceFactStateService states = new FlowInstanceFactStateService();
    private final ApprovalCaseService cases = new ApprovalCaseService();
    private final WorkflowBusinessEventProducer producer = new WorkflowBusinessEventProducer();
    private final BusinessModelProvider owner;
    private final BusinessModelBehaviorProvider behaviors;
    private BusinessModelBehavior publishedBehavior;
    private final com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime notificationRuntime = mock(com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime.class);
    private WorkflowBusinessNotificationOwnership notificationOwnership;
    private final ApprovalBusinessBindingValidator bindings = mock(ApprovalBusinessBindingValidator.class, CALLS_REAL_METHODS);
    private final DefaultBusinessEventPort port;
    private com.ouroboros.businessmodel.event.BusinessEventOccurrence<?> lastOccurrence;
    private final com.ouroboros.workflow.approval.FlowTaskService tasks = mock(com.ouroboros.workflow.approval.FlowTaskService.class);
    private final com.ouroboros.workflow.approval.FlowDefinitionService definitions = mock(com.ouroboros.workflow.approval.FlowDefinitionService.class);
    private ApprovalCaseService.Submission submission;
    private String instance = "instance";
    private org.flowable.engine.ProcessEngine engine;
    private String deploymentId;

    Fixture() { this(false); }

    Fixture(boolean notifications) {
      String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      boolean mysql = url != null && !url.isBlank();
      var source = mysql ? new DriverManagerDataSource(url, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
          System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD")) : new DriverManagerDataSource("jdbc:h2:mem:projection" + UUID.randomUUID()
          + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
      manager = new DataSourceTransactionManager(source);
      transaction = new TransactionTemplate(manager);
      var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.event.store").values());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.workflow.store").values());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").values().stream()
          .filter(meta -> List.of("FlowEventOutbox", "FlowInstanceFactState").contains(meta.getFullName())).toList());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.lifecycle").values().stream()
          .filter(meta -> "ApprovalCase".equals(meta.getFullName())).toList());
      var data = new DataModelMeta();
      data.setNamespace("cases"); data.setName("Document"); data.setRawName("projection_document");
      data.setPrimaryKeys(List.of("id"));
      var id = new DataModelFieldMeta(); id.setName("id"); id.setRawName("id"); id.setType("Long");
      var status = new DataModelFieldMeta(); status.setName("status"); status.setRawName("status"); status.setType("String");
      data.setFields(List.of(id, status));
      metas.add(data);
      String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
      metas.forEach(meta -> meta.setRawName(meta.getRawName() + "_" + suffix));
      var station = new SqlDataStation("projection", source, metas, MigrationStrategy.AUTO, mysql ? "MySQL" : "H2");
      var jdbc = new JdbcTemplate(source);
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      station.getDataModelList().forEach(model -> models.put(model.getFullName(), model));
      center = mockStatic(DataModelCenter.class);
      center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call -> Optional.ofNullable(models.get(call.getArgument(0))));
      center.when(DataModelCenter::getDataModelMap).thenReturn(models);
      center.when(DataModelCenter::hasSnapshot).thenReturn(true);
      var meta = new DefaultBusinessModelMeta(); meta.setNamespace("cases"); meta.setName("Document"); meta.setIsEnabled(true);
      var field = new BusinessModelFieldMeta(); field.setName("id"); field.setType("整数数字"); field.setIsPrimaryKey(true);
      meta.setFields(List.of(field));
      meta.setExtraProp("workflow", Map.of("isEnabled", true, "bindings", List.of(Map.of("processId", FLOW))));
      if (notifications) {
        meta.setEventSubscriptions(java.util.Arrays.stream(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule.values()).map(rule -> {
          var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT); subscription.setName(rule.subscriptionName());
          subscription.getSourceEvent().setModelFullName(MODEL); subscription.getSourceEvent().setEventName(rule.eventName()); subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("businessNotificationListener");
          subscription.getTarget().setConfig(rule.listenerConfig());
          return subscription;
        }).toList());
      }
      var model = new DefaultBusinessModel(meta, modelProvider(Map.of()));
      owner = modelProvider(Map.of(MODEL, model));
      publishedBehavior = model.getBehavior();
      var subscriptions = model.getBehavior().getEvents().values().stream().map(event -> {
        var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
        subscription.setName(event.getName()); subscription.getSourceEvent().setModelFullName(MODEL); subscription.getSourceEvent().setEventName(event.getName());
        subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("audit"); return subscription;
      }).toList();
      var consumer = new BusinessModelBehavior("audit.History", List.of(), List.of(), subscriptions, List.of(), "revision");
      behaviors = new BusinessModelBehaviorProvider() {
        public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) {
          return Optional.ofNullable(getBusinessModelBehaviorMap().get(name));
        }
        public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, publishedBehavior, "audit.History", consumer); }
      };
      when(notificationRuntime.resolveUser(anyString())).thenReturn(Optional.of("notification-service"));
      when(notificationRuntime.inspectAttempt(anyString(), anyString())).thenReturn(Optional.of(
          new com.ouroboros.businessmodel.event.BusinessEventDeliveryAttempt("revision", null, "businessNotificationListener", "1",
              com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT)));
      notificationOwnership = new WorkflowBusinessNotificationOwnership(owner, behaviors, () -> notificationRuntime);
      org.springframework.test.util.ReflectionTestUtils.setField(outbox, "notificationOwnership", notificationOwnership);
      var recorder = new DataModelBusinessEventOccurrenceRecorder(behaviors, ignored -> { });
      port = new DefaultBusinessEventPort(behaviors, () -> List.of(producer), occurrence -> { recorder.record(occurrence); lastOccurrence = occurrence; }, manager);
      models.get(MODEL).insert(Map.of("id", 42L, "status", "draft")).get();
      submission = transaction.execute(tx -> cases.reserveSubmission(MODEL, "42", FLOW));
    }

    WorkflowBusinessFactProjection receiver() {
      return receiver(new WorkflowBusinessFactStore(), outbox);
    }

    WorkflowBusinessFactProjection receiver(WorkflowBusinessFactStore store, FlowEventOutboxService source) {
      return new WorkflowBusinessFactProjection(owner, source, cases, states, store, producer, port, manager, () -> bindings, () -> notificationOwnership);
    }

    <T> T inThread(Supplier<T> operation) {
      try (var scoped = mockStatic(DataModelCenter.class)) {
        scoped.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call -> Optional.ofNullable(models.get(call.getArgument(0))));
        scoped.when(DataModelCenter::getDataModelMap).thenReturn(models);
        scoped.when(DataModelCenter::hasSnapshot).thenReturn(true);
        return operation.get();
      }
    }

    FlowInstanceEvent source(FlowInstanceEvent.EventType type) { return source(type, null); }

    void startLegacyEngine() {
      var config = new org.flowable.spring.SpringProcessEngineConfiguration();
      config.setDataSource(manager.getDataSource()); config.setTransactionManager(manager);
      config.setDatabaseSchemaUpdate("true"); config.setAsyncExecutorActivate(false);
      config.setDisableIdmEngine(true); config.setDisableEventRegistry(true);
      engine = config.buildProcessEngine();
      var deployment = engine.getRepositoryService().createDeployment().addString("approval.bpmn20.xml", """
          <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="legacy">
            <process id="approval" isExecutable="true">
              <startEvent id="start"/><sequenceFlow id="toTask" sourceRef="start" targetRef="task"/>
              <userTask id="task"/><sequenceFlow id="toEnd" sourceRef="task" targetRef="end"/><endEvent id="end"/>
            </process>
          </definitions>
          """).deploy();
      deploymentId = deployment.getId();
      instance = transaction.execute(tx -> {
        var started = engine.getRuntimeService().startProcessInstanceByKey(FLOW, Map.of("businessRecordType", MODEL, "businessRecordId", "42",
            "approvalCaseId", submission.approvalCaseId(), "submissionRound", submission.submissionRound(), "callbackFlow", BINDING.callbackFlow()));
        cases.bindExecution(submission.approvalCaseId(), submission.submissionRound(), started.getId());
        return started.getId();
      });
      var verifier = new com.ouroboros.workflow.approval.flowable.service.FlowableBusinessBindingValidator(engine.getRuntimeService(),
          engine.getTaskService(), engine.getRepositoryService(), engine.getHistoryService(), cases,
          new com.ouroboros.workflow.approval.flowable.event.FlowableSourceTransaction(config));
      doAnswer(call -> verifier.inspectInstance(call.getArgument(0), call.getArgument(1)))
          .when(bindings).inspectInstance(anyString(), anyString());
    }

    void completeLegacy(String outcome) {
      transaction.executeWithoutResult(tx -> {
        engine.getRuntimeService().setVariable(instance, com.ouroboros.workflow.approval.flowable.transpiler.Constants.Variable.PROCESS_OUTCOME, outcome);
        engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(instance).singleResult().getId());
      });
    }

    FlowInstanceEvent legacy(FlowInstanceEvent.EventType type, String outcome) {
      var legacy = event(type, outcome);
      transaction.executeWithoutResult(tx -> outbox.enqueue(legacy));
      return legacy;
    }

    FlowInstanceEvent source(FlowInstanceEvent.EventType type, String outcome) { return source(type, outcome, ignored -> { }); }

    FlowInstanceEvent source(FlowInstanceEvent.EventType type, String outcome, java.util.function.Consumer<FlowInstanceEvent> configure) {
      var event = event(type, outcome);
      configure.accept(event);
      transaction.executeWithoutResult(tx -> {
        states.restoreIdentity(event); cases.lockForSource(event); states.advance(event); cases.applyInstanceEvent(event); outbox.enqueue(event);
      });
      return event;
    }

    FlowInstanceEvent event(FlowInstanceEvent.EventType type, String outcome) {
      var event = new FlowInstanceEvent(); event.setEventFactId(UUID.randomUUID().toString()); event.setFlowKey(FLOW);
      event.setInstanceId(instance); event.setEventType(type); event.setOutcome(outcome); event.setOccurredAt(TIME);
      event.setBusinessContext(new FlowInstanceEvent.BusinessContext(MODEL, "42")); event.setCallbackFlow(BINDING.callbackFlow());
      event.setApprovalCaseId(submission.approvalCaseId()); event.setSubmissionRound(submission.submissionRound());
      return event;
    }

    FlowTaskEvent task(TaskState state, String action) { return task(state, action, ignored -> { }); }

    FlowTaskEvent task(TaskState state, String action, java.util.function.Consumer<FlowTaskEvent> configure) {
      var task = new FlowTaskEvent(); task.setEventFactId(UUID.randomUUID().toString()); task.setProjectionVersion(1);
      task.setFlowKey(FLOW); task.setInstanceId(instance); task.setTaskId("task"); task.setState(state); task.setOccurredAt(TIME);
      task.setBusinessContext(new FlowInstanceEvent.BusinessContext(MODEL, "42")); task.setCallbackFlow(BINDING.callbackFlow());
      task.setApprovalCaseId(submission.approvalCaseId()); task.setSubmissionRound(submission.submissionRound());
      if (action != null) { task.setFactKind(FlowTaskEvent.FactKind.ACTION_COMPLETED); task.setActionName(action); }
      configure.accept(task);
      transaction.executeWithoutResult(tx -> outbox.enqueue(task));
      return task;
    }

    DefaultBusinessWorkflowNotificationSource notificationSource(com.ouroboros.businessmodel.event.BusinessEventDisclosure disclosure) {
      return new DefaultBusinessWorkflowNotificationSource(owner, behaviors, disclosure, new WorkflowBusinessFactStore(), outbox, tasks, definitions);
    }

    com.ouroboros.businessmodel.event.BusinessEventDeliveryContext delivery(String eventName) {
      var subscription = behaviors.getBusinessModelBehavior(MODEL).orElseThrow().getEventSubscriptions().get(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.Rule.forEvent(eventName).orElseThrow().subscriptionName());
      return new com.ouroboros.businessmodel.event.BusinessEventDeliveryContext("delivery", MODEL, subscription.getName(),
          "notification-service", 1, instance, null, subscription.getTarget().getConfig());
    }

    void notification(FlowTaskEvent event) {
      var configuration = new com.ouroboros.workflow.approval.definition.FlowDefinition.Notification();
      configuration.setTaskCreatedTemplate("task-template"); configuration.setTaskTimeoutRemindTemplate("task-template");
      event.setNotification(configuration); event.setCandidateUsers(List.of("bob", "alice", "alice"));
      event.setNodeId("review"); event.setTaskName("Review");
      event.setEventTime(java.time.LocalDateTime.of(2026, 10, 30, 12, 0));
    }

    void notification(FlowInstanceEvent event) {
      var configuration = new com.ouroboros.workflow.approval.definition.FlowDefinition.Notification();
      configuration.setInstanceCompletedTemplate("result-template"); configuration.setInstanceRejectedTemplate("result-template");
      event.setNotification(configuration); event.setStarter("starter");
      event.setEventTime(java.time.LocalDateTime.of(2026, 10, 30, 12, 0));
    }

    <T> T asConsumer(Supplier<T> operation) {
      var user = mock(User.class); when(user.getUserIdentity()).thenReturn("notification-service");
      return CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user), operation);
    }

    DataModel facts() { return models.get(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME); }
    String status() { return models.get(MODEL).get(42L).get().get("status").toString(); }
    void status(String value) { models.get(MODEL).update(42L, Map.of("status", value)).get(); }

    void assertCounts(int facts, int deliveries, int receipts) {
      assertEquals((long) facts, facts().count(Map.of()).get());
      assertEquals((long) deliveries, models.get(BusinessEventOutbox.MODEL_FULL_NAME).count(Map.of()).get());
      assertEquals((long) receipts, models.get("WorkflowBusinessFactReceipt").count(Map.of()).get());
    }

    @Override
    public void close() {
      try { if (engine != null) { engine.getRepositoryService().deleteDeployment(deploymentId, true); engine.close(); } }
      finally { center.close(); }
    }
  }

  private static BusinessModelProvider modelProvider(Map<String, BusinessModel> models) {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.ofNullable(models.get(name)); }
      public Map<String, BusinessModel> getBusinessModelMap() { return models; }
    };
  }
}
