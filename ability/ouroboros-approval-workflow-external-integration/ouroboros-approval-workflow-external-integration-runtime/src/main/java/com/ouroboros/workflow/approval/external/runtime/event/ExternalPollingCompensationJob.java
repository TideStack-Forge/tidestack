package com.ouroboros.workflow.approval.external.runtime.event;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.RegisteredAdapter;
import com.ouroboros.workflow.approval.external.runtime.WorkflowEngineAdapterRegistry;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;

import io.vavr.control.Try;

/**
 * 轮询补偿与对账：对每个声明 POLLING 能力的 active route，以及仍被 RUNNING binding 引用的
 * 历史 route，按同步状态增量拉取外部实例变化，合成事件信封进入同一 inbox 管道。轮询是补偿机制，
 * 不是第二套状态机。
 *
 * <p>调度选型（M9 决策记录）：仓库调度模块（ouroboros-scheduler）面向 Cron/LogicFlow 任务，
 * 对纯固定延迟补偿任务过重；采用 Spring {@link Scheduled} + 可配置延迟，后续如需纳入
 * 平台调度体系可平滑迁移。
 */
@Component
@ConditionalOnProperty(
    prefix = "ouroboros.workflow.approval.external.scheduling",
    name = "enabled",
    havingValue = "true")
public class ExternalPollingCompensationJob {

  private static final Logger logger = LoggerFactory.getLogger(ExternalPollingCompensationJob.class);

  private static final DateTimeFormatter SYNC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final String WORKER_ID = "external-poll-" + UUID.randomUUID();

  private final WorkflowEngineAdapterRegistry registry;
  private final ConnectionAwareAdapterResolver adapterResolver;
  private final WorkflowEngineMappingService mappingService;
  private final ExternalInstanceBindingService instanceBindingService;
  private final ExternalSyncStateService syncStateService;
  private final ExternalEventIngestService ingestService;

  @Autowired
  public ExternalPollingCompensationJob(
      WorkflowEngineAdapterRegistry registry,
      ConnectionAwareAdapterResolver adapterResolver,
      WorkflowEngineMappingService mappingService,
      ExternalInstanceBindingService instanceBindingService,
      ExternalSyncStateService syncStateService,
      ExternalEventIngestService ingestService) {
    this.registry = registry;
    this.adapterResolver = adapterResolver;
    this.mappingService = mappingService;
    this.instanceBindingService = instanceBindingService;
    this.syncStateService = syncStateService;
    this.ingestService = ingestService;
  }

  @Scheduled(
      fixedDelayString = "${ouroboros.workflow.approval.external.poll-delay-ms:60000}",
      initialDelayString = "${ouroboros.workflow.approval.external.poll-delay-ms:60000}")
  public void run() {
    Map<String, WorkflowEngineMapping> routes = collectPollingRoutes();
    for (WorkflowEngineMapping mapping : routes.values()) {
      RegisteredAdapter registered = registry.getAdapter(mapping.getAdapterId()).orElse(null);
      if (registered == null || !registered.capabilities().contains(Capability.POLLING)) {
        continue;
      }
      try {
        ExternalInstanceReader reader = adapterResolver.resolvePort(mapping, ExternalInstanceReader.class);
        pollOnce(reader, mapping);
      } catch (RuntimeException ex) {
        logger.warn("轮询补偿失败: adapterId={}, mappingId={}, cause={}",
            mapping.getAdapterId(), mapping.getId(), ex.getMessage());
      }
    }
  }

  private Map<String, WorkflowEngineMapping> collectPollingRoutes() {
    Map<String, WorkflowEngineMapping> routes = new LinkedHashMap<>();
    for (WorkflowEngineMapping mapping : mappingService.findActiveAllForSystem()) {
      addPollingRoute(routes, mapping);
    }

    String afterLocalInstanceId = null;
    while (true) {
      List<ExternalInstanceBinding> bindings = instanceBindingService.searchRunningPage(afterLocalInstanceId, 200);
      if (bindings.isEmpty()) {
        return routes;
      }
      for (ExternalInstanceBinding binding : bindings) {
        afterLocalInstanceId = binding.getLocalInstanceId();
        WorkflowEngineMapping mapping = mappingService.findByIdForSystem(binding.getMappingId()).orElse(null);
        if (mapping == null || !java.util.Objects.equals(mapping.getVersion(), binding.getMappingVersion())) {
          logger.warn("轮询补偿无法恢复 RUNNING binding 的映射版本: localInstanceId={}, mappingId={}, mappingVersion={}",
              binding.getLocalInstanceId(), binding.getMappingId(), binding.getMappingVersion());
          continue;
        }
        addPollingRoute(routes, mapping);
      }
    }
  }

