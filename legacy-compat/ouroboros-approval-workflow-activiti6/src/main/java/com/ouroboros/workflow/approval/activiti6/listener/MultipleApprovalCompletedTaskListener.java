package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.TaskListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalCompletedTaskListener.BEAN_NAME)
public class MultipleApprovalCompletedTaskListener implements TaskListener {
  public static final String BEAN_NAME = "multipleApprovalCompletedTaskListener";

  static Logger logger = LoggerFactory.getLogger(MultipleApprovalCompletedTaskListener.class);
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  /**
   * 多签节点根据传入的局部变量approve，设置全局变量
   * 根据approve的值更新当前多实例的执行情况，并修改 任务节点唯一id+变量名称 的全局变量
   * NR_OF_APPROVES_VARIABLE_NAME：当前节点的同意人数，全局变量
   * NR_OF_INSTANCE_VARIABLE_NAME：当前节点的实例总数，全局变量
   * PERCENTAGE_VARIABLE_NAME：当前节点的同意百分比，局部变量
   * APPROVE_FORM_PROPERTY_ID：当前节点的是否同意，局部变量
   * APPROVED_VARIABLE_NAME：当前多实例是否同意，全局变量
   */
  @Override
  public void notify(DelegateTask task) {
    logger.debug("MultipleApprovalCompletedListener");

    DelegateExecution execution = task.getExecution();
    String nodeId = taskNodeId(task);
    if (nodeId == null) {
      logger.warn("MultipleApprovalCompletedListener missing taskDefinitionKey");
      return;
    }

    String session = Optional.ofNullable(task.getVariableLocal(Constants.Variable.MULTIPLE_APPROVAL_SESSION, String.class))
        .orElseGet(() -> task.getVariable(Constants.Variable.getMultipleApprovalSessionVariableName(nodeId), String.class));
    int nrOfApproved = readInt(execution, nodeId + Constants.Variable.NR_OF_APPROVED_SUFFIX);
    int nrOfRejected = readInt(execution, nodeId + Constants.Variable.NR_OF_REJECTED_SUFFIX);
    int percentage = readInt(execution, nodeId + Constants.Variable.MULTIPLE_APPROVED_PERCENTAGE_SUFFIX);
    boolean isSequential = readBoolean(execution, nodeId + Constants.Variable.MULTIPLE_APPROVAL_IS_SEQUENTIAL_SUFFIX);
    int nrOfInstance = Math.max(readInt(execution, "nrOfInstances"), 1);

    String actionName = task.getVariableLocal(Constants.Variable.LOCAL_APPROVAL_ACTION, String.class);
    boolean currentTaskApproved = Constants.Value.APPROVED.equalsIgnoreCase(actionName);
    task.setVariableLocal(Constants.Variable.TASK_APPROVED, currentTaskApproved);

    if (currentTaskApproved) {
      nrOfApproved++;
    } else {
      nrOfRejected++;
    }

    boolean isFinalApproved = (float) nrOfApproved / nrOfInstance >= (float) percentage / 100;
    int remained = nrOfInstance - nrOfApproved - nrOfRejected;

    // 判断是否可以提前结束
    int nrOfApprovedRequired = (int) Math.ceil((float) nrOfInstance * percentage / 100) - nrOfApproved;

    boolean canComplete =
        // 如果已经全部完成，即可结束
        nrOfInstance == nrOfApproved + nrOfRejected
            // 串行模式下，有人拒绝即可结束
            || (isSequential && !currentTaskApproved)
            // 如果已达到同意人数，即可结束
            || isFinalApproved
            // 如果剩余人数不足以达到同意人数，即可结束
            || nrOfApprovedRequired > remained;

    execution.setVariable(nodeId + Constants.Variable.NR_OF_APPROVED_SUFFIX, nrOfApproved);
    execution.setVariable(nodeId + Constants.Variable.NR_OF_REJECTED_SUFFIX, nrOfRejected);
    execution.setVariable(nodeId + Constants.Variable.APPROVED_SUFFIX, isFinalApproved);
    execution.setVariable(nodeId + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, canComplete);
    execution.setVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID, task.getId());
    if (canComplete) {
      execution.setVariable(nodeId + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, task.getId());
    }

    // session
    if (session != null) {
      execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX, canComplete);
      execution.setVariable(Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.APPROVED_SUFFIX, isFinalApproved);
    }

    if (canComplete) {
      return;
    }
    dispatchProcessingEvent(task, actionName, currentTaskApproved);
  }

  private int readInt(DelegateExecution execution, String variableName) {
    Integer value = execution.getVariable(variableName, Integer.class);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return 0;
  }

  private boolean readBoolean(DelegateExecution execution, String variableName) {
    return Boolean.TRUE.equals(execution.getVariable(variableName, Boolean.class));
  }

  List<String> getCandidateUserList(DelegateTask task) {
    String nodeId = taskNodeId(task);
    return Optional.ofNullable(nodeId)
        .map(Constants.Variable::getMultipleApprovalAssigneeListVariableName)
        .map(variableName -> task.getVariable(variableName, List.class))
        .orElse(Collections.emptyList());
  }

  void dispatchProcessingEvent(DelegateTask task, String actionName, Boolean approved) {
    var event = new FlowTaskEvent();
    var comment = approved ? null : readLatestComment(task.getId());

    event.setTaskId(task.getId());
    event.setTaskName(task.getName());
    event.setCandidateUsers(getCandidateUserList(task));
    event.setAssignee(task.getAssignee());
    event.setComment(comment);
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, task));
    event.setState(TaskState.PROCESSING);
    event.setNodeId(taskNodeId(task));
    event.setInstanceId(task.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(actionName);
    event.setNotification(BpmnUtil.readNotification(repositoryService, task.getProcessDefinitionId()));

    // 设置任务节点定义Supplier
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, task.getProcessDefinitionId(), event.getNodeId()));

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
        .filter(java.util.Objects::nonNull)
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
