package com.ouroboros.workflow.approval.activiti6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.activiti6.service.Activiti6FlowDefinitionService;
import com.ouroboros.workflow.approval.activiti6.service.Activiti6FlowInstanceService;
import com.ouroboros.workflow.approval.activiti6.service.Activiti6FlowManageService;
import com.ouroboros.workflow.approval.activiti6.service.Activiti6FlowTaskService;

class ApprovalWorkflowActiviti6AutoConfigurationContextTest {

  @Test
  void shouldRegisterDispatchersTogetherWithEngineServicesWhenEngineIsActiviti6() {
    contextRunner()
        .withPropertyValues("ouroboros.workflow.approval.engine=activiti6")
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowActiviti6AutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(1, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(Activiti6FlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(Activiti6FlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(Activiti6FlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(Activiti6FlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(InstanceEventDispatcher.class).size());
          assertEquals(1, context.getBeansOfType(TaskEventDispatcher.class).size());
        });
  }

  @Test
  void shouldNotRegisterActiviti6ServicesWhenEngineUsesLegacyAlias() {
    contextRunner()
        .withPropertyValues("ouroboros.workflow.approval.engine=activiti")
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowActiviti6AutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(0, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(0, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(0, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(0, context.getBeansOfType(FlowTaskService.class).size());
          assertEquals(0, context.getBeansOfType(InstanceEventDispatcher.class).size());
          assertEquals(0, context.getBeansOfType(TaskEventDispatcher.class).size());
        });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withBean(TaskService.class, () -> mock(TaskService.class))
        .withBean(RuntimeService.class, () -> mock(RuntimeService.class))
        .withBean(RepositoryService.class, () -> mock(RepositoryService.class))
        .withBean(HistoryService.class, () -> mock(HistoryService.class))
        .withBean(FormService.class, () -> mock(FormService.class))
        .withBean(SpringProcessEngineConfiguration.class, () -> mock(SpringProcessEngineConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
  }
}
