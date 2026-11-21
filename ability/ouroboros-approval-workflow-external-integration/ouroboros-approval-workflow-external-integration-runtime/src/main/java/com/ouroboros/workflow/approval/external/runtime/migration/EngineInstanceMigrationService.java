package com.ouroboros.workflow.approval.external.runtime.migration;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;
import com.ouroboros.workflow.approval.external.runtime.routing.RoutedFlowInstanceService;

import io.vavr.control.Try;

/**
 * 运行中实例迁移（Phase 4 M21-T4，迁移治理）。
 *
 * <p>对本地 RUNNING 实例执行"外部重发起 + 绑定记录 + 本地取消"：外部发起成功并写入
 * {@link ExternalInstanceBinding}（localInstanceId=原内置实例 ID）后才取消内置实例；
 * 外部发起失败不动内置实例（设计 L961"外部失败不回退内置"的原子性语义）。
 *
 * <p>限制（写入 M21-T5 切换手册）：平台 FlowInstanceService 无按实例读取变量的 API，
 * 迁移发起的外部实例不携带原实例的业务变量/表单数据——本服务适用于无业务记录关联
 * 或允许外部重新发起的流程；有业务记录关联的流程迁移由运维在外部引擎重新发起，
 * 不走本服务。迁移是显式运维操作，默认生产节奏不迁移运行中实例（设计 L901）。
 */
@Component
public class EngineInstanceMigrationService {

  private static final Logger logger = LoggerFactory.getLogger(EngineInstanceMigrationService.class);

  private final WorkflowEngineMappingService mappingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final FlowInstanceService flowInstanceService;
  private final RoutedFlowInstanceService routedFlowInstanceService;

  @Autowired
  public EngineInstanceMigrationService(
      WorkflowEngineMappingService mappingService,
      ExternalInstanceBindingService instanceBindingService,
      FlowInstanceService flowInstanceService,
      RoutedFlowInstanceService routedFlowInstanceService) {
    this.mappingService = mappingService;
    this.instanceBindingService = instanceBindingService;
    this.flowInstanceService = flowInstanceService;
    this.routedFlowInstanceService = routedFlowInstanceService;
  }

  /**
   * 迁移单个本地运行中实例到外部引擎。
   *
   * @throws BusinessException 映射校验失败、适配器/启动端口缺失、外部发起失败、绑定写入失败
   */
  public MigrationResult migrate(String mappingId, String localInstanceId, String userId) {
    WorkflowEngineMapping mapping = requireMigratableMapping(mappingId);
    if (instanceBindingService.findByLocalId(localInstanceId).isPresent()) {
      return MigrationResult.skipped(localInstanceId);
    }
    Try<String> started = routedFlowInstanceService.startExternalForMigration(mapping, localInstanceId, userId);
    if (started.isFailure()) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.ENGINE_CONNECTION_UNAVAILABLE
          + "] 迁移外部发起失败: " + started.getCause().getMessage(), started.getCause());
    }
    ExternalInstanceBinding binding = instanceBindingService.findByLocalId(localInstanceId).orElse(null);
    if (binding == null) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_PERSISTENCE_FAILED
          + "] 外部启动成功但迁移绑定不存在: " + localInstanceId);
    }
    Try<Void> cancelled = flowInstanceService.cancel(localInstanceId, userId, "迁移至外部引擎");
    if (cancelled.isFailure()) {
      // 外部绑定已成立，本地取消失败可重试（迁移结果仍为成功，取消由运维跟进）
      logger.warn("迁移后本地实例取消失败（外部绑定已成立，可重试取消）: localInstanceId={}, cause={}",
          localInstanceId, cancelled.getCause().getMessage());
    }
    return MigrationResult.migrated(localInstanceId, binding.getExternalInstanceId());
  }

  /**
   * 迁移前置校验：映射存在且 ACTIVE、authority=EXTERNAL_ENGINE（控制器与迁移服务共用）。
   *
   * @param mappingId 映射 ID
   * @return 通过校验的映射（调用方用其 flowKey 枚举目标实例）
   * @throws BusinessException 映射不存在或不可迁移
   */
  public WorkflowEngineMapping requireMigratableMapping(String mappingId) {
    Optional<WorkflowEngineMapping> mappingOpt = mappingService.findByIdForSystem(mappingId);
    if (mappingOpt.isEmpty()) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
          + "] 映射不存在: " + mappingId);
    }
    WorkflowEngineMapping mapping = mappingOpt.get();
    if (!WorkflowEngineMapping.STATUS_ACTIVE.equals(mapping.getStatus())
        || !WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      throw new BusinessException("[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
          + "] 只有 ACTIVE 且 authority=EXTERNAL_ENGINE 的映射可迁移运行中实例: " + mappingId);
    }
    return mapping;
  }

  /** 单实例迁移结果。 */
  public record MigrationResult(String localInstanceId, String status, String externalInstanceId) {

    public static MigrationResult migrated(String localInstanceId, String externalInstanceId) {
      return new MigrationResult(localInstanceId, "MIGRATED", externalInstanceId);
    }

    public static MigrationResult skipped(String localInstanceId) {
      return new MigrationResult(localInstanceId, "SKIPPED_ALREADY_BOUND", null);
    }
  }
}
