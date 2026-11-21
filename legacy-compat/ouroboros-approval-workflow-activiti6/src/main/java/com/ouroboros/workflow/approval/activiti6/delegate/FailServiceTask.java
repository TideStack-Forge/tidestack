package com.ouroboros.workflow.approval.activiti6.delegate;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

@Component(FailServiceTask.BEAN_NAME)
public class FailServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "failServiceTask";

  @Override
  public void execute(DelegateExecution execution) {
    execution.setVariable(Constants.Variable.PROCESS_APPROVED, false);
  }
}
