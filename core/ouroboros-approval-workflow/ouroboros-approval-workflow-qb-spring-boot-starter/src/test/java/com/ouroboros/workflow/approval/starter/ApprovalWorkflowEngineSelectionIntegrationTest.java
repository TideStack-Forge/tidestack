package com.ouroboros.workflow.approval.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import com.ouroboros.AppContext;
import com.ouroboros.dynamicform.DynamicFormAutoConfiguration;
import com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.ApprovalWorkflowAutoConfiguration;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.dynamicform.ApprovalWorkflowDynamicFormAutoConfiguration;
import com.ouroboros.workflow.approval.flowable.ApprovalWorkflowFlowableAutoConfiguration;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowDefinitionService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowInstanceService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowManageService;
import com.ouroboros.workflow.approval.flowable.service.FlowableFlowTaskService;
import com.ouroboros.workflow.approval.manage.ApprovalWorkflowManageAutoConfiguration;
import com.ouroboros.workflow.approval.web.ApprovalWorkflowWebAutoConfiguration;

class ApprovalWorkflowEngineSelectionIntegrationTest {
  private static final String ACTIVITI_AUTO_CONFIGURATION =
      "com.ouroboros.workflow.approval.activiti6.ApprovalWorkflowActiviti6AutoConfiguration";
  private static final String ACTIVITI_PROCESS_ENGINE_CONFIGURATION =
      "org.activiti.spring.SpringProcessEngineConfiguration";

  @TempDir
  Path tempDir;

  @Test
  void shouldExposeOnlyFlowableEngineAutoConfigurationOnStarterClasspath() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader());

    assertTrue(factoryNames.contains(ApprovalWorkflowFlowableAutoConfiguration.class.getName()));
    assertFalse(factoryNames.contains(ACTIVITI_AUTO_CONFIGURATION));
  }

  @Test
  void shouldLoadOnlyFlowableImplementationWhenEngineIsFlowable() {
    contextRunner()
        .withPropertyValues("ouroboros.workflow.approval.engine=flowable")
        .withConfiguration(AutoConfigurations.of(
            ApprovalWorkflowAutoConfiguration.class,
            ApprovalWorkflowQbAdapterAutoConfiguration.class,
            AopAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            ApprovalWorkflowFlowableAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertFlowableOnly(context);
          assertActivitiTypesAbsent(context);
        });
  }

  @Test
  void shouldDefaultToFlowableImplementationWhenEnginePropertyIsMissing() {
    contextRunner()
        .withConfiguration(AutoConfigurations.of(
            ApprovalWorkflowAutoConfiguration.class,
            ApprovalWorkflowQbAdapterAutoConfiguration.class,
            AopAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            ApprovalWorkflowFlowableAutoConfiguration.class))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertFlowableOnly(context);
          assertActivitiTypesAbsent(context);
        });
  }

  @Test
  void shouldLoadRealStarterAutoConfigurationSurfaceWithoutInactiveEngineOnClasspath() {
    contextRunnerWithoutFlowFormService()
        .withBean(AppContext.class, () -> new AppContext() {
          @Override
          public String getHomePath() {
            return tempDir.toString();
          }
        })
        .withConfiguration(AutoConfigurations.of(
            ApprovalWorkflowAutoConfiguration.class,
            ApprovalWorkflowQbAdapterAutoConfiguration.class,
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
          assertFlowableOnly(context);
          assertEquals(1, context.getBeansOfType(FlowFormService.class).size());
          assertEquals(1, context.getBeansOfType(FlowFormProvider.class).size());
          assertActivitiTypesAbsent(context);
        });
  }

  /**
   * web 层 ApprovalWorkflowInstanceController 依赖 StartRequirementsResolver，而它的内置实现
   * （DefaultStartRequirementsResolver）注册在 approval-workflow-runtime 的自动配置里。
   *
   * <p>本测试只验证"starter 装配面 + 引擎选择"，不重复验证 runtime 的 Bean 装配
   * （那由 ApprovalWorkflowAdapterAutoConfigurationContextTest 覆盖），因此提供一个最小桩，
   * 避免为了一个 Bean 引入整条平台装配（rbac / 组织架构 / logicflow）。
   */
  private static StartRequirementsResolver stubStartRequirementsResolver() {
    return (flowKey, formData) -> io.vavr.control.Try.success(StartRequirements.none(true));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
        .withBean(FlowFormService.class, () -> mock(FlowFormService.class))
        .withBean(StartRequirementsResolver.class,
            ApprovalWorkflowEngineSelectionIntegrationTest::stubStartRequirementsResolver)
        .withBean("flowableHistoryService", org.flowable.engine.HistoryService.class, () -> mock(org.flowable.engine.HistoryService.class))
        .withBean("flowableRepositoryService", org.flowable.engine.RepositoryService.class, () -> mock(org.flowable.engine.RepositoryService.class))
        .withBean("flowableRuntimeService", org.flowable.engine.RuntimeService.class, () -> mock(org.flowable.engine.RuntimeService.class))
        .withBean("flowableTaskService", org.flowable.engine.TaskService.class, () -> mock(org.flowable.engine.TaskService.class))
        .withBean("flowableProcessEngineConfiguration", org.flowable.spring.SpringProcessEngineConfiguration.class,
            () -> mock(org.flowable.spring.SpringProcessEngineConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
  }

  private ApplicationContextRunner contextRunnerWithoutFlowFormService() {
    return new ApplicationContextRunner()
        .withBean(ApprovalCaseService.class, () -> mock(ApprovalCaseService.class))
        .withBean(StartRequirementsResolver.class,
            ApprovalWorkflowEngineSelectionIntegrationTest::stubStartRequirementsResolver)
        .withBean("flowableHistoryService", org.flowable.engine.HistoryService.class, () -> mock(org.flowable.engine.HistoryService.class))
        .withBean("flowableRepositoryService", org.flowable.engine.RepositoryService.class, () -> mock(org.flowable.engine.RepositoryService.class))
        .withBean("flowableRuntimeService", org.flowable.engine.RuntimeService.class, () -> mock(org.flowable.engine.RuntimeService.class))
        .withBean("flowableTaskService", org.flowable.engine.TaskService.class, () -> mock(org.flowable.engine.TaskService.class))
        .withBean("flowableProcessEngineConfiguration", org.flowable.spring.SpringProcessEngineConfiguration.class,
            () -> mock(org.flowable.spring.SpringProcessEngineConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
  }

  private void assertFlowableOnly(AssertableApplicationContext context) {
    assertEquals(1, context.getBeansOfType(FlowManageService.class).size());
    assertEquals(1, context.getBeansOfType(FlowDefinitionService.class).size());
    assertEquals(1, context.getBeansOfType(FlowInstanceService.class).size());
    assertEquals(1, context.getBeansOfType(FlowTaskService.class).size());
    assertEquals(1, context.getBeansOfType(FlowableFlowManageService.class).size());
    assertEquals(1, context.getBeansOfType(FlowableFlowDefinitionService.class).size());
    assertEquals(1, context.getBeansOfType(FlowableFlowInstanceService.class).size());
    assertEquals(1, context.getBeansOfType(FlowableFlowTaskService.class).size());
  }

  private void assertActivitiTypesAbsent(AssertableApplicationContext context) {
    ClassLoader classLoader = context.getClassLoader();
    assertFalse(ClassUtils.isPresent(ACTIVITI_AUTO_CONFIGURATION, classLoader));
    assertFalse(ClassUtils.isPresent(ACTIVITI_PROCESS_ENGINE_CONFIGURATION, classLoader));
  }
}
