package com.ouroboros.workflow.approval.activiti6.delegate;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;

/**
 * 初始化流程变量
 */
@Component(InitializeFlowServiceTask.BEAN_NAME)
public class InitializeFlowServiceTask implements JavaDelegate {
  public static final String BEAN_NAME = "initializeFlowServiceTask";

  final static Logger logger = org.slf4j.LoggerFactory.getLogger(InitializeFlowServiceTask.class);

  @Override
  public void execute(DelegateExecution execution) {
    logger.trace("enter InitializeFlowServiceTask");
    execution.setVariable(Constants.Variable.PROCESS_APPROVED, true);
  }
}
