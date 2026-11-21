package com.ouroboros.workflow.approval.external.runtime.action;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

import io.vavr.control.Try;

/** 外部动作事实仓储：reserve、claim/lease 和 token fencing 的单一实现。 */
@Service
public class ExternalActionFactService extends SimpleModelServiceImpl<String, ExternalActionFact> {

  public static final int DEFAULT_LEASE_SECONDS = 300;
  private static final int MAX_RECOVERY_SCAN_LIMIT = 1_000;

  public record Reservation(ExternalActionFact fact, boolean claimed) {
  }

  public record RecoveryClaim(ExternalActionFact fact, String claimToken) {
  }

  @Transactional
  public Try<Reservation> reserve(ExternalActionFact fact) {
    fact.setActiveRouteKey(activeRouteKey(fact.getTargetType(), fact.getTargetLocalId()));
    // Same-state task commands (for example reminders) may legitimately occur more than once.
    // Only an unfinished command is coalesced; recovery always claims its original fact ID.
    fact.setIdempotencyRouteKey(isRepeatableTaskCommand(fact)
        ? ExternalRouteKeys.of("task-command-occurrence", fact.getId())
        : ExternalRouteKeys.of("action-idempotency", fact.getTargetType(), fact.getTargetLocalId(),
            fact.getCanonicalAction(), fact.getSourceState(), fact.getRequestFingerprint()));
    fact.setStatus(ExternalActionFact.STATUS_EXECUTING);
    fact.setRemoteCallPhase(ExternalActionFact.PHASE_NOT_STARTED);
    fact.setClaimToken(UUID.randomUUID().toString());
    LocalDateTime now = LocalDateTime.now();
    fact.setClaimUntil(now.plusSeconds(DEFAULT_LEASE_SECONDS));
    fact.setCreatedAt(now);
    fact.setUpdatedAt(now);
    try {
      // Saga bookkeeping must not inherit the caller's business write set or resource guard.
      var inserted = DataModelExecutionContext.withPlatformWrite(() -> getDataModel().insert(fact));
      if (inserted.isSuccess()) {
        return Try.success(new Reservation(inserted.get(), true));
      }
      return handleInsertFailure(fact, inserted.getCause());
    } catch (RuntimeException ex) {
      return handleInsertFailure(fact, ex);
    }
  }

