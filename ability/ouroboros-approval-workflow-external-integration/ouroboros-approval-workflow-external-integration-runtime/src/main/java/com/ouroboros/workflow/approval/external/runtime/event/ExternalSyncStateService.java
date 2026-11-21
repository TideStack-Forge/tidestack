package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;

import io.vavr.control.Try;

/**
 * 轮询同步状态仓储。
 */
@Service
public class ExternalSyncStateService extends SimpleModelServiceImpl<String, ExternalSyncState> {

  public static final int DEFAULT_LEASE_SECONDS = 300;

  public record Claim(ExternalSyncState state, String claimToken) {
  }

  public Optional<ExternalSyncState> findById(String id) {
    try {
      return Optional.ofNullable(getByPrimaryKey(id));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部同步状态失败: " + id, ex);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Claim> claim(String adapterId, String connectionId, String owner, int leaseSeconds) {
    if (owner == null || owner.isBlank()) {
      throw new BusinessException("外部同步 claim owner 不能为空");
    }
    String id = ExternalSyncState.buildId(adapterId, connectionId);
    LocalDateTime now = LocalDateTime.now();
    String claimToken = UUID.randomUUID().toString();
    Optional<ExternalSyncState> existing = findById(id);
    if (existing.isEmpty()) {
      ExternalSyncState created = new ExternalSyncState();
      created.setId(id);
      created.setAdapterId(adapterId);
      created.setConnectionId(connectionId);
      created.setLastSyncAt(now.minusYears(1));
      created.setClaimOwner(owner);
      created.setClaimToken(claimToken);
      created.setClaimUntil(now.plusSeconds(Math.max(leaseSeconds, 1)));
      created.setUpdatedAt(now);
      Try<ExternalSyncState> result;
      try {
        result = getDataModel().insert(created);
      } catch (OuroborosException ex) {
        if (!ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
          throw new BusinessException("创建外部同步状态失败: " + id, ex);
        }
        return Optional.empty();
      }
      if (result.isSuccess()) {
        return Optional.of(new Claim(result.get(), claimToken));
      }
      Throwable cause = result.getCause();
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
        // Another node won the first insert. End this claim attempt and let the next poll
        // observe the committed row without continuing inside a possibly rollback-only transaction.
        return Optional.empty();
      }
      throw new BusinessException("创建外部同步状态失败: " + id, cause);
    }
    if (existing.isEmpty() || !isClaimable(existing.get(), now)) {
      return Optional.empty();
    }
    ExternalSyncState state = existing.get();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", id));
    if (state.getClaimToken() == null) {
      where.put("updatedAt", Map.of("=", state.getUpdatedAt()));
    } else {
      where.put("claimToken", Map.of("=", state.getClaimToken()));
      where.put("claimUntil", Map.of("=", state.getClaimUntil()));
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("claimOwner", owner);
    data.put("claimToken", claimToken);
    data.put("claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)));
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "领取外部同步状态失败: " + id)) {
      return Optional.empty();
    }
    return findById(id).map(claimed -> new Claim(claimed, claimToken));
  }

  /** 当前租约持有者记录游标并释放租约；过期 token 不得推进状态。 */
  @Transactional
  public boolean recordSync(String id, String claimToken, LocalDateTime lastSyncAt, String cursor) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("lastSyncAt", lastSyncAt);
    data.put("cursor", cursor);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  /** 首次处理一个 run 时固定窗口；后续页不得重新读取当前时间。 */
  @Transactional
  public boolean beginRun(String id, String claimToken, LocalDateTime runFrom, LocalDateTime runUntil) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("runFrom", runFrom);
    data.put("runUntil", runUntil);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  /**
   * 成功处理非末页，只推进 cursor，保留固定窗口和 watermark，并释放当前页 claim，
   * 使下一次调度可以继续同一 run 的后续页面。
   */
  @Transactional
  public boolean recordPage(String id, String claimToken, String cursor) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("cursor", cursor);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  /** 成功处理末页后原子推进 watermark，并清理 run 状态。 */
  @Transactional
  public boolean completeRun(String id, String claimToken, LocalDateTime watermark) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("lastSyncAt", watermark);
    data.put("runFrom", null);
    data.put("runUntil", null);
    data.put("cursor", null);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  /** 同步失败时释放当前租约，不改变游标。 */
  @Transactional
  public boolean release(String id, String claimToken) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(id, claimToken, data);
  }

  private boolean isClaimable(ExternalSyncState state, LocalDateTime now) {
    return state.getClaimToken() == null
        || state.getClaimUntil() != null && !state.getClaimUntil().isAfter(now);
  }

  private boolean updateClaimed(String id, String claimToken, Map<String, Object> data) {
    if (claimToken == null || claimToken.isBlank()) {
      return false;
    }
    return conditionalUpdate(Map.of(
        "id", Map.of("=", id),
        "claimToken", Map.of("=", claimToken)), data, "更新外部同步状态失败: " + id);
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
