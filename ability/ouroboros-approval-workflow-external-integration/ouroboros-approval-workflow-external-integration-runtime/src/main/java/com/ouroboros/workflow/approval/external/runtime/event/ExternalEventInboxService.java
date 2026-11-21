package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.common.service.outbox.OutboxBackoff;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;

import io.vavr.control.Try;

/**
 * 收件箱仓储：幂等写入与状态流转。
 */
@Service
public class ExternalEventInboxService extends SimpleModelServiceImpl<String, ExternalEventInbox> {

  private static final Logger logger = LoggerFactory.getLogger(ExternalEventInboxService.class);

  public static final int DEFAULT_LEASE_SECONDS = 300;
  public static final int DEFAULT_RECOVERY_BATCH_SIZE = 100;
  private static final int MAX_RECOVERY_SCAN_LIMIT = 1_000;

  public record Claim(ExternalEventInbox inbox, String claimToken) {
  }

  /**
   * 幂等写入：同 (adapterId, connectionId, eventId) 已存在时返回空（不重复写）。
   */
  @Transactional
  public Try<ExternalEventInbox> createIfAbsent(ExternalEventInbox inbox) {
    if (inbox.getId() == null) {
      inbox.setId(UUID.randomUUID().toString());
    }
    LocalDateTime now = LocalDateTime.now();
    inbox.setStatus(ExternalEventInbox.STATUS_PENDING);
    inbox.setRetryCount(0);
    inbox.setNextAttemptAt(null);
    inbox.setClaimOwner(null);
    inbox.setClaimToken(null);
    inbox.setClaimUntil(null);
    inbox.setReceivedAt(now);
    inbox.setCreatedAt(now);
    inbox.setUpdatedAt(now);
    try {
      var insert = getDataModel().insert(inbox);
      if (insert.isSuccess()) {
        return Try.success(insert.get());
      }
      Throwable cause = insert.getCause();
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
        return resolveDuplicate(inbox);
      }
      return Try.failure(new BusinessException("写入外部事件收件箱失败: " + cause.getMessage(), cause));
    } catch (OuroborosException ex) {
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
        return resolveDuplicate(inbox);
      }
      return Try.failure(OuroborosExceptionMapper.toOuroborosException(
          ex, cause -> new BusinessException("写入外部事件收件箱失败: " + cause.getMessage(), cause)));
    }
  }

  private Try<ExternalEventInbox> resolveDuplicate(ExternalEventInbox requested) {
    Optional<ExternalEventInbox> existing = findByEventId(
        requested.getAdapterId(), requested.getConnectionId(), requested.getEventId());
    if (existing.isEmpty()) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
              + "] 外部事件唯一键冲突但回查失败: eventId=" + requested.getEventId()));
    }
    if (sameEventFact(existing.get(), requested)) {
      return Try.success(null);
    }
    logger.error(
        "外部事件 eventId 事实冲突: adapterId={}, connectionId={}, eventId={}, existingDigest={}, incomingDigest={}",
        requested.getAdapterId(), requested.getConnectionId(), requested.getEventId(),
        existing.get().getPayloadDigest(), requested.getPayloadDigest());
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
            + "] 相同 eventId 对应不同外部事件事实: adapterId=" + requested.getAdapterId()
            + ", connectionId=" + requested.getConnectionId()
            + ", eventId=" + requested.getEventId()));
  }

  private boolean sameEventFact(ExternalEventInbox existing, ExternalEventInbox requested) {
    return Objects.equals(existing.getExternalInstanceId(), requested.getExternalInstanceId())
        && Objects.equals(existing.getExternalTaskId(), requested.getExternalTaskId())
        && Objects.equals(existing.getSequence(), requested.getSequence())
        && Objects.equals(existing.getExternalEventTime(), requested.getExternalEventTime())
        && Objects.equals(existing.getEventType(), requested.getEventType())
        && Objects.equals(existing.getResultCode(), requested.getResultCode())
        && Objects.equals(existing.getPayloadDigest(), requested.getPayloadDigest());
  }

  public Optional<ExternalEventInbox> findByEventId(String adapterId, String connectionId, String eventId) {
    if (eventId == null || eventId.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "adapterId", Map.of("=", adapterId),
          "connectionId", Map.of("=", connectionId),
          "eventId", Map.of("=", eventId)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部事件收件箱失败: " + adapterId + "/" + connectionId + "/" + eventId, ex);
    }
  }

  public Optional<ExternalEventInbox> findById(String inboxId) {
    try {
      return Optional.ofNullable(getByPrimaryKey(inboxId, Map.of()));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部事件收件箱失败: " + inboxId, ex);
    }
  }

  /**
   * 原子领取 PENDING 或租约到期的 PROCESSING 条目。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Claim> claim(String inboxId, String owner, int leaseSeconds) {
    Optional<ExternalEventInbox> inboxOpt = findById(inboxId);
    if (inboxOpt.isEmpty()) {
      return Optional.empty();
    }
    ExternalEventInbox inbox = inboxOpt.get();
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", inboxId));
    if (ExternalEventInbox.STATUS_PENDING.equals(inbox.getStatus())) {
      where.put("status", Map.of("=", ExternalEventInbox.STATUS_PENDING));
    } else if (ExternalEventInbox.STATUS_PROCESSING.equals(inbox.getStatus())
        && inbox.getClaimUntil() != null
        && inbox.getClaimUntil().isBefore(now)) {
      where.put("status", Map.of("=", ExternalEventInbox.STATUS_PROCESSING));
      where.put("claimUntil", Map.of("<", now));
    } else {
      return Optional.empty();
    }
    String claimToken = UUID.randomUUID().toString();
    LocalDateTime claimUntil = now.plusSeconds(Math.max(leaseSeconds, 1));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalEventInbox.STATUS_PROCESSING);
    data.put("claimOwner", owner);
    data.put("claimToken", claimToken);
    data.put("claimUntil", claimUntil);
    data.put("lastError", null);
    data.put("nextAttemptAt", null);
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "领取收件箱条目失败: " + inboxId)) {
      return Optional.empty();
    }
    return findById(inboxId).map(claimed -> new Claim(claimed, claimToken));
  }

  @Transactional
  public boolean markProcessed(String inboxId, String claimToken) {
    return completeClaimed(inboxId, claimToken, ExternalEventInbox.STATUS_PROCESSED, null, false);
  }

  @Transactional
  public boolean markStale(String inboxId, String claimToken) {
    return completeClaimed(inboxId, claimToken, ExternalEventInbox.STATUS_STALE, null, false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailure(String inboxId, String claimToken, String lastError) {
    Optional<ExternalEventInbox> inboxOpt = findById(inboxId);
    if (inboxOpt.isEmpty()
        || !ExternalEventInbox.STATUS_PROCESSING.equals(inboxOpt.get().getStatus())
        || !claimToken.equals(inboxOpt.get().getClaimToken())) {
      return false;
    }
    int nextRetryCount = inboxOpt.get().getRetryCount() + 1;
    String targetStatus = nextRetryCount > ExternalEventInbox.MAX_RETRY_COUNT
        ? ExternalEventInbox.STATUS_DEAD
        : ExternalEventInbox.STATUS_FAILED;
    Map<String, Object> data = claimedCompletionData(targetStatus, lastError);
    data.put("retryCount", nextRetryCount);
    data.put("nextAttemptAt", ExternalEventInbox.STATUS_DEAD.equals(targetStatus)
        ? null : OutboxBackoff.nextAttemptAt(inboxOpt.get().getRetryCount()));
    return updateClaimed(inboxId, claimToken, data);
  }

  @Transactional
  public boolean markDead(String inboxId, String claimToken, String lastError) {
    return completeClaimed(inboxId, claimToken, ExternalEventInbox.STATUS_DEAD, lastError, false);
  }

  private boolean completeClaimed(
      String inboxId,
      String claimToken,
      String targetStatus,
      String lastError,
      boolean incrementRetry) {
    Map<String, Object> data = claimedCompletionData(targetStatus, lastError);
    if (incrementRetry) {
      Optional<ExternalEventInbox> inboxOpt = findById(inboxId);
      if (inboxOpt.isEmpty()) {
        return false;
      }
      data.put("retryCount", inboxOpt.get().getRetryCount() + 1);
    }
    return updateClaimed(inboxId, claimToken, data);
  }

  private Map<String, Object> claimedCompletionData(String targetStatus, String lastError) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", targetStatus);
    data.put("lastError", lastError);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    if (!ExternalEventInbox.STATUS_FAILED.equals(targetStatus)) {
      data.put("nextAttemptAt", null);
    }
    LocalDateTime now = LocalDateTime.now();
    if (ExternalEventInbox.STATUS_PROCESSED.equals(targetStatus)
        || ExternalEventInbox.STATUS_STALE.equals(targetStatus)) {
      data.put("processedAt", now);
    }
    data.put("updatedAt", now);
    return data;
  }

  private boolean updateClaimed(String inboxId, String claimToken, Map<String, Object> data) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", inboxId));
    where.put("status", Map.of("=", ExternalEventInbox.STATUS_PROCESSING));
    where.put("claimToken", Map.of("=", claimToken));
    return conditionalUpdate(where, data, "更新收件箱 claim 状态失败: " + inboxId);
  }

  /**
   * 自动重试：到期 FAILED → PENDING，保留累计重试次数。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> requeueAutomatically(String inboxId, LocalDateTime now) {
    Optional<ExternalEventInbox> inboxOpt = findById(inboxId);
    if (inboxOpt.isEmpty()) {
      return Try.failure(new BusinessException("收件箱条目不存在: " + inboxId));
    }
    ExternalEventInbox inbox = inboxOpt.get();
    if (!ExternalEventInbox.STATUS_FAILED.equals(inbox.getStatus())) {
      return Try.failure(new BusinessException("仅 FAILED 可自动重试，当前状态: " + inbox.getStatus()));
    }
    if (inbox.getRetryCount() > ExternalEventInbox.MAX_RETRY_COUNT) {
      return Try.failure(new BusinessException("收件箱条目已超过自动重试上限: " + inboxId));
    }
    if (inbox.getNextAttemptAt() != null && inbox.getNextAttemptAt().isAfter(now)) {
      return Try.failure(new BusinessException("收件箱条目尚未到自动重试时间: " + inboxId));
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", inboxId));
    where.put("status", Map.of("=", ExternalEventInbox.STATUS_FAILED));
    where.put("retryCount", Map.of("=", inbox.getRetryCount()));
    if (inbox.getNextAttemptAt() != null) {
      where.put("nextAttemptAt", Map.of("<", now.plusNanos(1)));
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalEventInbox.STATUS_PENDING);
    data.put("nextAttemptAt", null);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    try {
      if (!conditionalUpdate(where, data, "自动重试收件箱条目失败: " + inboxId)) {
        return Try.failure(new BusinessException("自动重试收件箱条目状态已变化: " + inboxId));
      }
      return Try.success(null);
    } catch (BusinessException ex) {
      return Try.failure(ex);
    }
  }

  /** 人工重放：FAILED/DEAD → PENDING，显式重置重试预算。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Try<Void> replayManually(String inboxId) {
    Optional<ExternalEventInbox> inboxOpt = findById(inboxId);
    if (inboxOpt.isEmpty()) {
      return Try.failure(new BusinessException("收件箱条目不存在: " + inboxId));
    }
    ExternalEventInbox inbox = inboxOpt.get();
    if (!ExternalEventInbox.STATUS_FAILED.equals(inbox.getStatus())
        && !ExternalEventInbox.STATUS_DEAD.equals(inbox.getStatus())) {
      return Try.failure(new BusinessException("仅 FAILED/DEAD 可人工重放，当前状态: " + inbox.getStatus()));
    }
    Map<String, Object> where = Map.of(
        "id", Map.of("=", inboxId),
        "status", Map.of("=", inbox.getStatus()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalEventInbox.STATUS_PENDING);
    data.put("retryCount", 0);
    data.put("nextAttemptAt", null);
    data.put("lastError", null);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    if (!conditionalUpdate(where, data, "人工重放收件箱条目失败: " + inboxId)) {
      return Try.failure(new BusinessException("人工重放收件箱条目状态已变化: " + inboxId));
    }
    return Try.success(null);
  }

  /**
   * 兜底扫描：PENDING 但接收时间早于阈值的条目（after-commit 丢失兜底）。
   */
  public List<ExternalEventInbox> findPendingOlderThan(LocalDateTime threshold) {
    return findPendingOlderThan(threshold, DEFAULT_RECOVERY_BATCH_SIZE);
  }

  /**
   * 兜底扫描：PENDING 但接收时间早于阈值的条目（after-commit 丢失兜底）。
   * 条件、排序和批次限制全部下推到数据模型查询。
   */
  public List<ExternalEventInbox> findPendingOlderThan(LocalDateTime threshold, int limit) {
    int scanLimit = boundedRecoveryLimit(limit);
    if (scanLimit == 0) {
      return List.of();
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("status", Map.of("=", ExternalEventInbox.STATUS_PENDING));
    where.put("receivedAt", Map.of("<", threshold));
    try {
      return (List<ExternalEventInbox>) searchList(candidateStatement(
          where, orderBy("receivedAt", "createdAt", "id"), scanLimit));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询待处理外部事件收件箱失败", ex);
    }
  }

  /**
   * 兜底扫描：可重试的失败条目。
   */
  public List<ExternalEventInbox> findFailedRetryable(LocalDateTime now) {
    return findFailedRetryable(now, DEFAULT_RECOVERY_BATCH_SIZE);
  }

  /** 兜底扫描：可重试的失败条目，时间和重试预算在数据库侧过滤。 */
  public List<ExternalEventInbox> findFailedRetryable(LocalDateTime now, int limit) {
    int scanLimit = boundedRecoveryLimit(limit);
    if (scanLimit == 0) {
      return List.of();
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("status", Map.of("=", ExternalEventInbox.STATUS_FAILED));
    where.put("retryCount", Map.of("<=", ExternalEventInbox.MAX_RETRY_COUNT));
    Map<String, Object> due = new LinkedHashMap<>();
    due.put("nextAttemptAt", Map.of("<=", now));
    Map<String, Object> immediate = new LinkedHashMap<>();
    immediate.put("nextAttemptAt", null);
    where.put("$or", List.of(due, immediate));
    try {
      return (List<ExternalEventInbox>) searchList(candidateStatement(
          where, orderBy("nextAttemptAt", "createdAt", "id"), scanLimit));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询待重试外部事件收件箱失败", ex);
    }
  }

  /**
   * 兜底扫描：租约已过期的 PROCESSING 条目。
   */
  public List<ExternalEventInbox> findExpiredProcessing(LocalDateTime now) {
    return findExpiredProcessing(now, DEFAULT_RECOVERY_BATCH_SIZE);
  }

  /** 兜底扫描：租约已过期的 PROCESSING 条目。 */
  public List<ExternalEventInbox> findExpiredProcessing(LocalDateTime now, int limit) {
    int scanLimit = boundedRecoveryLimit(limit);
    if (scanLimit == 0) {
      return List.of();
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("status", Map.of("=", ExternalEventInbox.STATUS_PROCESSING));
    where.put("claimUntil", Map.of("<=", now));
    try {
      return (List<ExternalEventInbox>) searchList(candidateStatement(
          where, orderBy("claimUntil", "createdAt", "id"), scanLimit));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询过期处理中外部事件收件箱失败", ex);
    }
  }

  private int boundedRecoveryLimit(int limit) {
    return Math.min(Math.max(limit, 0), MAX_RECOVERY_SCAN_LIMIT);
  }

  private Map<String, String> orderBy(String... fields) {
    Map<String, String> order = new LinkedHashMap<>();
    for (String field : fields) {
      order.put(field, "asc");
    }
    return order;
  }

  private Map<String, Object> candidateStatement(
      Map<String, Object> where, Map<String, String> order, int limit) {
    Map<String, Object> statement = new LinkedHashMap<>();
    statement.put("WHERE", where);
    statement.put("ORDER", order);
    statement.put("LIMIT", limit);
    return statement;
  }

  private boolean conditionalUpdate(Map<String, Object> where, Map<String, Object> data, String message) {
    try {
      var result = getDataModel().update(where, data);
      if (result.isFailure()) {
        throw new BusinessException(message, result.getCause());
      }
      Long rows = result.get();
      return rows != null && rows == 1;
    } catch (OuroborosException ex) {
      throw new BusinessException(message, ex);
    }
  }
}
