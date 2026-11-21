package com.ouroboros.workflow.approval.runtime.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.runtime.testutil.TestObjectProviders;

/**
 * 业务回调直连监听器测试：直连路由（callbackFlow/recordType）和统一载荷。
 */
class ApprovalProcessCallbackListenerTest {

  @Test
  void explicitCallbackRetainsFormalBindingAndRejectsDifferentModelFlowRoutes() {
    var facade = mock(LogicFlowExecutionFacade.class);
    var projection = mock(com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection.class);
    when(projection.projectInstance(any(), any(), any())).thenReturn(true);
    var catalog = catalogWithFlow("supplier.processCallback");
    var binding = new CallbackBinding("flow-1", "supplier.processCallback", "supplier", "status", Map.of("approved", "done"));
    catalog.register(binding);
    var listener = new ApprovalProcessCallbackListener(facade, mock(PlatformTransactionManager.class), catalog, List.of(projection));
    var event = instanceEvent(EventType.APPROVED, "approved");
    event.setFlowKey("flow-1");
    event.setCallbackFlow("supplier.processCallback");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    listener.onEvent(event);
    verify(projection).projectInstance(eq(event), eq(binding), any());
    event.setFlowKey("different-flow");
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> listener.onEvent(event));
    verify(facade, never()).execute(anyString(), anyList());
  }

  @Test
  void businessProjectionOwnsTheRequiredCallbackAndFailureCannotFallBackToASecondExecution() {
    var facade = mock(LogicFlowExecutionFacade.class);
    var manager = mock(PlatformTransactionManager.class);
    when(facade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var projection = mock(com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection.class);
    when(projection.projectInstance(any(), any(), any())).thenAnswer(call -> {
      call.<Runnable>getArgument(2).run();
      return true;
    });
    var listener = new ApprovalProcessCallbackListener(facade, manager,
        catalogWithFlow("supplier.processCallback"), List.of(projection));
    var event = instanceEvent(EventType.APPROVED, "approved");
    event.setCallbackFlow("supplier.processCallback");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    listener.onEvent(event);
    verify(facade).execute(eq("supplier.processCallback"), anyList());
    verify(manager, never()).getTransaction(any());

    doThrow(new IllegalStateException("receipt failed")).when(projection).projectInstance(any(), any(), any());
    assertThrows(IllegalStateException.class, () -> listener.onEvent(event));
    verify(facade).execute(eq("supplier.processCallback"), anyList());
    var task = new com.ouroboros.workflow.approval.model.events.FlowTaskEvent();
    listener.onTaskEvent(task);
    verify(projection).projectTask(task);
  }

  @Test
  void callbackFlowDrivesDirectExecutionWithUnifiedPayload() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.APPROVED, "approved");
    event.setCallbackFlow("supplier.processCallback");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    listener.onApproved(event);

    ArgumentCaptor<String> flowNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(flowNameCaptor.capture(), inputsCaptor.capture());
    assertEquals("supplier.processCallback", flowNameCaptor.getValue());

    // 统一载荷：扁平 map，无伪 HTTP 包装
    var payload = assertInstanceOf(Map.class, inputsCaptor.getValue().get(0));
    assertThat(payload).doesNotContainKeys("method", "path", "callbackUrl");
    assertEquals("instance-1", payload.get("instanceId"));
    assertEquals("event-fact-1", payload.get("eventFactId"));
    assertEquals("APPROVED", payload.get("eventType"));
    assertEquals("approved", payload.get("outcome"));
    var businessContext = assertInstanceOf(Map.class, payload.get("businessContext"));
    assertEquals("supplier", businessContext.get("recordType"));
    assertEquals("sup-1", businessContext.get("recordId"));
    verify(transactionManager).commit(any());
    var definition = ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertEquals(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW,
        definition.getValue().getPropagationBehavior());
  }

  @Test
  void recordTypeFallbackResolvesCallbackFlowByConvention() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.REJECTED, "rejected");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    listener.onRejected(event);

    ArgumentCaptor<String> flowNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionFacade).execute(flowNameCaptor.capture(), anyList());
    assertEquals("supplier.processCallback", flowNameCaptor.getValue());
  }

  @Test
  void recordTypeWithRegisteredBindingInjectsStatusMetadataIntoPayload() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var catalog = catalogWithFlow("supplier.processCallback");
    catalog.register(new CallbackBinding("FLOW_1", "supplier.processCallback", "supplier",
        "status", Map.of("approved", Map.of("code", "20"))));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalog);

    var event = instanceEvent(EventType.APPROVED, "approved");
    event.setBusinessContext(new FlowInstanceEvent.BusinessContext("supplier", "sup-1"));
    listener.onApproved(event);

    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(anyString(), inputsCaptor.capture());
    var payload = assertInstanceOf(Map.class, inputsCaptor.getValue().get(0));
    assertEquals("status", payload.get("statusField"));
    assertInstanceOf(Map.class, payload.get("statusMap"));
    assertThat(payload.get("statusMap")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsKey("approved");
  }

  @Test
  void suspendedEventUsesTheSameCallbackPathAsOtherBusinessStates() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.SUSPENDED, null);
    event.setCallbackFlow("supplier.processCallback");
    listener.onSuspended(event);

    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(anyString(), inputsCaptor.capture());
    var payload = assertInstanceOf(Map.class, inputsCaptor.getValue().get(0));
    assertEquals("SUSPENDED", payload.get("eventType"));
    verify(transactionManager).commit(any());
  }

  @Test
  void resumedEventUsesTheSameCallbackPathAndCarriesProcessingEvent() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.success("ok"));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.RESUMED, null);
    event.setCallbackFlow("supplier.processCallback");
    listener.onResumed(event);

    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(eq("supplier.processCallback"), inputsCaptor.capture());
    var payload = assertInstanceOf(Map.class, inputsCaptor.getValue().get(0));
    assertEquals("RESUMED", payload.get("eventType"));
    verify(transactionManager).commit(any());
  }

  @Test
  void unknownCallbackFlowFailsForOutboxRetry() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.APPROVED, "approved");
    event.setCallbackFlow("unknown.processCallback");
    assertThrows(RuntimeException.class, () -> listener.onApproved(event));

    verify(executionFacade, never()).execute(anyString(), anyList());
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  void directExecutionFailureRollsBackAndPropagates() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.failure(new IllegalStateException("boom")));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    var event = instanceEvent(EventType.REJECTED, "rejected");
    event.setCallbackFlow("supplier.processCallback");
    assertThrows(IllegalStateException.class, () -> listener.onRejected(event));

    verify(transactionManager).rollback(any());
  }

  @Test
  void missingCallbackBindingDoesNotCreateCallback() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("other.processCallback"));
    var event = instanceEvent(EventType.APPROVED, "approved");

    listener.onApproved(event);

    verify(executionFacade, never()).execute(anyString(), anyList());
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  void callbackFailureShouldRollbackAndPropagates() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList())).thenReturn(Try.failure(new IllegalStateException("boom")));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));
    var event = instanceEvent(EventType.REJECTED, "rejected");
    event.setCallbackFlow("supplier.processCallback");

    assertThrows(IllegalStateException.class, () -> listener.onRejected(event));

    verify(transactionManager).rollback(any());
  }

  @Test
  void rollbackFailureIsPreservedOnCallbackFailure() {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList()))
        .thenReturn(Try.failure(new IllegalStateException("callback failed")));
    doThrow(new IllegalStateException("rollback failed")).when(transactionManager).rollback(any());
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));
    var event = instanceEvent(EventType.REJECTED, "rejected");
    event.setCallbackFlow("supplier.processCallback");

    IllegalStateException failure = assertThrows(
        IllegalStateException.class, () -> listener.onRejected(event));

    assertThat(failure.getMessage()).isEqualTo(
        "Approval process callback execution failed: flowKey=AWF_FLOW_SINGLE_L1, "
            + "instanceId=instance-1, callbackFlow=supplier.processCallback");
    assertThat(failure.getSuppressed()).hasSize(1);
    assertThat(failure.getSuppressed()[0]).hasMessage("rollback failed");
  }

  private ApprovalProcessCallbackBindingCatalog catalogWithFlow(String flowName) {
    var flowModelProvider = mock(FlowModelProvider.class);
    when(flowModelProvider.getFlowModel(flowName)).thenReturn(Optional.of(mock(com.ouroboros.logicflow.model.FlowModel.class)));
    return new ApprovalProcessCallbackBindingCatalog(flowModelProvider, TestObjectProviders.empty());
  }

  private FlowInstanceEvent instanceEvent(EventType eventType, String outcome) {
    var event = new FlowInstanceEvent();
    event.setFlowKey("AWF_FLOW_SINGLE_L1");
    event.setInstanceId("instance-1");
    event.setEventType(eventType);
    event.setStarter("starter-1");
    event.setLastTaskId("task-1");
    event.setOutcome(outcome);
    event.setEventFactId("event-fact-1");
    return event;
  }
}
