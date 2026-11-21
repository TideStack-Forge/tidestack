package com.ouroboros.workflow.approval.activiti6.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class MultipleApprovalStartExecutionListenerTest {
  @Test
  void notifyDispatchesUncreatedEventForMultiInstanceRoot() {
    CapturingFlowTaskListener collector = new CapturingFlowTaskListener();
    MultipleApprovalStartExecutionListener listener = newListener(null, newDispatcher(collector));
    DelegateExecution execution = mock(DelegateExecution.class);
    UserTask userTask = new UserTask();

    userTask.setId("multi-a");
    userTask.setName("多人会签");

    when(execution.isMultiInstanceRoot()).thenReturn(true);
    when(execution.getCurrentFlowElement()).thenReturn(userTask);
    when(execution.getProcessInstanceId()).thenReturn("instance-1");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(execution.getVariable(assigneeListVariable("multi-a")))
        .thenReturn(Collections.singletonList("user-a"));
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");

    listener.notify(execution);

    FlowTaskEvent event = collector.lastEvent();
    assertEquals("instance-1", event.getInstanceId());
    assertEquals("multi-a", event.getNodeId());
    assertEquals("多人会签", event.getTaskName());
    assertEquals(Collections.singletonList("user-a"), event.getCandidateUsers());
    assertEquals("flow-a", event.getFlowKey());
    assertEquals(TaskState.UNCREATED, event.getState());
    assertNotNull(event.getEventTime());
  }

  @Test
  void notifyDispatchesUncreatedEventForAggregationExecutionWithoutMultiInstanceRootFlag() {
    CapturingFlowTaskListener collector = new CapturingFlowTaskListener();
    MultipleApprovalStartExecutionListener listener = newListener(null, newDispatcher(collector));
    DelegateExecution execution = mock(DelegateExecution.class);
    UserTask userTask = new UserTask();

    userTask.setId("multi-a");
    userTask.setName("多人会签");

    when(execution.isMultiInstanceRoot()).thenReturn(false);
    when(execution.getCurrentFlowElement()).thenReturn(userTask);
    when(execution.getProcessInstanceId()).thenReturn("instance-2");
    when(execution.getProcessDefinitionId()).thenReturn("process-definition-2");
    when(execution.getVariable(assigneeListVariable("multi-a")))
        .thenReturn(Collections.singletonList("user-b"));
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-b");
    when(execution.getVariableLocal("loopCounter")).thenReturn(null);

    listener.notify(execution);

    FlowTaskEvent event = collector.lastEvent();
    assertEquals("instance-2", event.getInstanceId());
    assertEquals("multi-a", event.getNodeId());
    assertEquals(Collections.singletonList("user-b"), event.getCandidateUsers());
    assertEquals(TaskState.UNCREATED, event.getState());
  }

  @Test
  void notifyDoesNotDispatchUncreatedEventForChildLoopExecution() {
    CapturingFlowTaskListener collector = new CapturingFlowTaskListener();
    MultipleApprovalStartExecutionListener listener = newListener(null, newDispatcher(collector));
    DelegateExecution execution = mock(DelegateExecution.class);
    UserTask userTask = new UserTask();

    userTask.setId("multi-a");
    userTask.setName("多人会签");

    when(execution.isMultiInstanceRoot()).thenReturn(false);
    when(execution.getCurrentFlowElement()).thenReturn(userTask);
    when(execution.getVariable(assigneeListVariable("multi-a")))
        .thenReturn(Collections.singletonList("user-c"));
    when(execution.getVariableLocal("loopCounter")).thenReturn(1);

    listener.notify(execution);

    assertEquals(0, collector.eventCount());
  }

  private MultipleApprovalStartExecutionListener newListener(
      RepositoryService repositoryService,
      TaskEventDispatcher dispatcher
  ) {
    MultipleApprovalStartExecutionListener listener = new MultipleApprovalStartExecutionListener();
    setField(listener, "repositoryService", repositoryService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private TaskEventDispatcher newDispatcher(FlowTaskListener listener) {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
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

  private String assigneeListVariable(String nodeId) {
    return nodeId + "_" + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST;
  }

  private static final class CapturingFlowTaskListener implements FlowTaskListener {
    private final List<FlowTaskEvent> events = new ArrayList<FlowTaskEvent>();

    @Override
    public void onEvent(FlowTaskEvent event) {
      events.add(event);
    }

    private FlowTaskEvent lastEvent() {
      if (events.isEmpty()) {
        throw new AssertionError("No task event captured");
      }
      return events.get(events.size() - 1);
    }

    private int eventCount() {
      return events.size();
    }
  }
}
