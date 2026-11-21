package com.ouroboros.workflow.approval.external;

import java.util.List;

/**
 * 外部审批记录分页（时间线）。记录字段由适配器定义，可空集合表示无记录。
 *
 * @param records 记录列表
 * @param hasMore 是否还有下一页
 */
public record ExternalRecordPage(List<ExternalRecordEntry> records, boolean hasMore) {

  /**
   * 单条审批记录（时间线项）。
   *
   * @param nodeId   节点标识（可空）
   * @param operator 操作人（可空）
   * @param action   操作（approve/reject/transfer 等，可空）
   * @param comment  意见（可空）
   * @param time     操作时间（适配器定义格式，可空）
   */
  public record ExternalRecordEntry(String nodeId, String operator, String action, String comment, String time) {
  }
}
