package com.ouroboros.workflow.approval.external.runtime.event;

import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

/** 将适配器实例快照转换为统一 inbox 事件事实。 */
public final class ExternalSnapshotEnvelopeFactory {

  /**
   * Lifecycle values are provider-neutral and must include the non-terminal
   * suspended state. A suspended snapshot is a valid state transition and is
   * intentionally routed through the same inbox pipeline as RUNNING/terminal
   * snapshots.
   */
  private static final Set<String> LIFECYCLE_STATUSES =
      Set.of("RUNNING", "SUSPENDED", "FINISHED", "CANCELED");
  private static final Set<String> TERMINAL_LIFECYCLE_STATUSES = Set.of("FINISHED", "CANCELED");
  private static final Set<String> RESULT_CODES = Set.of("APPROVED", "REJECTED", "CANCELED", "ERROR");

  private ExternalSnapshotEnvelopeFactory() {
  }

  public static ExternalCallbackEnvelope create(
      String adapterId, String connectionId, ExternalInstanceSnapshot snapshot) {
    if (snapshot == null || !hasText(snapshot.externalInstanceId())) {
      throw new BusinessException("外部实例快照缺少实例 ID");
    }
    if (!LIFECYCLE_STATUSES.contains(snapshot.lifecycleStatus())) {
      throw new BusinessException("外部实例快照生命周期状态非法: " + snapshot.lifecycleStatus());
    }
    if (hasText(snapshot.resultCode()) && !RESULT_CODES.contains(snapshot.resultCode())) {
      throw new BusinessException("外部实例快照结果状态非法: " + snapshot.resultCode());
    }
    if (TERMINAL_LIFECYCLE_STATUSES.contains(snapshot.lifecycleStatus())
        && !hasText(snapshot.resultCode())) {
      throw new BusinessException("外部终态快照缺少规范结果状态: " + snapshot.externalInstanceId());
    }
    String factDigest = ExternalRouteKeys.of(
        "snapshot-fact", adapterId, connectionId, snapshot.externalInstanceId(),
        snapshot.lifecycleStatus(), snapshot.resultCode(), snapshot.outcome());
    return new ExternalCallbackEnvelope(
        "snapshot:" + factDigest,
        adapterId,
        connectionId,
        snapshot.externalInstanceId(),
        null,
        null,
        null,
        snapshot.lifecycleStatus(),
        snapshot.resultCode(),
        factDigest);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
