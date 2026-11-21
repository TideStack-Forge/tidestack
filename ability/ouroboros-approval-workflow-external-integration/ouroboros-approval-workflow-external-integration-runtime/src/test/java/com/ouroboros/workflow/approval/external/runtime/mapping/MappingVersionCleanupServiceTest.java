package com.ouroboros.workflow.approval.external.runtime.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;

/**
 * M21-T3 映射版本清理服务测试：保留窗口、绑定引用保护、ACTIVE 永不清理。
 */
class MappingVersionCleanupServiceTest {

  private WorkflowEngineMappingService mappingService;
  private ExternalResultBindingService resultBindingService;
  private ExternalInstanceBindingService instanceBindingService;
  private ExternalTaskBindingService taskBindingService;
  private MappingVersionCleanupService cleanupService;

  private final LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);

  @BeforeEach
  void setUp() {
    mappingService = mock(WorkflowEngineMappingService.class);
    resultBindingService = mock(ExternalResultBindingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    taskBindingService = mock(ExternalTaskBindingService.class);
    cleanupService = new MappingVersionCleanupService(
        mappingService, resultBindingService, instanceBindingService, taskBindingService);
    when(mappingService.acquireBindingReferenceLock(anyString())).thenReturn(true);
    when(mappingService.deleteMappingIfBindingReferenceEpoch(anyString(), any())).thenReturn(true);
    when(resultBindingService.countByMappingId("m-1")).thenReturn(0L);
    when(instanceBindingService.countByMappingId("m-1")).thenReturn(0L);
    when(taskBindingService.countByMappingId("m-1")).thenReturn(0L);
  }

  @Test
  void expiredDraftWithoutBindingsIsDeleted() {
    WorkflowEngineMapping draft = mapping("m-1", WorkflowEngineMapping.STATUS_DRAFT, now.minusDays(40));
    stubCurrent(draft);
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of(draft));
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of());

    MappingVersionCleanupService.CleanupReport report =
        cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    assertThat(report.deleted()).isEqualTo(1);
    assertThat(report.skippedDueToBinding()).isZero();
    verify(mappingService).deleteMappingIfBindingReferenceEpoch("m-1", 1L);
  }

  @Test
  void expiredRetiredWithInstanceBindingIsSkipped() {
    WorkflowEngineMapping retired = mapping("m-1", WorkflowEngineMapping.STATUS_RETIRED, now.minusDays(100));
    stubCurrent(retired);
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of(retired));
    when(instanceBindingService.countByMappingId("m-1")).thenReturn(2L);

    MappingVersionCleanupService.CleanupReport report =
        cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    assertThat(report.deleted()).isZero();
    assertThat(report.skippedDueToBinding()).isEqualTo(1);
    verify(mappingService, never()).deleteMappingIfBindingReferenceEpoch(anyString(), any());
  }

  @Test
  void expiredRetiredWithResultBindingIsSkipped() {
    WorkflowEngineMapping retired = mapping("m-1", WorkflowEngineMapping.STATUS_RETIRED, now.minusDays(100));
    stubCurrent(retired);
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of(retired));
    when(resultBindingService.countByMappingId("m-1")).thenReturn(1L);

    MappingVersionCleanupService.CleanupReport report =
        cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    assertThat(report.skippedDueToBinding()).isEqualTo(1);
    verify(mappingService, never()).deleteMappingIfBindingReferenceEpoch(anyString(), any());
  }

  @Test
  void resultBindingProtectionShortCircuitsOtherBindingCounts() {
    WorkflowEngineMapping retired = mapping("m-1", WorkflowEngineMapping.STATUS_RETIRED, now.minusDays(100));
    stubCurrent(retired);
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of(retired));
    when(resultBindingService.countByMappingId("m-1")).thenReturn(1L);

    cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    verify(instanceBindingService, never()).countByMappingId("m-1");
    verify(taskBindingService, never()).countByMappingId("m-1");
  }

  @Test
  void expiredRetiredWithTaskBindingIsSkipped() {
    WorkflowEngineMapping retired = mapping("m-1", WorkflowEngineMapping.STATUS_RETIRED, now.minusDays(100));
    stubCurrent(retired);
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of(retired));
    when(taskBindingService.countByMappingId("m-1")).thenReturn(1L);

    MappingVersionCleanupService.CleanupReport report =
        cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    assertThat(report.skippedDueToBinding()).isEqualTo(1);
    verify(mappingService, never()).deleteMappingIfBindingReferenceEpoch(anyString(), any());
  }

  @Test
  void recentMappingIsKept() {
    WorkflowEngineMapping draft = mapping("m-1", WorkflowEngineMapping.STATUS_DRAFT, now.minusDays(5));
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of(draft));
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of());

    MappingVersionCleanupService.CleanupReport report =
        cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    assertThat(report.deleted()).isZero();
    verify(mappingService, never()).deleteMappingIfBindingReferenceEpoch(anyString(), any());
  }

  @Test
  void activeMappingsAreNeverQueriedForCleanup() {
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_DRAFT)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_VALIDATING)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_INVALID)).thenReturn(List.of());
    when(mappingService.findByStatusForSystem(WorkflowEngineMapping.STATUS_RETIRED)).thenReturn(List.of());

    cleanupService.cleanup(now.minusDays(30), now.minusDays(90));

    verify(mappingService, never()).findByStatusForSystem(WorkflowEngineMapping.STATUS_ACTIVE);
    verify(mappingService, never()).findByStatusForSystem(WorkflowEngineMapping.STATUS_PAUSED);
  }

  private WorkflowEngineMapping mapping(String id, String status, LocalDateTime updatedAt) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setStatus(status);
    mapping.setBindingReferenceEpoch(1L);
    mapping.setUpdatedAt(updatedAt);
    return mapping;
  }

  private void stubCurrent(WorkflowEngineMapping mapping) {
    when(mappingService.findByIdForSystem(mapping.getId())).thenReturn(java.util.Optional.of(mapping));
  }
}
