package com.ouroboros.workflow.approval.external.runtime.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.migration.EngineInstanceMigrationService;
import com.ouroboros.workflow.approval.external.runtime.web.vo.InstanceMigrationReportVO;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;

/**
 * 运行中实例迁移入口（M21-T4，迁移治理）。
 *
 * <p>{@code POST /api/approval-workflow/engine-mappings/{mappingId}/migrate-running}：
 * 枚举映射 flowKey 的本地 RUNNING 实例（请求体可传 {@code instanceIds} 限定目标，不传则按
 * {@code getInstances(flowKey,"running",offset,1000)} 分页遍历直至空页，实例 ID 去重），逐实例迁移，
 * 单实例失败不中断整体。迁移是激活级运维操作（权限点 MAPPING_ACTIVATE），
 * 默认生产节奏不迁移运行中实例（设计 L901）。
 */
@RestController
@RequestMapping("/api/approval-workflow/engine-mappings")
public class EngineInstanceMigrationController {

  private static final Logger logger = LoggerFactory.getLogger(EngineInstanceMigrationController.class);

  private static final int PAGE_SIZE = 1000;

  private final FlowInstanceService flowInstanceService;
  private final EngineInstanceMigrationService migrationService;
  private final EngineMappingPermissionGuard permissionGuard;

  @Autowired
  public EngineInstanceMigrationController(
      FlowInstanceService flowInstanceService,
      EngineInstanceMigrationService migrationService,
      EngineMappingPermissionGuard permissionGuard) {
    this.flowInstanceService = flowInstanceService;
    this.migrationService = migrationService;
    this.permissionGuard = permissionGuard;
  }

  @PostMapping("/{mappingId}/migrate-running")
  public InstanceMigrationReportVO migrateRunning(
      @PathVariable String mappingId,
      @RequestBody(required = false) List<String> instanceIds) {
    permissionGuard.requirePermission(EngineMappingPermissions.MAPPING_ACTIVATE);
    WorkflowEngineMapping mapping = migrationService.requireMigratableMapping(mappingId);
    List<String> targets = resolveTargets(mapping, instanceIds);
    List<InstanceMigrationReportVO.Item> items = new ArrayList<>();
    int migrated = 0;
    int skipped = 0;
    int failed = 0;
    for (String localInstanceId : targets) {
      try {
        EngineInstanceMigrationService.MigrationResult result =
            migrationService.migrate(mappingId, localInstanceId, currentUserId());
        if ("MIGRATED".equals(result.status())) {
          migrated++;
          items.add(new InstanceMigrationReportVO.Item(
              localInstanceId, "MIGRATED", "externalInstanceId=" + result.externalInstanceId()));
        } else {
          skipped++;
          items.add(new InstanceMigrationReportVO.Item(localInstanceId, "SKIPPED_ALREADY_BOUND", null));
        }
      } catch (BusinessException ex) {
        failed++;
        logger.debug("实例迁移失败: mappingId={}, instanceId={}, cause={}",
            mappingId, localInstanceId, ex.getMessage());
        items.add(new InstanceMigrationReportVO.Item(localInstanceId, "FAILED", ex.getMessage()));
      }
    }
    return new InstanceMigrationReportVO(
        mapping.getFlowKey(), mappingId, targets.size(), migrated, skipped, failed, items);
  }

  private List<String> resolveTargets(WorkflowEngineMapping mapping, List<String> instanceIds) {
    if (instanceIds != null && !instanceIds.isEmpty()) {
      return instanceIds;
    }
    LinkedHashSet<String> targets = new LinkedHashSet<>();
    int offset = 0;
    while (true) {
      List<FlowInstanceDetail> page =
          flowInstanceService.getInstances(mapping.getFlowKey(), "running", offset, PAGE_SIZE);
      if (page.isEmpty()) {
        break;
      }
      for (FlowInstanceDetail detail : page) {
        targets.add(detail.getInstanceId());
      }
      if (page.size() < PAGE_SIZE) {
        break;
      }
      offset += PAGE_SIZE;
    }
    return new ArrayList<>(targets);
  }

  private String currentUserId() {
    return com.ouroboros.workflow.approval.CurrentUserSupplier.getCurrentUserId()
        .filter(id -> !"anonymousUser".equals(id))
        .orElseThrow(() -> new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] 无权限：未登录用户"));
  }
}
