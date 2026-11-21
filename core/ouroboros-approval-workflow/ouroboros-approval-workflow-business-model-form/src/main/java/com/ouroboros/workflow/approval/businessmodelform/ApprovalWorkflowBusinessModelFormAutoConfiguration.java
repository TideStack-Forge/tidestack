package com.ouroboros.workflow.approval.businessmodelform;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class ApprovalWorkflowBusinessModelFormAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowBusinessModelFormAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Business Model Form Auto Configuration loaded");
  }
}
