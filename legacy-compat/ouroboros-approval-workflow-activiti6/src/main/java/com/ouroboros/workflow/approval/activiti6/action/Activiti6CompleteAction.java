package com.ouroboros.workflow.approval.activiti6.action;

import org.activiti.engine.TaskService;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.FlowTaskAction;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

@Component
public class Activiti6CompleteAction implements FlowTaskAction<Task> {
  @Autowired
  TaskService taskService;

  @Override
  public String actionName() {
    return Constants.Value.COMPLETE;
  }

  @Override
  public Boolean supportTask(Object task) {
    return Activiti6TaskKinds.isHandleTask(taskService, task);
  }

  @Override
  public Try<Void> doAction(Task task) {
    return Try.run(() -> {
      taskService.setVariableLocal(task.getId(), Constants.Variable.LOCAL_APPROVAL_ACTION, actionName());
      taskService.complete(task.getId());
    });
  }
}
