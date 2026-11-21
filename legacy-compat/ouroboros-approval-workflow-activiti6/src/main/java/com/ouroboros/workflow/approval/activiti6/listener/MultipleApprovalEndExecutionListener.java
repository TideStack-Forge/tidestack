package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.Collections;
import java.util.List;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalEndExecutionListener.BEAN_NAME)
public class MultipleApprovalEndExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "multipleApprovalEndExecutionListener";

  final static Logger logger = LoggerFactory.getLogger(MultipleApprovalEndExecutionListener.class);
  @Autowired
  private HistoryService historyService;
  @Autowired
  private TaskService taskService;
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    var flowElement = execution.getCurrentFlowElement();
    var nodeId = flowElement == null ? null : flowElement.getId();
    logger.debug("MultipleApprovalEndExecutionListener");
    if (!isMultiInstanceAggregationExecution(execution)) {
      return;
    }

    if (StringUtils.isBlank(nodeId)) {
      return;
    }

    if (historyService == null) {
      return;
    }

    var finalTaskId = execution.getVariable(nodeId + Constants.Variable.MULTIPLE_APPROVAL_FINAL_TASK_ID_SUFFIX, String.class);
    var lastTask = StringUtils.isNotBlank(finalTaskId)
        ? historyService.createHistoricTaskInstanceQuery()
            .taskId(finalTaskId)
            .singleResult()
        : historyService.createHistoricTaskInstanceQuery()
            .processInstanceId(execution.getProcessInstanceId())
            .taskDefinitionKey(nodeId)
            .finished()
            .orderByHistoricTaskInstanceEndTime()
            .desc()
            .listPage(0, 1)
            .stream()
            .findFirst()
            .orElse(null);
    if (lastTask == null) {
      return;
    }

    var actionVariable = historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(execution.getProcessInstanceId())
        .taskId(lastTask.getId())
        .variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)
        .singleResult();
    var assignees = execution.getVariable(
        Constants.Variable.getMultipleApprovalAssigneeListVariableName(nodeId),
        List.class);
    var approved = Boolean.TRUE.equals(execution.getVariable(nodeId + Constants.Variable.APPROVED_SUFFIX, Boolean.class));

    var event = new FlowTaskEvent();
    event.setTaskId(lastTask.getId());
    event.setTaskName(lastTask.getName());
    event.setCandidateUsers(assignees == null ? Collections.<String>emptyList() : assignees);
    event.setAssignee(lastTask.getAssignee());
    event.setComment(approved ? null : readLatestComment(lastTask.getId()));
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, execution));
    event.setState(approved ? TaskState.APPROVED : TaskState.REJECTED);
    event.setNodeId(nodeId);
    event.setInstanceId(execution.getProcessInstanceId());
    event.setEventTime(Dates.now());
    event.setActionName(actionVariable == null ? null : String.valueOf(actionVariable.getValue()));
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, execution.getProcessDefinitionId(), nodeId));
    taskEventDispatcher.dispatchEvent(event);
  }

  private boolean isMultiInstanceAggregationExecution(DelegateExecution execution) {
    if (execution.isMultiInstanceRoot()) {
      return true;
    }

    return execution.getVariableLocal("loopCounter") == null;
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
