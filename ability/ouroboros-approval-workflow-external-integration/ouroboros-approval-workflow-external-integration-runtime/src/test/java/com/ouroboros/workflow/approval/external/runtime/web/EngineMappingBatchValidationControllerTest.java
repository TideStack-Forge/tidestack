package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.web.vo.BatchValidationReportVO;

import io.vavr.control.Try;

/**
 * M21-T1 批量验证控制器测试：全过/单项失败不中断/连接缺失/健康失败/只读性（不调用落库 validate）。
 */
class EngineMappingBatchValidationControllerTest {

  private WorkflowEngineMappingService mappingService;
  private WorkflowEngineAdapterRegistry registry;
  private EnterpriseConnectionCatalog connectionConfigProvider;
  private ConnectionAwareAdapterResolver adapterResolver;
  private WorkflowScopeResolver scopeResolver;
  private EngineMappingBatchValidationController controller;
  private RegisteredAdapter registered;

  @BeforeEach
  void setUp() {
    mappingService = mock(WorkflowEngineMappingService.class);
    registry = mock(WorkflowEngineAdapterRegistry.class);
    connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    adapterResolver = new ConnectionAwareAdapterResolver(registry, connectionConfigProvider);
    scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("scope-a");
    controller = new EngineMappingBatchValidationController(
        mappingService, adapterResolver, scopeResolver, mock(EngineMappingPermissionGuard.class));
    registered = mock(RegisteredAdapter.class);
    when(registry.getAdapter(anyString())).thenReturn(Optional.of(registered));
  }

  @Test
  void allOkReportsOkCountEqualsTotal() {
    WorkflowEngineMapping mapping = externalMapping("m-1", "flow-1");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(mapping));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(Try.success(mapping));
    when(connectionConfigProvider.resolve("conn-1", "adapter-1"))
        .thenReturn(Optional.of(new ResolvedEnterpriseConnection(
            "conn-1", java.util.Map.of("provider", "adapter-1"), null)));
    ApprovalWorkflowEngineAdapterProvider provider = mock(ApprovalWorkflowEngineAdapterProvider.class);
    ApprovalWorkflowEngineAdapter adapter = mock(ApprovalWorkflowEngineAdapter.class,
        withSettings().extraInterfaces(ExternalProcessDefinitionValidator.class));
    when(registered.provider()).thenReturn(provider);
    when(provider.provider()).thenReturn("adapter-1");
    when(provider.create(any(ResolvedEnterpriseConnection.class))).thenReturn(adapter);
    when(adapter.health()).thenReturn(Try.success(new ExternalHealth(true, "ok")));
    ExternalProcessDefinitionValidator catalog = (ExternalProcessDefinitionValidator) adapter;
    when(catalog.validateDefinition(any(ExternalProcessDefinitionRef.class))).thenReturn(
        Try.success(mock(com.ouroboros.workflow.approval.external.ExternalProcessDefinition.class)));

    BatchValidationReportVO report = controller.batchValidate();

