package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ouroboros.workflow.approval.BuiltInFlowManageService;
import com.ouroboros.workflow.approval.FlowDeploymentResult;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowManageService;

import io.vavr.control.Try;

/**
 * M5 部署路由测试：外部权威映射只验证定义不部署内置，无映射委托内置。
 */
class RoutedFlowManageServiceTest {

  private BuiltInFlowManageService internal;
  private WorkflowEngineMappingService mappingService;
  private WorkflowEngineAdapterRegistry registry;
  private com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver scopeResolver;
  private RoutedFlowManageService routed;

  @BeforeEach
  void setUp() {
    internal = mock(BuiltInFlowManageService.class);
    mappingService = mock(WorkflowEngineMappingService.class);
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(new FullCatalogProvider()));
    scopeResolver = mock(com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    FlowManageObjectProvider provider = mock(FlowManageObjectProvider.class);
    when(provider.stream()).thenReturn(Stream.of(internal));
    routed = new RoutedFlowManageService(
        provider, mappingService, ConnectionAwareAdapterResolverTestSupport.resolver(registry), scopeResolver);
  }

  interface FlowManageObjectProvider extends ObjectProvider<BuiltInFlowManageService> {
  }

  @Test
  void noMappingDelegatesToInternal() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.empty());
    when(internal.deployForRuntime("flow-a", "{}")).thenReturn(Try.success(FlowDeploymentResult.builtIn("pd-1")));

    Try<FlowDeploymentResult> result = routed.deployForRuntime("flow-a", "{}");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().executionMode()).isEqualTo("BUILT_IN");
    assertThat(result.get().processDefinitionId()).isEqualTo("pd-1");
  }

  @Test
  void externalAuthorityMappingValidatesDefinitionOnly() {
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(mapping()));

    Try<FlowDeploymentResult> result = routed.deployForRuntime("flow-a", "{}");

    assertThat(result.isSuccess()).isTrue();
    FlowDeploymentResult deployment = result.get();
    assertThat(deployment.executionMode()).isEqualTo("EXTERNAL");
    assertThat(deployment.integrationMode()).isEqualTo("RESULT_ONLY");
    assertThat(deployment.authority()).isEqualTo("EXTERNAL_ENGINE");
    assertThat(deployment.adapterId()).isEqualTo("full-catalog");
    assertThat(deployment.mappingId()).isEqualTo("m-1");
    assertThat(deployment.processDefinitionId()).isNull();
    assertThat(deployment.externalDefinitionRef()).isEqualTo("DEF-1");
    verify(internal, never()).deployForRuntime("flow-a", "{}");
  }

  @Test
  void ouRoborosAuthorityMappingDelegatesToInternal() {
    WorkflowEngineMapping m = mapping();
    m.setAuthority("OUROBOROS");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(m));
    when(internal.deployForRuntime("flow-a", "{}")).thenReturn(Try.success(FlowDeploymentResult.builtIn("pd-1")));

    Try<FlowDeploymentResult> result = routed.deployForRuntime("flow-a", "{}");

    assertThat(result.get().executionMode()).isEqualTo("BUILT_IN");
    verify(internal).deployForRuntime("flow-a", "{}");
  }

  @Test
  void mirrorDeploymentKeepsBuiltInProcessIdAndRecordsExternalProjectionIdentity() {
    WorkflowEngineMapping m = mapping();
    m.setAuthority("OUROBOROS");
    when(mappingService.findActive("default", "flow-a")).thenReturn(Optional.of(m));
    when(internal.deployForRuntime("flow-a", "{}")).thenReturn(Try.success(FlowDeploymentResult.builtIn("pd-1")));

    FlowDeploymentResult result = routed.deployForRuntime("flow-a", "{}").get();

    assertThat(result.executionMode()).isEqualTo("BUILT_IN");
    assertThat(result.authority()).isEqualTo("OUROBOROS");
    assertThat(result.processDefinitionId()).isEqualTo("pd-1");
    assertThat(result.adapterId()).isEqualTo("full-catalog");
    assertThat(result.mappingId()).isEqualTo("m-1");
    assertThat(result.mappingVersion()).isEqualTo(1);
    assertThat(result.externalDefinitionRef()).isEqualTo("DEF-1");
    verify(internal).deployForRuntime("flow-a", "{}");
  }

  @Test
  void deployForRuntimeConsultsResolvedScope() {
    when(scopeResolver.resolve()).thenReturn("scope-test");
    when(mappingService.findActive("scope-test", "flow-a")).thenReturn(Optional.empty());
    when(internal.deployForRuntime("flow-a", "{}")).thenReturn(Try.success(FlowDeploymentResult.builtIn("pd-1")));

    routed.deployForRuntime("flow-a", "{}");

    verify(mappingService).findActive("scope-test", "flow-a");
  }

  private WorkflowEngineMapping mapping() {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId("m-1");
    m.setScope("default");
    m.setFlowKey("flow-a");
    m.setAdapterId("full-catalog");
    m.setConnectionId("conn-1");
    m.setExternalDefinitionNamespace("corp-a");
    m.setAuthority("EXTERNAL_ENGINE");
    m.setExternalDefinitionId("DEF-1");
    m.setVersion(1);
    m.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return m;
  }

  /** 带 catalog 端口的测试适配器。 */
  static final class FullCatalogProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("full-catalog", "catalog", "1.0", Set.of("approve"), Set.of(), Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new WorkflowEngineMappingValidatorTest.FullTestAdapter();
    }
  }
}
