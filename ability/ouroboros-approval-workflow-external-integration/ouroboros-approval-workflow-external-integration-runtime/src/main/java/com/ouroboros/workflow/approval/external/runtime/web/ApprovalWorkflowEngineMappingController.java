package com.ouroboros.workflow.approval.external.runtime.web;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeUnconfiguredException;
import com.ouroboros.workflow.approval.external.runtime.web.vo.EngineMappingActivationVO;
import com.ouroboros.workflow.approval.external.runtime.web.vo.EngineMappingVO;

import io.vavr.control.Try;

/**
 * 流程映射管理入口（设计文档 §流程映射管理 7 端点）。
 *
 * <p>不可变版本语义：ACTIVE/PAUSED 映射的 PUT 走 createDraft 生成新草稿；
 * 返回 VO 不含任何凭据；权限点见 {@link EngineMappingPermissions}。
 */
@RestController
@RequestMapping("/api/approval-workflow/engine-mappings")
public class ApprovalWorkflowEngineMappingController {

  private final WorkflowEngineMappingService mappingService;
  private final WorkflowEngineAdapterRegistry registry;
  private final EnterpriseConnectionCatalog connectionConfigProvider;
  private final WorkflowScopeResolver scopeResolver;
  private final EngineMappingPermissionGuard permissionGuard;

  @Autowired
  public ApprovalWorkflowEngineMappingController(
      WorkflowEngineMappingService mappingService,
      WorkflowEngineAdapterRegistry registry,
      EnterpriseConnectionCatalog connectionConfigProvider,
      WorkflowScopeResolver scopeResolver,
      EngineMappingPermissionGuard permissionGuard) {
    this.mappingService = mappingService;
    this.registry = registry;
    this.connectionConfigProvider = connectionConfigProvider;
    this.scopeResolver = scopeResolver;
    this.permissionGuard = permissionGuard;
  }

