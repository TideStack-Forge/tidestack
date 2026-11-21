package com.ouroboros.workflow.approval.manage;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

@DataModelScan
@ComponentScan
public class ApprovalWorkflowManageAutoConfiguration {
  Logger logger = LoggerFactory.getLogger(ApprovalWorkflowManageAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Manage Auto Configuration loaded");
  }
}
