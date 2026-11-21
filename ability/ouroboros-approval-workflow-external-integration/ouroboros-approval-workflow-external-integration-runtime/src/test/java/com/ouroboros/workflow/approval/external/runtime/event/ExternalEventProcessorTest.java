package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

import io.vavr.control.Try;

/**
 * M9 外部事件处理器规则测试：终态闭环、运行中、未知状态、乱序、绑定缺失、重试与死信。
 */
class ExternalEventProcessorTest {

  private Map<String, ExternalEventInbox> store;
  private ExternalEventInboxService inboxService;
  private ExternalResultBindingService resultBindingService;
  private ExternalInstanceBindingService instanceBindingService;
  private ExternalTaskBindingService taskBindingService;
  private WorkflowEngineMappingService mappingService;
  private InstanceEventDispatcher eventDispatcher;
  private TaskEventDispatcher taskEventDispatcher;
  private ExternalStartFactService startFactService;
  private ExternalEventProcessor processor;

  @BeforeEach
  void setUp() {
    store = new LinkedHashMap<>();
    inboxService = mock(ExternalEventInboxService.class);
    when(inboxService.findByEventId(any(), any(), any())).thenAnswer(inv -> store.values().stream()
        .filter(row -> row.getAdapterId().equals(inv.getArgument(0))
            && row.getConnectionId().equals(inv.getArgument(1))
            && row.getEventId().equals(inv.getArgument(2)))
        .findFirst());
    when(inboxService.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    when(inboxService.claim(any(), any(), anyInt())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      ExternalEventInbox row = store.get(id);
      if (row == null || !ExternalEventInbox.STATUS_PENDING.equals(row.getStatus())) {
        return Optional.empty();
      }
      String claimToken = UUID.randomUUID().toString();
      row.setStatus(ExternalEventInbox.STATUS_PROCESSING);
      row.setClaimOwner(inv.getArgument(1));
      row.setClaimToken(claimToken);
      row.setClaimUntil(LocalDateTime.now().plusSeconds(((Number) inv.getArgument(2)).longValue()));
      row.setLastError(null);
      row.setUpdatedAt(LocalDateTime.now());
      return Optional.of(new ExternalEventInboxService.Claim(row, claimToken));
    });
    when(inboxService.markProcessed(any(), any())).thenAnswer(inv -> completeClaim(
        inv.getArgument(0), inv.getArgument(1), ExternalEventInbox.STATUS_PROCESSED, null));
    when(inboxService.markStale(any(), any())).thenAnswer(inv -> completeClaim(
        inv.getArgument(0), inv.getArgument(1), ExternalEventInbox.STATUS_STALE, null));
    when(inboxService.markFailure(any(), any(), any())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      String token = inv.getArgument(1);
      String error = inv.getArgument(2);
      ExternalEventInbox row = store.get(id);
      if (!ownsClaim(row, token)) {
        return false;
      }
      int nextRetry = row.getRetryCount() + 1;
      row.setStatus(nextRetry > ExternalEventInbox.MAX_RETRY_COUNT
          ? ExternalEventInbox.STATUS_DEAD
          : ExternalEventInbox.STATUS_FAILED);
      row.setRetryCount(nextRetry);
      row.setLastError(error);
      row.setClaimOwner(null);
      row.setClaimToken(null);
      row.setClaimUntil(null);
      row.setUpdatedAt(LocalDateTime.now());
      return true;
    });

