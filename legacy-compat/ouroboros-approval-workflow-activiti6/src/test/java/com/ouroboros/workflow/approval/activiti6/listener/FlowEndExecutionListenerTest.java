package com.ouroboros.workflow.approval.activiti6.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.listener.FlowInstanceListener;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

class FlowEndExecutionListenerTest {
  @Test
  void notifyUsesExplicitLastTaskIdWithoutFallingBackToHistory() {
    HistoryService historyService = mock(HistoryService.class);
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowEndExecutionListener listener = newListener(null, historyService, newDispatcher(collector));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-parent-1");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.TRUE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn("child-task-9");
    when(execution.getVariable(Constants.Variable.PROCESS_OUTCOME)).thenReturn("approved");

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.APPROVED, event.getEventType());
    assertEquals("parent-flow", event.getFlowKey());
    assertEquals("starter-1", event.getStarter());
    assertEquals("child-task-9", event.getLastTaskId());
    assertEquals("approved", event.getOutcome());
    verify(historyService, never()).createHistoricTaskInstanceQuery();
  }

  @Test
  void notifyUsesRejectedOutcomeForEventType() {
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowEndExecutionListener listener = newListener(null, null, newDispatcher(collector));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-rejected-outcome");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.TRUE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn("task-1");
    when(execution.getVariable(Constants.Variable.PROCESS_OUTCOME)).thenReturn("rejected");

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.REJECTED, event.getEventType());
    assertEquals("rejected", event.getOutcome());
  }

  @Test
  void notifyKeepsCustomOutcomeReadOnlyForApprovedEventType() {
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowEndExecutionListener listener = newListener(null, null, newDispatcher(collector));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-custom-outcome");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.TRUE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn("task-1");
    when(execution.getVariable(Constants.Variable.PROCESS_OUTCOME)).thenReturn("finance-approved");

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.APPROVED, event.getEventType());
    assertEquals("finance-approved", event.getOutcome());
  }

  @Test
  void notifyFallsBackToParentHistoryWhenExplicitLastTaskIdMissing() {
    HistoryService historyService = mock(HistoryService.class);
    HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
    HistoricTaskInstance lastTask = mock(HistoricTaskInstance.class);
    when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("instance-parent-1")).thenReturn(taskQuery);
    when(taskQuery.finished()).thenReturn(taskQuery);
    when(taskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.listPage(0, 1)).thenReturn(Collections.singletonList(lastTask));
    when(lastTask.getId()).thenReturn("parent-task-3");

    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowEndExecutionListener listener = newListener(null, historyService, newDispatcher(collector));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-parent-1");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.FALSE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn(null);

    listener.notify(execution);

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.REJECTED, event.getEventType());
    assertEquals("parent-task-3", event.getLastTaskId());
    verify(historyService).createHistoricTaskInstanceQuery();
  }

  @Test
  void notifyLeavesFlowKeyAndLastTaskIdNullWhenEngineServicesMissing() {
    CollectingFlowInstanceListener collector = new CollectingFlowInstanceListener();
    FlowEndExecutionListener listener = newListener(null, null, newDispatcher(collector));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-parent-2");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-parent");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.TRUE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn(null);
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-2");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn(null);

    assertDoesNotThrow(() -> listener.notify(execution));

    FlowInstanceEvent event = collector.lastEvent();
    assertEquals(FlowInstanceEvent.EventType.APPROVED, event.getEventType());
    assertEquals(null, event.getFlowKey());
    assertEquals("starter-2", event.getStarter());
    assertEquals(null, event.getLastTaskId());
  }

  @Test
  void notifyPropagatesInstanceListenerFailures() {
    FlowEndExecutionListener listener =
        newListener(null, null, newDispatcher(new ThrowingFlowInstanceListener("dispatch failed")));

    DelegateExecution execution = mock(DelegateExecution.class);
    when(execution.getProcessInstanceId()).thenReturn("instance-parent-throw");
    when(execution.getVariable(Constants.Variable.PROCESS_APPROVED)).thenReturn(Boolean.TRUE);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY)).thenReturn("parent-flow");
    when(execution.getVariable(Constants.Variable.PROCESS_STARTER)).thenReturn("starter-1");
    when(execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID)).thenReturn("task-1");

    RuntimeException error = assertThrows(RuntimeException.class, () -> listener.notify(execution));
    assertEquals("dispatch failed", error.getMessage());
  }

  private FlowEndExecutionListener newListener(
      RepositoryService repositoryService,
      HistoryService historyService,
      InstanceEventDispatcher dispatcher
  ) {
    FlowEndExecutionListener listener = new FlowEndExecutionListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "historyService", historyService);
    setField(listener, "instanceEventDispatcher", dispatcher);
    return listener;
  }

  private InstanceEventDispatcher newDispatcher(FlowInstanceListener listener) {
    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return dispatcher;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private static final class CollectingFlowInstanceListener implements FlowInstanceListener {
    private final List<FlowInstanceEvent> events = new ArrayList<FlowInstanceEvent>();

    @Override
    public void onEvent(FlowInstanceEvent event) {
      events.add(event);
    }

    private FlowInstanceEvent lastEvent() {
      if (events.isEmpty()) {
        throw new AssertionError("No instance event captured");
      }
      return events.get(events.size() - 1);
    }
  }

  private static final class ThrowingFlowInstanceListener implements FlowInstanceListener {
    private final String message;

    private ThrowingFlowInstanceListener(String message) {
      this.message = message;
    }

    @Override
    public void onEvent(FlowInstanceEvent event) {
      throw new RuntimeException(message);
    }
  }
}
