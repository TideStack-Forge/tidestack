package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.migration.EngineInstanceMigrationService;
import com.ouroboros.workflow.approval.external.runtime.web.vo.InstanceMigrationReportVO;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;

/**
 * M21-T4 运行中实例迁移控制器测试：枚举迁移（含分页遍历）、指定实例、部分失败汇总、前置校验。
 */
class EngineInstanceMigrationControllerTest {

  private FlowInstanceService flowInstanceService;
  private EngineInstanceMigrationService migrationService;
  private EngineInstanceMigrationController controller;

  @BeforeEach
  void setUp() {
    flowInstanceService = mock(FlowInstanceService.class);
    migrationService = mock(EngineInstanceMigrationService.class);
    controller = new EngineInstanceMigrationController(
        flowInstanceService, migrationService, mock(EngineMappingPermissionGuard.class));
    when(migrationService.requireMigratableMapping("m-1")).thenReturn(activeExternalMapping());
  }

  @Test
  void migrateRunningEnumeratesLocalRunningInstances() {
    when(flowInstanceService.getInstances("flow-1", "running", 0, 1000))
        .thenReturn(List.of(detail("inst-1"), detail("inst-2")));
    when(migrationService.migrate(anyString(), anyString(), anyString()))
        .thenReturn(new EngineInstanceMigrationService.MigrationResult("inst-1", "MIGRATED", "ext-1"));

    InstanceMigrationReportVO report = controller.migrateRunning("m-1", null);

    assertThat(report.total()).isEqualTo(2);
    assertThat(report.migratedCount()).isEqualTo(2);
    assertThat(report.failedCount()).isZero();
    verify(flowInstanceService).getInstances("flow-1", "running", 0, 1000);
  }

  @Test
  void enumerationPaginatesUntilShortPage() {
    List<FlowInstanceDetail> firstPage = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      firstPage.add(detail("inst-" + i));
    }
    List<FlowInstanceDetail> secondPage =
        List.of(detail("inst-1000"), detail("inst-1001"), detail("inst-1002"));
    when(flowInstanceService.getInstances("flow-1", "running", 0, 1000)).thenReturn(firstPage);
    when(flowInstanceService.getInstances("flow-1", "running", 1000, 1000)).thenReturn(secondPage);
    when(flowInstanceService.getInstances("flow-1", "running", 2000, 1000)).thenReturn(List.of());
    when(migrationService.migrate(anyString(), anyString(), anyString()))
        .thenReturn(new EngineInstanceMigrationService.MigrationResult("x", "MIGRATED", "ext-x"));

    InstanceMigrationReportVO report = controller.migrateRunning("m-1", null);

    assertThat(report.total()).isEqualTo(1003);
    verify(flowInstanceService).getInstances("flow-1", "running", 0, 1000);
    verify(flowInstanceService).getInstances("flow-1", "running", 1000, 1000);
    verifyNoMoreInteractions(flowInstanceService);
  }

  @Test
  void specifiedInstanceIdsAreMigratedWithoutEnumeration() {
    when(migrationService.migrate(anyString(), anyString(), anyString()))
        .thenReturn(new EngineInstanceMigrationService.MigrationResult("inst-9", "MIGRATED", "ext-9"));

    InstanceMigrationReportVO report = controller.migrateRunning("m-1", List.of("inst-9"));

    assertThat(report.total()).isEqualTo(1);
    assertThat(report.migratedCount()).isEqualTo(1);
    verify(flowInstanceService, never()).getInstances(anyString(), anyString(), any(), any());
  }

  @Test
  void partialFailureIsCollectedWithoutAbortingOthers() {
    when(flowInstanceService.getInstances("flow-1", "running", 0, 1000))
        .thenReturn(List.of(detail("inst-1"), detail("inst-2")));
    when(migrationService.migrate("m-1", "inst-1", "test-admin"))
        .thenThrow(new BusinessException("[ENGINE_CONNECTION_UNAVAILABLE] 外部不可达"));
    when(migrationService.migrate("m-1", "inst-2", "test-admin"))
        .thenReturn(new EngineInstanceMigrationService.MigrationResult("inst-2", "MIGRATED", "ext-2"));

    InstanceMigrationReportVO report = controller.migrateRunning("m-1", null);

    assertThat(report.total()).isEqualTo(2);
    assertThat(report.migratedCount()).isEqualTo(1);
    assertThat(report.failedCount()).isEqualTo(1);
    assertThat(report.items()).extracting(InstanceMigrationReportVO.Item::status)
        .containsExactly("FAILED", "MIGRATED");
    assertThat(report.items().get(0).message()).contains("外部不可达");
  }

  @Test
  void nonActiveMappingIsRejectedBeforeEnumeration() {
    when(migrationService.requireMigratableMapping("m-1"))
        .thenThrow(new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
            + "] 只有 ACTIVE 且 authority=EXTERNAL_ENGINE 的映射可迁移运行中实例: m-1"));

    org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
        () -> controller.migrateRunning("m-1", List.of("inst-1")));

    verify(flowInstanceService, never()).getInstances(anyString(), anyString(), any(), any());
    verify(migrationService, never()).migrate(anyString(), anyString(), anyString());
  }

  private FlowInstanceDetail detail(String instanceId) {
    FlowInstanceDetail detail = new FlowInstanceDetail();
    detail.setInstanceId(instanceId);
    return detail;
  }

  private WorkflowEngineMapping activeExternalMapping() {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-1");
    mapping.setFlowKey("flow-1");
    mapping.setAdapterId("adapter-1");
    mapping.setConnectionId("conn-1");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    return mapping;
  }
}