    assertThat(report.total()).isEqualTo(1);
    assertThat(report.okCount()).isEqualTo(1);
    assertThat(report.failedCount()).isZero();
    assertThat(report.items()).singleElement().satisfies(item ->
        assertThat(item.status()).isEqualTo("OK"));
  }

  @Test
  void singleValidationFailureDoesNotStopOthers() {
    WorkflowEngineMapping bad = externalMapping("m-1", "flow-1");
    WorkflowEngineMapping good = externalMapping("m-2", "flow-2");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(bad, good));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(
        Try.failure(new BusinessException("[EXTERNAL_ACTION_NOT_MAPPED] 动作未映射")));
    when(mappingService.dryRunValidate("scope-a", "m-2")).thenReturn(Try.success(good));
    when(connectionConfigProvider.resolve(anyString(), anyString())).thenReturn(
        Optional.of(new ResolvedEnterpriseConnection(
            "conn-1", java.util.Map.of("provider", "adapter-1"), null)));
    ApprovalWorkflowEngineAdapterProvider provider = mock(ApprovalWorkflowEngineAdapterProvider.class);
    ApprovalWorkflowEngineAdapter adapter = mock(ApprovalWorkflowEngineAdapter.class,
        withSettings().extraInterfaces(ExternalProcessDefinitionValidator.class));
    when(registered.provider()).thenReturn(provider);
    when(provider.provider()).thenReturn("adapter-1");
    when(provider.create(any(ResolvedEnterpriseConnection.class))).thenReturn(adapter);
    when(adapter.health()).thenReturn(Try.success(new ExternalHealth(true, "ok")));
    ExternalProcessDefinitionValidator catalog = (ExternalProcessDefinitionValidator) adapter;
    when(catalog.validateDefinition(any(ExternalProcessDefinitionRef.class))).thenReturn(
        Try.success(mock(com.ouroboros.workflow.approval.external.ExternalProcessDefinition.class)));

    BatchValidationReportVO report = controller.batchValidate();

    assertThat(report.total()).isEqualTo(2);
    assertThat(report.failedCount()).isEqualTo(1);
    assertThat(report.okCount()).isEqualTo(1);
    assertThat(report.items()).extracting(BatchValidationReportVO.Item::status)
        .containsExactly("VALIDATION_FAILED", "OK");
  }

  @Test
  void longFailureMessageIsTruncatedToSummary() {
    WorkflowEngineMapping mapping = externalMapping("m-1", "flow-1");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(mapping));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(
        Try.failure(new BusinessException("[EXTERNAL_ACTION_NOT_MAPPED] " + "x".repeat(300))));

    BatchValidationReportVO report = controller.batchValidate();

    assertThat(report.items()).singleElement().satisfies(item -> {
      assertThat(item.status()).isEqualTo("VALIDATION_FAILED");
      assertThat(item.message()).startsWith("[EXTERNAL_ACTION_NOT_MAPPED]");
      assertThat(item.message()).hasSizeLessThanOrEqualTo(203);
      assertThat(item.message()).endsWith("…");
    });
  }

  @Test
  void missingConnectionConfigMarksConfigMissing() {
    WorkflowEngineMapping mapping = externalMapping("m-1", "flow-1");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(mapping));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(Try.success(mapping));
    ApprovalWorkflowEngineAdapterProvider provider = mock(ApprovalWorkflowEngineAdapterProvider.class);
    when(registered.provider()).thenReturn(provider);
    when(provider.provider()).thenReturn("adapter-1");
    when(connectionConfigProvider.resolve("conn-1", "adapter-1"))
        .thenReturn(Optional.empty());

    BatchValidationReportVO report = controller.batchValidate();

    assertThat(report.items()).singleElement().satisfies(item -> {
      assertThat(item.status()).isEqualTo("CONFIG_MISSING");
      assertThat(item.message()).contains("连接配置缺失");
    });
  }

  @Test
  void healthFailureMarksConnectionUnavailable() {
    WorkflowEngineMapping mapping = externalMapping("m-1", "flow-1");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(mapping));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(Try.success(mapping));
    when(connectionConfigProvider.resolve("conn-1", "adapter-1"))
        .thenReturn(Optional.of(new ResolvedEnterpriseConnection(
            "conn-1", java.util.Map.of("provider", "adapter-1"), null)));
    ApprovalWorkflowEngineAdapterProvider provider = mock(ApprovalWorkflowEngineAdapterProvider.class);
    ApprovalWorkflowEngineAdapter adapter = mock(ApprovalWorkflowEngineAdapter.class);
    when(registered.provider()).thenReturn(provider);
    when(provider.provider()).thenReturn("adapter-1");
    when(provider.create(any(ResolvedEnterpriseConnection.class))).thenReturn(adapter);
    when(adapter.health()).thenReturn(Try.failure(new RuntimeException("token 过期")));

    BatchValidationReportVO report = controller.batchValidate();

    assertThat(report.items()).singleElement().satisfies(item -> {
      assertThat(item.status()).isEqualTo("CONNECTION_UNAVAILABLE");
      assertThat(item.message()).contains("token 过期");
    });
  }

  @Test
  void batchValidateNeverCallsPersistingValidate() {
    WorkflowEngineMapping mapping = externalMapping("m-1", "flow-1");
    when(mappingService.findActiveByScope("scope-a")).thenReturn(List.of(mapping));
    when(mappingService.dryRunValidate("scope-a", "m-1")).thenReturn(Try.success(mapping));
    ApprovalWorkflowEngineAdapterProvider provider = mock(ApprovalWorkflowEngineAdapterProvider.class);
    when(registered.provider()).thenReturn(provider);
    when(provider.provider()).thenReturn("adapter-1");
    when(connectionConfigProvider.resolve("conn-1", "adapter-1"))
        .thenReturn(Optional.empty());

    controller.batchValidate();

    verify(mappingService, never()).validate(anyString(), anyString());
    verify(mappingService, never()).findActiveAllForSystem();
  }

  private WorkflowEngineMapping externalMapping(String id, String flowKey) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setScope("scope-a");
    mapping.setFlowKey(flowKey);
    mapping.setAdapterId("adapter-1");
    mapping.setConnectionId("conn-1");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setExternalDefinitionNamespace("ns");
    mapping.setExternalDefinitionId("DEF-1");
    mapping.setExternalDefinitionVersion("1");
    mapping.setExternalDefinitionSchemaHash("hash");
    return mapping;
  }
}
