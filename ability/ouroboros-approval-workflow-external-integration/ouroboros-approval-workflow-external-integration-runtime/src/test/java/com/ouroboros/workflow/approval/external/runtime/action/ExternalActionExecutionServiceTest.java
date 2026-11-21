package com.ouroboros.workflow.approval.external.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalActionCommand;
import com.ouroboros.workflow.approval.external.ExternalActionResult;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalTaskRef;
import com.ouroboros.workflow.approval.external.ExternalWorkflowActionExecutor;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.binding.ActionStatusUpdate;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;
import com.ouroboros.workflow.approval.model.TaskState;

import io.vavr.control.Try;

class ExternalActionExecutionServiceTest {

  private ExternalActionFactService factService;
  private ConnectionAwareAdapterResolver adapterResolver;
  private ExternalTaskBindingService taskBindingService;
  private ExternalInstanceBindingService instanceBindingService;
  private ExternalResultBindingService resultBindingService;
  private TaskEventDispatcher taskEventDispatcher;
  private ExternalWorkflowActionExecutor executor;
  private ExternalActionExecutionService service;

  @BeforeEach
  void setUp() {
    factService = mock(ExternalActionFactService.class);
    adapterResolver = mock(ConnectionAwareAdapterResolver.class);
    taskBindingService = mock(ExternalTaskBindingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    resultBindingService = mock(ExternalResultBindingService.class);
    taskEventDispatcher = mock(TaskEventDispatcher.class);
    executor = mock(ExternalWorkflowActionExecutor.class);
    when(taskBindingService.findByLocalId("task-1")).thenReturn(Optional.of(taskBinding()));
    when(adapterResolver.resolvePort(
        anyString(), anyString(), eq(ExternalWorkflowActionExecutor.class))).thenReturn(executor);
    when(factService.reserve(any())).thenAnswer(inv -> {
      ExternalActionFact fact = inv.getArgument(0);
      fact.setStatus(ExternalActionFact.STATUS_EXECUTING);
      fact.setRemoteCallPhase(ExternalActionFact.PHASE_NOT_STARTED);
      fact.setClaimToken("claim-1");
      when(factService.findById(fact.getId())).thenReturn(Optional.of(fact));
      return Try.success(new ExternalActionFactService.Reservation(fact, true));
    });
    when(factService.markInFlight(any(), any())).thenReturn(true);
    when(factService.markReplied(any(), any(), any())).thenAnswer(inv -> {
      factService.findById(inv.getArgument(0)).orElseThrow().setRepliedAt(java.time.LocalDateTime.now());
      return true;
    });
    when(factService.markSucceeded(any(), any())).thenReturn(true);
    service = new ExternalActionExecutionService(
        factService,
        adapterResolver,
        taskBindingService,
        instanceBindingService,
        resultBindingService,
        taskEventDispatcher,
        mock(InstanceEventDispatcher.class),
        mock(ExternalStartFactService.class),
        null);
  }

  @Test
  void acceptedNonterminalTaskCommandProducesAStableFactEvenWhenItsStatusWasAlreadyApplied() {
    var binding = taskBinding();
    when(executor.execute(any())).thenReturn(Try.success(acceptedResult()));
    when(taskBindingService.applyActionStatus("task-1", ExternalTaskBinding.STATUS_CREATED))
        .thenReturn(Try.success(ActionStatusUpdate.ALREADY_APPLIED));
    when(taskBindingService.findByLocalId("task-1")).thenReturn(Optional.of(binding));
    var transfer = new ExternalActionRequest("transfer", "transfer", "user-1", "handover", null, null, 3,
        request().externalInstanceRef(), request().externalTaskRef());
    assertThat(service.executeTask(binding, transfer, ExternalTaskBinding.STATUS_CREATED).isSuccess()).isTrue();
    var commandFact = ArgumentCaptor.forClass(ExternalActionFact.class);
    verify(factService).reserve(commandFact.capture());
    var event = ArgumentCaptor.forClass(FlowTaskEvent.class);
    verify(taskEventDispatcher).enqueueEvent(event.capture());
    assertThat(event.getValue().getEventFactId()).isEqualTo(commandFact.getValue().getId());
    assertThat(event.getValue().getFactKind()).isEqualTo(FlowTaskEvent.FactKind.ACTION_COMPLETED);
    assertThat(event.getValue().getActionName()).isEqualTo("transfer");
    assertThat(event.getValue().getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void persistsFactBeforeRemoteCallAndUsesFactIdAsIdempotencyKey() {
    ExternalTaskBinding binding = taskBinding();
    when(executor.execute(any())).thenReturn(Try.success(acceptedResult()));
    when(taskBindingService.applyActionStatus("task-1", ExternalTaskBinding.STATUS_APPROVED))
        .thenReturn(Try.success(ActionStatusUpdate.UPDATED));
    when(taskBindingService.findByLocalId("task-1")).thenReturn(Optional.of(binding));

    Try<Void> result = service.executeTask(binding, request(), ExternalTaskBinding.STATUS_APPROVED);

    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<ExternalActionFact> factCaptor = ArgumentCaptor.forClass(ExternalActionFact.class);
    ArgumentCaptor<ExternalActionCommand> commandCaptor = ArgumentCaptor.forClass(ExternalActionCommand.class);
    InOrder order = inOrder(factService, executor);
    order.verify(factService).reserve(factCaptor.capture());
    order.verify(factService).markInFlight(factCaptor.getValue().getId(), "claim-1");
    order.verify(executor).execute(commandCaptor.capture());
    assertThat(commandCaptor.getValue().idempotencyKey()).isEqualTo(factCaptor.getValue().getId());
    verify(factService).markSucceeded(factCaptor.getValue().getId(), "claim-1");
    verify(taskEventDispatcher).enqueueEvent(any());
  }

  @Test
  void explicitRemoteRejectionIsNotTreatedAsSuccess() {
    ExternalTaskBinding binding = taskBinding();
    when(executor.execute(any())).thenReturn(Try.success(
        new ExternalActionResult(false, "DENIED", null, null, null)));
    when(factService.markRejected(any(), any(), any())).thenReturn(true);

    Try<Void> result = service.executeTask(binding, request(), ExternalTaskBinding.STATUS_APPROVED);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verify(factService).markRejected(any(), eq("claim-1"), any());
    verify(taskBindingService, never()).applyActionStatus(any(), any());
    verify(factService, never()).markSucceeded(any(), any());
  }

  @Test
  void terminalInstanceDoesNotCallRemoteExecutorAgain() {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("instance-1");
    binding.setStatus(ExternalInstanceBinding.STATUS_FINISHED);

    Try<Void> result = service.executeInstance(binding, request(),
        ExternalInstanceBinding.STATUS_FINISHED, "approved", null);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verifyNoInteractions(factService, executor, adapterResolver);
  }

  @Test
  void terminalResultDoesNotCallRemoteExecutorAgain() {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId("tracking-1");
    binding.setStatus(ExternalResultBinding.STATUS_FAILED);

    Try<Void> result = service.executeResult(binding, request(), "cancelled", EventType.CANCELLED);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verifyNoInteractions(factService, executor, adapterResolver);
  }

  @Test
  void localTerminalConflictRecordsConflictAfterRemoteAcceptance() {
    ExternalTaskBinding binding = taskBinding();
    when(executor.execute(any())).thenReturn(Try.success(acceptedResult()));
    when(taskBindingService.applyActionStatus("task-1", ExternalTaskBinding.STATUS_APPROVED))
        .thenReturn(Try.success(ActionStatusUpdate.CONFLICT));
    when(factService.markConflict(any(), any(), any())).thenReturn(true);

    Try<Void> result = service.executeTask(binding, request(), ExternalTaskBinding.STATUS_APPROVED);

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).contains(EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT);
    verify(factService).markConflict(any(), eq("claim-1"), any());
    verify(factService, never()).markSucceeded(any(), any());
    verify(taskEventDispatcher, never()).enqueueEvent(any());
  }

  @Test
  void repliedRecoveryCompletesOnlyLocalState() throws Exception {
    ExternalActionFact fact = recoverableFact("fact-replied", ExternalActionFact.PHASE_REPLIED);
    when(taskBindingService.applyActionStatus("task-1", ExternalTaskBinding.STATUS_APPROVED))
        .thenReturn(Try.success(ActionStatusUpdate.ALREADY_APPLIED));

    Try<Void> result = service.recover(
        new ExternalActionFactService.RecoveryClaim(fact, "recovery-claim"));

    assertThat(result.isSuccess()).isTrue();
    verifyNoInteractions(executor);
    verify(adapterResolver, never()).resolvePort(anyString(), anyString(), any());
    verify(factService).markSucceeded("fact-replied", "recovery-claim");
    var event = ArgumentCaptor.forClass(FlowTaskEvent.class);
    verify(taskEventDispatcher).enqueueEvent(event.capture());
    assertThat(event.getValue().getEventFactId()).isEqualTo("fact-replied");
  }

  @Test
  void unknownOrInFlightRecoveryReusesOriginalFactIdForRemoteIdempotency() throws Exception {
    ExternalActionFact fact = recoverableFact("fact-unknown", ExternalActionFact.PHASE_IN_FLIGHT);
    when(executor.execute(any())).thenReturn(Try.success(acceptedResult()));
    when(taskBindingService.applyActionStatus("task-1", ExternalTaskBinding.STATUS_APPROVED))
        .thenReturn(Try.success(ActionStatusUpdate.ALREADY_APPLIED));

    Try<Void> result = service.recover(
        new ExternalActionFactService.RecoveryClaim(fact, "recovery-claim"));

    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<ExternalActionCommand> commandCaptor = ArgumentCaptor.forClass(ExternalActionCommand.class);
    verify(executor).execute(commandCaptor.capture());
    assertThat(commandCaptor.getValue().idempotencyKey()).isEqualTo("fact-unknown");
    verify(factService).markInFlight("fact-unknown", "recovery-claim");
    verify(factService).markSucceeded("fact-unknown", "recovery-claim");
  }

  private ExternalTaskBinding taskBinding() {
    ExternalTaskBinding binding = new ExternalTaskBinding();
    binding.setLocalTaskId("task-1");
    binding.setLocalInstanceId("instance-1");
    binding.setAdapterId("adapter-1");
    binding.setConnectionId("connection-1");
    binding.setMappingVersion(3);
    binding.setExternalTaskId("external-task-1");
    binding.setStatus(ExternalTaskBinding.STATUS_CREATED);
    return binding;
  }

  private ExternalActionRequest request() {
    return new ExternalActionRequest(
        "approve", "APPROVE", "user-1", "ok", null, null, 3,
        new ExternalInstanceRef("external-instance-1", "connection-1"),
        new ExternalTaskRef("external-task-1", "external-instance-1", "connection-1"));
  }

  private ExternalActionResult acceptedResult() {
    return new ExternalActionResult(true, "APPROVED", "approved", null, null);
  }

  private ExternalActionFact recoverableFact(String factId, String phase) throws Exception {
    when(taskBindingService.findByLocalId("task-1")).thenReturn(Optional.of(taskBinding()));
    ExternalActionCommand command = new ExternalActionCommand(
        "approve", "APPROVE", "user-1", "ok", null, null, factId, 3,
        new ExternalInstanceRef("external-instance-1", "connection-1"),
        new ExternalTaskRef("external-task-1", "external-instance-1", "connection-1"));
    ExternalActionFact fact = new ExternalActionFact();
    fact.setId(factId);
    fact.setTargetType(ExternalActionFact.TARGET_TASK);
    fact.setTargetLocalId("task-1");
    fact.setTargetStatus(ExternalTaskBinding.STATUS_APPROVED);
    fact.setCanonicalAction("approve");
    fact.setAdapterId("adapter-1");
    fact.setConnectionId("connection-1");
    fact.setMappingVersion(3);
    fact.setStatus(ExternalActionFact.STATUS_EXECUTING);
    fact.setRemoteCallPhase(phase);
    fact.setCommandJson(new ObjectMapper().writeValueAsString(command));
    when(factService.findById(factId)).thenReturn(Optional.of(fact));
    return fact;
  }
}
