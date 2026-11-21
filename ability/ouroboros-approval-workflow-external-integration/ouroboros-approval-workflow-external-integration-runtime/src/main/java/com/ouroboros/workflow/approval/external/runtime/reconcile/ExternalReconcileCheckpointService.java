package com.ouroboros.workflow.approval.external.runtime.reconcile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;

/** Claim/fencing and durable progress for the complete RUNNING binding scan. */
@Service
public class ExternalReconcileCheckpointService
    extends SimpleModelServiceImpl<String, ExternalReconcileCheckpoint> {

  public static final int DEFAULT_LEASE_SECONDS = 300;

  public record Claim(ExternalReconcileCheckpoint checkpoint, String claimToken) {}

  @Transactional
  public Optional<Claim> claim(String owner, int leaseSeconds) {
    if (owner == null || owner.isBlank()) {
      throw new BusinessException("外部状态对账 claim owner 不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    String token = UUID.randomUUID().toString();
    Optional<ExternalReconcileCheckpoint> existing = find();
    if (existing.isEmpty()) {
      ExternalReconcileCheckpoint created = new ExternalReconcileCheckpoint();
      created.setId(ExternalReconcileCheckpoint.ID);
      created.setClaimOwner(owner);
      created.setClaimToken(token);
      created.setClaimUntil(now.plusSeconds(Math.max(leaseSeconds, 1)));
      created.setUpdatedAt(now);
      try {
        var result = getDataModel().insert(created);
        if (result.isSuccess()) {
          return Optional.of(new Claim(result.get(), token));
        }
        if (!ExternalPersistenceFailures.isUniqueConstraintViolation(result.getCause())) {
          throw new BusinessException("创建外部状态对账检查点失败", result.getCause());
        }
        return Optional.empty();
      } catch (OuroborosException ex) {
        if (ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
          return Optional.empty();
        }
        throw new BusinessException("创建外部状态对账检查点失败", ex);
      }
    }
    ExternalReconcileCheckpoint checkpoint = existing.get();
    if (checkpoint.getClaimToken() != null
        && checkpoint.getClaimUntil() != null
        && checkpoint.getClaimUntil().isAfter(now)) {
      return Optional.empty();
    }
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", checkpoint.getId()));
    if (checkpoint.getClaimToken() == null) {
      where.put("updatedAt", Map.of("=", checkpoint.getUpdatedAt()));
    } else {
      where.put("claimToken", Map.of("=", checkpoint.getClaimToken()));
      where.put("claimUntil", Map.of("<", now.plusNanos(1)));
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("claimOwner", owner);
    data.put("claimToken", token);
    data.put("claimUntil", now.plusSeconds(Math.max(leaseSeconds, 1)));
    data.put("updatedAt", now);
    if (!update(where, data, "领取外部状态对账检查点失败")) {
      return Optional.empty();
    }
    return find().map(value -> new Claim(value, token));
  }

  @Transactional
  public boolean advance(String claimToken, String afterLocalInstanceId) {
    return updateClaimed(claimToken, Map.of(
        "afterLocalInstanceId", afterLocalInstanceId,
        "updatedAt", LocalDateTime.now()));
  }

  @Transactional
  public boolean complete(String claimToken) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("afterLocalInstanceId", null);
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(claimToken, data);
  }

  @Transactional
  public boolean release(String claimToken) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("claimOwner", null);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    return updateClaimed(claimToken, data);
  }

  private Optional<ExternalReconcileCheckpoint> find() {
    try {
      return Optional.ofNullable(getByPrimaryKey(ExternalReconcileCheckpoint.ID));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部状态对账检查点失败", ex);
    }
  }

  private boolean updateClaimed(String claimToken, Map<String, Object> data) {
    if (claimToken == null || claimToken.isBlank()) {
      return false;
    }
    return update(Map.of(
        "id", Map.of("=", ExternalReconcileCheckpoint.ID),
        "claimToken", Map.of("=", claimToken)), data,
        "更新外部状态对账检查点失败");
  }

  private boolean update(Map<String, Object> where, Map<String, Object> data, String message) {
    try {
      var result = getDataModel().update(where, data);
      if (result.isFailure()) {
        throw new BusinessException(message, result.getCause());
      }
      return result.get() != null && result.get() == 1;
    } catch (OuroborosException ex) {
      throw new BusinessException(message, ex);
    }
  }
}
