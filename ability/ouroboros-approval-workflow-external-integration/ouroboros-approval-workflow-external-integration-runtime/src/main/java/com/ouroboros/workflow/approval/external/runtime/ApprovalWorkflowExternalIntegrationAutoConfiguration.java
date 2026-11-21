package com.ouroboros.workflow.approval.external.runtime;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.workflow.approval.external.runtime.scope.DefaultWorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.web.ExternalBusinessRecordPermissionGuard;
import com.ouroboros.workflow.approval.external.runtime.web.FailClosedExternalBusinessRecordPermissionGuard;

/**
 * 审批流外部引擎对接运行时自动配置。
 */
@Configuration
@DataModelScan
@ComponentScan(basePackageClasses = ApprovalWorkflowExternalIntegrationRuntimeMarker.class)
@Import(ApprovalWorkflowExternalIntegrationAutoConfiguration.ExternalIntegrationSchedulingConfiguration.class)
public class ApprovalWorkflowExternalIntegrationAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowExternalIntegrationAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow External Integration Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnMissingBean(WorkflowScopeResolver.class)
  WorkflowScopeResolver defaultWorkflowScopeResolver() {
    return new DefaultWorkflowScopeResolver();
  }

  @Bean
  @ConditionalOnMissingBean(ExternalBusinessRecordPermissionGuard.class)
  ExternalBusinessRecordPermissionGuard failClosedExternalBusinessRecordPermissionGuard() {
    return new FailClosedExternalBusinessRecordPermissionGuard();
  }

  @Configuration
  @EnableScheduling
  @ConditionalOnProperty(
      prefix = "ouroboros.workflow.approval.external.scheduling",
      name = "enabled",
      havingValue = "true")
  static class ExternalIntegrationSchedulingConfiguration {
  }
}
