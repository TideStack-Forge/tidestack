package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.runtime.ExternalEventOrder;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;

import io.vavr.control.Try;

/**
 * 结果绑定仓储实现。
 */
@Service
public class ExternalResultBindingServiceImpl
    extends SimpleModelServiceImpl<String, ExternalResultBinding>
    implements ExternalResultBindingService {

  private static final int MAX_CAS_ATTEMPTS = 3;

  /** 生产 binding 写入必须与 mapping cleanup 竞争同一数据库行锁。 */
  @Autowired(required = false)
  private WorkflowEngineMappingService mappingService;

  @Override
  @Transactional
  public Try<ExternalResultBinding> create(ExternalResultBinding binding) {
    if (binding.getTrackingId() == null || binding.getTrackingId().isBlank()) {
      binding.setTrackingId(UUID.randomUUID().toString());
    }
    refreshRouteKeys(binding);
    LocalDateTime now = LocalDateTime.now();
    binding.setCreatedAt(now);
    binding.setUpdatedAt(now);
    try {
      Try<Void> lock = acquireMappingReferenceLock(binding.getMappingId());
      if (lock.isFailure()) {
        return Try.failure(lock.getCause());
      }
      var insert = getDataModel().insert(binding);
      if (insert.isSuccess()) {
        return Try.success(insert.get());
      }
      Throwable cause = insert.getCause();
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(cause)) {
        return findExistingForUniqueConflict(binding)
            .map(existing -> sameIdentity(binding, existing)
                ? Try.success(existing)
                : Try.<ExternalResultBinding>failure(new BusinessException(
                    "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                        + "] 外部结果绑定身份冲突: trackingId=" + binding.getTrackingId(), cause)))
            .orElseGet(() -> Try.failure(new BusinessException("结果绑定已存在但回查失败: "
                + binding.getAdapterId() + "/" + binding.getConnectionId(), cause)));
      }
      return Try.failure(new BusinessException("创建结果绑定失败: " + cause.getMessage(), cause));
    } catch (OuroborosException ex) {
      return Try.failure(new BusinessException("创建结果绑定失败: " + ex.getMessage(), ex));
    }
  }

  private Try<Void> acquireMappingReferenceLock(String mappingId) {
    if (mappingService == null || mappingId == null || mappingId.isBlank()) {
      return Try.success(null);
    }
    try {
      return mappingService.acquireBindingReferenceLock(mappingId)
          ? Try.success(null)
          : Try.failure(new BusinessException(
              "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                  + "] 映射不存在或正在被清理，拒绝创建外部结果绑定: " + mappingId));
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private boolean sameIdentity(ExternalResultBinding requested, ExternalResultBinding existing) {
    return Objects.equals(requested.getTrackingId(), existing.getTrackingId())
        && Objects.equals(requested.getScope(), existing.getScope())
        && Objects.equals(requested.getFlowKey(), existing.getFlowKey())
        && Objects.equals(requested.getMappingId(), existing.getMappingId())
        && Objects.equals(requested.getMappingVersion(), existing.getMappingVersion())
        && Objects.equals(requested.getApprovalCaseId(), existing.getApprovalCaseId())
        && Objects.equals(requested.getSubmissionRound(), existing.getSubmissionRound())
        && Objects.equals(requested.getAdapterId(), existing.getAdapterId())
        && Objects.equals(requested.getConnectionId(), existing.getConnectionId())
        && Objects.equals(requested.getBusinessRecordType(), existing.getBusinessRecordType())
        && Objects.equals(requested.getBusinessRecordId(), existing.getBusinessRecordId())
        && Objects.equals(requested.getExternalInstanceId(), existing.getExternalInstanceId())
        && Objects.equals(requested.getIdempotencyKey(), existing.getIdempotencyKey())
        && Objects.equals(requested.getExternalDefinitionId(), existing.getExternalDefinitionId());
  }

  private Optional<ExternalResultBinding> findExistingForUniqueConflict(ExternalResultBinding binding) {
    Optional<ExternalResultBinding> byExternalInstance = findByExternalInstance(
        binding.getAdapterId(), binding.getConnectionId(), binding.getExternalInstanceId());
    if (byExternalInstance.isPresent()) {
      return byExternalInstance;
    }
    if (isNotBlank(binding.getIdempotencyKey())) {
      Optional<ExternalResultBinding> byIdempotencyKey = findByIdempotencyKey(
          binding.getAdapterId(), binding.getConnectionId(), binding.getIdempotencyKey());
      if (byIdempotencyKey.isPresent()) {
        return byIdempotencyKey;
      }
    }
    if (isNotBlank(binding.getBusinessRecordType()) && isNotBlank(binding.getBusinessRecordId())) {
      return findRunningByBusinessRecord(
          binding.getScope(), binding.getFlowKey(), binding.getBusinessRecordType(), binding.getBusinessRecordId());
    }
    return Optional.empty();
  }

  private void refreshRouteKeys(ExternalResultBinding binding) {
    binding.setExternalInstanceRouteKey(isNotBlank(binding.getExternalInstanceId())
        ? ExternalRouteKeys.of(
            "result-external", binding.getAdapterId(), binding.getConnectionId(), binding.getExternalInstanceId())
        : ExternalRouteKeys.of("result-missing-external", binding.getTrackingId()));
    binding.setIdempotencyRouteKey(isNotBlank(binding.getIdempotencyKey())
        ? ExternalRouteKeys.of(
            "result-idempotency", binding.getAdapterId(), binding.getConnectionId(), binding.getIdempotencyKey())
        : ExternalRouteKeys.of("result-missing-idempotency", binding.getTrackingId()));
    binding.setBusinessRunningRouteKey(ExternalResultBinding.STATUS_RUNNING.equals(binding.getStatus())
        && isNotBlank(binding.getBusinessRecordType()) && isNotBlank(binding.getBusinessRecordId())
        ? ExternalRouteKeys.of(
            "result-running-business",
            binding.getScope(),
            binding.getFlowKey(),
            binding.getBusinessRecordType(),
            binding.getBusinessRecordId())
        : ExternalRouteKeys.of("result-terminal-business", binding.getTrackingId()));
  }

  private boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }

  @Override
  @Transactional
  public Try<Boolean> markFinished(
      String trackingId, String outcome, String externalRevision, LocalDateTime externalEventTime,
      String externalEventId) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalResultBinding> bindingOpt = findByTrackingId(trackingId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("结果绑定不存在: " + trackingId));
      }
      ExternalResultBinding binding = bindingOpt.get();
      if (ExternalEventOrder.compareFact(
          externalRevision, externalEventTime, externalEventId,
          binding.getExternalRevision(), binding.getExternalEventTime(), binding.getLastExternalEventId()) <= 0
          || ExternalResultBinding.STATUS_FINISHED.equals(binding.getStatus())) {
        return Try.success(false);
      }
      LocalDateTime now = LocalDateTime.now();
      Map<String, Object> where = sourceWhere(
          "trackingId", trackingId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("status", ExternalResultBinding.STATUS_FINISHED);
      data.put("businessRunningRouteKey", ExternalRouteKeys.of("result-terminal-business", trackingId));
      data.put("outcome", outcome);
      if (externalRevision != null) {
        data.put("externalRevision", externalRevision);
      }
      if (externalEventTime != null) {
        data.put("externalEventTime", externalEventTime);
      }
      data.put("lastExternalEventId", externalEventId);
      data.put("finalResultAt", now);
      data.put("updatedAt", now);
      try {
        if (updateBySource(where, data)) {
          return Try.success(true);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("更新结果绑定失败: " + trackingId, ex));
      }
    }
    return Try.failure(new BusinessException("CAS 更新结果绑定冲突未收敛: " + trackingId));
  }

  @Override
  public Optional<ExternalResultBinding> findByTrackingId(String trackingId) {
    try {
      return Optional.ofNullable(getByPrimaryKey(trackingId));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询结果绑定失败: " + trackingId, ex);
    }
  }

  @Override
  public Optional<ExternalResultBinding> findByIdempotencyKey(
      String adapterId, String connectionId, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "adapterId", adapterId,
          "connectionId", connectionId,
          "idempotencyKey", idempotencyKey))));
    } catch (OuroborosException ex) {
      throw new BusinessException(
          "按幂等键查询结果绑定失败: " + adapterId + "/" + connectionId + "/" + idempotencyKey, ex);
    }
  }

  @Override
  public Optional<ExternalResultBinding> findByExternalInstance(
      String adapterId, String connectionId, String externalInstanceId) {
    if (externalInstanceId == null || externalInstanceId.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "adapterId", adapterId,
          "connectionId", connectionId,
          "externalInstanceId", externalInstanceId))));
    } catch (OuroborosException ex) {
      throw new BusinessException(
          "按外部实例查询结果绑定失败: " + adapterId + "/" + connectionId + "/" + externalInstanceId, ex);
    }
  }

  @Override
  @Transactional
  public Try<Boolean> updateRevision(
      String trackingId, String externalRevision, LocalDateTime externalEventTime, String externalEventId) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalResultBinding> bindingOpt = findByTrackingId(trackingId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("结果绑定不存在: " + trackingId));
      }
      ExternalResultBinding binding = bindingOpt.get();
      if (ExternalEventOrder.compareFact(
          externalRevision, externalEventTime, externalEventId,
          binding.getExternalRevision(), binding.getExternalEventTime(), binding.getLastExternalEventId()) <= 0
          || ExternalResultBinding.STATUS_FINISHED.equals(binding.getStatus())) {
        return Try.success(false);
      }
      Map<String, Object> where = sourceWhere(
          "trackingId", trackingId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      if (externalRevision != null) {
        data.put("externalRevision", externalRevision);
      }
      if (externalEventTime != null) {
        data.put("externalEventTime", externalEventTime);
      }
      data.put("lastExternalEventId", externalEventId);
      data.put("updatedAt", LocalDateTime.now());
      try {
        if (updateBySource(where, data)) {
          return Try.success(true);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("更新结果绑定失败: " + trackingId, ex));
      }
    }
    return Try.failure(new BusinessException("CAS 更新结果绑定冲突未收敛: " + trackingId));
  }

  @Override
  @Transactional
  public Try<ActionStatusUpdate> applyActionFinished(String trackingId, String outcome) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalResultBinding> bindingOpt = findByTrackingId(trackingId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("结果绑定不存在: " + trackingId));
      }
      ExternalResultBinding binding = bindingOpt.get();
      if (ExternalResultBinding.STATUS_FINISHED.equals(binding.getStatus())) {
        return Try.success(outcome.equals(binding.getOutcome())
            ? ActionStatusUpdate.ALREADY_APPLIED : ActionStatusUpdate.CONFLICT);
      }
      Map<String, Object> where = sourceWhere(
          "trackingId", trackingId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      LocalDateTime now = LocalDateTime.now();
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("status", ExternalResultBinding.STATUS_FINISHED);
      data.put("businessRunningRouteKey", ExternalRouteKeys.of("result-terminal-business", trackingId));
      data.put("outcome", outcome);
      data.put("finalResultAt", now);
      data.put("updatedAt", now);
      try {
        if (updateBySource(where, data)) {
          return Try.success(ActionStatusUpdate.UPDATED);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("完成外部结果动作状态失败: " + trackingId, ex));
      }
    }
    return Try.success(ActionStatusUpdate.CONFLICT);
  }

  private Map<String, Object> sourceWhere(
      String idField,
      String id,
      String status,
      String currentRevision,
      LocalDateTime currentEventTime,
      String currentEventId) {
    Map<String, Object> where = new java.util.LinkedHashMap<>();
    where.put(idField, Map.of("=", id));
    where.put("status", Map.of("=", status));
    putNullableMatch(where, "externalRevision", currentRevision);
    putNullableMatch(where, "externalEventTime", currentEventTime);
    putNullableMatch(where, "lastExternalEventId", currentEventId);
    return where;
  }

  private void putNullableMatch(Map<String, Object> where, String field, Object value) {
    if (value == null) {
      where.put(field, null);
      return;
    }
    where.put(field, Map.of("=", value));
  }

  private boolean updateBySource(Map<String, Object> where, Map<String, Object> data) throws OuroborosException {
    var result = getDataModel().update(where, data);
    if (result.isFailure()) {
      throw new BusinessException("CAS 更新结果绑定失败", result.getCause());
    }
    Long rows = result.get();
    return rows != null && rows > 0;
  }

  @Override
  public Optional<ExternalResultBinding> findRunningByBusinessRecord(
      String scope, String flowKey, String recordType, String recordId) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "scope", scope,
          "flowKey", flowKey,
          "businessRecordType", recordType,
          "businessRecordId", recordId,
          "status", ExternalResultBinding.STATUS_RUNNING))));
    } catch (OuroborosException ex) {
      throw new BusinessException(
          "按业务记录查询运行中结果绑定失败: " + scope + "/" + flowKey + "/" + recordType + "/" + recordId, ex);
    }
  }

  @Override
  public Long countByMappingId(String mappingId) {
    try {
      return count(Map.of("mappingId", Map.of("=", mappingId)));
    } catch (OuroborosException ex) {
      throw new BusinessException("按映射统计结果绑定失败: " + mappingId, ex);
    }
  }
}
