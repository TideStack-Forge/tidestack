package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.external.runtime.routing.ExternalBusinessStateInspection;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedBusinessBindingValidator;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventRetrySweeper;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalPollingCompensationJob;
import com.ouroboros.workflow.approval.external.runtime.mapping.MappingVersionCleanupJob;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

/**
 * 外部集成调度开关测试：模块装配默认不启用补偿作业，显式打开才注册调度 bean。
 */
class ApprovalWorkflowExternalIntegrationSchedulingConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withAllowBeanDefinitionOverriding(true)
      .withConfiguration(AutoConfigurations.of(ApprovalWorkflowExternalIntegrationAutoConfiguration.class,
          com.ouroboros.enterprise.runtime.EnterpriseConnectorAutoConfiguration.class,
          com.ouroboros.security.externalauth.ExternalAuthAutoConfiguration.class))
      .withBean(WorkflowModelService.class, () -> mock(WorkflowModelService.class))
      .withBean(InstanceEventDispatcher.class, () -> mock(InstanceEventDispatcher.class))
      .withBean(TaskEventDispatcher.class, () -> mock(TaskEventDispatcher.class))
      .withBean(BuiltInFlowDefinitionService.class, () -> mock(BuiltInFlowDefinitionService.class))
      .withBean(BuiltInFlowInstanceService.class, () -> mock(BuiltInFlowInstanceService.class))
      .withBean(BuiltInFlowTaskService.class, () -> mock(BuiltInFlowTaskService.class))
      .withBean(BuiltInFlowManageService.class, () -> mock(BuiltInFlowManageService.class))
      .withBean(BuiltInApprovalBusinessBindingValidator.class, () -> mock(BuiltInApprovalBusinessBindingValidator.class))
      .withBean(BuiltInFlowTaskFactContextProvider.class, () -> mock(BuiltInFlowTaskFactContextProvider.class))
      .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
      .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
      .withBean(ConfigurationService.class, () -> mock(ConfigurationService.class))
      .withBean(com.ouroboros.configuration.management.ConfigurationManagementService.class,
          () -> mock(com.ouroboros.configuration.management.ConfigurationManagementService.class));

  @Test
  void scheduledJobsAreNotRegisteredByDefault() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(ExternalBusinessStateInspection.class);
      assertThat(context.getBean(ApprovalBusinessBindingValidator.class)).isInstanceOf(RoutedBusinessBindingValidator.class);
      assertThat(context).doesNotHaveBean(ExternalEventRetrySweeper.class);
      assertThat(context).doesNotHaveBean(ExternalPollingCompensationJob.class);
      assertThat(context).doesNotHaveBean(MappingVersionCleanupJob.class);
      assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
    });
  }

  @Test
  void scheduledJobsAreRegisteredOnlyWhenExplicitlyEnabled() {
    contextRunner
        .withPropertyValues("ouroboros.workflow.approval.external.scheduling.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(ExternalEventRetrySweeper.class);
          assertThat(context).hasSingleBean(ExternalPollingCompensationJob.class);
          assertThat(context).hasSingleBean(MappingVersionCleanupJob.class);
          assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
        });
  }
}
