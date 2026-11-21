package com.ouroboros.workflow.approval.activiti6.listener;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.ExecutionListener;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/**
 * 流程开始的时候，检查自己是否是作为子流程被调用的
 * 当前没有什么作用，只是为了记录一下
 */
@Component(FlowStartExecutionListener.BEAN_NAME)
public class FlowStartExecutionListener implements ExecutionListener {
  public static final String BEAN_NAME = "flowStartExecutionListener";

  final static Logger logger = LoggerFactory.getLogger(FlowStartExecutionListener.class);
  @Autowired
  private RepositoryService repositoryService;
  @Autowired
  private InstanceEventDispatcher instanceEventDispatcher;

  @Override
  public void notify(DelegateExecution execution) {
    logger.debug("流程:{}开始", execution.getProcessInstanceId());
    // 父流程
    configParentFlow(execution);
    dispatchEvent(execution);
  }

  void configParentFlow(DelegateExecution execution) {
    ExecutionEntity executionEntity = (ExecutionEntity) execution;
    ExecutionEntity processInstance = executionEntity.getProcessInstance();

    var superExecution = processInstance.getSuperExecution();
    logger.debug("processInstance id: {}, superExecution: {}", processInstance.getId(), superExecution == null ? "null" : superExecution.getId());

    execution.setVariable(Constants.Variable.HAS_PARENT_FLOW, superExecution != null);
    if (superExecution == null) {
      return;
    }
    var superProcessInstance = superExecution.getProcessInstance();
    var superProcessInstanceId = superProcessInstance.getProcessInstanceId();
    execution.setVariable(Constants.Variable.PARENT_FLOW_ID, superProcessInstanceId);
    logger.debug("流程:{}的父流程实例ID:{}", executionEntity.getProcessInstanceId(), superProcessInstanceId);
  }

  void dispatchEvent(DelegateExecution execution) {
    var flowInstanceEvent = new FlowInstanceEvent();
    flowInstanceEvent.setInstanceId(execution.getProcessInstanceId());
    flowInstanceEvent.setFlowKey(resolveFlowKey(execution));
    flowInstanceEvent.setEventType(FlowInstanceEvent.EventType.STARTED);
    flowInstanceEvent.setEventTime(Dates.now());
    flowInstanceEvent.setStarter(readStringVariable(execution, Constants.Variable.PROCESS_STARTER));
    instanceEventDispatcher.dispatchEvent(flowInstanceEvent);
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

  private String readStringVariable(DelegateExecution execution, String variableName) {
    var value = execution.getVariable(variableName);
    return value == null ? null : String.valueOf(value);
  }
}
