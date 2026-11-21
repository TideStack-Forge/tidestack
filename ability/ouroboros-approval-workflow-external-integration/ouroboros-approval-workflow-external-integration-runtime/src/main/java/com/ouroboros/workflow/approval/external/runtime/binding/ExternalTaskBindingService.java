package com.ouroboros.workflow.approval.external.runtime.binding;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 外部任务绑定仓储。
 */
@Service
public class ExternalTaskBindingService extends SimpleModelServiceImpl<String, ExternalTaskBinding> {

  private static final int MAX_CAS_ATTEMPTS = 3;

  /** 生产 binding 写入必须与 mapping cleanup 竞争同一数据库行锁。 */
  @Autowired(required = false)
  private WorkflowEngineMappingService mappingService;

  @Transactional
  public Try<ExternalTaskBinding> create(ExternalTaskBinding binding) {
    if (binding.getLocalTaskId() == null || binding.getLocalTaskId().isBlank()) {
      binding.setLocalTaskId(UUID.randomUUID().toString());
    }
    refreshExternalTaskRouteKey(binding);
    binding.setUpdatedAt(java.time.LocalDateTime.now());
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
        return findByExternalTask(binding.getAdapterId(), binding.getConnectionId(), binding.getExternalTaskId())
            .map(existing -> sameIdentity(binding, existing)
                ? Try.success(existing)
                : Try.<ExternalTaskBinding>failure(new BusinessException(
                    "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                        + "] 外部任务绑定身份冲突: externalTaskId="
                        + binding.getExternalTaskId(), cause)))
            .orElseGet(() -> Try.failure(new BusinessException(
                "外部任务绑定已存在但回查失败: " + binding.getAdapterId() + "/"
                    + binding.getConnectionId() + "/" + binding.getExternalTaskId(), cause)));
      }
      return Try.failure(new BusinessException("创建外部任务绑定失败: " + cause.getMessage(), cause));
    } catch (OuroborosException ex) {
      return Try.failure(new BusinessException("创建外部任务绑定失败: " + ex.getMessage(), ex));
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
                  + "] 映射不存在或正在被清理，拒绝创建外部任务绑定: " + mappingId));
    } catch (RuntimeException ex) {
      return Try.failure(ex);
    }
  }

  private boolean sameIdentity(ExternalTaskBinding requested, ExternalTaskBinding existing) {
    // externalTaskRouteKey 是外部任务的唯一事实键；localTaskId 是本地投影，首次并发发现时必然可能不同。
    // nodeId/assignee 来自可变外部快照，不应把同一外部任务的刷新误判为身份冲突。
    return Objects.equals(requested.getLocalInstanceId(), existing.getLocalInstanceId())
        && Objects.equals(requested.getScope(), existing.getScope())
        && Objects.equals(requested.getMappingId(), existing.getMappingId())
        && Objects.equals(requested.getMappingVersion(), existing.getMappingVersion())
        && Objects.equals(requested.getAdapterId(), existing.getAdapterId())
        && Objects.equals(requested.getConnectionId(), existing.getConnectionId())
        && Objects.equals(requested.getExternalTaskId(), existing.getExternalTaskId());
  }

  private void refreshExternalTaskRouteKey(ExternalTaskBinding binding) {
    if (isBlank(binding.getExternalTaskId())) {
      binding.setExternalTaskRouteKey(ExternalRouteKeys.of("task-pending", binding.getLocalTaskId()));
      return;
    }
    binding.setExternalTaskRouteKey(ExternalRouteKeys.of(
        "task-external", binding.getAdapterId(), binding.getConnectionId(), binding.getExternalTaskId()));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public Optional<ExternalTaskBinding> findByLocalId(String localTaskId) {
    try {
      return Optional.ofNullable(getByPrimaryKey(localTaskId));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部任务绑定失败: " + localTaskId, ex);
    }
  }

  public Optional<ExternalTaskBinding> findByExternalTask(
      String adapterId, String connectionId, String externalTaskId) {
    if (externalTaskId == null || externalTaskId.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "adapterId", adapterId,
          "connectionId", connectionId,
          "externalTaskId", externalTaskId))));
    } catch (OuroborosException ex) {
      throw new BusinessException(
          "按外部任务查询任务绑定失败: " + adapterId + "/" + connectionId + "/" + externalTaskId, ex);
    }
  }

  public List<ExternalTaskBinding> findByInstance(String localInstanceId) {
    try {
      return (List<ExternalTaskBinding>) searchList(Map.of("WHERE", Map.of(
          "localInstanceId", Map.of("=", localInstanceId))));
    } catch (OuroborosException ex) {
      throw new BusinessException("按实例查询外部任务绑定失败: " + localInstanceId, ex);
    }
  }

  /**
   * 全部活动外部任务绑定（CREATED/PROCESSING），供全量待办聚合使用。
   */
  public List<ExternalTaskBinding> findActive(String scope) {
    try {
      List<ExternalTaskBinding> created = (List<ExternalTaskBinding>) searchList(
          Map.of("WHERE", Map.of(
              "scope", Map.of("=", scope),
              "status", Map.of("=", ExternalTaskBinding.STATUS_CREATED))));
      List<ExternalTaskBinding> processing = (List<ExternalTaskBinding>) searchList(
          Map.of("WHERE", Map.of(
              "scope", Map.of("=", scope),
              "status", Map.of("=", ExternalTaskBinding.STATUS_PROCESSING))));
      List<ExternalTaskBinding> merged = new ArrayList<>(created);
      merged.addAll(processing);
      return merged;
    } catch (OuroborosException ex) {
      throw new BusinessException("查询活动外部任务绑定失败: scope=" + scope, ex);
    }
  }

  @Transactional
  public Try<Boolean> updateStatus(
      String localTaskId, String status, String externalRevision, LocalDateTime externalEventTime,
      String externalEventId) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalTaskBinding> bindingOpt = findByLocalId(localTaskId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("外部任务绑定不存在: " + localTaskId));
      }
      ExternalTaskBinding binding = bindingOpt.get();
      if (ExternalEventOrder.compareFact(
          externalRevision, externalEventTime, externalEventId,
          binding.getExternalRevision(), binding.getExternalEventTime(), binding.getLastExternalEventId()) <= 0
          || isTerminal(binding.getStatus())) {
        return Try.success(false);
      }
      Map<String, Object> where = sourceWhere(
          localTaskId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("status", status);
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
          return Try.failure(new BusinessException("更新外部任务绑定失败: " + localTaskId, result.getCause()));
        }
        Long rows = result.get();
        if (rows != null && rows > 0) {
          return Try.success(true);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("更新外部任务绑定失败: " + localTaskId, ex));
      }
    }
    return Try.failure(new BusinessException("CAS 更新外部任务绑定冲突未收敛: " + localTaskId));
  }

  /**
   * 依据外部权威快照同步处理人；终态绑定不回写，处理人未变化时不写库。
   *
   * <p>待办聚合以外部快照为权威：外部系统改派后本地绑定必须跟上，否则以绑定处理人为权威的
   * 动作授权（未声明 {@code EXTERNAL_TASK_AUTHORIZATION} 的适配器）会把新处理人拒之门外。
   */
  @Transactional
  public Try<Boolean> syncAssignee(String localTaskId, String assignee) {
    if (assignee == null || assignee.isBlank()) {
      return Try.failure(new BusinessException("外部任务处理人为空: " + localTaskId));
    }
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalTaskBinding> bindingOpt = findByLocalId(localTaskId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("外部任务绑定不存在: " + localTaskId));
      }
      ExternalTaskBinding binding = bindingOpt.get();
      if (isTerminal(binding.getStatus()) || assignee.equals(binding.getAssignee())) {
        return Try.success(false);
      }
      Map<String, Object> where = new java.util.LinkedHashMap<>();
      where.put("localTaskId", Map.of("=", localTaskId));
      putNullableMatch(where, "assignee", binding.getAssignee());
      Map<String, Object> data = new java.util.LinkedHashMap<>();
      data.put("assignee", assignee);
      data.put("updatedAt", java.time.LocalDateTime.now());
      try {
        var result = getDataModel().update(where, data);
        if (result.isFailure()) {
          return Try.failure(new BusinessException("同步外部任务处理人失败: " + localTaskId, result.getCause()));
        }
        Long rows = result.get();
        if (rows != null && rows > 0) {
          return Try.success(true);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("同步外部任务处理人失败: " + localTaskId, ex));
      }
    }
    return Try.failure(new BusinessException("CAS 同步外部任务处理人冲突未收敛: " + localTaskId));
  }

  /** 外部动作已接受后的本地状态完成；不改写外部事件顺序游标。 */
  @Transactional
  public Try<ActionStatusUpdate> applyActionStatus(String localTaskId, String targetStatus) {
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ExternalTaskBinding> bindingOpt = findByLocalId(localTaskId);
      if (bindingOpt.isEmpty()) {
        return Try.failure(new BusinessException("外部任务绑定不存在: " + localTaskId));
      }
      ExternalTaskBinding binding = bindingOpt.get();
      if (targetStatus.equals(binding.getStatus())) {
        return Try.success(ActionStatusUpdate.ALREADY_APPLIED);
      }
      if (isTerminal(binding.getStatus())) {
        return Try.success(ActionStatusUpdate.CONFLICT);
      }
      Map<String, Object> where = sourceWhere(
          localTaskId, binding.getStatus(), binding.getExternalRevision(), binding.getExternalEventTime(),
          binding.getLastExternalEventId());
      try {
        var result = getDataModel().update(where, Map.of(
            "status", targetStatus,
            "updatedAt", LocalDateTime.now()));
        if (result.isFailure()) {
          return Try.failure(new BusinessException("完成外部任务动作状态失败: " + localTaskId, result.getCause()));
        }
        if (result.get() != null && result.get() == 1) {
          return Try.success(ActionStatusUpdate.UPDATED);
        }
      } catch (OuroborosException ex) {
        return Try.failure(new BusinessException("完成外部任务动作状态失败: " + localTaskId, ex));
      }
    }
    return Try.success(ActionStatusUpdate.CONFLICT);
  }

  private Map<String, Object> sourceWhere(
      String localTaskId,
      String status,
      String currentRevision,
      LocalDateTime currentEventTime,
      String currentEventId) {
    Map<String, Object> where = new java.util.LinkedHashMap<>();
    where.put("localTaskId", Map.of("=", localTaskId));
    where.put("status", Map.of("=", status));
    putNullableMatch(where, "externalRevision", currentRevision);
    putNullableMatch(where, "externalEventTime", currentEventTime);
    putNullableMatch(where, "lastExternalEventId", currentEventId);
    return where;
  }

  private boolean isTerminal(String status) {
    return ExternalTaskBinding.STATUS_APPROVED.equals(status)
        || ExternalTaskBinding.STATUS_REJECTED.equals(status)
        || ExternalTaskBinding.STATUS_CANCELLED.equals(status);
  }

  private void putNullableMatch(Map<String, Object> where, String field, Object value) {
    if (value == null) {
      where.put(field, null);
      return;
    }
    where.put(field, Map.of("=", value));
  }

  /**
   * 按映射 ID 统计绑定数（映射版本清理的保护检查用，Phase 4 M21-T3）。
   */
  public Long countByMappingId(String mappingId) {
    try {
      return count(Map.of("mappingId", Map.of("=", mappingId)));
    } catch (OuroborosException ex) {
      throw new BusinessException("按映射统计外部任务绑定失败: " + mappingId, ex);
    }
  }
}
