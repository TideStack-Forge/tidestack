package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.Collections;
import java.util.List;

import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.ExecutionListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

@Component(MultipleApprovalStartExecutionListener.BEAN_NAME)
public class MultipleApprovalStartExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "multipleApprovalStartExecutionListener";
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private TaskEventDispatcher taskEventDispatcher;

  /**
   * 在多签节点开始时执行
   * 注意：第一次进入该UserTask，和每一次多实例时都会执行一次这个方法
   * 不同实例间不共用局部变量，但共用 任务唯一id+变量名 的变量
   * 要为候选人列表填充变量
   * loopCounter                  表示当前任务索引（activiti内置）
   * nrOfCompletedInstance        已完成实例数（activiti内置）
   * nrOfInstances                实例总数（activiti内置）
   * nrOfActiveInstances          进行中实例数（activiti内置）
   *
   * @param execution
  */
  @Override
  public void notify(DelegateExecution execution) {
    if (!isMultiInstanceAggregationExecution(execution)) {
      return;
    }

    var flowElement = execution.getCurrentFlowElement();
    if (!(flowElement instanceof UserTask)) {
      return;
    }
    UserTask userTask = (UserTask) flowElement;
    if (StringUtils.isBlank(userTask.getId())) {
      return;
    }

    Object assignees = execution.getVariable(Constants.Variable.getMultipleApprovalAssigneeListVariableName(userTask.getId()));
    List<String> candidates = assignees instanceof List ? (List<String>) assignees : Collections.<String>emptyList();
    if (candidates.isEmpty()) {
      return;
    }

    FlowTaskEvent event = new FlowTaskEvent();
    event.setInstanceId(execution.getProcessInstanceId());
    event.setTaskId(null);
    event.setTaskName(userTask.getName());
    event.setCandidateUsers(candidates);
    event.setAssignee(null);
    event.setFlowKey(BpmnUtil.resolveFlowKey(repositoryService, execution));
    event.setNodeId(userTask.getId());
    event.setState(TaskState.UNCREATED);
    event.setEventTime(Dates.now());
    event.setFlowNodeSupplier(BpmnUtil.getFlowNodeSupplier(repositoryService, execution.getProcessDefinitionId(), userTask.getId()));
    taskEventDispatcher.dispatchEvent(event);
  }

  private boolean isMultiInstanceAggregationExecution(DelegateExecution execution) {
    if (execution.isMultiInstanceRoot()) {
      return true;
    }

    return execution.getVariableLocal("loopCounter") == null;
  }
}
