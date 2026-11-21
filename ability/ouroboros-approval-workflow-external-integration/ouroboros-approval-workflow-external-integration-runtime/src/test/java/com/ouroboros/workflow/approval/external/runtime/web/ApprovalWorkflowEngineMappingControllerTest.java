package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterProviderBeanBridge;
import com.ouroboros.workflow.approval.external.runtime.web.vo.EngineMappingActivationVO;
import com.ouroboros.workflow.approval.external.runtime.web.vo.EngineMappingVO;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.ExternalHealth;

import io.vavr.control.Try;

/**
 * M14 映射管理控制器测试：七端点路由、不可变版本语义、作用域保护、激活摘要。
 */
class ApprovalWorkflowEngineMappingControllerTest {

  private WorkflowEngineMappingService mappingService;
  private WorkflowEngineAdapterRegistry registry;
  private EnterpriseConnectionCatalog connectionConfigProvider;
  private WorkflowScopeResolver scopeResolver;
  private EngineMappingPermissionGuard permissionGuard;
  private ApprovalWorkflowEngineMappingController controller;

  @BeforeEach
  void setUp() {
    mappingService = mock(WorkflowEngineMappingService.class);
    registry = new WorkflowEngineAdapterRegistry(
        new WorkflowEngineAdapterProviderBeanBridge(List.of()));
    registry.initialize(List.of(new DisplayNameProvider()));
    connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.providerOf("conn-1")).thenReturn(Optional.of("fake-dn"));
    scopeResolver = mock(WorkflowScopeResolver.class);
    when(scopeResolver.resolve()).thenReturn("default");
    permissionGuard = mock(EngineMappingPermissionGuard.class);
    controller = new ApprovalWorkflowEngineMappingController(
        mappingService, registry, connectionConfigProvider, scopeResolver, permissionGuard);
  }

  @Test
  void listDelegatesSearchWithFiltersAndGuardsViewPermission() {
    when(mappingService.search("default", "flow-a", "DRAFT", 0, 50))
        .thenReturn(List.of(entity("m-1", "DRAFT")));

    List<EngineMappingVO> result = controller.list("flow-a", "DRAFT", 0, 50);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo("m-1");
    verify(permissionGuard).requirePermission(EngineMappingPermissions.MAPPING_VIEW);
    verify(mappingService).search("default", "flow-a", "DRAFT", 0, 50);
  }

  @Test
  void createUsesResolvedScopeAndDelegates() {
    when(mappingService.create(any())).thenAnswer(inv -> {
      WorkflowEngineMapping m = inv.getArgument(0);
      m.setId("m-new");
      m.setStatus(WorkflowEngineMapping.STATUS_DRAFT);
      m.setVersion(1);
      return Try.success(m);
    });

    EngineMappingVO result = controller.create(request("flow-a", "fake-dn"));

    assertThat(result.getId()).isEqualTo("m-new");
    assertThat(result.getScope()).isEqualTo("default");
    verify(mappingService).create(any());
  }

  @Test
  void createRejectsRequestBodyScopeOverride() {
    EngineMappingVO request = request("flow-a", "fake-dn");
    request.setScope("other-scope");

    assertThatThrownBy(() -> controller.create(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("作用域不允许请求体覆盖");
    verify(mappingService, never()).create(any());
  }

  @Test
  void updateDraftDelegatesToUpdateDraft() {
    when(mappingService.findById("default", "m-1"))
        .thenReturn(Optional.of(entity("m-1", WorkflowEngineMapping.STATUS_DRAFT)));
    when(mappingService.updateDraft(eq("default"), eq("m-1"), any()))
        .thenAnswer(inv -> Try.success(entity("m-1", "DRAFT")));

    EngineMappingVO result = controller.update("m-1", request("flow-a", "fake-dn"));

    assertThat(result.getId()).isEqualTo("m-1");
    verify(mappingService).updateDraft(eq("default"), eq("m-1"), any());
    verify(mappingService, never()).createDraftWithChanges(any(), any(), any());
  }

  @Test
  void updateActiveMappingCreatesDraftWithRequestChanges() {
    when(mappingService.findById("default", "m-1"))
        .thenReturn(Optional.of(entity("m-1", WorkflowEngineMapping.STATUS_ACTIVE)));
    when(mappingService.createDraftWithChanges(eq("default"), eq("m-1"), any()))
        .thenAnswer(inv -> Try.success(entity("m-2", WorkflowEngineMapping.STATUS_DRAFT)));

    EngineMappingVO result = controller.update("m-1", request("flow-a", "fake-dn"));

    assertThat(result.getId()).isEqualTo("m-2");
    verify(mappingService, never()).updateDraft(any(), any(), any());
    verify(mappingService).createDraftWithChanges(eq("default"), eq("m-1"), any());
  }

  @Test
  void updateRejectsValidatingVersionInsteadOfCreatingOrMutatingDraft() {
    when(mappingService.findById("default", "m-1"))
        .thenReturn(Optional.of(entity("m-1", WorkflowEngineMapping.STATUS_VALIDATING)));

    assertThatThrownBy(() -> controller.update("m-1", request("flow-a", "fake-dn")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("版本不可原地编辑");
    verify(mappingService, never()).updateDraft(any(), any(), any());
    verify(mappingService, never()).createDraftWithChanges(any(), any(), any());
  }

  @Test
  void lifecycleEndpointsDelegateAndPropagateFailures() {
    when(mappingService.validate("default", "m-1")).thenReturn(Try.success(entity("m-1", "VALIDATING")));
    when(mappingService.pause("default", "m-1")).thenReturn(Try.success(entity("m-1", "PAUSED")));
    when(mappingService.retire("default", "m-1")).thenReturn(Try.success(entity("m-1", "RETIRED")));
    when(mappingService.activate("default", "m-1"))
        .thenReturn(Try.failure(new BusinessException("[EXTERNAL_CAPABILITY_UNSUPPORTED] 失败")));

    assertThat(controller.validate("m-1").getStatus()).isEqualTo("VALIDATING");
    assertThat(controller.pause("m-1").getStatus()).isEqualTo("PAUSED");
    assertThat(controller.retire("m-1").getStatus()).isEqualTo("RETIRED");
    assertThatThrownBy(() -> controller.activate("m-1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("EXTERNAL_CAPABILITY_UNSUPPORTED");
    verify(permissionGuard).requirePermission(EngineMappingPermissions.MAPPING_VALIDATE);
    verify(permissionGuard).requirePermission(EngineMappingPermissions.MAPPING_ACTIVATE);
  }

  @Test
  void activateReturnsVersionValidationAndAdapterSummary() {
    WorkflowEngineMapping mapping = entity("m-1", WorkflowEngineMapping.STATUS_ACTIVE);
    mapping.setVersion(3);
    mapping.setValidationMessage("ok");
    mapping.setExternalDefinitionId("PROC_1");
    mapping.setExternalDefinitionVersion("v2");
    mapping.setExternalDefinitionSchemaHash("hash-9");
    when(mappingService.activate("default", "m-1")).thenReturn(Try.success(mapping));

    EngineMappingActivationVO view = controller.activate("m-1");

    assertThat(view.getVersion()).isEqualTo(3);
    assertThat(view.getValidationMessage()).isEqualTo("ok");
    assertThat(view.getAdapterDisplayName()).isEqualTo("展示名");
    assertThat(view.getExternalDefinitionId()).isEqualTo("PROC_1");
    assertThat(view.getExecutionNotice()).isNull();
  }

  @Test
  void activateMirrorMappingIncludesNotice() {
    WorkflowEngineMapping mapping = entity("m-1", WorkflowEngineMapping.STATUS_ACTIVE);
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS);
    when(mappingService.activate("default", "m-1")).thenReturn(Try.success(mapping));

    EngineMappingActivationVO view = controller.activate("m-1");

    assertThat(view.getExecutionNotice()).isEqualTo("本地内置引擎仍负责执行");
  }

  private WorkflowEngineMapping entity(String id, String status) {
    WorkflowEngineMapping m = new WorkflowEngineMapping();
    m.setId(id);
    m.setScope("default");
    m.setFlowKey("flow-a");
    m.setAdapterId("fake-dn");
    m.setConnectionId("conn-1");
    m.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    m.setStatus(status);
    m.setVersion(1);
    return m;
  }

  private EngineMappingVO request(String flowKey, String adapterId) {
    EngineMappingVO vo = new EngineMappingVO();
    vo.setFlowKey(flowKey);
    vo.setAdapterId(adapterId);
    vo.setConnectionId("conn-1");
    vo.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    return vo;
  }

  static final class DisplayNameProvider implements ApprovalWorkflowEngineAdapterProvider {
    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor("fake-dn", "展示名", "1.0", java.util.Set.of("approve"), java.util.Set.of(), java.util.Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new ApprovalWorkflowEngineAdapter() {
        @Override
        public Try<ExternalHealth> health() {
          return Try.success(new ExternalHealth(true, "ok"));
        }
      };
    }
  }
}
