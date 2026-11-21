package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;
import java.util.Optional;

import io.vavr.control.Try;

/**
 * 结果绑定仓储服务。
 */
public interface ExternalResultBindingService {

  /**
   * 创建绑定（trackingId 为空时生成）。
   */
  Try<ExternalResultBinding> create(ExternalResultBinding binding);

  /**
   * 更新绑定状态与终态信息。
   */
  Try<Boolean> markFinished(
      String trackingId, String outcome, String externalRevision, LocalDateTime externalEventTime,
      String externalEventId);

  /**
   * 按 trackingId 查询。
   */
  Optional<ExternalResultBinding> findByTrackingId(String trackingId);

  /**
   * 按幂等键查询绑定（发起幂等去重）。
   */
  Optional<ExternalResultBinding> findByIdempotencyKey(String adapterId, String connectionId, String idempotencyKey);

  /**
   * 按外部实例 ID 查询绑定（事件处理定位）。
   */
  Optional<ExternalResultBinding> findByExternalInstance(String adapterId, String connectionId, String externalInstanceId);

  /**
   * 非终态更新：刷新 revision 与外部事件时间。
   */
  Try<Boolean> updateRevision(
      String trackingId, String externalRevision, LocalDateTime externalEventTime, String externalEventId);

  /** 外部撤销动作已接受后的本地终态完成。 */
  Try<ActionStatusUpdate> applyActionFinished(String trackingId, String outcome);

  /**
   * 查询同一映射下业务记录的未完成绑定（并发策略查重用）。
   */
  Optional<ExternalResultBinding> findRunningByBusinessRecord(String scope, String flowKey, String recordType, String recordId);

  /**
   * 按映射 ID 统计结果绑定数（映射版本清理的保护检查用）。
   */
  Long countByMappingId(String mappingId);
}
