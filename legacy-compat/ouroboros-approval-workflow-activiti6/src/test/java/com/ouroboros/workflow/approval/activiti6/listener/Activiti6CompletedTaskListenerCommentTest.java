package com.ouroboros.workflow.approval.activiti6.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.IdentityLink;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.listener.FlowTaskListener;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

class Activiti6CompletedTaskListenerCommentTest {
  @Test
  void singleRejectEventUsesLatestTaskComment() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener listener =
        newSingleListener(mockTaskServiceWithComments("task-1"), collector.dispatcher);

    listener.dispatchEvent(mockTask("task-1", "approve-a"), false);

    assertEquals("newer-comment", collector.listener.lastEvent().getComment());
  }

  @Test
  void multipleProcessingRejectEventUsesLatestTaskComment() {
    EventCollector collector = installTaskEventCollector();
    MultipleApprovalCompletedTaskListener listener =
        newMultipleListener(mockTaskServiceWithComments("task-2"), collector.dispatcher);

    listener.dispatchProcessingEvent(mockTask("task-2", "multi-a"), "reject", false);

    assertEquals("newer-comment", collector.listener.lastEvent().getComment());
  }

  @Test
  void singleRejectEventLeavesCommentNullWhenTaskServiceMissing() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener listener = newSingleListener(null, collector.dispatcher);

    assertDoesNotThrow(() -> listener.dispatchEvent(mockTask("task-4", "approve-a"), false));

    assertEquals(null, collector.listener.lastEvent().getComment());
  }

  @Test
  void singleCompletedEventCarriesActionName() {
    EventCollector collector = installTaskEventCollector();
    SingleApproveCompletedTaskListener listener =
        newSingleListener(mockTaskServiceWithComments("task-5"), collector.dispatcher);
    DelegateTask task = mockTask("task-5", "approve-a");

    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.APPROVED);

    listener.notify(task);

    assertEquals(Constants.Value.APPROVED, collector.listener.lastEvent().getActionName());
  }

  @Test
  void multipleCompletedEventDefaultsMissingCountersSkipsNullSessionWritesAndDefersFinalEvent() {
    EventCollector collector = installTaskEventCollector();
    MultipleApprovalCompletedTaskListener listener = newMultipleListener(null, collector.dispatcher);
    DelegateTask task = mockTask("task-6", "multi-a");
    DelegateExecution execution = task.getExecution();

    when(task.getVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION, String.class)).thenReturn(null);
    when(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class)).thenReturn(Constants.Value.APPROVED);
    when(execution.getVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, Integer.class)).thenReturn(null);
    when(execution.getVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, Integer.class)).thenReturn(null);
    when(execution.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX, Integer.class)).thenReturn(50);
    when(execution.getVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX, Boolean.class)).thenReturn(null);
    when(execution.getVariable("nrOfInstances", Integer.class)).thenReturn(2);

    assertDoesNotThrow(() -> listener.notify(task));

    verify(task).setVariableLocal(Constants.Variable.TASK_APPROVED, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.NR_OF_APPROVED_SUFFIX, 1);
    verify(execution).setVariable("multi-a" + Constants.Variable.NR_OF_REJECTED_SUFFIX, 0);
    verify(execution).setVariable("multi-a" + Constants.Variable.APPROVED_SUFFIX, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution).setVariable("multi-a" + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, "task-6");
    verify(execution, never()).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "null" + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, true);
    verify(execution, never()).setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + "null" + Constants.Variable.APPROVED_SUFFIX, true);
    assertEquals(0, collector.listener.eventCount());
  }

  private TaskService mockTaskServiceWithComments(String taskId) {
    TaskService taskService = mock(TaskService.class);
    Comment olderComment = mock(Comment.class);
    Comment newerComment = mock(Comment.class);

    when(olderComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 17, 30));
    when(olderComment.getFullMessage()).thenReturn("older-comment");
    when(newerComment.getTime()).thenReturn(dateOf(2026, Month.MARCH, 19, 17, 31));
    when(newerComment.getFullMessage()).thenReturn("newer-comment");

    when(taskService.getTaskComments(taskId)).thenReturn(Arrays.asList(olderComment, newerComment));
    return taskService;
  }

  private EventCollector installTaskEventCollector() {
    TaskEventDispatcher dispatcher = new TaskEventDispatcher();
    CapturingFlowTaskListener listener = new CapturingFlowTaskListener();
    setField(dispatcher, "listeners", Collections.singletonList(listener));
    return new EventCollector(dispatcher, listener);
  }

  private SingleApproveCompletedTaskListener newSingleListener(TaskService taskService, TaskEventDispatcher dispatcher) {
    SingleApproveCompletedTaskListener listener = new SingleApproveCompletedTaskListener();
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private MultipleApprovalCompletedTaskListener newMultipleListener(TaskService taskService, TaskEventDispatcher dispatcher) {
    MultipleApprovalCompletedTaskListener listener = new MultipleApprovalCompletedTaskListener();
    setField(listener, "taskService", taskService);
    setField(listener, "taskEventDispatcher", dispatcher);
    return listener;
  }

  private DelegateTask mockTask(String taskId, String nodeId) {
    DelegateTask task = mock(DelegateTask.class);
    DelegateExecution execution = mock(DelegateExecution.class);
    UserTask userTask = mock(UserTask.class);

    when(task.getId()).thenReturn(taskId);
    when(task.getName()).thenReturn("审批任务");
    when(task.getAssignee()).thenReturn("user-a");
    when(task.getCandidates()).thenReturn(Collections.<IdentityLink>emptySet());
    when(task.getExecution()).thenReturn(execution);
    when(task.getProcessInstanceId()).thenReturn("instance-1");
    when(task.getProcessDefinitionId()).thenReturn("process-definition-1");
    when(execution.getCurrentFlowElement()).thenReturn(userTask);
    when(execution.getVariable(Constants.Variable.PROCESS_FLOW_KEY, String.class)).thenReturn("flow-a");
    when(userTask.getId()).thenReturn(nodeId);
    when(task.getVariable(Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST, java.util.List.class))
        .thenReturn(Collections.singletonList("user-a"));
    return task;
  }

  private Date dateOf(int year, Month month, int dayOfMonth, int hour, int minute) {
    return Date.from(LocalDateTime.of(year, month, dayOfMonth, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant());
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

  private static final class CapturingFlowTaskListener implements FlowTaskListener {
    private FlowTaskEvent lastEvent;
    private int eventCount;

    @Override
    public void onEvent(FlowTaskEvent event) {
      lastEvent = event;
      eventCount++;
    }

    private FlowTaskEvent lastEvent() {
      if (lastEvent == null) {
        throw new AssertionError("No task event captured");
      }
      return lastEvent;
    }

    private int eventCount() {
      return eventCount;
    }
  }

  private static final class EventCollector {
    private final TaskEventDispatcher dispatcher;
    private final CapturingFlowTaskListener listener;

    private EventCollector(TaskEventDispatcher dispatcher, CapturingFlowTaskListener listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }
  }
}
