package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalCallbackEnvelope;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

import io.vavr.control.Try;

/** Durable, provider-neutral receipt and batch ingest implementation. */
@Service
public class PersistentExternalCallbackReceiptService
    extends SimpleModelServiceImpl<String, ExternalCallbackReceipt>
    implements ExternalCallbackReceiptService {

  private static final int LEASE_SECONDS = 300;
  private static final int RETENTION_SECONDS = 86_400;

  private final ExternalEventIngestService ingestService;

  @Autowired
  public PersistentExternalCallbackReceiptService(ExternalEventIngestService ingestService) {
    this.ingestService = ingestService;
  }

  @Override
  @Transactional(noRollbackFor = RuntimeException.class)
  public Try<Void> receive(
      String adapterId,
      String connectionId,
      String nonce,
      String payloadDigest,
      List<ExternalCallbackEnvelope> envelopes) {
    try {
      if (isBlank(adapterId) || isBlank(connectionId) || isBlank(nonce) || isBlank(payloadDigest)) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_EVENT_UNKNOWN + "] 回调 receipt 缺少必要身份字段"));
      }
      List<ExternalCallbackEnvelope> batch = envelopes == null ? List.of() : List.copyOf(envelopes);
      ReceiptClaim claim = open(adapterId, connectionId, nonce, payloadDigest, batch.size());
      if (claim.completed()) {
        return Try.success(null);
      }
      for (ExternalCallbackEnvelope envelope : batch) {
        if (envelope == null || !adapterId.equals(envelope.adapterId())
            || !connectionId.equals(envelope.connectionId())) {
          return failClaim(claim, "回调事件与 receipt route 不一致");
        }
        Try<Void> ingested = ingestService.ingest(envelope);
        if (ingested.isFailure()) {
          recordFailure(claim, ingested.getCause());
          return Try.failure(ingested.getCause());
        }
      }
      if (!complete(claim, payloadDigest, batch.size())) {
        return Try.failure(new BusinessException("回调 receipt claim 已失效，无法完成: " + claim.id()));
      }
      return Try.success(null);
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private ReceiptClaim open(
      String adapterId, String connectionId, String nonce, String payloadDigest, int eventCount) {
    LocalDateTime now = LocalDateTime.now();
    deleteExpiredCompleted(now);
    String routeKey = ExternalRouteKeys.of("callback-receipt", adapterId, connectionId, nonce);
    Optional<ExternalCallbackReceipt> existing = findByRouteKey(routeKey);
    if (existing.isEmpty()) {
      String token = UUID.randomUUID().toString();
      ExternalCallbackReceipt receipt = new ExternalCallbackReceipt();
      receipt.setId(UUID.randomUUID().toString());
      receipt.setAdapterId(adapterId);
      receipt.setConnectionId(connectionId);
      receipt.setNonce(nonce);
      receipt.setNonceRouteKey(routeKey);
      receipt.setPayloadDigest(payloadDigest);
      receipt.setEventCount(eventCount);
      receipt.setStatus(ExternalCallbackReceipt.STATUS_RECEIVING);
      receipt.setClaimToken(token);
      receipt.setClaimUntil(now.plusSeconds(LEASE_SECONDS));
      receipt.setExpiresAt(now.plusSeconds(RETENTION_SECONDS));
      receipt.setCreatedAt(now);
      receipt.setUpdatedAt(now);
      try {
        var insert = getDataModel().insert(receipt);
        if (insert.isSuccess()) {
          return new ReceiptClaim(insert.get().getId(), token, false);
        }
        Throwable cause = insert.getCause();
        if (!ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
          throw new BusinessException("创建外部回调 receipt 失败", cause);
        }
        existing = findByRouteKey(routeKey);
      } catch (RuntimeException ex) {
        if (!ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
          throw new BusinessException("创建外部回调 receipt 失败", ex);
        }
        existing = findByRouteKey(routeKey);
      }
    }
    return reopen(existing.orElseThrow(), payloadDigest, eventCount, now);
  }

  private ReceiptClaim reopen(
      ExternalCallbackReceipt receipt, String payloadDigest, int eventCount, LocalDateTime now) {
    if (!payloadDigest.equals(receipt.getPayloadDigest()) || eventCount != receipt.getEventCount()) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
          + "] 回调 receipt payload 或 eventCount 冲突: nonce=" + receipt.getNonce());
    }
    if (ExternalCallbackReceipt.STATUS_COMPLETED.equals(receipt.getStatus())) {
      return new ReceiptClaim(receipt.getId(), receipt.getClaimToken(), true);
    }
    if (receipt.getClaimUntil() != null && receipt.getClaimUntil().isAfter(now)) {
      throw new BusinessException("回调 receipt 正在由其他 worker 处理: nonce=" + receipt.getNonce());
    }
    String token = UUID.randomUUID().toString();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", receipt.getId()));
    where.put("status", Map.of("=", ExternalCallbackReceipt.STATUS_RECEIVING));
    where.put("payloadDigest", Map.of("=", payloadDigest));
    where.put("eventCount", Map.of("=", eventCount));
    if (receipt.getClaimUntil() == null) {
      where.put("claimToken", null);
    } else {
      where.put("claimToken", Map.of("=", receipt.getClaimToken()));
      where.put("claimUntil", Map.of("<", now.plusNanos(1)));
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("claimToken", token);
    data.put("claimUntil", now.plusSeconds(LEASE_SECONDS));
    data.put("lastError", null);
    data.put("updatedAt", now);
    if (!conditionalUpdate(where, data, "重新领取外部回调 receipt 失败")) {
      throw new BusinessException("回调 receipt claim 已被其他 worker 获取: nonce=" + receipt.getNonce());
    }
    return new ReceiptClaim(receipt.getId(), token, false);
  }

  private boolean complete(ReceiptClaim claim, String payloadDigest, int eventCount) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", claim.id()));
    where.put("status", Map.of("=", ExternalCallbackReceipt.STATUS_RECEIVING));
    where.put("claimToken", Map.of("=", claim.claimToken()));
    where.put("payloadDigest", Map.of("=", payloadDigest));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", ExternalCallbackReceipt.STATUS_COMPLETED);
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("eventCount", eventCount);
    data.put("lastError", null);
    data.put("completedAt", LocalDateTime.now());
    data.put("updatedAt", LocalDateTime.now());
    return conditionalUpdate(where, data, "完成外部回调 receipt 失败");
  }

  private Try<Void> failClaim(ReceiptClaim claim, String message) {
    recordFailure(claim, new BusinessException(message));
    return Try.failure(new BusinessException(message));
  }

  private void recordFailure(ReceiptClaim claim, Throwable cause) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", claim.id()));
    where.put("status", Map.of("=", ExternalCallbackReceipt.STATUS_RECEIVING));
    where.put("claimToken", Map.of("=", claim.claimToken()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("lastError", cause == null ? "unknown" : String.valueOf(cause.getMessage()));
    data.put("claimToken", null);
    data.put("claimUntil", null);
    data.put("updatedAt", LocalDateTime.now());
    conditionalUpdate(where, data, "记录外部回调 receipt 失败原因失败");
  }

  private Optional<ExternalCallbackReceipt> findByRouteKey(String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "nonceRouteKey", Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部回调 receipt 失败", ex);
    }
  }

  private void deleteExpiredCompleted(LocalDateTime now) {
    try {
      var result = getDataModel().delete(Map.of(
          "status", Map.of("=", ExternalCallbackReceipt.STATUS_COMPLETED),
          "expiresAt", Map.of("<", now)));
      if (result.isFailure()) {
        throw new BusinessException("清理外部回调 receipt 失败", result.getCause());
      }
    } catch (OuroborosException ex) {
      throw new BusinessException("清理外部回调 receipt 失败", ex);
    }
  }

  private boolean conditionalUpdate(Map<String, Object> where, Map<String, Object> data, String message) {
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record ReceiptClaim(String id, String claimToken, boolean completed) {
  }
}