  private Try<Reservation> handleInsertFailure(ExternalActionFact requested, Throwable cause) {
    if (!ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
      return Try.failure(new BusinessException("创建外部动作事实失败", cause));
    }
    Optional<ExternalActionFact> idempotent = findByRouteKey(
        "idempotencyRouteKey", requested.getIdempotencyRouteKey());
    if (idempotent.isPresent()
        && requested.getRequestFingerprint().equals(idempotent.get().getRequestFingerprint())) {
      return Try.success(new Reservation(idempotent.get(), false));
    }
    Optional<ExternalActionFact> active = findByRouteKey("activeRouteKey", requested.getActiveRouteKey());
    if (active.isPresent() && Objects.equals(requested.getRequestFingerprint(), active.get().getRequestFingerprint())
        && Objects.equals(requested.getCanonicalAction(), active.get().getCanonicalAction())
        && Objects.equals(requested.getSourceState(), active.get().getSourceState())
        && Objects.equals(requested.getTargetStatus(), active.get().getTargetStatus())) {
      return Try.success(new Reservation(active.get(), false));
    }
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT + "] 目标已有未完成外部动作: target="
            + requested.getTargetType() + "/" + requested.getTargetLocalId()
            + active.map(value -> ", factId=" + value.getId()).orElse(""), cause));
  }

  private boolean isRepeatableTaskCommand(ExternalActionFact fact) {
    return ExternalActionFact.TARGET_TASK.equals(fact.getTargetType())
        && Objects.equals(fact.getSourceState(), fact.getTargetStatus())
        && !List.of("APPROVED", "REJECTED", "CANCELLED").contains(fact.getTargetStatus());
  }

  public Optional<ExternalActionFact> findById(String id) {
    try {
      return Optional.ofNullable(getByPrimaryKey(id, Map.of()));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部动作事实失败: " + id, ex);
    }
  }

  private Optional<ExternalActionFact> findByRouteKey(String field, String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          field, Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部动作事实失败: " + field, ex);
    }
  }

  @Transactional
  public boolean markInFlight(String factId, String claimToken) {
    return updateClaimed(factId, claimToken, Map.of(
        "remoteCallPhase", ExternalActionFact.PHASE_IN_FLIGHT,
        "updatedAt", LocalDateTime.now()));
  }

  /** 当前 worker 持有 claim 且 lease 尚未过期时续租。 */
  @Transactional
  public boolean renewClaim(String factId, String claimToken, int leaseSeconds) {
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("status", Map.of("=", ExternalActionFact.STATUS_EXECUTING));
    where.put("claimToken", Map.of("=", claimToken));
    where.put("claimUntil", Map.of(">=", now));
    return conditionalUpdate(where, Map.of(
        "claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)),
        "updatedAt", now), "续租外部动作事实失败: " + factId);
  }

  @Transactional
  public boolean markReplied(String factId, String claimToken, String resultJson) {
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("remoteCallPhase", ExternalActionFact.PHASE_REPLIED);
    data.put("accepted", Boolean.TRUE);
    data.put("resultJson", resultJson);
    data.put("repliedAt", now);
    data.put("updatedAt", now);
    return updateClaimed(factId, claimToken, data);
  }

  @Transactional
  public boolean markUnknown(String factId, String claimToken, String error) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalActionFact.STATUS_UNKNOWN);
    data.put("lastError", error);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(factId, claimToken, data);
  }

  @Transactional
  public boolean markRejected(String factId, String claimToken, String resultJson) {
    return markTerminal(factId, claimToken, ExternalActionFact.STATUS_REJECTED, resultJson,
        "外部系统未接受动作");
  }

  @Transactional
  public boolean markConflict(String factId, String claimToken, String error) {
    return markTerminal(factId, claimToken, ExternalActionFact.STATUS_CONFLICT, null, error);
  }

  @Transactional
  public boolean markSucceeded(String factId, String claimToken) {
    return markTerminal(factId, claimToken, ExternalActionFact.STATUS_SUCCEEDED, null, null);
  }

  private boolean markTerminal(
      String factId, String claimToken, String status, String resultJson, String error) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", status);
    data.put("activeRouteKey", terminalRouteKey("active", factId));
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("lastError", error);
    if (resultJson != null) {
      data.put("resultJson", resultJson);
      data.put("accepted", Boolean.FALSE);
      data.put("remoteCallPhase", ExternalActionFact.PHASE_REPLIED);
    }
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(factId, claimToken, data);
  }

  public List<RecoveryClaim> claimRecoverable(int limit, int leaseSeconds) {
    int size = Math.min(Math.max(limit, 0), MAX_RECOVERY_SCAN_LIMIT);
    if (size == 0) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<ExternalActionFact> candidates = new ArrayList<>();
    try {
      candidates.addAll((List<ExternalActionFact>) searchList(candidateStatement(
          Map.of("status", Map.of("=", ExternalActionFact.STATUS_UNKNOWN)),
          orderBy("createdAt", "id"), size)));
      candidates.addAll((List<ExternalActionFact>) searchList(candidateStatement(
          Map.of(
              "status", Map.of("=", ExternalActionFact.STATUS_EXECUTING),
              "claimUntil", Map.of("<=", now)),
          orderBy("claimUntil", "createdAt", "id"), size)));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询可恢复外部动作事实失败", ex);
    }
    List<RecoveryClaim> claimed = new ArrayList<>();
    for (ExternalActionFact fact : candidates) {
      if (claimed.size() >= size) {
        break;
      }
      claimForRecovery(fact.getId(), leaseSeconds).ifPresent(claimed::add);
    }
    return claimed;
  }

  @Transactional
  public Optional<RecoveryClaim> claimForRecovery(String factId, int leaseSeconds) {
    Optional<ExternalActionFact> factOpt = findById(factId);
    if (factOpt.isEmpty()) {
      return Optional.empty();
    }
    ExternalActionFact fact = factOpt.get();
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    if (ExternalActionFact.STATUS_UNKNOWN.equals(fact.getStatus())) {
      where.put("status", Map.of("=", ExternalActionFact.STATUS_UNKNOWN));
    } else if (ExternalActionFact.STATUS_EXECUTING.equals(fact.getStatus())
        && fact.getClaimUntil() != null && !fact.getClaimUntil().isAfter(now)) {
      where.put("status", Map.of("=", ExternalActionFact.STATUS_EXECUTING));
      where.put("claimUntil", Map.of("<", now.plusNanos(1)));
    } else {
      return Optional.empty();
    }
    String claimToken = UUID.randomUUID().toString();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalActionFact.STATUS_EXECUTING);
    data.put("claimToken", claimToken);
    data.put("claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)));
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "领取外部动作事实失败: " + factId)) {
      return Optional.empty();
    }
    return findById(factId).map(claimed -> new RecoveryClaim(claimed, claimToken));
  }

  private boolean updateClaimed(String factId, String claimToken, Map<String, Object> data) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", factId));
    where.put("status", Map.of("=", ExternalActionFact.STATUS_EXECUTING));
    where.put("claimToken", Map.of("=", claimToken));
    return conditionalUpdate(where, data, "更新外部动作事实失败: " + factId);
  }

  private boolean conditionalUpdate(Map<String, Object> where, Map<String, Object> data, String message) {
    try {
      var result = DataModelExecutionContext.withPlatformWrite(() -> getDataModel().update(where, data));
      if (result.isFailure()) {
        throw new BusinessException(message, result.getCause());
      }
      return result.get() != null && result.get() == 1;
    } catch (OuroborosException ex) {
      throw new BusinessException(message, ex);
    }
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

  private String activeRouteKey(String targetType, String targetLocalId) {
    return ExternalRouteKeys.of("action-active", targetType, targetLocalId);
  }

  private String terminalRouteKey(String routeType, String factId) {
    return ExternalRouteKeys.of("action-terminal", routeType, factId);
  }
}
