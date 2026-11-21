package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;

import io.vavr.control.Try;

/**
 * 外部流程引擎映射服务实现。持久化遵循既有 SimpleModelServiceImpl + TypedDataModel 模式。
 */
@Service
public class WorkflowEngineMappingServiceImpl
    extends SimpleModelServiceImpl<String, WorkflowEngineMapping>
    implements WorkflowEngineMappingService {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(WorkflowEngineMappingServiceImpl.class);

  @Autowired
  WorkflowEngineMappingValidator validator;

  @Override
  @Transactional
  public Try<WorkflowEngineMapping> create(WorkflowEngineMapping draft) {
    if (draft.getId() == null) {
      draft.setId(UUID.randomUUID().toString());
    }
    draft.setVersion(draft.getVersion() == null ? 1 : draft.getVersion());
    if (draft.getBindingReferenceEpoch() == null) {
      draft.setBindingReferenceEpoch(0L);
    }
    draft.setStatus(WorkflowEngineMapping.STATUS_DRAFT);
    if (draft.getValidationMessage() == null) {
      draft.setValidationMessage("");
    }
    refreshActiveRouteKey(draft);
    LocalDateTime now = databaseNow();
    draft.setCreatedAt(now);
    draft.setUpdatedAt(now);
    try {
      return Try.success(add(draft));
    } catch (OuroborosException ex) {
      return Try.failure(new BusinessException("创建映射失败: " + ex.getMessage(), ex));
    }
  }

  @Override
  public Try<WorkflowEngineMapping> validate(String scope, String mappingId) {
    Optional<WorkflowEngineMapping> mappingOpt = findById(scope, mappingId);
    if (mappingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();
    String sourceStatus = mapping.getStatus();
    if (!WorkflowEngineMapping.STATUS_DRAFT.equals(sourceStatus)
        && !WorkflowEngineMapping.STATUS_INVALID.equals(sourceStatus)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 只有 DRAFT/INVALID 状态可执行正式校验，当前状态: " + sourceStatus));
    }

    // 先以 version + updatedAt CAS 占用 VALIDATING；校验期间禁止原地编辑。
    LocalDateTime validationStartedAt = databaseNow();
    Map<String, Object> beginWhere = new LinkedHashMap<>();
    beginWhere.put("id", Map.of("=", mapping.getId()));
    beginWhere.put("scope", Map.of("=", mapping.getScope()));
    beginWhere.put("status", Map.of("=", sourceStatus));
    beginWhere.put("version", Map.of("=", mapping.getVersion()));
    beginWhere.put("updatedAt", Map.of("=", mapping.getUpdatedAt()));
    Map<String, Object> beginData = new LinkedHashMap<>();
    beginData.put("status", WorkflowEngineMapping.STATUS_VALIDATING);
    beginData.put("validationMessage", "");
    beginData.put("updatedAt", validationStartedAt);
    if (!conditionalUpdate(beginWhere, beginData,
        "开始映射校验时版本已发生变化: " + mapping.getId())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] 映射在开始校验前已发生变更: " + mapping.getId()));
    }
    mapping.setStatus(WorkflowEngineMapping.STATUS_VALIDATING);
    mapping.setValidationMessage("");
    mapping.setUpdatedAt(validationStartedAt);

    // 适配器校验可能发起外部 HTTP 请求，必须发生在数据库事务之外。
    Try<Void> validation = validator.run(mapping);
    String targetStatus = validation.isSuccess()
        ? WorkflowEngineMapping.STATUS_VALIDATED : WorkflowEngineMapping.STATUS_INVALID;
    String validationMessage = validation.isSuccess() ? "" : validation.getCause().getMessage();
    Try<WorkflowEngineMapping> written = writeValidationResult(
        mapping, WorkflowEngineMapping.STATUS_VALIDATING, targetStatus, validationMessage);
    if (written.isFailure()) {
      return written;
    }
    return validation.isSuccess() ? written : Try.failure(validation.getCause());
  }

  @Override
  public Try<WorkflowEngineMapping> dryRunValidate(String scope, String mappingId) {
    Optional<WorkflowEngineMapping> mappingOpt = findById(scope, mappingId);
    if (mappingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();
    return validator.run(mapping).map(ignored -> mapping);
  }

  @Override
  @Transactional
  public synchronized Try<WorkflowEngineMapping> activate(String scope, String mappingId) {
    // 注意：同 scope+flowKey 已有 ACTIVE 映射时，旧映射自动置 RETIRED（不可变版本语义：
    // 新流程使用新版本，运行中的外部实例仍按绑定版本继续，见设计文档"映射保存为不可变版本"）。

    Optional<WorkflowEngineMapping> mappingOpt = findById(scope, mappingId);
    if (mappingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();
    if (!WorkflowEngineMapping.STATUS_VALIDATED.equals(mapping.getStatus())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 只有 VALIDATED 状态可激活，当前状态: " + mapping.getStatus()));
    }
    findActive(mapping.getScope(), mapping.getFlowKey()).ifPresent(existing -> {
      existing.setStatus(WorkflowEngineMapping.STATUS_RETIRED);
      updateBestEffort(existing);
    });
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    updateBestEffort(mapping);
    return Try.success(mapping);
  }

  @Override
  @Transactional
  public Try<WorkflowEngineMapping> pause(String scope, String mappingId) {
    return transition(scope, mappingId, WorkflowEngineMapping.STATUS_ACTIVE, WorkflowEngineMapping.STATUS_PAUSED);
  }

  @Override
  @Transactional
  public Try<WorkflowEngineMapping> retire(String scope, String mappingId) {
    return findById(scope, mappingId)
        .filter(m -> WorkflowEngineMapping.STATUS_ACTIVE.equals(m.getStatus())
            || WorkflowEngineMapping.STATUS_PAUSED.equals(m.getStatus()))
        .map(m -> {
          m.setStatus(WorkflowEngineMapping.STATUS_RETIRED);
          updateBestEffort(m);
          return Try.<WorkflowEngineMapping>success(m);
        })
        .orElseGet(() -> Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 只有 ACTIVE/PAUSED 状态可下线，映射不存在或状态不满足: " + mappingId)));
  }

  @Override
  @Transactional
  public Try<WorkflowEngineMapping> createDraftWithChanges(
      String scope,
      String mappingId,
      WorkflowEngineMapping changes) {
    Optional<WorkflowEngineMapping> existingOpt = findById(scope, mappingId);
    if (existingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping existing = existingOpt.get();
    if (!WorkflowEngineMapping.STATUS_ACTIVE.equals(existing.getStatus())
        && !WorkflowEngineMapping.STATUS_PAUSED.equals(existing.getStatus())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 只有 ACTIVE/PAUSED 映射允许创建新草稿，当前状态: " + existing.getStatus()));
    }
    WorkflowEngineMapping draft = copyWithoutIdentity(existing);
    draft.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
    applyEditableFields(draft, changes);
    return create(draft);
  }

  @Override
  public Try<WorkflowEngineMapping> updateDraft(String scope, String mappingId, WorkflowEngineMapping draft) {
    Optional<WorkflowEngineMapping> existingOpt = findById(scope, mappingId);
    if (existingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping existing = existingOpt.get();
    if (!WorkflowEngineMapping.STATUS_DRAFT.equals(existing.getStatus())
        && !WorkflowEngineMapping.STATUS_INVALID.equals(existing.getStatus())) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 只有 DRAFT/INVALID 映射允许原地编辑，当前状态: " + existing.getStatus()));
    }
    // 编辑必须与 validate 的占用 CAS 竞争；不能用整行无条件 update 覆盖
    // 另一个 worker 已经写入的 VALIDATING/VALIDATED 结果。
    WorkflowEngineMapping candidate = copyWithIdentity(existing);
    applyEditableFields(candidate, draft);
    candidate.setValidationMessage("");
    candidate.setExternalDefinitionValidatedAt(null);
    LocalDateTime updatedAt = databaseNow();
    candidate.setUpdatedAt(updatedAt);
    refreshActiveRouteKey(candidate);

    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", existing.getId()));
    where.put("scope", Map.of("=", existing.getScope()));
    where.put("status", Map.of("=", existing.getStatus()));
    where.put("version", Map.of("=", existing.getVersion()));
    where.put("updatedAt", Map.of("=", existing.getUpdatedAt()));
    Map<String, Object> data = editableData(candidate);
    data.put("validationMessage", "");
    data.put("externalDefinitionValidatedAt", null);
    data.put("activeRouteKey", candidate.getActiveRouteKey());
    data.put("updatedAt", updatedAt);
    if (!conditionalUpdate(where, data, "编辑映射时版本已发生变化: " + mappingId)) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
              + "] 映射在编辑期间已发生变更，拒绝覆盖: " + mappingId));
    }
    return Try.success(candidate);
  }

  @Override
  public java.util.List<WorkflowEngineMapping> search(
      String scope,
      String flowKey,
      String status,
      int offset,
      int limit) {
    java.util.Map<String, Object> statement = new java.util.LinkedHashMap<>();
    statement.put("scope", java.util.Map.of("=", scope));
    if (flowKey != null && !flowKey.isBlank()) {
      statement.put("flowKey", java.util.Map.of("=", flowKey));
    }
    if (status != null && !status.isBlank()) {
      statement.put("status", java.util.Map.of("=", status));
    }
    java.util.List<WorkflowEngineMapping> all;
    try {
      all = (java.util.List<WorkflowEngineMapping>) searchList(Map.of("WHERE", statement));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 映射搜索失败: scope=" + scope + ", flowKey=" + flowKey + ", status=" + status, ex);
    }
    if (all.size() <= offset) {
      return java.util.List.of();
    }
    int toIndex = Math.min(all.size(), offset + limit);
    return new java.util.ArrayList<>(all.subList(offset, toIndex));
  }

  public java.util.List<WorkflowEngineMapping> findActiveAllForSystem() {
    try {
      return (java.util.List<WorkflowEngineMapping>) searchList(Map.of("WHERE", Map.of(
          "status", Map.of("=", WorkflowEngineMapping.STATUS_ACTIVE))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询 ACTIVE 映射列表失败", ex);
    }
  }

  @Override
  public java.util.List<WorkflowEngineMapping> findActiveByScope(String scope) {
    try {
      return (java.util.List<WorkflowEngineMapping>) searchList(Map.of("WHERE", Map.of(
          "scope", Map.of("=", scope),
          "status", Map.of("=", WorkflowEngineMapping.STATUS_ACTIVE))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询 scope ACTIVE 映射列表失败: scope=" + scope, ex);
    }
  }

  @Override
  public java.util.List<WorkflowEngineMapping> findByScopeAndFlowKey(String scope, String flowKey) {
    try {
      return (java.util.List<WorkflowEngineMapping>) searchList(Map.of("WHERE", Map.of(
          "scope", Map.of("=", scope),
          "flowKey", Map.of("=", flowKey))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询 scope + flowKey 映射列表失败: scope=" + scope + ", flowKey=" + flowKey, ex);
    }
  }

  @Override
  public Optional<WorkflowEngineMapping> findActive(String scope, String flowKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "scope", Map.of("=", scope),
          "flowKey", Map.of("=", flowKey),
          "status", Map.of("=", WorkflowEngineMapping.STATUS_ACTIVE)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询 ACTIVE 映射失败: scope=" + scope + ", flowKey=" + flowKey, ex);
    }
  }

  @Override
  public Optional<WorkflowEngineMapping> findById(String scope, String mappingId) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "id", Map.of("=", mappingId),
          "scope", Map.of("=", scope)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询 scope 映射失败: scope=" + scope + ", id=" + mappingId, ex);
    }
  }

  @Override
  public Optional<WorkflowEngineMapping> findByIdForSystem(String mappingId) {
    try {
      return Optional.ofNullable(getByPrimaryKey(mappingId));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 查询映射失败: " + mappingId, ex);
    }
  }

  @Override
  public java.util.List<WorkflowEngineMapping> findByStatusForSystem(String status) {
    try {
      return (java.util.List<WorkflowEngineMapping>) searchList(Map.of("WHERE", Map.of(
          "status", Map.of("=", status))));
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 按状态查询映射失败: " + status, ex);
    }
  }

  @Override
  @Transactional
  public boolean acquireBindingReferenceLock(String mappingId) {
    if (mappingId == null || mappingId.isBlank()) {
      return false;
    }
    for (int attempt = 0; attempt < 3; attempt++) {
      Optional<WorkflowEngineMapping> currentOpt = findByIdForSystem(mappingId);
      if (currentOpt.isEmpty()) {
        return false;
      }
      WorkflowEngineMapping current = currentOpt.get();
      long currentEpoch = current.getBindingReferenceEpoch() == null
          ? 0L : current.getBindingReferenceEpoch();
      final long nextEpoch;
      try {
        nextEpoch = Math.addExact(currentEpoch, 1L);
      } catch (ArithmeticException ex) {
        throw new BusinessException("映射绑定引用 epoch 溢出: " + mappingId, ex);
      }

      Map<String, Object> where = new LinkedHashMap<>();
      where.put("id", Map.of("=", mappingId));
      if (current.getBindingReferenceEpoch() == null) {
        where.put("bindingReferenceEpoch", null);
      } else {
        where.put("bindingReferenceEpoch", Map.of("=", current.getBindingReferenceEpoch()));
      }
      try {
        var result = getDataModel().update(where, Map.of("bindingReferenceEpoch", nextEpoch));
        if (result.isFailure()) {
          throw new BusinessException("取得映射绑定引用锁失败: " + mappingId, result.getCause());
        }
        Long rows = result.get();
        if (rows != null && rows == 1) {
          return true;
        }
      } catch (OuroborosException ex) {
        throw new BusinessException("取得映射绑定引用锁失败: " + mappingId, ex);
      }
    }
    return false;
  }

  @Override
  public boolean deleteMappingIfBindingReferenceEpoch(String mappingId, Long expectedEpoch) {
    if (mappingId == null || mappingId.isBlank() || expectedEpoch == null) {
      return false;
    }
    try {
      var result = getDataModel().delete(Map.of(
          "id", Map.of("=", mappingId),
          "bindingReferenceEpoch", Map.of("=", expectedEpoch)));
      if (result.isFailure()) {
        throw new BusinessException("条件删除映射失败: " + mappingId, result.getCause());
      }
      Long rows = result.get();
      return rows != null && rows == 1;
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 条件删除映射失败: " + mappingId, ex);
    }
  }

  @Override
  public void deleteMappingForSystem(String mappingId) {
    try {
      delete(mappingId);
    } catch (OuroborosException ex) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 删除映射失败: " + mappingId, ex);
    }
  }

  private Try<WorkflowEngineMapping> transition(
      String scope,
      String mappingId,
      String fromStatus,
      String toStatus) {
    Optional<WorkflowEngineMapping> mappingOpt = findById(scope, mappingId);
    if (mappingOpt.isEmpty()) {
      return mappingInvisible(mappingId);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();
    if (!fromStatus.equals(mapping.getStatus())) {
      return Try.failure(new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 状态迁移非法: " + mapping.getStatus() + " -> " + toStatus));
    }
    mapping.setStatus(toStatus);
    updateBestEffort(mapping);
    return Try.success(mapping);
  }

  /**
   * 更新映射并持久化；持久化失败抛出 {@link BusinessException}（由调用方事务回滚）。
   */
  private void updateBestEffort(WorkflowEngineMapping mapping) {
    refreshActiveRouteKey(mapping);
    LocalDateTime updatedAt = databaseNow();
    mapping.setUpdatedAt(updatedAt);
    try {
      var result = getDataModel().update(
          Map.of("id", Map.of("=", mapping.getId())),
          Map.of(
              "status", mapping.getStatus(),
              "activeRouteKey", mapping.getActiveRouteKey(),
              "updatedAt", updatedAt));
      if (result.isFailure()) {
        throw new BusinessException("更新映射失败: " + mapping.getId(), result.getCause());
      }
      Long rows = result.get();
      if (rows == null || rows != 1) {
        throw new BusinessException("更新映射时记录已不存在或发生并发变更: " + mapping.getId());
      }
    } catch (OuroborosException ex) {
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
        throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
            + "] 同 scope + flowKey 已存在 ACTIVE 映射: " + mapping.getScope()
            + "/" + mapping.getFlowKey(), ex);
      }
      throw new BusinessException("更新映射失败: " + mapping.getId(), ex);
    }
  }

  private boolean conditionalUpdate(
      Map<String, Object> where,
      Map<String, Object> data,
      String message) {
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

  private void refreshActiveRouteKey(WorkflowEngineMapping mapping) {
    if (WorkflowEngineMapping.STATUS_ACTIVE.equals(mapping.getStatus())) {
      mapping.setActiveRouteKey(ExternalRouteKeys.of("mapping-active", mapping.getScope(), mapping.getFlowKey()));
      return;
    }
    mapping.setActiveRouteKey(ExternalRouteKeys.of("mapping-inactive", mapping.getId()));
  }

  private Try<WorkflowEngineMapping> writeValidationResult(
      WorkflowEngineMapping mapping,
      String sourceStatus,
      String targetStatus,
      String validationMessage) {
    LocalDateTime updatedAt = databaseNow();
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("id", Map.of("=", mapping.getId()));
    where.put("scope", Map.of("=", mapping.getScope()));
    where.put("status", Map.of("=", sourceStatus));
    where.put("version", Map.of("=", mapping.getVersion()));
    where.put("updatedAt", Map.of("=", mapping.getUpdatedAt()));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status", targetStatus);
    data.put("validationMessage", validationMessage);
    data.put("updatedAt", updatedAt);
    try {
      var result = getDataModel().update(where, data);
      if (result.isFailure()) {
        return Try.failure(new BusinessException("写回映射校验结果失败: " + mapping.getId(), result.getCause()));
      }
      Long rows = result.get();
      if (rows == null || rows != 1) {
        return Try.failure(new BusinessException(
            "[" + EngineIntegrationErrorCodes.EXTERNAL_ACTION_CONFLICT
                + "] 映射在校验期间已发生变更，拒绝覆盖: " + mapping.getId()));
      }
      mapping.setStatus(targetStatus);
      mapping.setValidationMessage(validationMessage);
      mapping.setUpdatedAt(updatedAt);
      return Try.success(mapping);
    } catch (OuroborosException ex) {
      return Try.failure(new BusinessException("写回映射校验结果失败: " + mapping.getId(), ex));
    }
  }

  private WorkflowEngineMapping copyWithoutIdentity(WorkflowEngineMapping source) {
    WorkflowEngineMapping copy = new WorkflowEngineMapping();
    copy.setScope(source.getScope());
    copy.setFlowKey(source.getFlowKey());
    copy.setAdapterId(source.getAdapterId());
    copy.setConnectionId(source.getConnectionId());
    copy.setExternalDefinitionId(source.getExternalDefinitionId());
    copy.setExternalDefinitionNamespace(source.getExternalDefinitionNamespace());
    copy.setExternalDefinitionVersion(source.getExternalDefinitionVersion());
    copy.setExternalDefinitionSchemaHash(source.getExternalDefinitionSchemaHash());
    copy.setAuthority(source.getAuthority());
    copy.setResultBindingJson(source.getResultBindingJson());
    copy.setStartOptionsJson(source.getStartOptionsJson());
    copy.setActionMappingsJson(source.getActionMappingsJson());
    copy.setResultMappingsJson(source.getResultMappingsJson());
    copy.setCallbackJson(source.getCallbackJson());
    copy.setCreatedBy(source.getCreatedBy());
    return copy;
  }

  private WorkflowEngineMapping copyWithIdentity(WorkflowEngineMapping source) {
    WorkflowEngineMapping copy = copyWithoutIdentity(source);
    copy.setId(source.getId());
    copy.setStatus(source.getStatus());
    copy.setActiveRouteKey(source.getActiveRouteKey());
    copy.setVersion(source.getVersion());
    copy.setBindingReferenceEpoch(source.getBindingReferenceEpoch());
    copy.setValidationMessage(source.getValidationMessage());
    copy.setExternalDefinitionValidatedAt(source.getExternalDefinitionValidatedAt());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }

  private Map<String, Object> editableData(WorkflowEngineMapping mapping) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("flowKey", mapping.getFlowKey());
    data.put("adapterId", mapping.getAdapterId());
    data.put("connectionId", mapping.getConnectionId());
    data.put("externalDefinitionNamespace", mapping.getExternalDefinitionNamespace());
    data.put("externalDefinitionId", mapping.getExternalDefinitionId());
    data.put("externalDefinitionVersion", mapping.getExternalDefinitionVersion());
    data.put("externalDefinitionSchemaHash", mapping.getExternalDefinitionSchemaHash());
    data.put("authority", mapping.getAuthority());
    data.put("resultBindingJson", mapping.getResultBindingJson());
    data.put("startOptionsJson", mapping.getStartOptionsJson());
    data.put("actionMappingsJson", mapping.getActionMappingsJson());
    data.put("resultMappingsJson", mapping.getResultMappingsJson());
    data.put("callbackJson", mapping.getCallbackJson());
    return data;
  }

  private void applyEditableFields(WorkflowEngineMapping target, WorkflowEngineMapping changes) {
    target.setFlowKey(changes.getFlowKey());
    target.setAdapterId(changes.getAdapterId());
    target.setConnectionId(changes.getConnectionId());
    target.setExternalDefinitionId(changes.getExternalDefinitionId());
    target.setExternalDefinitionNamespace(changes.getExternalDefinitionNamespace());
    target.setExternalDefinitionVersion(changes.getExternalDefinitionVersion());
    target.setExternalDefinitionSchemaHash(changes.getExternalDefinitionSchemaHash());
    target.setAuthority(changes.getAuthority());
    target.setResultBindingJson(changes.getResultBindingJson());
    target.setStartOptionsJson(changes.getStartOptionsJson());
    target.setActionMappingsJson(changes.getActionMappingsJson());
    target.setResultMappingsJson(changes.getResultMappingsJson());
    target.setCallbackJson(changes.getCallbackJson());
  }

  private Try<WorkflowEngineMapping> mappingInvisible(String mappingId) {
    return Try.failure(new BusinessException(
        "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE + "] 映射不存在或无权访问: " + mappingId));
  }

  /**
   * Mapping timestamps participate in CAS predicates and are persisted in the generated
   * DATETIME columns. Keep the value at the column's second precision so a value written by
   * one operation is exactly comparable when it is read by the next operation.
   */
  private LocalDateTime databaseNow() {
    return LocalDateTime.now().withNano(0);
  }
}
