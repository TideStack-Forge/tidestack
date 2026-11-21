package com.ouroboros.workflow.approval.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import com.ouroboros.AppContext;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.dynamicform.DynamicFormAutoConfiguration;
import com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.webapi.WebAPIModelCatalog;
import com.ouroboros.workflow.approval.ApprovalWorkflowAutoConfiguration;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.CandidateProvider;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.dynamicform.ApprovalWorkflowDynamicFormAutoConfiguration;
import com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.event.TaskEventDispatcher;
import com.ouroboros.workflow.approval.flowable.ApprovalWorkflowFlowableAutoConfiguration;
import com.ouroboros.workflow.approval.manage.ApprovalWorkflowManageAutoConfiguration;
import com.ouroboros.workflow.approval.provider.PositionCandidateProvider;
import com.ouroboros.workflow.approval.provider.RoleCandidateProvider;
import com.ouroboros.workflow.approval.provider.FormFieldCandidateProvider;
import com.ouroboros.workflow.approval.provider.StarterCandidateProvider;
import com.ouroboros.workflow.approval.provider.UserCandidateProvider;
import com.ouroboros.workflow.approval.provider.UserSelectCandidateProvider;
import com.ouroboros.workflow.approval.resolver.EmployeeUsernameResolver;
import com.ouroboros.workflow.approval.resolver.RoleCandidateUserResolver;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackBindingCatalog;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackListener;
import com.ouroboros.workflow.approval.runtime.controller.ApprovalWorkflowDynamicFormRecordController;
import com.ouroboros.workflow.approval.runtime.notification.ApprovalNotificationListener;
import com.ouroboros.workflow.approval.web.ApprovalWorkflowWebAutoConfiguration;
import com.ouroboros.workflow.approval.web.user.CandidateUserResolverRegistry;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

class ApprovalWorkflowAdapterAutoConfigurationContextTest {
  private static final String ACTIVITI_AUTO_CONFIGURATION =
      "com.ouroboros.workflow.approval.activiti6.ApprovalWorkflowActiviti6AutoConfiguration";
  private static final String ACTIVITI_PROCESS_ENGINE_CONFIGURATION =
      "org.activiti.spring.SpringProcessEngineConfiguration";

  @TempDir
  Path tempDir;

  @Test
  void shouldExposeCoreApprovalAutoConfigurationOnRuntimeClasspath() {
    var factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader());