    resultBindingService = mock(ExternalResultBindingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    when(instanceBindingService.updateStatus(any(), any(), any(), any(), any(), any()))
        .thenReturn(Try.success(true));
    taskBindingService = mock(ExternalTaskBindingService.class);
    when(taskBindingService.updateStatus(any(), any(), any(), any(), any()))
        .thenReturn(Try.success(true));
    mappingService = mock(WorkflowEngineMappingService.class);
    eventDispatcher = mock(InstanceEventDispatcher.class);
    taskEventDispatcher = mock(TaskEventDispatcher.class);
    startFactService = mock(ExternalStartFactService.class);
    processor = new ExternalEventProcessor(
        inboxService, resultBindingService, instanceBindingService, taskBindingService,
        mappingService, eventDispatcher, taskEventDispatcher, startFactService);

    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    when(resultBindingService.markFinished(any(), any(), any(), any(), any()))
        .thenAnswer(inv -> Try.success(true));
    when(resultBindingService.updateRevision(any(), any(), any(), any()))
        .thenAnswer(inv -> Try.success(true));
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mappingWithResultMappings()));
  }

  private boolean completeClaim(String id, String token, String status, String error) {
    ExternalEventInbox row = store.get(id);
    if (!ownsClaim(row, token)) {
      return false;
    }
    row.setStatus(status);
    row.setLastError(error);
    row.setClaimOwner(null);
    row.setClaimToken(null);
    row.setClaimUntil(null);
    if (ExternalEventInbox.STATUS_PROCESSED.equals(status) || ExternalEventInbox.STATUS_STALE.equals(status)) {
      row.setProcessedAt(LocalDateTime.now());
    }
    row.setUpdatedAt(LocalDateTime.now());
    return true;
  }

  private boolean ownsClaim(ExternalEventInbox row, String token) {
    return row != null
        && ExternalEventInbox.STATUS_PROCESSING.equals(row.getStatus())
        && token != null
        && token.equals(row.getClaimToken());
  }

  @Test
  void terminalApprovedClosesBindingAndPublishesEvent() {
    ingestRow("evt-1", "ext-1", "APPROVED", null);

    Try<Void> result = processor.processByEnvelope(envelope("evt-1", "APPROVED", null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-1").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(resultBindingService).markFinished("tracking-1", "approved", null, null, "evt-1");
    verify(startFactService).releaseBusinessReservation(
        "default", "flow-a", "supplier", "sup-1", "tracking-1");
    ArgumentCaptor<FlowInstanceEvent> captor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(captor.capture());
    FlowInstanceEvent event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo(EventType.APPROVED);
    assertThat(event.getInstanceId()).isEqualTo("tracking-1");
    assertThat(event.getBusinessContext())
        .isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
  }

  @Test
  void runningStatusOnlyUpdatesRevision() {
    ingestRow("evt-2", "ext-1", "RUNNING", null);

    Try<Void> result = processor.processByEnvelope(envelope("evt-2", "RUNNING", null));

    assertThat(result.isSuccess()).isTrue();
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(resultBindingService).updateRevision("tracking-1", null, null, "evt-2");
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void unknownStatusFailsWithoutCompletingBinding() {
    ingestRow("evt-3", "ext-1", "WEIRD", null);

    Try<Void> result = processor.processByEnvelope(envelope("evt-3", "WEIRD", null));

    assertThat(result.isFailure()).isTrue();
    assertThat(store.get("row-evt-3").getStatus()).isEqualTo(ExternalEventInbox.STATUS_FAILED);
    assertThat(store.get("row-evt-3").getRetryCount()).isEqualTo(1);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void staleSequenceIsMarkedStaleWithoutOverwrite() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalRevision("seq-9");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-4", "ext-1", "APPROVED", "seq-5");

    Try<Void> result = processor.processByEnvelope(envelope("evt-4", "APPROVED", "seq-5"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-4").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void numericSequenceComparesByNumber() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalRevision("9");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-4b", "ext-1", "APPROVED", "10");

    Try<Void> result = processor.processByEnvelope(envelope("evt-4b", "APPROVED", "10"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-4b").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(resultBindingService).markFinished("tracking-1", "approved", "10", null, "evt-4b");
  }

  @Test
  void numericSequenceOlderThanStoredRevisionIsStale() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalRevision("10");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-4c", "ext-1", "APPROVED", "9");

    Try<Void> result = processor.processByEnvelope(envelope("evt-4c", "APPROVED", "9"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-4c").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void eventWithoutSequenceUsesExternalEventTimeForStaleProtection() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalEventTime(LocalDateTime.parse("2026-08-26T10:00:00"));
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRowWithEventTime("evt-4d", "ext-1", "APPROVED", null, "2026-08-26T09:59:59");

    Try<Void> result = processor.processByEnvelope(envelopeWithEventTime(
        "evt-4d", "APPROVED", null, "2026-08-26T09:59:59"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-4d").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void equalEventTimeWithoutSequenceIsIdempotent() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalEventTime(LocalDateTime.parse("2026-08-26T10:00:00"));
    binding.setLastExternalEventId("evt-4e");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRowWithEventTime("evt-4e", "ext-1", "APPROVED", null, "2026-08-26T10:00:00");

    Try<Void> result = processor.processByEnvelope(envelopeWithEventTime(
        "evt-4e", "APPROVED", null, "2026-08-26T10:00:00"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-4e").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void newerEventTimeWithoutSequenceUpdatesBindingAndSavesEventTime() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalEventTime(LocalDateTime.parse("2026-08-26T10:00:00"));
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRowWithEventTime("evt-4f", "ext-1", "RUNNING", null, "2026-08-26T10:00:01");

    Try<Void> result = processor.processByEnvelope(envelopeWithEventTime(
        "evt-4f", "RUNNING", null, "2026-08-26T10:00:01"));

    assertThat(result.isSuccess()).isTrue();
    verify(resultBindingService).updateRevision(
        "tracking-1", null, LocalDateTime.parse("2026-08-26T10:00:01"), "evt-4f");
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void missingBindingFailsWithRetry() {
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-x"))
        .thenReturn(Optional.empty());
    ingestRow("evt-5", "ext-x", "APPROVED", null);

    Try<Void> result = processor.processByEnvelope(envelope("evt-5", "APPROVED", null));

    assertThat(result.isFailure()).isTrue();
    assertThat(store.get("row-evt-5").getStatus()).isEqualTo(ExternalEventInbox.STATUS_FAILED);
    assertThat(store.get("row-evt-5").getLastError()).contains("绑定不存在");
  }

  @Test
  void repeatedFailuresReachDeadLetter() {
    ingestRow("evt-6", "ext-1", "WEIRD", null);
    ExternalEventInbox row = store.get("row-evt-6");
    for (int i = 1; i <= ExternalEventInbox.MAX_RETRY_COUNT + 1; i++) {
      row.setStatus(ExternalEventInbox.STATUS_PENDING);
      processor.processByEnvelope(envelope("evt-6", "WEIRD", null));
    }
    assertThat(store.get("row-evt-6").getStatus()).isEqualTo(ExternalEventInbox.STATUS_DEAD);
  }

  @Test
  void fullProcessTerminalApprovedClosesInstanceBindingAndPublishesEvent() {
    stubInstanceBinding("ext-2");
    ingestRow("evt-7", "ext-2", "APPROVED", null);

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-7", "APPROVED", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-7").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(instanceBindingService).updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_FINISHED, "approved", null, null, "evt-7");
    verify(startFactService).releaseBusinessReservation(
        "default", "flow-a", "supplier", "sup-1", "local-inst-1");
    ArgumentCaptor<FlowInstanceEvent> captor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(captor.capture());
    FlowInstanceEvent event = captor.getValue();
    assertThat(event.getEventType()).isEqualTo(EventType.APPROVED);
    assertThat(event.getInstanceId()).isEqualTo("local-inst-1");
    assertThat(event.getFlowKey()).isEqualTo("flow-a");
    assertThat(event.getBusinessContext())
        .isEqualTo(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    assertThat(event.getCallbackFlow()).isEqualTo("cb-1");
  }

  @Test
  void fullProcessRunningUpdatesInstanceBindingWithoutEvent() {
    stubInstanceBinding("ext-2");
    ingestRow("evt-8", "ext-2", "RUNNING", null);

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-8", "RUNNING", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    verify(instanceBindingService).updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_RUNNING, null, null, null, "evt-8");
    verify(eventDispatcher, never()).dispatchEvent(any());
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void fullProcessRunningAfterSuspensionPublishesResumedEvent() {
    ExternalInstanceBinding binding = instanceBinding("ext-2");
    binding.setStatus(ExternalInstanceBinding.STATUS_SUSPENDED);
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-2"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-8-resume", "ext-2", "RUNNING", null);

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-8-resume", "RUNNING", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    verify(instanceBindingService).updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_RUNNING, null, null, null, "evt-8-resume");
    ArgumentCaptor<FlowInstanceEvent> eventCaptor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo(EventType.RESUMED);
    assertThat(eventCaptor.getValue().getInstanceId()).isEqualTo("local-inst-1");
    assertThat(eventCaptor.getValue().getEventFactId()).isEqualTo("evt-8-resume");
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void fullProcessUnknownStatusFailsWithRetry() {
    stubInstanceBinding("ext-2");
    ingestRow("evt-9", "ext-2", "WEIRD", null);

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-9", "WEIRD", null, null, "ext-2"));

    assertThat(result.isFailure()).isTrue();
    assertThat(store.get("row-evt-9").getStatus()).isEqualTo(ExternalEventInbox.STATUS_FAILED);
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void fullProcessStaleSequenceIsMarkedStale() {
    ExternalInstanceBinding binding = instanceBinding("ext-2");
    binding.setExternalRevision("seq-9");
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-2"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-10", "ext-2", "APPROVED", "seq-5");

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-10", "APPROVED", "seq-5", null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-10").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(instanceBindingService, never()).updateStatus(any(), any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void taskEventCreatesTaskBindingAndDispatchesFlowTaskEvent() {
    stubInstanceBinding("ext-2");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "task-1"))
        .thenReturn(Optional.empty());
    when(taskBindingService.create(any())).thenAnswer(inv -> {
      ExternalTaskBinding created = inv.getArgument(0);
      created.setLocalTaskId("local-task-1");
      return Try.success(created);
    });
    LocalDateTime eventTime = LocalDateTime.parse("2026-08-26T10:00:01");
    ingestRowWithEventTime("evt-11", "ext-2", "agree", null, eventTime.toString());
    ExternalEventInbox row = store.get("row-evt-11");
    row.setExternalTaskId("task-1");

    Try<Void> result = processor.processByEnvelope(
        new ExternalCallbackEnvelope(
            "evt-11", "fake-adapter", "conn-1", "ext-2", "task-1", null, eventTime.toString(), null, "agree", null));

    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<ExternalTaskBinding> createdCaptor = ArgumentCaptor.forClass(ExternalTaskBinding.class);
    verify(taskBindingService).create(createdCaptor.capture());
    assertThat(createdCaptor.getValue().getLocalInstanceId()).isEqualTo("local-inst-1");
    assertThat(createdCaptor.getValue().getExternalTaskId()).isEqualTo("task-1");
    assertThat(createdCaptor.getValue().getExternalEventTime()).isNull();
    verify(taskBindingService).updateStatus(
        "local-task-1", ExternalTaskBinding.STATUS_APPROVED, null, eventTime, "evt-11");
    ArgumentCaptor<FlowTaskEvent> eventCaptor = ArgumentCaptor.forClass(FlowTaskEvent.class);
    verify(taskEventDispatcher).dispatchEvent(eventCaptor.capture());
    FlowTaskEvent event = eventCaptor.getValue();
    assertThat(event.getTaskId()).isEqualTo("local-task-1");
    assertThat(event.getInstanceId()).isEqualTo("local-inst-1");
    assertThat(event.getState()).isEqualTo(TaskState.APPROVED);
    assertThat(event.getEventFactId()).isEqualTo(row.getId());
    assertThat(event.getEventTime()).isEqualTo(eventTime);
  }

  @Test
  void existingTaskEventOutOfOrderIsMarkedStale() {
    stubInstanceBinding("ext-2");
    ExternalTaskBinding task = new ExternalTaskBinding();
    task.setLocalTaskId("local-task-1");
    task.setLocalInstanceId("local-inst-1");
    task.setScope("default");
    task.setMappingId("m-1");
    task.setMappingVersion(1);
    task.setAdapterId("fake-adapter");
    task.setConnectionId("conn-1");
    task.setExternalRevision("seq-9");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "task-1"))
        .thenReturn(Optional.of(task));
    ingestRow("evt-12", "ext-2", "refuse", "seq-5");
    ExternalEventInbox row = store.get("row-evt-12");
    row.setExternalTaskId("task-1");

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-12", "refuse", "seq-5", "task-1", "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-12").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(taskBindingService, never()).updateStatus(any(), any(), any(), any(), any());
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void existingTaskEventUpdatesStatusAndDispatchesRejected() {
    stubInstanceBinding("ext-2");
    ExternalTaskBinding task = new ExternalTaskBinding();
    task.setLocalTaskId("local-task-1");
    task.setLocalInstanceId("local-inst-1");
    task.setScope("default");
    task.setMappingId("m-1");
    task.setMappingVersion(1);
    task.setAdapterId("fake-adapter");
    task.setConnectionId("conn-1");
    task.setExternalRevision("seq-9");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "task-1"))
        .thenReturn(Optional.of(task));
    ingestRow("evt-13", "ext-2", "refuse", "seq-90");
    ExternalEventInbox row = store.get("row-evt-13");
    row.setExternalTaskId("task-1");

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-13", "refuse", "seq-90", "task-1", "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    verify(taskBindingService, never()).create(any());
    verify(taskBindingService).updateStatus(
        "local-task-1", ExternalTaskBinding.STATUS_REJECTED, "seq-90", null, "evt-13");
    verify(instanceBindingService, never()).updateStatus(any(), any(), any(), any(), any(), any());
    verify(startFactService, never()).releaseBusinessReservation(any(), any(), any(), any(), any());
    ArgumentCaptor<FlowTaskEvent> eventCaptor = ArgumentCaptor.forClass(FlowTaskEvent.class);
    verify(taskEventDispatcher).dispatchEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getState()).isEqualTo(TaskState.REJECTED);
  }

  @Test
  void equalSequenceResultBindingIsIdempotent() {
    ExternalResultBinding binding = binding("tracking-1", "ext-1", "m-1");
    binding.setExternalRevision("seq-9");
    binding.setLastExternalEventId("evt-14");
    when(resultBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-1"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-14", "ext-1", "APPROVED", "seq-9");

    Try<Void> result = processor.processByEnvelope(envelope("evt-14", "APPROVED", "seq-9"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-14").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(resultBindingService, never()).markFinished(any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void equalSequenceInstanceBindingIsIdempotent() {
    ExternalInstanceBinding binding = instanceBinding("ext-2");
    binding.setExternalRevision("seq-9");
    binding.setLastExternalEventId("evt-15");
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", "ext-2"))
        .thenReturn(Optional.of(binding));
    ingestRow("evt-15", "ext-2", "APPROVED", "seq-9");

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-15", "APPROVED", "seq-9", null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-15").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(instanceBindingService, never()).updateStatus(any(), any(), any(), any(), any(), any());
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void equalSequenceExistingTaskIsIdempotent() {
    stubInstanceBinding("ext-2");
    ExternalTaskBinding task = new ExternalTaskBinding();
    task.setLocalTaskId("local-task-1");
    task.setLocalInstanceId("local-inst-1");
    task.setScope("default");
    task.setMappingId("m-1");
    task.setMappingVersion(1);
    task.setAdapterId("fake-adapter");
    task.setConnectionId("conn-1");
    task.setExternalRevision("seq-9");
    task.setLastExternalEventId("evt-16");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "task-1"))
        .thenReturn(Optional.of(task));
    ingestRow("evt-16", "ext-2", "agree", "seq-9");
    ExternalEventInbox row = store.get("row-evt-16");
    row.setExternalTaskId("task-1");

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-16", "agree", "seq-9", "task-1", "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-16").getStatus()).isEqualTo(ExternalEventInbox.STATUS_PROCESSED);
    verify(taskBindingService, never()).updateStatus(any(), any(), any(), any(), any());
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void fullProcessCustomStatusMappedViaResultMappings() {
    stubInstanceBinding("ext-2");
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mappingWithEntry(
        "AGREED", "APPROVED", "approved")));
    ingestRow("evt-17", "ext-2", "AGREED", null);

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-17", "AGREED", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    verify(instanceBindingService).updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_FINISHED, "approved", null, null, "evt-17");
    ArgumentCaptor<FlowInstanceEvent> captor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(EventType.APPROVED);
    assertThat(captor.getValue().getOutcome()).isEqualTo("approved");
  }

  @Test
  void fullProcessErrorTerminalPublishesFailedEvent() {
    stubInstanceBinding("ext-2");
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(mappingWithEntry(
        "ERROR", "FAILED", "failed")));
    ingestRow("evt-18", "ext-2", "ERROR", null);

    Try<Void> result = processor.processByEnvelope(envelopeFor("evt-18", "ERROR", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    verify(instanceBindingService).updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_FINISHED, "failed", null, null, "evt-18");
    ArgumentCaptor<FlowInstanceEvent> captor = ArgumentCaptor.forClass(FlowInstanceEvent.class);
    verify(eventDispatcher).dispatchEvent(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo(EventType.FAILED);
    assertThat(captor.getValue().getOutcome()).isEqualTo("failed");
  }

  @Test
  void resultTerminalCasMissMarksStaleWithoutPublishingEvent() {
    when(resultBindingService.markFinished(
        "tracking-1", "approved", null, null, "evt-cas-result"))
        .thenReturn(Try.success(false));
    ingestRow("evt-cas-result", "ext-1", "APPROVED", null);

    Try<Void> result = processor.processByEnvelope(envelope("evt-cas-result", "APPROVED", null));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-cas-result").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void instanceTerminalCasMissMarksStaleWithoutPublishingEvent() {
    stubInstanceBinding("ext-2");
    when(instanceBindingService.updateStatus(
        "local-inst-1", ExternalInstanceBinding.STATUS_FINISHED, "approved", null, null,
        "evt-cas-instance"))
        .thenReturn(Try.success(false));
    ingestRow("evt-cas-instance", "ext-2", "APPROVED", null);

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-cas-instance", "APPROVED", null, null, "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-cas-instance").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(eventDispatcher, never()).dispatchEvent(any());
  }

  @Test
  void taskStatusCasMissMarksStaleWithoutPublishingEvent() {
    stubInstanceBinding("ext-2");
    ExternalTaskBinding task = new ExternalTaskBinding();
    task.setLocalTaskId("local-task-1");
    task.setLocalInstanceId("local-inst-1");
    task.setScope("default");
    task.setMappingId("m-1");
    task.setMappingVersion(1);
    task.setAdapterId("fake-adapter");
    task.setConnectionId("conn-1");
    when(taskBindingService.findByExternalTask("fake-adapter", "conn-1", "task-1"))
        .thenReturn(Optional.of(task));
    when(taskBindingService.updateStatus(
        "local-task-1", ExternalTaskBinding.STATUS_APPROVED, null, null, "evt-cas-task"))
        .thenReturn(Try.success(false));
    ingestRow("evt-cas-task", "ext-2", "agree", null);
    store.get("row-evt-cas-task").setExternalTaskId("task-1");

    Try<Void> result = processor.processByEnvelope(
        envelopeFor("evt-cas-task", "agree", null, "task-1", "ext-2"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(store.get("row-evt-cas-task").getStatus()).isEqualTo(ExternalEventInbox.STATUS_STALE);
    verify(taskEventDispatcher, never()).dispatchEvent(any());
  }

  private WorkflowEngineMapping mappingWithEntry(String externalStatus, String systemEvent, String outcome) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-1");
    mapping.setVersion(1);
    mapping.setFlowKey("flow-a");
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    mapping.setResultMappingsJson("""
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","outcome":"cancelled"},
          {"externalStatus":"ERROR","outcome":"failed"},
          {"externalStatus":"%s","systemEvent":"%s","outcome":"%s"}
        ]
        """.formatted(externalStatus, systemEvent, outcome));
    return mapping;
  }

  private void stubInstanceBinding(String externalInstanceId) {
    when(instanceBindingService.findByExternalInstance("fake-adapter", "conn-1", externalInstanceId))
        .thenReturn(Optional.of(instanceBinding(externalInstanceId)));
  }

  private ExternalInstanceBinding instanceBinding(String externalInstanceId) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("local-inst-1");
    binding.setScope("default");
    binding.setFlowKey("flow-a");
    binding.setMappingId("m-1");
    binding.setMappingVersion(1);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setExternalInstanceId(externalInstanceId);
    binding.setBusinessRecordType("supplier");
    binding.setBusinessRecordId("sup-1");
    binding.setCallbackFlow("cb-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_RUNNING);
    return binding;
  }

  private void ingestRow(String eventId, String externalInstanceId, String resultCode, String sequence) {
    ingestRowWithEventTime(eventId, externalInstanceId, resultCode, sequence, null);
  }

  private void ingestRowWithEventTime(
      String eventId, String externalInstanceId, String resultCode, String sequence, String externalEventTime) {
    ExternalEventInbox row = new ExternalEventInbox();
    row.setId("row-" + eventId);
    row.setAdapterId("fake-adapter");
    row.setConnectionId("conn-1");
    row.setEventId(eventId);
    row.setExternalInstanceId(externalInstanceId);
    row.setSequence(sequence);
    row.setExternalEventTime(externalEventTime);
    row.setResultCode(resultCode);
    row.setStatus(ExternalEventInbox.STATUS_PENDING);
    row.setRetryCount(0);
    row.setReceivedAt(LocalDateTime.now());
    store.put(row.getId(), row);
  }

  private ExternalCallbackEnvelope envelope(String eventId, String resultCode, String sequence) {
    return envelopeFor(eventId, resultCode, sequence, null, "ext-1");
  }

  private ExternalCallbackEnvelope envelopeWithEventTime(
      String eventId, String resultCode, String sequence, String externalEventTime) {
    return new ExternalCallbackEnvelope(
        eventId, "fake-adapter", "conn-1", "ext-1", null, sequence, externalEventTime, null, resultCode, null);
  }

  private ExternalCallbackEnvelope envelopeFor(
      String eventId, String resultCode, String sequence, String externalTaskId, String externalInstanceId) {
    return new ExternalCallbackEnvelope(
        eventId, "fake-adapter", "conn-1", externalInstanceId, externalTaskId, sequence, null, null, resultCode, null);
  }

  private ExternalResultBinding binding(String trackingId, String externalInstanceId, String mappingId) {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId(trackingId);
    binding.setScope("default");
    binding.setFlowKey("flow-a");
    binding.setMappingId(mappingId);
    binding.setAdapterId("fake-adapter");
    binding.setConnectionId("conn-1");
    binding.setBusinessRecordType("supplier");
    binding.setBusinessRecordId("sup-1");
    binding.setExternalInstanceId(externalInstanceId);
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    return binding;
  }

  private WorkflowEngineMapping mappingWithResultMappings() {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-1");
    mapping.setVersion(1);
    mapping.setFlowKey("flow-a");
    mapping.setAdapterId("fake-adapter");
    mapping.setConnectionId("conn-1");
    mapping.setResultMappingsJson("""
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","systemEvent":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","systemEvent":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","systemEvent":"CANCELLED","outcome":"cancelled"}
        ]
        """);
    return mapping;
  }
}
