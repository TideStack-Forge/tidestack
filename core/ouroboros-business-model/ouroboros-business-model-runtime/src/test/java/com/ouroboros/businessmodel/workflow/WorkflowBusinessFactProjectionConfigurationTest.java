package com.ouroboros.businessmodel.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventProducer;
import com.ouroboros.businessmodel.event.BusinessEventRuntimeAutoConfiguration;
import com.ouroboros.businessmodel.workflow.store.WorkflowBusinessFactStore;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowBusinessFactProjectionConfigurationTest {
  @Test
  void businessEventRuntimeWithoutApprovalDoesNotAddAWorkflowProducer() {
    base().run(context -> assertThat(context).hasNotFailed().hasSingleBean(BusinessEventPort.class)
        .doesNotHaveBean(WorkflowBusinessFactStore.class).doesNotHaveBean(ApprovalBusinessFactProjection.class)
        .doesNotHaveBean(BusinessEventProducer.class));
  }

  @Test
  void approvalRuntimeInstallsOneFactOwnerAndSourceGuardedProducerThroughAutoConfiguration() {
    approval().run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(BusinessEventPort.class)
          .hasSingleBean(WorkflowBusinessFactStore.class).hasSingleBean(ApprovalBusinessFactProjection.class)
          .hasSingleBean(BusinessEventProducer.class);
      assertThat(context.getBean(ApprovalBusinessFactProjection.class)).isInstanceOf(WorkflowBusinessFactProjection.class);
      assertThat(context.getBean(BusinessEventProducer.class).getKind()).isEqualTo("workflow");
    });
  }

  @Test
  void explicitFactOwnerUsesTheExistingExtensionWithoutCreatingASecondOwner() {
    var owner = mock(ApprovalBusinessFactProjection.class);
    approval().withBean(ApprovalBusinessFactProjection.class, () -> owner).run(context -> {
      assertThat(context).hasNotFailed().hasSingleBean(ApprovalBusinessFactProjection.class);
      assertThat(context.getBean(ApprovalBusinessFactProjection.class)).isSameAs(owner);
    });
  }

  @Test
  void workflowNotificationReadPortRequiresTheExistingTaskDefinitionAndDisclosureServices() {
    approval().run(context -> assertThat(context).doesNotHaveBean(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class));
    approval().withBean(com.ouroboros.workflow.approval.FlowTaskService.class, () -> mock(com.ouroboros.workflow.approval.FlowTaskService.class))
        .withBean(com.ouroboros.workflow.approval.FlowDefinitionService.class, () -> mock(com.ouroboros.workflow.approval.FlowDefinitionService.class))
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(com.ouroboros.businessmodel.event.BusinessWorkflowNotificationSource.class));
  }

  private ApplicationContextRunner base() {
    return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(BusinessEventRuntimeAutoConfiguration.class,
        WorkflowBusinessFactProjectionAutoConfiguration.class)).withBean(org.springframework.transaction.PlatformTransactionManager.class,
        () -> new DataSourceTransactionManager(new org.h2.jdbcx.JdbcDataSource()));
  }

  private ApplicationContextRunner approval() {
    return base().withBean(FlowEventOutboxService.class, () -> mock(FlowEventOutboxService.class))
        .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
        .withBean(FlowInstanceFactStateService.class, () -> mock(FlowInstanceFactStateService.class));
  }
}