    assertEquals(true, factoryNames.contains(ApprovalWorkflowAutoConfiguration.class.getName()));
  }

  @Test
  void shouldRegisterOnlyRuntimeCandidateBeans() {
    new ApplicationContextRunner()
        .withBean(EmployeeAggregator.class, () -> mock(EmployeeAggregator.class))
        .withBean(OrganizationAggregator.class, () -> mock(OrganizationAggregator.class))
        .withBean(RoleCenter.class, () -> mock(RoleCenter.class))
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowAdapterAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(6, context.getBeansOfType(CandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(PositionCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(StarterCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(FormFieldCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(UserCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(RoleCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(UserSelectCandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(EmployeeUsernameResolver.class).size());
          assertEquals(1, context.getBeansOfType(RoleCandidateUserResolver.class).size());
          assertEquals(0, context.getBeansOfType(ApprovalNotificationListener.class).size());
          assertEquals(0, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(0, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(0, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(0, context.getBeansOfType(FlowTaskService.class).size());
        });
  }

  @Test
  void shouldLoadRealRuntimeAutoConfigurationSurfaceWithDefaultFlowableEngine() {
    new ApplicationContextRunner()
        .withBean(AppContext.class, () -> new AppContext() {
          @Override
          public String getHomePath() {
            return tempDir.toString();
          }
        })
        .withBean(EmployeeAggregator.class, () -> mock(EmployeeAggregator.class))
        .withBean(OrganizationAggregator.class, () -> mock(OrganizationAggregator.class))
        .withBean(RoleCenter.class, () -> mock(RoleCenter.class))
        .withBean("flowableHistoryService", org.flowable.engine.HistoryService.class, () -> mock(org.flowable.engine.HistoryService.class))
        .withBean("flowableRepositoryService", org.flowable.engine.RepositoryService.class, () -> mock(org.flowable.engine.RepositoryService.class))
        .withBean("flowableRuntimeService", org.flowable.engine.RuntimeService.class, () -> mock(org.flowable.engine.RuntimeService.class))
        .withBean("flowableTaskService", org.flowable.engine.TaskService.class, () -> mock(org.flowable.engine.TaskService.class))
        .withBean("flowableProcessEngineConfiguration", org.flowable.spring.SpringProcessEngineConfiguration.class,
            () -> mock(org.flowable.spring.SpringProcessEngineConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withConfiguration(AutoConfigurations.of(
            ApprovalWorkflowAutoConfiguration.class,
            ApprovalWorkflowAdapterAutoConfiguration.class,
            ApprovalWorkflowFlowableAutoConfiguration.class,
            ApprovalWorkflowWebAutoConfiguration.class,
            ApprovalWorkflowDynamicFormAutoConfiguration.class,
            ApprovalWorkflowManageAutoConfiguration.class,
            DynamicFormAutoConfiguration.class,
            FieldTypeRuntimeAutoConfiguration.class,
            AopAutoConfiguration.class,
            TransactionAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(6, context.getBeansOfType(CandidateProvider.class).size());
          assertEquals(1, context.getBeansOfType(FlowManageService.class).size());
          assertEquals(1, context.getBeansOfType(FlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(FlowInstanceService.class).size());
          assertEquals(1, context.getBeansOfType(FlowTaskService.class).size());
          assertEquals(1, context.getBeansOfType(FlowFormService.class).size());
          assertEquals(1, context.getBeansOfType(ApprovalWorkflowDynamicFormRecordController.class).size());
          assertEquals(2, context.getBeansOfType(FlowFormProvider.class).size());
          assertEquals(1, context.getBeansOfType(InstanceEventDispatcher.class).size());
          assertEquals(1, context.getBeansOfType(TaskEventDispatcher.class).size());
          assertEquals(1, context.getBeansOfType(UsernameResolverRegistry.class).size());
          assertEquals(1, context.getBeansOfType(CandidateUserResolverRegistry.class).size());
          assertEquals(1, context.getBeansOfType(BuiltInFlowDefinitionService.class).size());
          assertEquals(1, context.getBeansOfType(BuiltInStartRequirementsResolver.class).size());
          assertEquals(1, context.getBeansOfType(StartRequirementsResolver.class).size());
          assertFalse(ClassUtils.isPresent(ACTIVITI_AUTO_CONFIGURATION, context.getClassLoader()));
          assertFalse(ClassUtils.isPresent(ACTIVITI_PROCESS_ENGINE_CONFIGURATION, context.getClassLoader()));
        });
  }

  @Test
  void shouldRegisterNotificationListenerWhenNotificationDispatcherExists() {
    new ApplicationContextRunner()
        .withBean(EmployeeAggregator.class, () -> mock(EmployeeAggregator.class))
        .withBean(OrganizationAggregator.class, () -> mock(OrganizationAggregator.class))
        .withBean(RoleCenter.class, () -> mock(RoleCenter.class))
        .withBean(EmployeeNotificationDispatcher.class, () -> mock(EmployeeNotificationDispatcher.class))
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowAdapterAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(1, context.getBeansOfType(ApprovalNotificationListener.class).size());
        });
  }

  @Test
  void shouldRegisterProcessCallbackListenerWhenRuntimeDependenciesExist() {
    new ApplicationContextRunner()
        .withBean(EmployeeAggregator.class, () -> mock(EmployeeAggregator.class))
        .withBean(OrganizationAggregator.class, () -> mock(OrganizationAggregator.class))
        .withBean(RoleCenter.class, () -> mock(RoleCenter.class))
        .withBean(WebAPIModelCatalog.class, () -> mock(WebAPIModelCatalog.class))
        .withBean(LogicFlowExecutionFacade.class, () -> mock(LogicFlowExecutionFacade.class))
        .withBean(FlowModelProvider.class, () -> mock(FlowModelProvider.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withConfiguration(AutoConfigurations.of(ApprovalWorkflowAdapterAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertEquals(1, context.getBeansOfType(ApprovalProcessCallbackBindingCatalog.class).size());
          assertEquals(1, context.getBeansOfType(ApprovalProcessCallbackListener.class).size());
        });
  }
}
