package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;
import java.util.List;
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
 * 外部实例绑定仓储。
 */
@Service
public class ExternalInstanceBindingService extends SimpleModelServiceImpl<String, ExternalInstanceBinding> {

  private static final int MAX_CAS_ATTEMPTS = 3;

  /** 生产 binding 写入必须与 mapping cleanup 竞争同一数据库行锁。 */
  @Autowired(required = false)
  private WorkflowEngineMappingService mappingService;

  @Transactional
  public Try<ExternalInstanceBinding> create(ExternalInstanceBinding binding) {
    if (binding.getLocalInstanceId() == null || binding.getLocalInstanceId().isBlank()) {
      binding.setLocalInstanceId(UUID.randomUUID().toString());
    }
    refreshExternalInstanceRouteKey(binding);
    java.time.LocalDateTime now = java.time.LocalDateTime.now();
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
        return findByExternalInstance(binding.getAdapterId(), binding.getConnectionId(), binding.getExternalInstanceId())
            .map(existing -> sameIdentity(binding, existing)
                ? Try.success(existing)
                : Try.<ExternalInstanceBinding>failure(new BusinessException(
                    "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                        + "] 外部实例绑定身份冲突: externalInstanceId="
                        + binding.getExternalInstanceId(), cause)))
            .orElseGet(() -> Try.failure(new BusinessException(
                "外部实例绑定已存在但回查失败: " + binding.getAdapterId() + "/"
                    + binding.getConnectionId() + "/" + binding.getExternalInstanceId(), cause)));
      }
      return Try.failure(new BusinessException("创建外部实例绑定失败: " + cause.getMessage(), cause));
    } catch (OuroborosException ex) {
      return Try.failure(new BusinessException("创建外部实例绑定失败: " + ex.getMessage(), ex));
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
                  + "] 映射不存在或正在被清理，拒绝创建外部实例绑定: " + mappingId));
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private boolean sameIdentity(ExternalInstanceBinding requested, ExternalInstanceBinding existing) {
    return Objects.equals(requested.getLocalInstanceId(), existing.getLocalInstanceId())
        && Objects.equals(requested.getFlowKey(), existing.getFlowKey())
        && Objects.equals(requested.getScope(), existing.getScope())
        && Objects.equals(requested.getMappingId(), existing.getMappingId())
        && Objects.equals(requested.getMappingVersion(), existing.getMappingVersion())
        && Objects.equals(requested.getApprovalCaseId(), existing.getApprovalCaseId())
        && Objects.equals(requested.getSubmissionRound(), existing.getSubmissionRound())
        && Objects.equals(requested.getAdapterId(), existing.getAdapterId())
        && Objects.equals(requested.getConnectionId(), existing.getConnectionId())
        && Objects.equals(requested.getExternalInstanceId(), existing.getExternalInstanceId())
        && Objects.equals(requested.getExternalDefinitionId(), existing.getExternalDefinitionId())
        && Objects.equals(requested.getBusinessRecordType(), existing.getBusinessRecordType())
        && Objects.equals(requested.getBusinessRecordId(), existing.getBusinessRecordId())
        && Objects.equals(requested.getCallbackFlow(), existing.getCallbackFlow());
  }

  private void refreshExternalInstanceRouteKey(ExternalInstanceBinding binding) {
    if (isBlank(binding.getExternalInstanceId())) {
      binding.setExternalInstanceRouteKey(ExternalRouteKeys.of("instance-pending", binding.getLocalInstanceId()));
      return;
    }
    binding.setExternalInstanceRouteKey(ExternalRouteKeys.of(
        "instance-external", binding.getAdapterId(), binding.getConnectionId(), binding.getExternalInstanceId()));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public Optional<ExternalInstanceBinding> findByLocalId(String localInstanceId) {
    try {
      return Optional.ofNullable(getByPrimaryKey(localInstanceId));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部实例绑定失败: " + localInstanceId, ex);
    }
  }

  public Optional<ExternalInstanceBinding> findByExternalInstance(
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
          "按外部实例查询实例绑定失败: " + adapterId + "/" + connectionId + "/" + externalInstanceId, ex);
    }
  }

  @Transactional
  public Try<Boolean> updateStatus(
      String localInstanceId, String status, String outcome, String externalRevision,
      LocalDateTime externalEventTime, String externalEventId) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalInstanceBinding> bindingOpt = findByLocalId(localInstanceId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("外部实例绑定不存在: " + localInstanceId));
      }
      ExternalInstanceBinding binding = bindingOpt.get();
      if (ExternalEventOrder.compareFact(
          externalRevision, externalEventTime, externalEventId,
          binding.getExternalRevision(), binding.getExternalEventTime(), binding.getLastExternalEventId()) <= 0
          || ExternalInstanceBinding.STATUS_FINISHED.equals(binding.getStatus())) {
        return Try.success(false);
      }
      Map<String, Object> where = sourceWhere(
          localInstanceId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("status", status);
      if (outcome != null) {
        data.put("outcome", outcome);
      }
      if (externalRevision != null) {
        data.put("externalRevision", externalRevision);
      }
      if (externalEventTime != null) {
        data.put("externalEventTime", externalEventTime);
      }
      data.put("lastExternalEventId", externalEventId);
      data.put("updatedAt", java.time.LocalDateTime.now());
      try {
        var result = getDataModel().update(where, data);
        if (result.isFailure()) {
          return Try.failure(new BusinessException("更新外部实例绑定失败: " + localInstanceId, result.getCause()));
        }
        Long rows = result.get();
        if (rows != null && rows > 0) {
          return Try.success(true);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("更新外部实例绑定失败: " + localInstanceId, ex));
      }
    }
    return Try.failure(new BusinessException("CAS 更新外部实例绑定冲突未收敛: " + localInstanceId));
  }

  /** 外部动作已接受后的本地状态完成；不改写外部事件顺序游标。 */
  @Transactional
  public Try<ActionStatusUpdate> applyActionStatus(
      String localInstanceId, String targetStatus, String outcome) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalInstanceBinding> bindingOpt = findByLocalId(localInstanceId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("外部实例绑定不存在: " + localInstanceId));
      }
      ExternalInstanceBinding binding = bindingOpt.get();
      if (targetStatus.equals(binding.getStatus())
          && (outcome == null || outcome.equals(binding.getOutcome()))) {
        return Try.success(ActionStatusUpdate.ALREADY_APPLIED);
      }
      if (ExternalInstanceBinding.STATUS_FINISHED.equals(binding.getStatus())) {
        return Try.success(ActionStatusUpdate.CONFLICT);
      }
      Map<String, Object> where = sourceWhere(
          localInstanceId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("status", targetStatus);
      if (outcome != null) {
        data.put("outcome", outcome);
      }
      data.put("updatedAt", LocalDateTime.now());
      try {
        var result = getDataModel().update(where, data);
        if (result.isFailure()) {
          return Try.failure(new BusinessException("完成外部实例动作状态失败: " + localInstanceId, result.getCause()));
        }
        if (result.get() != null && result.get() == 1) {
          return Try.success(ActionStatusUpdate.UPDATED);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("完成外部实例动作状态失败: " + localInstanceId, ex));
      }
    }
    return Try.success(ActionStatusUpdate.CONFLICT);
  }

  private Map<String, Object> sourceWhere(
      String localInstanceId,
      String status,
      String currentRevision,
      LocalDateTime currentEventTime,
      String currentEventId) {
    Map<String, Object> where = new java.util.LinkedHashMap<>();
    where.put("localInstanceId", Map.of("=", localInstanceId));
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

  /**
   * 按完整映射 route 查询实例绑定。
   */
  public List<ExternalInstanceBinding> searchByRoute(
      String scope,
      String mappingId,
      Integer mappingVersion,
      String adapterId,
      String connectionId,
      String status,
      Integer offset,
      Integer limit) {
    int fromIndex = offset == null ? 0 : Math.max(offset, 0);
    int size = limit == null ? 100 : Math.max(limit, 0);
    try {
      List<ExternalInstanceBinding> all = (List<ExternalInstanceBinding>) searchList(routeStatement(
          scope, mappingId, mappingVersion, adapterId, connectionId, status));
      if (all.size() <= fromIndex) {
        return List.of();
      }
      int toIndex = Math.min(all.size(), fromIndex + size);
      return all.subList(fromIndex, toIndex);
    } catch (OuroborosException ex) {
      throw new BusinessException("按完整路由查询外部实例绑定失败: "
          + scope + "/" + mappingId + "/" + mappingVersion + "/" + adapterId + "/" + connectionId, ex);
    }
  }

  public Long countByRoute(
      String scope,
      String mappingId,
      Integer mappingVersion,
      String adapterId,
      String connectionId,
      String status) {
    try {
      return count(routeStatement(scope, mappingId, mappingVersion, adapterId, connectionId, status));
    } catch (OuroborosException ex) {
      throw new BusinessException("按完整路由统计外部实例绑定失败: "
          + scope + "/" + mappingId + "/" + mappingVersion + "/" + adapterId + "/" + connectionId, ex);
    }
  }

  /**
   * 按稳定主键分页读取所有 RUNNING 绑定。补偿必须以绑定保存的 mapping 版本为路由事实，
   * 不能先枚举 ACTIVE mapping 后再反查，因此该查询不带 mapping 条件。
   */
  public List<ExternalInstanceBinding> searchRunningPage(String afterLocalInstanceId, int limit) {
    int size = Math.max(limit, 0);
    if (size == 0) {
      return List.of();
    }
    Map<String, Object> where = new java.util.LinkedHashMap<>();
    where.put("status", Map.of("=", ExternalInstanceBinding.STATUS_RUNNING));
    if (afterLocalInstanceId != null && !afterLocalInstanceId.isBlank()) {
      where.put("localInstanceId", Map.of(">", afterLocalInstanceId));
    }
    try {
      var result = getDataModel().query(null, where, "localInstanceId asc", 0, size);
      if (result.isFailure()) {
        throw new BusinessException("分页查询 RUNNING 外部实例绑定失败", result.getCause());
      }
      return result.get();
    } catch (OuroborosException ex) {
      throw new BusinessException("分页查询 RUNNING 外部实例绑定失败", ex);
    }
  }

  private Map<String, Object> routeStatement(
      String scope,
      String mappingId,
      Integer mappingVersion,
      String adapterId,
      String connectionId,
      String status) {
    return Map.of(
        "scope", Map.of("=", scope),
        "mappingId", Map.of("=", mappingId),
        "mappingVersion", Map.of("=", mappingVersion),
        "adapterId", Map.of("=", adapterId),
        "connectionId", Map.of("=", connectionId),
        "status", Map.of("=", status));
  }

  /**
   * 按映射 ID 统计绑定数（映射版本清理的保护检查用，Phase 4 M21-T3）。
   */
  public Long countByMappingId(String mappingId) {
    try {
      return count(Map.of("mappingId", Map.of("=", mappingId)));
    } catch (OuroborosException ex) {
      throw new BusinessException("按映射统计外部实例绑定失败: " + mappingId, ex);
    }
  }
}
