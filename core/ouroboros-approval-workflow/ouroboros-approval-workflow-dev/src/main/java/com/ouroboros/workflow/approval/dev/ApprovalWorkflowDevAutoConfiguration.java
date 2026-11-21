package com.ouroboros.workflow.approval.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class ApprovalWorkflowDevAutoConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalWorkflowDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    LOGGER.info("Approval Workflow Dev Auto Configuration loaded");
  }
}
