package com.ouroboros.workflow.approval.activiti6.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.activiti.engine.TaskService;
import org.activiti.engine.task.Task;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

class Activiti6TaskActionTest {
  @Test
  void completeActionOnlySupportsHandleTasks() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.TASK_KIND))
        .thenReturn(Constants.Value.TASK_KIND_HANDLE);

    Object completeAction = Class.forName("com.ouroboros.workflow.approval.activiti6.action.Activiti6CompleteAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(completeAction, "taskService", taskService);

    assertEquals(Constants.Value.COMPLETE, invoke(completeAction, "actionName"));
    assertEquals(Boolean.TRUE, invoke(completeAction, "supportTask", Object.class, task));

    Try<?> result = (Try<?>) invoke(completeAction, "doAction", Task.class, task);

    assertTrue(result.isSuccess());
    verify(taskService).setVariableLocal("task-1", Constants.Variable.LOCAL_APPROVAL_ACTION, Constants.Value.COMPLETE);
    verify(taskService).complete("task-1");
  }

  @Test
  void approvalActionsDoNotSupportHandleTasks() throws Exception {
    TaskService taskService = mock(TaskService.class);
    Task task = mock(Task.class);
    when(task.getId()).thenReturn("task-1");
    when(taskService.getVariableLocal("task-1", Constants.Variable.TASK_KIND))
        .thenReturn(Constants.Value.TASK_KIND_HANDLE);

    Object approveAction = Class.forName("com.ouroboros.workflow.approval.activiti6.action.Activiti6ApproveAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(approveAction, "taskService", taskService);

    Object rejectAction = Class.forName("com.ouroboros.workflow.approval.activiti6.action.Activiti6RejectAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(rejectAction, "taskService", taskService);

    Object rollbackAction = Class.forName("com.ouroboros.workflow.approval.activiti6.action.Activiti6RollbackAction")
        .getDeclaredConstructor()
        .newInstance();
    setField(rollbackAction, "taskService", taskService);

    assertEquals(Boolean.FALSE, invoke(approveAction, "supportTask", Object.class, task));
    assertEquals(Boolean.FALSE, invoke(rejectAction, "supportTask", Object.class, task));
    assertEquals(Boolean.FALSE, invoke(rollbackAction, "supportTask", Object.class, task));
  }

  private Object invoke(Object target, String methodName) throws Exception {
    Method method = target.getClass().getMethod(methodName);
    return method.invoke(target);
  }

  private Object invoke(Object target, String methodName, Class<?> argumentType, Object argument) throws Exception {
    Method method = target.getClass().getMethod(methodName, argumentType);
    return method.invoke(target, argument);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
