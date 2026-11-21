package com.ouroboros.workflow.approval.external.runtime.reconcile;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalEventIngestService;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalSnapshotEnvelopeFactory;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator;

import io.vavr.control.Try;

/**
 * 外部权威状态对账作业（Phase 4 M21-T2，迁移治理）。
 *
 * <p>逐条核对本地 RUNNING、{@code authority=EXTERNAL_ENGINE} 的绑定。绑定固定的 mapping
 * version 是补偿路由事实，因此 mapping 被下线后，已有外部实例仍会被继续对账：
 * 外部已终态（FINISHED/CANCELED）而本地绑定仍 RUNNING → 状态漂移，经
 * {@link ExternalEventIngestService#ingest} 补发终态事件进 inbox（复用既有结果归一化流水线，
 * 禁止直接改绑定表绕过事件流水线）；外部查询失败仅记日志，下一轮再查。
 * 无 {@link ExternalInstanceDetailReader} 能力的适配器跳过。
 */
@Component
@ConditionalOnProperty(
    prefix = "ouroboros.workflow.approval.external.scheduling",
    name = "enabled",
    havingValue = "true")
public class ExternalStateReconcileJob {

  private static final Logger logger = LoggerFactory.getLogger(ExternalStateReconcileJob.class);

  /**
   * 外部状态词域（终态）：{@link ExternalInstanceDetailReader} 返回的
   * {@code ExternalInstanceSnapshot.lifecycleStatus()} 字面量（FINISHED/CANCELED），非归一化结果状态。
   *
   * <p>引用关系：仅本作业 {@link #reconcileOne} 用于状态漂移判定（外部已终态而本地绑定仍
   * RUNNING → 补发终态事件）。与 {@code ExternalStatusMapper.TERMINAL_STATUSES}
   * （归一化状态词域）及 {@code ExternalEventProcessor.TERMINAL_EVENT_TYPES}
   * （事件类型词域）分属不同词域，勿混用。
   */
  private static final Set<String> EXTERNAL_TERMINAL_STATUSES = Set.of("FINISHED", "CANCELED");
  private static final int PAGE_SIZE = 200;

  private final WorkflowEngineMappingService mappingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final ExternalEventIngestService ingestService;
  private final ExternalReconcileCheckpointService checkpointService;

  @Autowired
  public ExternalStateReconcileJob(
      WorkflowEngineMappingService mappingService,
      ExternalInstanceBindingService instanceBindingService,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalEventIngestService ingestService,
      ExternalReconcileCheckpointService checkpointService) {
    this.mappingService = mappingService;
    this.instanceBindingService = instanceBindingService;
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.ingestService = ingestService;
    this.checkpointService = checkpointService;
  }

