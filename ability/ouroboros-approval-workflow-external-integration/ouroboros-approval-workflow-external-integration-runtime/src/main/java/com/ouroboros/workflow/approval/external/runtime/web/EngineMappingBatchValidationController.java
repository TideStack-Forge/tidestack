package com.ouroboros.workflow.approval.external.runtime.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionValidator;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinitionRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.scope.WorkflowScopeResolver;
import com.ouroboros.workflow.approval.external.runtime.web.vo.BatchValidationReportVO;

import io.vavr.control.Try;

/**
 * 映射批量验证入口（M21-T1，迁移治理）。
 *
 * <p>对全部 ACTIVE 映射依次执行三项只读检查，单项失败不中断整体：
 * ① dry-run 激活校验（{@link WorkflowEngineMappingService#dryRunValidate}，不落库）；
 * ② 连接健康检查；③ 外部定义校验（仅 {@code authority=EXTERNAL_ENGINE} 的映射）。
 * 批量验证只读：不修改任何映射状态，可在切换前反复运行（设计文档 §兼容与切换策略 L902）。
 */
@RestController
@RequestMapping("/api/approval-workflow/engine-mappings")
public class EngineMappingBatchValidationController {

  private final WorkflowEngineMappingService mappingService;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final WorkflowScopeResolver scopeResolver;
  private final EngineMappingPermissionGuard permissionGuard;

  @Autowired
  public EngineMappingBatchValidationController(
      WorkflowEngineMappingService mappingService,
      ConnectionAwareAdapterResolver adapterResolver,
      WorkflowScopeResolver scopeResolver,
      EngineMappingPermissionGuard permissionGuard) {
    this.mappingService = mappingService;
    this.adapterResolver = adapterResolver;
    this.scopeResolver = scopeResolver;
    this.permissionGuard = permissionGuard;
  }

  @PostMapping("/batch-validate")
  public BatchValidationReportVO batchValidate() {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_VALIDATE);
    String scope = scopeResolver.resolve();
    List<BatchValidationReportVO.Item> items = new ArrayList<>();
    int okCount = 0;
    for (WorkflowEngineMapping mapping : mappingService.findActiveByScope(scope)) {
      BatchValidationReportVO.Item item = validateOne(scope, mapping);
      items.add(item);
      if ("OK".equals(item.status())) {
        okCount++;
      }
    }
    return new BatchValidationReportVO(items, items.size(), okCount, items.size() - okCount);
  }

  private BatchValidationReportVO.Item validateOne(String scope, WorkflowEngineMapping mapping) {
    Try<WorkflowEngineMapping> dryRun = mappingService.dryRunValidate(scope, mapping.getId());
    if (dryRun.isFailure()) {
      return item(mapping, "VALIDATION_FAILED", dryRun.getCause().getMessage());
    }
    Try<ExternalHealth> health;
    try {
      health = adapterResolver.resolve(mapping).health();
    } catch (BusinessException ex) {
      if (isMissingConnectionConfig(ex)) {
        return item(mapping, "CONFIG_MISSING", "连接配置缺失: " + ex.getMessage());
      }
      return item(mapping, "CONNECTION_UNAVAILABLE", ex.getMessage());
    }
    if (health.isFailure()) {
      return item(mapping, "CONNECTION_UNAVAILABLE", health.getCause().getMessage());
    }
    if (WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      ExternalProcessDefinitionValidator catalog;
      try {
        catalog = adapterResolver.resolvePort(mapping, ExternalProcessDefinitionValidator.class);
      } catch (BusinessException ex) {
        return item(mapping, "DEFINITION_DISCOVERY_UNSUPPORTED", ex.getMessage());
      }
      Try<com.ouroboros.workflow.approval.external.ExternalProcessDefinition> validated =
          catalog.validateDefinition(new ExternalProcessDefinitionRef(
              mapping.getExternalDefinitionNamespace(),
              mapping.getExternalDefinitionId(),
              mapping.getExternalDefinitionVersion(),
              mapping.getExternalDefinitionSchemaHash()));
      if (validated.isFailure()) {
        return item(mapping, "DEFINITION_INVALID", validated.getCause().getMessage());
      }
    }
    return item(mapping, "OK", null);
  }

  /**
   * 组装报告项；message 仅记录错误码与原因摘要（超 200 字符截断），不含凭据/密钥。
   */
  private BatchValidationReportVO.Item item(
      WorkflowEngineMapping mapping, String status, String message) {
    return new BatchValidationReportVO.Item(
        mapping.getId(), mapping.getFlowKey(), mapping.getAdapterId(),
        mapping.getConnectionId(), status, truncateMessage(message));
  }

  private String truncateMessage(String message) {
    if (message == null || message.length() <= 200) {
      return message;
    }
    return message.substring(0, 200) + "…";
  }

  private boolean isMissingConnectionConfig(BusinessException ex) {
    String message = ex.getMessage();
    return message != null && message.contains("连接配置不存在");
  }
}
