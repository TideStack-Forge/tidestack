package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.task.IdentityLink;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(SingleApproveCompletedTaskListener.BEAN_NAME)
public class SingleApproveCompletedTaskListener implements TaskListener {
  public static final String BEAN_NAME = "singleApproveCompletedTaskListener";
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  /**
   * 单签（审批）节点根据传入的局部变量approve和action，设置全局变量
   * 我们同样根据 任务节点唯一id+变量名称 的全局变量来存储该节点的执行情况
   * approved：是否同意
   * action：具体的操作(approve/reject/rollback)
   * 节点的拒绝原因我们使用userTask的Comment去存储
   */
  @Override
  public void notify(DelegateTask task) {
    var execution = task.getExecution();
    var nodeId = taskNodeId(task);

    if (nodeId == null) {
      return;
    }

    // 将本地变量中的 Action 名字存入全局变量中
    var action = task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class);
    var approved = Constants.Value.APPROVED.equalsIgnoreCase(action);

    task.setVariableLocal(Constants.Variable.TASK_APPROVED, approved);

    execution.setVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, task.getId());
    execution.setVariable(nodeId + Constants.Variable.APPROVAL_ACTION_SUFFIX, action);
    execution.setVariable(nodeId + Constants.Variable.APPROVED_SUFFIX, approved);

    dispatchEvent(task, approved);
  }

  void dispatchEvent(DelegateTask task, Boolean approved) {
    var event = new FlowTaskEvent();
    var comment = approved ? null : readLatestComment(task.getId());
    var nodeId = taskNodeId(task);
    if (nodeId == null) {
      return;
    }

    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(Optional.ofNullable(task.getCandidates())
        .orElse(Collections.<IdentityLink>emptySet())
        .stream()
        .filter(Objects::nonNull)
        .map(IdentityLink::getUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));
    event.setAssignee(task.getAssignee());
    event.setComment(comment);
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setState(approved ? TaskState.APPROVED : TaskState.REJECTED);
    event.setNodeId(nodeId);
    event.setInstanceId(task.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class));
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));

    // 设置任务节点定义Supplier
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, task.getProcessDefinitionId(), nodeId));

    taskEventDispatcher.dispatchEvent(event);
  }

  private String taskNodeId(DelegateTask task) {
    return StringUtils.defaultIfBlank(task.getTaskDefinitionKey(),
        task.getExecution() == null || task.getExecution().getCurrentFlowElement() == null
            ? null
            : task.getExecution().getCurrentFlowElement().getId());
  }

  private String readLatestComment(String taskId) {
    if (taskService == null) {
      return null;
    }
    return taskService.getTaskComments(taskId).stream()
        .filter(Objects::nonNull)
        .max((left, right) -> {
          var leftTime = left.getTime();
          var rightTime = right.getTime();
          if (leftTime == null && rightTime == null) {
            return 0;
          }
          if (leftTime == null) {
            return -1;
          }
          if (rightTime == null) {
            return 1;
          }
          return leftTime.compareTo(rightTime);
        })
        .map(comment -> comment.getFullMessage())
        .orElse(null);
  }
}
