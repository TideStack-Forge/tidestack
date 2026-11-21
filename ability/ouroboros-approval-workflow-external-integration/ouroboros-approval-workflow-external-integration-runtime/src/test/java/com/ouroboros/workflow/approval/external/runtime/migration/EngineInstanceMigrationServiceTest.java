package com.ouroboros.workflow.approval.external.runtime.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;

import io.vavr.control.Try;

/**
 * M21-T4 运行中实例迁移服务测试：成功迁移时序、外部失败不动内置、已绑定跳过、映射前置校验。
 */
class EngineInstanceMigrationServiceTest {

  private WorkflowEngineMappingService mappingService;
  private ExternalInstanceBindingService instanceBindingService;
  private FlowInstanceService flowInstanceService;
  private RoutedFlowInstanceService routedFlowInstanceService;
  private EngineInstanceMigrationService migrationService;

  @BeforeEach
  void setUp() {
    mappingService = mock(WorkflowEngineMappingService.class);
    instanceBindingService = mock(ExternalInstanceBindingService.class);
    flowInstanceService = mock(FlowInstanceService.class);
    routedFlowInstanceService = mock(RoutedFlowInstanceService.class);
    migrationService = new EngineInstanceMigrationService(
        mappingService, instanceBindingService, flowInstanceService, routedFlowInstanceService);
    when(instanceBindingService.findByLocalId("inst-1")).thenReturn(Optional.empty());
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(activeExternalMapping()));
  }

  @Test
  void successfulMigrationWritesBindingThenCancelsLocalInstance() {
    when(routedFlowInstanceService.startExternalForMigration(any(), eq("inst-1"), eq("test-admin")))
        .thenReturn(Try.success("inst-1"));
    when(instanceBindingService.findByLocalId("inst-1"))
        .thenReturn(Optional.empty(), Optional.of(migrationBinding("ext-9")));
    when(flowInstanceService.cancel("inst-1", "test-admin", "迁移至外部引擎"))
        .thenReturn(Try.success(null));

    EngineInstanceMigrationService.MigrationResult result =
        migrationService.migrate("m-1", "inst-1", "test-admin");

    assertThat(result.status()).isEqualTo("MIGRATED");
    assertThat(result.externalInstanceId()).isEqualTo("ext-9");
    verify(routedFlowInstanceService).startExternalForMigration(any(), eq("inst-1"), eq("test-admin"));
    verify(flowInstanceService).cancel("inst-1", "test-admin", "迁移至外部引擎");
  }

  @Test
  void migrationUsesTheUnifiedExternalStartFactPath() {
    when(routedFlowInstanceService.startExternalForMigration(any(), eq("inst-1"), eq("test-admin")))
        .thenReturn(Try.success("inst-1"));
    when(instanceBindingService.findByLocalId("inst-1"))
        .thenReturn(Optional.empty(), Optional.of(migrationBinding("ext-9")));
    when(flowInstanceService.cancel("inst-1", "test-admin", "迁移至外部引擎"))
        .thenReturn(Try.success(null));

    migrationService.migrate("m-1", "inst-1", "test-admin");

    verify(routedFlowInstanceService).startExternalForMigration(any(), eq("inst-1"), eq("test-admin"));
  }

  @Test
  void externalStartFailureLeavesLocalInstanceUntouched() {
    when(routedFlowInstanceService.startExternalForMigration(any(), eq("inst-1"), eq("test-admin")))
        .thenReturn(Try.failure(new RuntimeException("外部不可达")));

    assertThatThrownBy(() -> migrationService.migrate("m-1", "inst-1", "test-admin"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("迁移外部发起失败");

    verify(instanceBindingService, never()).create(any());
    verify(flowInstanceService, never()).cancel(any(), any(), any());
  }

  @Test
  void alreadyBoundInstanceIsSkippedWithoutStartingExternal() {
    when(instanceBindingService.findByLocalId("inst-1"))
        .thenReturn(Optional.of(new ExternalInstanceBinding()));

    EngineInstanceMigrationService.MigrationResult result =
        migrationService.migrate("m-1", "inst-1", "test-admin");

    assertThat(result.status()).isEqualTo("SKIPPED_ALREADY_BOUND");
    verify(routedFlowInstanceService, never()).startExternalForMigration(any(), any(), any());
    verify(flowInstanceService, never()).cancel(any(), any(), any());
  }

  @Test
  void nonActiveMappingIsRejected() {
    WorkflowEngineMapping paused = activeExternalMapping();
    paused.setStatus(WorkflowEngineMapping.STATUS_PAUSED);
    when(mappingService.findByIdForSystem("m-1")).thenReturn(Optional.of(paused));

    assertThatThrownBy(() -> migrationService.migrate("m-1", "inst-1", "test-admin"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("只有 ACTIVE 且 authority=EXTERNAL_ENGINE");
  }

  @Test
  void localCancelFailureStillReportsMigrated() {
    when(routedFlowInstanceService.startExternalForMigration(any(), eq("inst-1"), eq("test-admin")))
        .thenReturn(Try.success("inst-1"));
    when(instanceBindingService.findByLocalId("inst-1"))
        .thenReturn(Optional.empty(), Optional.of(migrationBinding("ext-9")));
    when(flowInstanceService.cancel("inst-1", "test-admin", "迁移至外部引擎"))
        .thenReturn(Try.failure(new RuntimeException("取消失败")));

    EngineInstanceMigrationService.MigrationResult result =
        migrationService.migrate("m-1", "inst-1", "test-admin");

    assertThat(result.status()).isEqualTo("MIGRATED");
    verify(routedFlowInstanceService).startExternalForMigration(any(), eq("inst-1"), eq("test-admin"));
  }

  private WorkflowEngineMapping activeExternalMapping() {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId("m-1");
    mapping.setFlowKey("flow-1");
    mapping.setAdapterId("adapter-1");
    mapping.setConnectionId("conn-1");
    mapping.setAuthority(WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE);
    mapping.setStatus(WorkflowEngineMapping.STATUS_ACTIVE);
    mapping.setVersion(3);
    mapping.setExternalDefinitionNamespace("ns");
    mapping.setExternalDefinitionId("DEF-1");
    mapping.setExternalDefinitionVersion("1");
    mapping.setExternalDefinitionSchemaHash("hash");
    return mapping;
  }

  private ExternalInstanceBinding migrationBinding(String externalInstanceId) {
    ExternalInstanceBinding binding = new ExternalInstanceBinding();
    binding.setLocalInstanceId("inst-1");
    binding.setExternalInstanceId(externalInstanceId);
    return binding;
  }
}
