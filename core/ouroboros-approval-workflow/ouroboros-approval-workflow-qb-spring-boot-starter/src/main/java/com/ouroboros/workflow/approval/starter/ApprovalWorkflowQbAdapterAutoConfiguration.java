package com.ouroboros.workflow.approval.starter;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;

@Configuration
@DataModelScan
public class ApprovalWorkflowQbAdapterAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowQbAdapterAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Qb Adapter Auto Configuration loaded");
  }
}
