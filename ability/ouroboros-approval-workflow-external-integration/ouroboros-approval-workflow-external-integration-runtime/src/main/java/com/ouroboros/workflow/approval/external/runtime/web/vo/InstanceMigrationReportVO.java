package com.ouroboros.workflow.approval.external.runtime.web.vo;

import java.util.List;

/**
 * 运行中实例迁移报告（M21-T4）：逐实例迁移结果汇总，单实例失败不中断整体。
 */
public record InstanceMigrationReportVO(
    String flowKey,
    String mappingId,
    int total,
    int migratedCount,
    int skippedAlreadyBoundCount,
    int failedCount,
    List<Item> items) {

  public record Item(String instanceId, String status, String message) {
  }
}