  /** 供不启动 Spring 的规则测试使用；生产必须注入持久化 checkpoint。 */
  public ExternalStateReconcileJob(
      WorkflowEngineMappingService mappingService,
      ExternalInstanceBindingService instanceBindingService,
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      ExternalEventIngestService ingestService) {
    this(mappingService, instanceBindingService, registry, adapterResolver, ingestService, null);
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.state-reconcile-delay-ms:300000}",
      initialDelayString = "${ouroboros.workflow.approval.external.state-reconcile-delay-ms:300000}")
  public void reconcile() {
    if (checkpointService == null) {
      reconcileInMemory();
      return;
    }
    ExternalReconcileCheckpointService.Claim claim = checkpointService
        .claim("external-state-reconcile", ExternalReconcileCheckpointService.DEFAULT_LEASE_SECONDS)
        .orElse(null);
    if (claim == null) {
      return;
    }
    int compensated = 0;
    try {
      String afterLocalInstanceId = claim.checkpoint().getAfterLocalInstanceId();
      while (true) {
        java.util.List<ExternalInstanceBinding> bindings =
            instanceBindingService.searchRunningPage(afterLocalInstanceId, PAGE_SIZE);
        if (bindings.isEmpty()) {
          if (!checkpointService.complete(claim.claimToken())) {
            throw new IllegalStateException("外部状态对账检查点 claim 已失效，无法完成本轮扫描");
          }
          break;
        }
        for (ExternalInstanceBinding binding : bindings) {
          try {
            compensated += reconcileBinding(binding);
          } catch (RuntimeException ex) {
            // A single broken route or transient provider failure must not hold the
            // keyset checkpoint at this row and starve every later RUNNING binding.
            // The next full scan retries this binding from the beginning.
            logger.warn("状态对账单条绑定失败，保留到下一轮重试: localInstanceId={}, cause={}",
                binding.getLocalInstanceId(), ex.getMessage());
          }
        }
        afterLocalInstanceId = bindings.get(bindings.size() - 1).getLocalInstanceId();
        if (!checkpointService.advance(claim.claimToken(), afterLocalInstanceId)) {
          throw new IllegalStateException("外部状态对账检查点 claim 已失效，无法推进: " + afterLocalInstanceId);
        }
      }
      if (compensated > 0) {
        logger.info("状态对账完成: 补发 {} 条外部终态事件", compensated);
      }
    } catch (RuntimeException ex) {
      checkpointService.release(claim.claimToken());
      throw ex;
    }
  }

  private void reconcileInMemory() {
    int compensated = 0;
    String afterLocalInstanceId = null;
    while (true) {
      java.util.List<ExternalInstanceBinding> bindings =
          instanceBindingService.searchRunningPage(afterLocalInstanceId, PAGE_SIZE);
      if (bindings.isEmpty()) {
        break;
      }
      for (ExternalInstanceBinding binding : bindings) {
        try {
          compensated += reconcileBinding(binding);
        } catch (RuntimeException ex) {
          logger.debug("测试模式状态对账查询失败: localInstanceId={}", binding.getLocalInstanceId(), ex);
        }
      }
      afterLocalInstanceId = bindings.get(bindings.size() - 1).getLocalInstanceId();
    }
    if (compensated > 0) {
      logger.info("状态对账完成: 补发 {} 条外部终态事件", compensated);
    }
  }

  private int reconcileBinding(ExternalInstanceBinding binding) {
    WorkflowEngineMapping mapping = mappingService.findByIdForSystem(binding.getMappingId()).orElse(null);
    if (mapping == null || !java.util.Objects.equals(mapping.getVersion(), binding.getMappingVersion())) {
      logger.warn("状态对账无法恢复绑定映射版本: localInstanceId={}, mappingId={}, mappingVersion={}",
          binding.getLocalInstanceId(), binding.getMappingId(), binding.getMappingVersion());
      throw new IllegalStateException("状态对账无法恢复绑定映射版本: localInstanceId="
          + binding.getLocalInstanceId());
    }
    if (!WorkflowEngineMappingValidator.AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      return 0;
    }
    RegisteredAdapter registered = registry.getAdapter(mapping.getAdapterId()).orElse(null);
    if (registered == null) {
      logger.warn("状态对账无法解析适配器: localInstanceId={}, adapterId={}",
          binding.getLocalInstanceId(), mapping.getAdapterId());
      throw new IllegalStateException("状态对账无法解析适配器: " + mapping.getAdapterId());
    }
    ExternalInstanceDetailReader reader;
    try {
      reader = adapterResolver.resolvePort(mapping, ExternalInstanceDetailReader.class);
    } catch (RuntimeException ex) {
      logger.warn("状态对账无法解析连接级实例读取端口: localInstanceId={}, adapterId={}, connectionId={}",
          binding.getLocalInstanceId(), mapping.getAdapterId(), mapping.getConnectionId(), ex);
      throw new IllegalStateException("状态对账无法解析连接级实例读取端口: localInstanceId="
          + binding.getLocalInstanceId(), ex);
    }
    if (binding.getExternalInstanceId() == null) {
      return 0;
    }
    return reconcileOne(mapping, reader, binding);
  }

  private int reconcileOne(
      WorkflowEngineMapping mapping, ExternalInstanceDetailReader reader,
      ExternalInstanceBinding binding) {
    Try<ExternalInstanceSnapshot> fetched = reader.getInstance(
        new ExternalInstanceRef(binding.getExternalInstanceId(), binding.getConnectionId()));
    if (fetched.isFailure()) {
      logger.debug("状态对账查询失败（下一轮再查）: externalInstanceId={}, cause={}",
          binding.getExternalInstanceId(), fetched.getCause().getMessage());
      throw new IllegalStateException("状态对账查询失败: externalInstanceId="
          + binding.getExternalInstanceId(), fetched.getCause());
    }
    ExternalInstanceSnapshot snapshot = fetched.get();
    if (snapshot.lifecycleStatus() == null
        || !EXTERNAL_TERMINAL_STATUSES.contains(snapshot.lifecycleStatus())) {
      return 0;
    }
    var envelope = ExternalSnapshotEnvelopeFactory.create(
        mapping.getAdapterId(), mapping.getConnectionId(), snapshot);
    Try<Void> ingested = ingestService.ingest(envelope);
    if (ingested.isFailure()) {
      logger.warn("状态漂移补偿入箱失败: eventId={}, cause={}",
          envelope.eventId(), ingested.getCause().getMessage());
      throw new IllegalStateException("状态漂移补偿入箱失败: eventId=" + envelope.eventId(),
          ingested.getCause());
    }
    logger.info("状态漂移补偿: 外部终态 {} 补发事件, adapterId={}, connectionId={}, flowKey={}, "
            + "externalInstanceId={}, mappingVersion={}",
        snapshot.lifecycleStatus(), mapping.getAdapterId(), mapping.getConnectionId(),
        mapping.getFlowKey(), binding.getExternalInstanceId(), mapping.getVersion());
    return 1;
  }
}
