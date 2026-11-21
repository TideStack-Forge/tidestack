package com.ouroboros.workflow.approval.web;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
public class ApprovalWorkflowWebAutoConfiguration {
  Logger logger = LoggerFactory.getLogger(ApprovalWorkflowWebAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Web Api Auto Configuration loaded");
  }
}
