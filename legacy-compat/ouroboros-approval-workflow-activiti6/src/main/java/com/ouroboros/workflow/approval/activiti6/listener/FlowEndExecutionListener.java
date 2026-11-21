package com.ouroboros.workflow.approval.activiti6.listener;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Converter;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

@Component(FlowEndExecutionListener.BEAN_NAME)
public class FlowEndExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "flowEndExecutionListener";

  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private HistoryService historyService;
  @Autowired
  private InstanceEventDispatcher instanceEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    var flowInstanceEvent = new FlowInstanceEvent();

    flowInstanceEvent.setInstanceId(execution.getProcessInstanceId());
    flowInstanceEvent.setFlowKey(resolveFlowKey(execution));
    flowInstanceEvent.setEventType(readEventType(execution));
    flowInstanceEvent.setEventTime(Dates.now());
    flowInstanceEvent.setStarter(readStringVariable(execution, Constants.Variable.PROCESS_STARTER));
    flowInstanceEvent.setLastTaskId(getLastFinishedTaskId(execution));
    flowInstanceEvent.setOutcome(readStringVariable(execution, Constants.Variable.PROCESS_OUTCOME));
    flowInstanceEvent.setNotification(BpmnUtil.readNotification(repositoryService, execution.getProcessDefinitionId()));
    instanceEventDispatcher.dispatchEvent(flowInstanceEvent);
  }

  private String getLastFinishedTaskId(DelegateExecution execution) {
    var explicitLastTaskId = execution.getVariable(Constants.Variable.PROCESS_LAST_FINISHED_TASK_ID);
    if (explicitLastTaskId != null) {
      return String.valueOf(explicitLastTaskId);
    }

    if (historyService == null) {
      return null;
    }

    var lastDoneTaskFromHistory = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(execution.getProcessInstanceId())
        .finished()
        .orderByHistoricTaskInstanceEndTime()
        .desc()
        .listPage(0, 1);
    return lastDoneTaskFromHistory.isEmpty() ? null : lastDoneTaskFromHistory.get(0).getId();
  }

  private String resolveFlowKey(DelegateExecution execution) {
    var flowKey = readStringVariable(execution, Constants.Variable.PROCESS_FLOW_KEY);
    if (flowKey != null) {
      return flowKey;
    }

    if (repositoryService == null || execution.getProcessDefinitionId() == null) {
      return null;
    }

    ProcessDefinition processDefinition = repositoryService.getProcessDefinition(execution.getProcessDefinitionId());
    return processDefinition == null ? null : processDefinition.getKey();
  }

  private FlowInstanceEvent.EventType readEventType(DelegateExecution execution) {
    var outcome = readStringVariable(execution, Constants.Variable.PROCESS_OUTCOME);
    if (EndNode.OUTCOME_REJECTED.equals(outcome)) {
      return FlowInstanceEvent.EventType.REJECTED;
    }
    var approvalResult = Converter.toBoolean(execution.getVariable(Constants.Variable.PROCESS_APPROVED));
    return approvalResult ? FlowInstanceEvent.EventType.APPROVED : FlowInstanceEvent.EventType.REJECTED;
  }

  private String readStringVariable(DelegateExecution execution, String variableName) {
    var value = execution.getVariable(variableName);
    return value == null ? null : String.valueOf(value);
  }
}
