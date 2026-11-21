package com.ouroboros.workflow.approval.external.runtime.mapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.start.ExternalStartFactService;

/**
 * 映射版本清理（Phase 4 M21-T3，迁移治理）。
 *
 * <p>清理规则：DRAFT/VALIDATING/INVALID 状态且 {@code updatedAt} 早于草稿保留窗口 →
 * 候选删除；RETIRED 状态且 {@code updatedAt} 早于下线保留窗口 → 候选删除；
 * ACTIVE/PAUSED 永不清理。删除前强制保护检查：任一结果/实例/任务绑定引用该 {@code mappingId}
 * → 跳过（设计文档 L903：下线映射只影响新实例，运行中实例继续使用其绑定的映射版本）。
 */
@Component
public class MappingVersionCleanupService {

  private static final List<String> DRAFT_LIKE_STATUSES = List.of(
      WorkflowEngineMapping.STATUS_DRAFT,
      WorkflowEngineMapping.STATUS_VALIDATING,
      WorkflowEngineMapping.STATUS_INVALID);

  private final WorkflowEngineMappingService mappingService;
  private final ExternalResultBindingService resultBindingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalTaskBindingService taskBindingService;
  private final ExternalStartFactService startFactService;

  @Autowired
  public MappingVersionCleanupService(
      WorkflowEngineMappingService mappingService,
      ExternalResultBindingService resultBindingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalTaskBindingService taskBindingService,
      ExternalStartFactService startFactService) {
    this.mappingService = mappingService;
    this.resultBindingService = resultBindingService;
    this.instanceBindingService = instanceBindingService;
    this.taskBindingService = taskBindingService;
    this.startFactService = startFactService;
  }

  /** 保留给不装配 start fact 仓储的规则测试。 */
  public MappingVersionCleanupService(
      WorkflowEngineMappingService mappingService,
      ExternalResultBindingService resultBindingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalTaskBindingService taskBindingService) {
    this(mappingService, resultBindingService, instanceBindingService, taskBindingService, null);
  }

  /**
   * 执行清理：返回删除与跳过统计。
   *
   * @param draftCutoff   草稿保留窗口下界（早于该时刻的 DRAFT/VALIDATING/INVALID 候选删除）
   * @param retiredCutoff 下线保留窗口下界（早于该时刻的 RETIRED 候选删除）
   */
  /**
   * Candidate enumeration happens before each mapping row is locked. READ_COMMITTED is required
   * so a binding committed after that enumeration is visible to the protected count after the
   * mapping lock is acquired; MySQL's default REPEATABLE_READ would retain the earlier snapshot.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CleanupReport cleanup(LocalDateTime draftCutoff, LocalDateTime retiredCutoff) {
    int deleted = 0;
    int skipped = 0;
    for (String status : DRAFT_LIKE_STATUSES) {
      for (WorkflowEngineMapping mapping : mappingService.findByStatusForSystem(status)) {
        if (isOlderThan(mapping, draftCutoff)) {
          CleanupDecision decision = deleteIfUnreferenced(mapping);
          deleted += decision.deleted();
          skipped += decision.skipped();
        }
      }
    }
    for (WorkflowEngineMapping mapping : mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)) {
      if (isOlderThan(mapping, retiredCutoff)) {
        CleanupDecision decision = deleteIfUnreferenced(mapping);
        deleted += decision.deleted();
        skipped += decision.skipped();
      }
    }
    return new CleanupReport(deleted, skipped);
  }

  private boolean isOlderThan(WorkflowEngineMapping mapping, LocalDateTime cutoff) {
    return mapping.getUpdatedAt() != null && mapping.getUpdatedAt().isBefore(cutoff);
  }

  private CleanupDecision deleteIfUnreferenced(WorkflowEngineMapping candidate) {
    if (!mappingService.acquireBindingReferenceLock(candidate.getId())) {
      return CleanupDecision.SKIPPED;
    }
    WorkflowEngineMapping current = mappingService.findByIdForSystem(candidate.getId()).orElse(null);
    if (!sameCandidate(candidate, current)) {
      return CleanupDecision.SKIPPED;
    }
    if (protectedByBinding(current)) {
      return CleanupDecision.SKIPPED;
    }
    Long expectedEpoch = current.getBindingReferenceEpoch();
    if (!mappingService.deleteMappingIfBindingReferenceEpoch(candidate.getId(), expectedEpoch)) {
      return CleanupDecision.SKIPPED;
    }
    return CleanupDecision.DELETED;
  }

  private boolean sameCandidate(WorkflowEngineMapping candidate, WorkflowEngineMapping current) {
    return current != null
        && Objects.equals(candidate.getId(), current.getId())
        && Objects.equals(candidate.getStatus(), current.getStatus())
        && Objects.equals(candidate.getVersion(), current.getVersion())
        && Objects.equals(candidate.getUpdatedAt(), current.getUpdatedAt());
  }

  private boolean protectedByBinding(WorkflowEngineMapping mapping) {
    return resultBindingService.countByMappingId(mapping.getId()) > 0
        || instanceBindingService.countByMappingId(mapping.getId()) > 0
        || taskBindingService.countByMappingId(mapping.getId()) > 0
        || startFactService != null && startFactService.countNonTerminalByMappingId(mapping.getId()) > 0;
  }

  private enum CleanupDecision {
    DELETED(1, 0),
    SKIPPED(0, 1);

    private final int deleted;
    private final int skipped;

    CleanupDecision(int deleted, int skipped) {
      this.deleted = deleted;
      this.skipped = skipped;
    }

    int deleted() {
      return deleted;
    }

    int skipped() {
      return skipped;
    }
  }

  /** 清理结果统计。 */
  public record CleanupReport(int deleted, int skippedDueToBinding) {
  }
}
