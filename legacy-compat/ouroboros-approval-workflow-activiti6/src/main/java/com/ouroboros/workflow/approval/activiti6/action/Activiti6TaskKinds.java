package com.ouroboros.workflow.approval.activiti6.action;

import org.activiti.engine.TaskService;
import org.activiti.engine.task.Task;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

final class Activiti6TaskKinds {
  private Activiti6TaskKinds() {
  }

  static boolean isApprovalTask(TaskService taskService, Object task) {
    return task instanceof Task && !isHandleTask(taskService, (Task) task);
  }

  static boolean isHandleTask(TaskService taskService, Object task) {
    if (!(task instanceof Task)) {
      return false;
    }
    Task activitiTask = (Task) task;
    Object kind = taskService == null
        ? null
        : taskService.getVariableLocal(activitiTask.getId(), Constants.Variable.TASK_KIND);
    return Constants.Value.TASK_KIND_HANDLE.equals(kind);
  }
}
