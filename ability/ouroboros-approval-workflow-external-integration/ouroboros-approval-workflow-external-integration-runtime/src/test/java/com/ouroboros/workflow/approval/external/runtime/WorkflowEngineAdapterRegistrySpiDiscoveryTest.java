package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.BuiltInFlowInstanceService;
import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.BuiltInFlowTaskService;
import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

import io.vavr.control.Try;

/**
 * M2 SPI 发现集成测试：验证测试资源 {@code META-INF/services/...ApprovalWorkflowEngineAdapterProvider}
 * 中注册的 {@link SpiDiscoveredTestProvider} 能被 Registry 发现并装配。
 */
@SpringBootTest(classes = {ApprovalWorkflowExternalIntegrationAutoConfiguration.class,
    com.ouroboros.enterprise.runtime.EnterpriseConnectorAutoConfiguration.class,
    com.ouroboros.security.externalauth.ExternalAuthAutoConfiguration.class})
@Import(WorkflowEngineAdapterRegistrySpiDiscoveryTest.MockDependencyConfig.class)
class WorkflowEngineAdapterRegistrySpiDiscoveryTest {

  // Imported explicitly: the runtime's component scan must not discover this fixture in other tests.
  static class MockDependencyConfig {
    @Bean
    BuiltInApprovalBusinessBindingValidator builtInApprovalBusinessBindingValidator() {
      return mock(BuiltInApprovalBusinessBindingValidator.class);
    }

    @Bean
    ApprovalCaseService approvalCaseService() {
      return mock(ApprovalCaseService.class);
    }

    @Bean
    BuiltInFlowTaskFactContextProvider builtInFlowTaskFactContextProvider() {
      return mock(BuiltInFlowTaskFactContextProvider.class);
    }

    @Bean
    com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider builtInTaskFacts() {
      return mock(com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider.class);
    }

    @Bean
    com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator builtInBindingValidator() {
      return mock(com.ouroboros.workflow.approval.BuiltInApprovalBusinessBindingValidator.class);
    }

    @Bean
    com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService approvalCaseService() {
      return mock(com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.class);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    WorkflowModelService workflowModelService() {
      return mock(WorkflowModelService.class);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    ExternalResultBindingService externalResultBindingService() {
      return mock(ExternalResultBindingService.class);
    }

    @Bean
    InstanceEventDispatcher instanceEventDispatcher() {
      return mock(InstanceEventDispatcher.class);
    }

    @Bean
    com.ouroboros.workflow.approval.event.TaskEventDispatcher taskEventDispatcher() {
      return mock(com.ouroboros.workflow.approval.event.TaskEventDispatcher.class);
    }

    @Bean
    BuiltInFlowDefinitionService flowDefinitionService() {
      return mock(BuiltInFlowDefinitionService.class);
    }

    @Bean
    BuiltInFlowInstanceService flowInstanceService() {
      return mock(BuiltInFlowInstanceService.class);
    }

    @Bean
    BuiltInFlowTaskService flowTaskService() {
      return mock(BuiltInFlowTaskService.class);
    }

    @Bean
    BuiltInFlowManageService flowManageService() {
      return mock(BuiltInFlowManageService.class);
    }

    @Bean
    org.springframework.transaction.PlatformTransactionManager platformTransactionManager() {
      return mock(org.springframework.transaction.PlatformTransactionManager.class);
    }

    @Bean
    com.ouroboros.configuration.management.ConfigurationManagementService configurationManagementService() {
      return mock(com.ouroboros.configuration.management.ConfigurationManagementService.class);
    }

    @Bean
    com.ouroboros.configuration.ConfigurationService configurationService() {
      return mock(com.ouroboros.configuration.ConfigurationService.class);
    }
  }

  @Autowired
  WorkflowEngineAdapterRegistry registry;

  @Test
  void spiDiscoveredProviderIsRegistered() {
    assertThat(registry.hasAdapter("spi-test-adapter")).isTrue();
    assertThat(registry.capabilitiesOf("spi-test-adapter"))
        .containsExactlyInAnyOrder(Capability.EXTERNAL_START);
    assertThat(registry.getAdapter("spi-test-adapter").orElseThrow().descriptor().displayName())
        .isEqualTo("SPI 测试适配器");
  }

  /** 通过 SPI 服务文件注册的测试 provider（测试资源）。 */
  public static final class SpiDiscoveredTestProvider implements ApprovalWorkflowEngineAdapterProvider {

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("spi-test-adapter", "SPI 测试适配器", "1.0", Set.of("approve"), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new SpiDiscoveredEngineAdapter();
    }
  }

  /** SPI 测试适配器：实现启动端口，验证能力派生。 */
  static final class SpiDiscoveredEngineAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalWorkflowStarter {

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalStartResult> start(ExternalStartCommand command) {
      return Try.success(new ExternalStartResult("ext-" + command.idempotencyKey(), null, null));
    }
  }
}
