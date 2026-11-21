package com.ouroboros.workflow.approval.external.runtime.assignment;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ExternalPersistenceFailures;
import com.ouroboros.workflow.approval.external.runtime.ExternalRouteKeys;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;

/**
 * 外部处理人映射缺失队列服务。
 */
@Service
public class ExternalPendingAssignmentService extends SimpleModelServiceImpl<String, ExternalPendingAssignment> {

  @Transactional
  public void recordMissingMapping(
      WorkflowEngineMapping mapping,
      ExternalTaskSnapshot snapshot,
      String reason) {
    if (mapping == null || snapshot == null || isBlank(snapshot.externalTaskId())) {
      throw new BusinessException("外部待处理 assignment 缺少映射或外部任务 ID");
    }
    String routeKey = assignmentRouteKey(mapping, snapshot);
    Optional<ExternalPendingAssignment> existing = findByRouteKey(routeKey);
    if (existing.isPresent()) {
      refresh(existing.get(), reason);
      return;
    }
    ExternalPendingAssignment assignment = new ExternalPendingAssignment();
    LocalDateTime now = LocalDateTime.now();
    assignment.setId(UUID.randomUUID().toString());
    assignment.setScope(mapping.getScope());
    assignment.setFlowKey(mapping.getFlowKey());
    assignment.setMappingId(mapping.getId());
    assignment.setMappingVersion(mapping.getVersion());
    assignment.setAdapterId(mapping.getAdapterId());
    assignment.setConnectionId(mapping.getConnectionId());
    assignment.setExternalInstanceId(rawValue(snapshot, "externalInstanceId", "processInstanceId"));
    assignment.setExternalTaskId(snapshot.externalTaskId());
    assignment.setExternalUserId(snapshot.assignee());
    assignment.setAssignmentRouteKey(routeKey);
    assignment.setReason(normalizeReason(reason));
    assignment.setStatus(ExternalPendingAssignment.STATUS_PENDING);
    assignment.setFirstSeenAt(now);
    assignment.setLastSeenAt(now);
    try {
      add(assignment);
    } catch (OuroborosException ex) {
      if (ExternalPersistenceFailures.isUniqueConstraintViolation(ex)) {
        findByRouteKey(routeKey).ifPresent(hit -> refresh(hit, reason));
        return;
      }
      throw new BusinessException("记录外部待处理 assignment 失败: "
          + mapping.getAdapterId() + "/" + mapping.getConnectionId() + "/" + snapshot.externalTaskId(), ex);
    }
  }

  public Optional<ExternalPendingAssignment> findByRouteKey(String routeKey) {
    try {
      return Optional.ofNullable(searchOne(Map.of("WHERE", Map.of(
          "assignmentRouteKey", Map.of("=", routeKey)))));
    } catch (OuroborosException ex) {
      throw new BusinessException("查询外部待处理 assignment 失败: " + routeKey, ex);
    }
  }

  public String assignmentRouteKey(WorkflowEngineMapping mapping, ExternalTaskSnapshot snapshot) {
    return ExternalRouteKeys.of(
        "pending-assignment",
        mapping.getScope(),
        mapping.getAdapterId(),
        mapping.getConnectionId(),
        snapshot.externalTaskId(),
        snapshot.assignee());
  }

  private void refresh(ExternalPendingAssignment assignment, String reason) {
    assignment.setStatus(ExternalPendingAssignment.STATUS_PENDING);
    assignment.setReason(normalizeReason(reason));
    assignment.setLastSeenAt(LocalDateTime.now());
    try {
      update(assignment.getId(), assignment);
    } catch (OuroborosException ex) {
      throw new BusinessException("刷新外部待处理 assignment 失败: " + assignment.getId(), ex);
    }
  }

  private String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return EngineIntegrationErrorCodes.EXTERNAL_USER_MAPPING_MISSING;
    }
    return reason;
  }

  private String rawValue(ExternalTaskSnapshot snapshot, String... keys) {
    if (snapshot.raw() == null) {
      return null;
    }
    for (String key : keys) {
      Object value = snapshot.raw().get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value);
      }
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
