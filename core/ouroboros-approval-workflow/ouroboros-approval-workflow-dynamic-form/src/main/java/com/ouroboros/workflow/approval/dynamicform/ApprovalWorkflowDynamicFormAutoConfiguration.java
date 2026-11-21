package com.ouroboros.workflow.approval.dynamicform;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.workflow.approval.runtime.controller.ApprovalWorkflowDynamicFormRecordController;

@Import(ApprovalWorkflowDynamicFormRecordController.class)
@DataModelScan
@ComponentScan
public class ApprovalWorkflowDynamicFormAutoConfiguration {
  final static Logger logger = LoggerFactory.getLogger(ApprovalWorkflowDynamicFormAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow DynamicForm Auto Configuration loaded");
  }
}
