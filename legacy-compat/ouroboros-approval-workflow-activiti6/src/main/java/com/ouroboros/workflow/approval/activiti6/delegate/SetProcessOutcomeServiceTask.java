package com.ouroboros.workflow.approval.activiti6.delegate;

import java.util.Optional;

import org.activiti.bpmn.model.CustomProperty;
import org.activiti.bpmn.model.ServiceTask;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.nodes.EndNode;

@Component(SetProcessOutcomeServiceTask.BEAN_NAME)
public class SetProcessOutcomeServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "setProcessOutcomeServiceTask";

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable(Constants.Variable.PROCESS_OUTCOME, readOutcome(execution));
  }

  private String readOutcome(DelegateExecution execution) {
    if (!(execution.getCurrentFlowElement() instanceof ServiceTask)) {
      return EndNode.OUTCOME_APPROVED;
    }
    ServiceTask serviceTask = (ServiceTask) execution.getCurrentFlowElement();
    return Optional.ofNullable(readProperty(serviceTask, Constants.Property.PROCESS_OUTCOME))
        .filter(value -> !value.trim().isEmpty())
        .orElse(EndNode.OUTCOME_APPROVED);
  }

  private String readProperty(ServiceTask serviceTask, String name) {
    if (serviceTask.getCustomProperties() == null) {
      return null;
    }
    for (CustomProperty customProperty : serviceTask.getCustomProperties()) {
      if (name.equals(customProperty.getName())) {
        return customProperty.getSimpleValue();
      }
    }
    return null;
  }
}
