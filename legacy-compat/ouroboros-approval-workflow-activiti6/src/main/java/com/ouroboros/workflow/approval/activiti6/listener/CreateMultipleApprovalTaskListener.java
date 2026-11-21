package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(CreateMultipleApprovalTaskListener.BEAN_NAME)
public class CreateMultipleApprovalTaskListener implements TaskListener {
  public static final String BEAN_NAME = "createMultipleApprovalTaskListener";
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateTask delegateTask) {
    var nodeId = delegateTask.getTaskDefinitionKey();
    if (nodeId == null) {
      return;
    }
    var session = delegateTask.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName(nodeId));
    delegateTask.setVariableLocal(Constants.Variable.MULTIPLE_APPROVAL_SESSION, session);
    dispatchEvent(delegateTask);
  }

  void dispatchEvent(DelegateTask task) {
    var nodeId = task.getTaskDefinitionKey();
    var candidates = Optional.ofNullable(nodeId)
        .map(Constants.Variable::getMultipleApprovalAssigneeListVariableName)
        .map(task::getVariable)
        .filter(List.class::isInstance)
        .map(List.class::cast)
        .orElseGet(Collections::emptyList);
    var taskEvent = new FlowTaskEvent();
    taskEvent.setInstanceId(task.getProcessInstanceId());
    taskEvent.setTaskId(task.getId());
    taskEvent.setTaskName(task.getName());
    taskEvent.setCandidateUsers(candidates);
    taskEvent.setAssignee(task.getAssignee());
    taskEvent.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    taskEvent.setNodeId(nodeId);
    taskEvent.setState(TaskState.CREATED);
    taskEvent.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));
    if (task.getCreateTime() == null) {
      taskEvent.setEventTime(Dates.now());
    } else {
      taskEvent.setEventTime(task.getCreateTime());
    }

    // 设置任务节点定义Supplier
    taskEvent.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, task.getProcessDefinitionId(), nodeId));

    taskEventDispatcher.dispatchEvent(taskEvent);
  }
}
