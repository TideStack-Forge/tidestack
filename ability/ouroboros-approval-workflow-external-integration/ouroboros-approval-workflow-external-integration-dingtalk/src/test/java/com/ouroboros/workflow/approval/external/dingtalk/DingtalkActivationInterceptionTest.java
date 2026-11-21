package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

/**
 * M17 激活拦截测试：钉钉默认稳定能力只允许 RESULT_ONLY，FULL_PROCESS 不进入默认 provider。
 */
class DingtalkActivationInterceptionTest {

  private static final DingtalkCredential TEST_CREDENTIAL =
      DingtalkCredential.internalApp("app-key", "app-secret", "callback-token", "encoding-aes-key");

  private WorkflowEngineAdapterRegistry registry;
  private EnterpriseConnectionCatalog connectionConfigProvider;
  private WorkflowEngineMappingValidator validator;

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(new DingtalkEngineAdapterProvider()));
    connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    when(workflowModelService.getByPrimaryKey("flow-a"))
        .thenReturn(new com.ouroboros.workflow.approval.manage.entity.WorkflowModel());
    ConnectionAwareAdapterResolver adapterResolver = new ConnectionAwareAdapterResolver(registry, connectionConfigProvider);
    validator = new WorkflowEngineMappingValidator(
        registry, adapterResolver, workflowModelService);
  }

  @Test
  void fullProcessIsRejectedByDefaultProviderCapabilities() {
    when(connectionConfigProvider.resolve("conn-1", "dingtalk"))
        .thenReturn(Optional.of(new ResolvedEnterpriseConnection(
            "conn-1", Map.of("provider", "dingtalk"), TEST_CREDENTIAL)));
    WorkflowEngineMapping mapping = mapping();
    mapping.setActionMappingsJson("""
        [{"systemAction":"approve","externalAction":"agree"}]
        """);

    // 默认钉钉 provider 没有任务查询端口 → 派生 RESULT_ONLY，任务动作映射必须被拒绝。
    assertThat(validator.integrationModeOf(mapping)).isEqualTo("RESULT_ONLY");
    assertThat(validator.run(mapping).getCause().getMessage())
        .contains("RESULT_ONLY 不允许配置任务动作映射");
  }

  @Test
  void resultOnlyMappingPassesDescriptorConstraints() {
    when(connectionConfigProvider.resolve("conn-1", "dingtalk"))
        .thenReturn(Optional.of(new ResolvedEnterpriseConnection(
            "conn-1", Map.of("provider", "dingtalk"), TEST_CREDENTIAL)));
    WorkflowEngineMapping mapping = mapping();

    var result = validator.run(mapping);

    // 描述符约束通过：失败只能发生在后续定义校验（钉钉客户端无凭据时定义校验失败），
    // 绝不能是 FULL_PROCESS 门禁。
    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause().getMessage()).doesNotContain("FULL_PROCESS");
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setScope("default");
    m.setFlowKey("flow-a");
    m.setAdapterId(DingtalkEngineAdapterProvider.ADAPTER_ID);
    m.setConnectionId("conn-1");
    m.setExternalDefinitionNamespace("corp-a");
    m.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    m.setExternalDefinitionId("PROC_EXPENSE");
    m.setResultMappingsJson("""
        [
          {"externalStatus":"RUNNING","systemState":"PROCESSING"},
          {"externalStatus":"APPROVED","outcome":"approved"},
          {"externalStatus":"REJECTED","outcome":"rejected"},
          {"externalStatus":"CANCELED","outcome":"cancelled"},
          {"externalStatus":"ERROR","outcome":"failed"}
        ]
        """);
    return m;
  }
}