  private void addPollingRoute(Map<String, WorkflowEngineMapping> routes, WorkflowEngineMapping mapping) {
    if (!com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingValidator
        .AUTHORITY_EXTERNAL_ENGINE.equals(mapping.getAuthority())) {
      return;
    }
    String routeId = ExternalSyncState.buildId(mapping.getAdapterId(), mapping.getConnectionId());
    routes.putIfAbsent(routeId, mapping);
  }

  private void pollOnce(ExternalInstanceReader reader, WorkflowEngineMapping mapping) {
    var claim = syncStateService.claim(
        mapping.getAdapterId(), mapping.getConnectionId(), WORKER_ID,
        ExternalSyncStateService.DEFAULT_LEASE_SECONDS).orElse(null);
    if (claim == null) {
      return;
    }
    var state = claim.state();
    try {
      pollClaimed(reader, mapping, claim);
    } catch (RuntimeException ex) {
      syncStateService.release(state.getId(), claim.claimToken());
      throw ex;
    }
  }

  private void pollClaimed(
      ExternalInstanceReader reader,
      WorkflowEngineMapping mapping,
      ExternalSyncStateService.Claim claim) {
    ExternalSyncState state = claim.state();
    if (state.getRunUntil() == null) {
      LocalDateTime runFrom = state.getLastSyncAt();
      // The provider contract and persistence schema use second precision. Keeping
      // nanoseconds here would make the committed watermark differ from the query window.
      LocalDateTime runUntil = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
      if (!syncStateService.beginRun(state.getId(), claim.claimToken(), runFrom, runUntil)) {
        throw new IllegalStateException("轮询同步租约已失效，无法固定查询窗口: " + state.getId());
      }
      state.setRunFrom(runFrom);
      state.setRunUntil(runUntil);
    }
    if (state.getRunFrom() == null || state.getRunUntil() == null) {
      throw new IllegalStateException("轮询同步状态缺少固定查询窗口: " + state.getId());
    }
    String cursor = state.getCursor();
    ExternalInstancePage page = reader.queryChangedInstances(new ExternalInstanceQuery(
            mapping.getConnectionId(),
            state.getRunFrom().format(SYNC_TIME_FORMAT),
            state.getRunUntil().format(SYNC_TIME_FORMAT),
            null,
            cursor,
            100))
        .getOrElseThrow(ex -> new IllegalStateException("增量查询失败: " + ex.getMessage(), ex));

    if (page == null || page.instances() == null) {
      throw new IllegalStateException("轮询 provider 返回非法页: instances 为空");
    }
    for (ExternalInstanceSnapshot snapshot : page.instances()) {
      if (snapshot == null || snapshot.externalInstanceId() == null || snapshot.lifecycleStatus() == null) {
        throw new IllegalStateException("轮询 provider 返回非法实例快照，保留当前 cursor");
      }
      var envelope = ExternalSnapshotEnvelopeFactory.create(
          mapping.getAdapterId(), mapping.getConnectionId(), snapshot);
      Try<Void> ingested = ingestService.ingest(envelope);
      if (ingested.isFailure()) {
        throw new IllegalStateException("轮询事件入箱失败: eventId=" + envelope.eventId()
            + ", cause=" + ingested.getCause().getMessage(), ingested.getCause());
      }
    }
    boolean complete = page.cursor() == null || page.cursor().isBlank();
    boolean recorded = complete
        ? syncStateService.completeRun(state.getId(), claim.claimToken(), state.getRunUntil())
        : syncStateService.recordPage(state.getId(), claim.claimToken(), page.cursor());
    if (!recorded) {
      throw new IllegalStateException("轮询同步租约已失效，拒绝推进 cursor: " + state.getId());
    }
  }
}
