package com.ouroboros.businessmodel.workflow;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.action.BusinessActionInvocationValidator;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;

@AutoConfiguration(before = com.ouroboros.businessmodel.action.BusinessActionRuntimeAutoConfiguration.class)
public class BusinessWorkflowRuntimeAutoConfiguration {
  @Bean
  public BusinessActionInvocationValidator workflowBusinessActionValidator(ObjectProvider<ApprovalBusinessBindingValidator> bindings) {
    return new WorkflowBusinessActionValidator(BusinessModelCenter.buildInProvider, bindings::getObject);
  }
}