  @GetMapping
  public List<EngineMappingVO> list(
      @RequestParam(required = false) String flowKey,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "0") Integer offset,
      @RequestParam(required = false, defaultValue = "50") Integer limit) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_VIEW);
    Optional<String> scope = resolveScopeForRead();
    if (scope.isEmpty()) {
      return List.of();
    }
    return mappingService.search(scope.get(), flowKey, status, offset, limit).stream()
        .map(mapping -> EngineMappingVO.fromEntity(mapping, registry))
        .toList();
  }

  /**
   * 读路径的作用域：未装配 {@link WorkflowScopeResolver} 时返回空，表示平台未启用外部协作，
   * 映射列表降级为空；写路径仍走 {@code scopeResolver.resolve()} 显式失败。
   */
  private Optional<String> resolveScopeForRead() {
    try {
      return Optional.of(scopeResolver.resolve());
    } catch (WorkflowScopeUnconfiguredException unconfigured) {
      return Optional.empty();
    }
  }

  @PostMapping
  public EngineMappingVO create(@RequestBody EngineMappingVO request) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_EDIT);
    String scope = scopeResolver.resolve();
    if (request.getScope() != null && !request.getScope().isBlank()
        && !scope.equals(request.getScope())) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 作用域不允许请求体覆盖: " + request.getScope());
    }
    WorkflowEngineMapping draft = toEntity(request);
    deriveAdapterFromConnection(draft);
    draft.setScope(scope);
    Try<WorkflowEngineMapping> created = mappingService.create(draft);
    if (created.isFailure()) {
      throw new BusinessException(created.getCause().getMessage(), created.getCause());
    }
    return EngineMappingVO.fromEntity(created.get(), registry);
  }

  @PutMapping("/{id}")
  public EngineMappingVO update(@PathVariable String id, @RequestBody EngineMappingVO request) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_EDIT);
    String scope = scopeResolver.resolve();
    if (request.getScope() != null && !request.getScope().isBlank()
        && !scope.equals(request.getScope())) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 作用域不允许请求体覆盖: " + request.getScope());
    }
    WorkflowEngineMapping existing = mappingService.findById(scope, id)
        .orElseThrow(() -> new BusinessException(
            "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 映射不存在或无权访问: " + id));
    String status = existing.getStatus();
    boolean editable = WorkflowEngineMapping.STATUS_DRAFT.equals(status)
        || WorkflowEngineMapping.STATUS_INVALID.equals(status);
    if (!editable
        && !WorkflowEngineMapping.STATUS_ACTIVE.equals(status)
        && !WorkflowEngineMapping.STATUS_PAUSED.equals(status)) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] " + status + " 映射版本不可原地编辑: " + id);
    }
    if (!editable) {
      // 不可变版本语义：ACTIVE/PAUSED 编辑生成新草稿
      WorkflowEngineMapping changes = toEntity(request);
      deriveAdapterFromConnection(changes);
      Try<WorkflowEngineMapping> draft = mappingService.createDraftWithChanges(scope, id, changes);
      if (draft.isFailure()) {
        throw new BusinessException(draft.getCause().getMessage(), draft.getCause());
      }
      return EngineMappingVO.fromEntity(draft.get(), registry);
    }
    WorkflowEngineMapping changes = toEntity(request);
    deriveAdapterFromConnection(changes);
    Try<WorkflowEngineMapping> updated = mappingService.updateDraft(scope, id, changes);
    if (updated.isFailure()) {
      throw new BusinessException(updated.getCause().getMessage(), updated.getCause());
    }
    return EngineMappingVO.fromEntity(updated.get(), registry);
  }

  @PostMapping("/{id}/validate")
  public EngineMappingVO validate(@PathVariable String id) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_VALIDATE);
    Try<WorkflowEngineMapping> result = mappingService.validate(scopeResolver.resolve(), id);
    if (result.isFailure()) {
      throw new BusinessException(result.getCause().getMessage(), result.getCause());
    }
    return EngineMappingVO.fromEntity(result.get(), registry);
  }

  @PostMapping("/{id}/activate")
  public EngineMappingActivationVO activate(@PathVariable String id) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_ACTIVATE);
    Try<WorkflowEngineMapping> result = mappingService.activate(scopeResolver.resolve(), id);
    if (result.isFailure()) {
      throw new BusinessException(result.getCause().getMessage(), result.getCause());
    }
    return activationView(result.get());
  }

  @PostMapping("/{id}/pause")
  public EngineMappingVO pause(@PathVariable String id) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_PAUSE);
    Try<WorkflowEngineMapping> result = mappingService.pause(scopeResolver.resolve(), id);
    if (result.isFailure()) {
      throw new BusinessException(result.getCause().getMessage(), result.getCause());
    }
    return EngineMappingVO.fromEntity(result.get(), registry);
  }

  @PostMapping("/{id}/retire")
  public EngineMappingVO retire(@PathVariable String id) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_RETIRE);
    Try<WorkflowEngineMapping> result = mappingService.retire(scopeResolver.resolve(), id);
    if (result.isFailure()) {
      throw new BusinessException(result.getCause().getMessage(), result.getCause());
    }
    return EngineMappingVO.fromEntity(result.get(), registry);
  }

  private EngineMappingActivationVO activationView(WorkflowEngineMapping mapping) {
    EngineMappingActivationVO view = new EngineMappingActivationVO();
    view.setId(mapping.getId());
    view.setVersion(mapping.getVersion());
    view.setValidationMessage(mapping.getValidationMessage());
    view.setAdapterId(mapping.getAdapterId());
    RegisteredAdapter registered = registry.getAdapter(mapping.getAdapterId()).orElse(null);
    view.setAdapterDisplayName(registered == null ? null : registered.descriptor().displayName());
    view.setExternalDefinitionId(mapping.getExternalDefinitionId());
    view.setExternalDefinitionVersion(mapping.getExternalDefinitionVersion());
    view.setExternalDefinitionSchemaHash(mapping.getExternalDefinitionSchemaHash());
    if (WorkflowEngineMappingValidator.AUTHORITY_OUROBOROS.equals(mapping.getAuthority())) {
      view.setExecutionNotice("本地内置引擎仍负责执行");
    }
    return view;
  }

  private WorkflowEngineMapping toEntity(EngineMappingVO vo) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setScope(vo.getScope());
    mapping.setFlowKey(vo.getFlowKey());
    mapping.setAdapterId(vo.getAdapterId());
    mapping.setConnectionId(vo.getConnectionId());
    mapping.setExternalDefinitionId(vo.getExternalDefinitionId());
    mapping.setExternalDefinitionVersion(vo.getExternalDefinitionVersion());
    mapping.setExternalDefinitionSchemaHash(vo.getExternalDefinitionSchemaHash());
    mapping.setAuthority(vo.getAuthority());
    mapping.setResultBindingJson(vo.getResultBindingJson());
    mapping.setStartOptionsJson(vo.getStartOptionsJson());
    mapping.setActionMappingsJson(vo.getActionMappingsJson());
    mapping.setResultMappingsJson(vo.getResultMappingsJson());
    mapping.setCallbackJson(vo.getCallbackJson());
    return mapping;
  }

  private void deriveAdapterFromConnection(WorkflowEngineMapping mapping) {
    String provider = connectionConfigProvider.providerOf(mapping.getConnectionId())
        .orElseThrow(() -> new BusinessException(
            "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
                + "] 连接不存在或未配置: " + mapping.getConnectionId()));
    RegisteredAdapter registered = registry.getAdapterByProvider(provider)
        .orElseThrow(() -> new BusinessException(
            "[" + EngineIntegrationErrorCodes.ENGINE_ADAPTER_NOT_FOUND
                + "] provider 未注册适配器: " + provider));
    String requestedAdapter = mapping.getAdapterId();
    if (requestedAdapter != null && !requestedAdapter.isBlank()
        && !requestedAdapter.equals(registered.descriptor().adapterId())) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
              + "] adapterId 与 connectionId 不匹配，adapterId 由连接 provider 派生: "
              + mapping.getConnectionId());
    }
    mapping.setAdapterId(registered.descriptor().adapterId());
  }
}
