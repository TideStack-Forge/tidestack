package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.service.impl.DevHelperRefreshSessions.RefreshRequest;
import com.ouroboros.dev.app.service.impl.DevHelperRefreshSessions.RefreshFailureDecision;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.event.DevHelperStatusChangedEvent;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.app.service.AppDevHelperLogService;
import com.ouroboros.dev.app.service.runtime.DevHelperRuntimeSpecFactory;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;

@Component
public class AppDevHelperContainerServiceImpl implements AppDevHelperContainerService {
  private static final String HELPER_PROFILE = "dev-helper";

  private final DevHelperWorkspace workspace;
  private final DevHelperRefreshSessions refreshSessions = new DevHelperRefreshSessions();
  private final DevRuntimeService devRuntimeService;
  private final RuntimeStateStore runtimeStateStore;
  private final DevHelperRuntimeSpecFactory runtimeSpecFactory;
  private final DevHelperCommandService devHelperCommandService;
  private final DevContainerConfig devContainerConfig;
  private final RuntimeImageViewService runtimeImageViewService;
  private final AppDevHelperLogService appDevHelperLogService;
  private final ApplicationEventPublisher eventPublisher;
  private final ConcurrentMap<String, Object> helperOperationMonitors = new ConcurrentHashMap<String, Object>();

  public AppDevHelperContainerServiceImpl(DevelopWorkspaceContext developWorkspaceContext,
                                          DevRuntimeService devRuntimeService,
                                          RuntimeStateStore runtimeStateStore,
                                          DevHelperRuntimeSpecFactory runtimeSpecFactory,
                                          DevHelperCommandService devHelperCommandService,
                                          DevContainerConfig devContainerConfig,
                                          RuntimeImageViewService runtimeImageViewService,
                                          AppDevHelperLogService appDevHelperLogService,
                                          ApplicationEventPublisher eventPublisher) {
    this.workspace = new DevHelperWorkspace(developWorkspaceContext);
    this.devRuntimeService = devRuntimeService;
    this.runtimeStateStore = runtimeStateStore;
    this.runtimeSpecFactory = runtimeSpecFactory;
    this.devHelperCommandService = devHelperCommandService;
    this.devContainerConfig = devContainerConfig;
    this.runtimeImageViewService = runtimeImageViewService;
    this.appDevHelperLogService = appDevHelperLogService;
    this.eventPublisher = eventPublisher;
    this.devHelperCommandService.registerCommandResultListener(
        Commands.KNOCK_KNOCK.getCommand(),
        this::handleHelperOnline
    );
    this.devHelperCommandService.registerCommandResultListener(
        Commands.REFRESH_METADATA.getCommand(),
        this::handleRefreshResult
    );
  }

  @Override
  public Try<String> create(AppDevHelperRequest request) {
    if (request == null) {
      return Try.failure(new BusinessException("开发助手运行时请求不能为空"));
    }
    return withHelperOperation(request.getAppName(), () -> createRuntime(request, false)
        .map(created -> created.instance.getRuntimeId()));
  }

  @Override
  public Try<String> createAndStart(AppDevHelperRequest request) {
    if (request == null) {
      return Try.failure(new BusinessException("开发助手运行时请求不能为空"));
    }
    return withHelperOperation(request.getAppName(), () -> createRuntime(request, true)
        .flatMap(created -> {
          boolean lifecycleAdvanced = created.lifecyclePrepared
              || created.instance == null
              || created.instance.getStatus() != RuntimeStatus.RUNNING
                  && created.instance.getStatus() != RuntimeStatus.STARTING;
          if (lifecycleAdvanced && !created.lifecyclePrepared) {
            advanceHelperLifecycle(request.getAppName(), true);
          }
          return devRuntimeService.start(RuntimeIdentityKey.from(created.spec))
              .map(instance -> created.instance.getRuntimeId())
              .onFailure(ignored -> {
                if (lifecycleAdvanced) {
                  advanceHelperLifecycle(request.getAppName(), false);
                }
              });
        }));
  }

  private Try<CreatedHelperRuntime> createRuntime(
      AppDevHelperRequest request,
      boolean acceptingActivationsOnCreate
  ) {
    appDevHelperLogService.clear(request.getAppName());
    appDevHelperLogService.appendSystemLog(request.getAppName(), "开始创建开发助手运行时");
    return Try.of(() -> runtimeSpecFactory.create(request))
        .map(this::preserveStoredAppIncarnation)
        .flatMap(spec -> {
          RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
          boolean existingRuntime = runtimeStateStore.findSpec(key).isPresent()
              && runtimeStateStore.findInstance(key).isPresent();
          if (!existingRuntime) {
            advanceHelperLifecycle(request.getAppName(), acceptingActivationsOnCreate);
          }
          Try<CreatedHelperRuntime> creation = workspace.prepare(spec.getAppName())
              .flatMap(ignored -> devRuntimeService.create(spec))
              .map(instance -> new CreatedHelperRuntime(spec, instance, !existingRuntime));
          if (!existingRuntime && acceptingActivationsOnCreate) {
            creation.onFailure(ignored -> advanceHelperLifecycle(request.getAppName(), false));
          }
          return creation;
        })
        .map(instance -> {
          appDevHelperLogService.appendSystemLog(request.getAppName(), "开发助手运行时已创建: " + instance.instance.getRuntimeId());
          return instance;
        });
  }

  private RuntimeSpec preserveStoredAppIncarnation(RuntimeSpec candidate) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(candidate);
    String currentAppIncarnationId = runtimeStateStore.findSpec(key)
        .map(this::appIncarnationId)
        .orElse(null);
    if (StringUtils.isBlank(currentAppIncarnationId)) {
      return candidate;
    }
    Map<String, String> env = new LinkedHashMap<String, String>(candidate.getEnv());
    env.put(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, currentAppIncarnationId);
    return candidate.withEnv(env);
  }

  @Override
  public Try<Void> delete(String appName) {
    return withHelperOperation(appName, () -> deleteRuntime(appName));
  }

  private Try<Void> deleteRuntime(String appName) {
    appDevHelperLogService.clear(appName);
    refreshSessions.clear(appName);
    advanceHelperLifecycle(appName, false);
    List<RuntimeRecord> runtimeRecords = resolveRuntimes(appName);
    if (runtimeRecords.isEmpty()) {
      return Try.success(null);
    }
    Try<Void> result = Try.success(null);
    for (RuntimeRecord record : runtimeRecords) {
      result = result.flatMap(ignored -> stopAndDelete(record));
    }
    return result;
  }

  @Override
  public Try<Void> start(String appName) {
    return withHelperOperation(appName, () -> start(appName, true));
  }

  private Try<Void> start(String appName, boolean buildWorkspace) {
    refreshSessions.clear(appName);
    appDevHelperLogService.appendSystemLog(appName, "启动开发助手运行时");
    return resolveRuntime(appName)
        .map(this::refreshRecord)
        .map(record -> {
          boolean lifecycleAdvanced = record.instance == null
              || record.instance.getStatus() != RuntimeStatus.RUNNING
                  && record.instance.getStatus() != RuntimeStatus.STARTING;
          if (lifecycleAdvanced) {
            advanceHelperLifecycle(appName, true);
          }
          return buildRunWorkspace(appName, buildWorkspace)
              .flatMap(v -> devRuntimeService.start(record.key))
              .map(instance -> (Void) null)
              .onFailure(ignored -> {
                if (lifecycleAdvanced) {
                  advanceHelperLifecycle(appName, false);
                }
              });
        })
        .orElseGet(() -> Try.failure(new BusinessException("助手容器不存在！")));
  }

  @Override
  public Try<Void> stop(String appName) {
    return withHelperOperation(appName, () -> stopRuntime(appName));
  }

  private Try<Void> stopRuntime(String appName) {
    refreshSessions.clearStatus(appName);
    advanceHelperLifecycle(appName, false);
    appDevHelperLogService.appendSystemLog(appName, "停止开发助手运行时");
    return resolveRuntime(appName)
        .map(record -> devRuntimeService.stop(record.key).map(instance -> (Void) null))
        .orElseGet(() -> Try.failure(new BusinessException("助手容器不存在！")));
  }

  @Override
  public Try<Void> kill(String appName) {
    return stop(appName);
  }

  @Override
  public Try<Void> restart(String appName) {
    return withHelperOperation(appName, () -> restartRuntime(appName));
  }

  private Try<Void> restartRuntime(String appName) {
    refreshSessions.clear(appName);
    advanceHelperLifecycle(appName, true);
    refreshSessions.setStatus(appName, ContainerStatus.LAUNCHING);
    appDevHelperLogService.resetRuntimeOutput(appName);
    appDevHelperLogService.appendSystemLog(appName, "重启开发助手运行时");
    return resolveRuntime(appName)
        .map(record -> {
          devRuntimeService.resetLogStream(record.key);
          return workspace.prepare(appName)
              .flatMap(v -> devRuntimeService.restart(record.key))
              .flatMap(instance -> syncRuntimeLogs(appName))
              .map(instance -> (Void) null)
              .onFailure(throwable -> refreshSessions.clearStatus(appName));
        })
        .orElseGet(() -> {
          refreshSessions.clearStatus(appName);
          return Try.failure(new BusinessException("助手容器不存在！"));
        });
  }

  @Override
  public Try<Void> reload(String appName) {
    return withHelperOperation(appName, () -> reloadRuntime(appName));
  }

  private Try<Void> reloadRuntime(String appName) {
    Optional<RuntimeRecord> runtimeRecord = resolveRuntime(appName).map(this::refreshRecord);
    if (!runtimeRecord.isPresent()) {
      return Try.failure(new BusinessException("助手容器不存在！"));
    }
    if (runtimeRecord.get().instance == null || runtimeRecord.get().instance.getStatus() != RuntimeStatus.RUNNING) {
      return Try.failure(new BusinessException("开发助手已停止！"));
    }
    HelperIdentity helperIdentity = refreshSessions.identity(appName);
    if (runtimeRecord.get().spec.getBackendType() == BackendType.DOCKER && helperIdentity == null) {
      return Try.failure(new BusinessException("开发助手尚未完成元数据协议握手"));
    }
    appDevHelperLogService.appendSystemLog(appName, "刷新开发助手工作区");
    if (runtimeRecord.get().spec.getBackendType() == BackendType.KUBERNETES) {
      advanceHelperLifecycle(appName, true);
      return devRuntimeService.reloadWorkspace(runtimeRecord.get().spec).map(instance -> (Void) null);
    }
    HelperRuntimeTarget runtimeTarget = runtimeTarget(runtimeRecord.get());
    if (runtimeTarget == null || StringUtils.isBlank(runtimeTarget.getRuntimeId())) {
      return Try.failure(new BusinessException("开发助手运行时标识不可用"));
    }
    refreshSessions.setStatus(appName, ContainerStatus.LAUNCHING);
    return workspace.prepare(appName)
        .map(v -> {
          requestMetadataRefresh(
              appName,
              runtimeRecord.get().instance.getRuntimeGeneration(),
              helperIdentity,
              runtimeTarget
          );
          return (Void) null;
        });
  }

  @Override
  public Try<Map<String, Object>> getStatus(String appName) {
    return resolveRuntime(appName)
        .map(this::refreshRecord)
        .map(record -> Try.success(buildStatusPayload(record)))
        .orElseGet(() -> Try.success(notFoundPayload(appName)));
  }

  @Override
  public Try<Void> syncRuntimeLogs(String appName) {
    Optional<RuntimeRecord> runtimeRecord = resolveRuntime(appName);
    if (!runtimeRecord.isPresent()) {
      return Try.success(null);
    }
    return devRuntimeService.getLogs(runtimeRecord.get().key)
        .map(logs -> {
          appDevHelperLogService.appendRuntimeOutput(appName, logs);
          return (Void) null;
        })
        .recoverWith(error -> {
          appDevHelperLogService.appendSystemLog(appName, "读取开发助手运行日志失败: " + error.getMessage());
          return Try.success(null);
        });
  }

  @Override
  public Try<String> updateImage(String appName) {
    return withHelperOperation(appName, () -> updateRuntimeImage(appName));
  }

  private Try<String> updateRuntimeImage(String appName) {
    appDevHelperLogService.appendSystemLog(appName, "更新开发助手镜像");
    Optional<RuntimeRecord> runtimeRecord = resolveRuntime(appName).map(this::refreshRecord);
    if (!runtimeRecord.isPresent()) {
      return Try.failure(new BusinessException("助手容器不存在！"));
    }
    boolean shouldStart = runtimeRecord
        .map(record -> record.instance != null && shouldResumeAfterImageUpdate(record.instance.getStatus()))
        .orElse(false);
    RuntimeSpec recreatedSpec = runtimeSpecFactory.recreate(runtimeRecord.get().spec);

    return delete(appName)
        .flatMap(v -> create(recreatedSpec))
        .flatMap(runtimeId -> shouldStart
            ? start(appName, false).map(v -> runtimeId)
            : Try.success(runtimeId));
  }

  private Try<String> create(RuntimeSpec spec) {
    advanceHelperLifecycle(spec.getAppName(), false);
    appDevHelperLogService.clear(spec.getAppName());
    appDevHelperLogService.appendSystemLog(spec.getAppName(), "开始创建开发助手运行时");
    return workspace.prepare(spec.getAppName())
        .flatMap(v -> devRuntimeService.create(spec))
        .map(instance -> {
          appDevHelperLogService.appendSystemLog(spec.getAppName(), "开发助手运行时已创建: " + instance.getRuntimeId());
          return instance.getRuntimeId();
        });
  }

  private Try<Void> buildRunWorkspace(String appName, boolean buildWorkspace) {
    if (!buildWorkspace) {
      return Try.success(null);
    }
    return workspace.prepare(appName);
  }

  private <T> Try<T> withHelperOperation(String appName, Supplier<Try<T>> operation) {
    String operationKey = appName == null ? "" : appName.trim();
    Object monitor = helperOperationMonitors.computeIfAbsent(operationKey, ignored -> new Object());
    synchronized (monitor) {
      return operation.get();
    }
  }

  private static final class CreatedHelperRuntime {
    private final RuntimeSpec spec;
    private final RuntimeInstance instance;
    private final boolean lifecyclePrepared;

    private CreatedHelperRuntime(
        RuntimeSpec spec,
        RuntimeInstance instance,
        boolean lifecyclePrepared
    ) {
      this.spec = spec;
      this.instance = instance;
      this.lifecyclePrepared = lifecyclePrepared;
    }
  }

  private boolean shouldResumeAfterImageUpdate(RuntimeStatus status) {
    return status == RuntimeStatus.RUNNING
        || status == RuntimeStatus.STARTING
        || status == RuntimeStatus.RELOADING;
  }

  @EventListener(ContextClosedEvent.class)
  void onContextClosed() {
    runtimeStateStore.allSpecs(RuntimeKind.DEV_HELPER).keySet().forEach(key -> devRuntimeService.delete(key));
  }

  @EventListener(DeleteAppEvent.class)
  void deleteAppEventListener(DeleteAppEvent event) {
    delete(event.getAppName());
  }

  private void handleHelperOnline(CommandResult result) {
    if (result == null || !result.getSuccess() || StringUtils.isBlank(result.getAppName())) {
      return;
    }
    String appName = result.getAppName().trim();
    Optional<HelperIdentity> helperIdentity = MetadataRefreshProtocol.helperIdentity(result);
    if (!helperIdentity.isPresent()) {
      appDevHelperLogService.appendSystemLog(appName, "开发助手不支持当前元数据快照协议，请更新助手镜像");
      return;
    }
    Long activationEpoch = refreshSessions.activationEpoch(appName);
    if (activationEpoch == null) {
      appDevHelperLogService.appendSystemLog(appName, "忽略非活动开发助手运行时的协议握手");
      return;
    }
    List<RuntimeRecord> runtimeRecords = resolveRuntimes(appName);
    Optional<RuntimeRecord> runtimeRecord = resolveRuntimeForHelper(runtimeRecords, helperIdentity.get());
    if (!runtimeRecord.isPresent()
        || runtimeRecord.get().instance == null
        || !isActiveRuntimeStatus(runtimeRecord.get().instance.getStatus())) {
      boolean matchingIncarnation = runtimeRecords.stream()
          .anyMatch(record -> Objects.equals(
              appIncarnationId(record.spec),
              helperIdentity.get().getAppIncarnationId()
          ));
      appDevHelperLogService.appendSystemLog(
          appName,
          runtimeRecords.isEmpty() || matchingIncarnation
              ? "忽略非活动开发助手运行时的协议握手"
              : "忽略不属于当前应用实例的开发助手协议握手"
      );
      return;
    }
    String appIncarnationId = appIncarnationId(runtimeRecord.get().spec);
    if (!Objects.equals(appIncarnationId, helperIdentity.get().getAppIncarnationId())) {
      appDevHelperLogService.appendSystemLog(appName, "忽略不属于当前应用实例的开发助手协议握手");
      return;
    }
    HelperRuntimeTarget runtimeTarget = runtimeTarget(runtimeRecord.get());
    if (runtimeTarget == null
        || StringUtils.isBlank(runtimeTarget.getRuntimeId())
        || !rememberHelperIdentity(appName, helperIdentity.get(), runtimeTarget, activationEpoch.longValue())) {
      return;
    }
    eventPublisher.publishEvent(new HelperRuntimeActivatedEvent(appName, helperIdentity.get(), runtimeTarget));
    if (runtimeRecord.get().spec.getBackendType() == BackendType.DOCKER) {
      requestMetadataRefresh(
          appName,
          runtimeRecord.get().instance.getRuntimeGeneration(),
          helperIdentity.get(),
          runtimeTarget
      );
    }
  }

  private boolean isActiveRuntimeStatus(RuntimeStatus status) {
    return status == RuntimeStatus.STARTING
        || status == RuntimeStatus.RUNNING
        || status == RuntimeStatus.RELOADING;
  }

  private String appIncarnationId(RuntimeSpec spec) {
    return spec == null || spec.getEnv() == null
        ? null
        : StringUtils.trimToNull(spec.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV));
  }



  private boolean rememberHelperIdentity(String appName, HelperIdentity candidate,
      HelperRuntimeTarget runtimeTarget, long expectedActivationEpoch) {
    var retired = refreshSessions.activate(appName, candidate, runtimeTarget, expectedActivationEpoch);
    if (retired == null) return false;
    publishHelperRetired(appName, retired.identity(), retired.target());
    return true;
  }

  private void requestMetadataRefresh(String appName, Long runtimeGeneration,
      HelperIdentity helperIdentity, HelperRuntimeTarget runtimeTarget) {
    var request = refreshSessions.request(appName, runtimeGeneration, helperIdentity, runtimeTarget);
    if (request != null) sendMetadataRefresh(request);
  }

  private void sendMetadataRefresh(RefreshRequest request) {
    try {
      eventPublisher.publishEvent(request.toRequestedEvent());
      devHelperCommandService.exec(
          request.appName,
          Commands.REFRESH_METADATA.getCommand(),
          MetadataRefreshProtocol.refreshParameters(
              request.runtimeGeneration,
              request.helperIdentity,
              request.refreshOperationId
          )
      );
    } catch (RuntimeException exception) {
      String reason = exception.getMessage() == null || exception.getMessage().trim().isEmpty()
          ? "发送元数据刷新命令失败"
          : exception.getMessage();
      RefreshFailureDecision decision = refreshSessions.failOrRetry(
          request.appName,
          CommandFailureType.TRANSPORT,
          lifecycle -> lifecycle.matchesPendingRefresh(request)
      );
      if (!decision.matched) {
        throw exception;
      }
      publishMetadataSnapshotRejected(decision.failedRequest, reason, CommandFailureType.TRANSPORT);
      if (decision.retryRequest != null) {
        sendMetadataRefresh(decision.retryRequest);
        return;
      }
      publishHelperStatusChanged(request.appName);
      throw exception;
    }
  }

  private void handleRefreshResult(CommandResult result) {
    if (result == null || StringUtils.isBlank(result.getAppName())) {
      return;
    }
    String appName = result.getAppName().trim();
    appDevHelperLogService.appendCommandResult(appName, result);
    if (result.getSuccess()) {
      return;
    }
    RefreshFailureDecision decision = refreshSessions.failOrRetry(
        appName,
        result.getFailureType(),
        lifecycle -> lifecycle.matchesPendingRefresh(result)
    );
    if (!decision.matched) {
      return;
    }
    if (result.getFailureType() != null) {
      publishMetadataSnapshotRejected(
          decision.failedRequest,
          StringUtils.defaultIfBlank(result.getMessage(), "元数据刷新失败"),
          result.getFailureType()
      );
    }
    if (decision.retryRequest != null) {
      try {
        sendMetadataRefresh(decision.retryRequest);
      } catch (RuntimeException exception) {
        appDevHelperLogService.appendSystemLog(appName, "重试发送元数据刷新命令失败: " + exception.getMessage());
      }
      return;
    }
    publishHelperStatusChanged(appName);
  }

  @EventListener(MetadataSnapshotPublishedEvent.class)
  void handleMetadataSnapshotPublished(MetadataSnapshotPublishedEvent event) {
    if (event == null || StringUtils.isBlank(event.getAppName())) {
      return;
    }
    String appName = event.getAppName().trim();
    boolean dockerRuntime = event.getRuntimeTarget() != null
        && BackendType.DOCKER.name().equals(event.getRuntimeTarget().getBackendType());
    if (!matchesCurrentGeneration(appName, event.getRuntimeGeneration(), event.getRuntimeTarget())) {
      return;
    }
    if (!refreshSessions.complete(event, dockerRuntime)) return;
    publishHelperStatusChanged(appName);
  }

  @EventListener(MetadataSnapshotRejectedEvent.class)
  void handleMetadataSnapshotRejected(MetadataSnapshotRejectedEvent event) {
    if (event == null || StringUtils.isBlank(event.getAppName())) {
      return;
    }
    String appName = event.getAppName().trim();
    if (!matchesCurrentGeneration(appName, event.getRuntimeGeneration(), event.getRuntimeTarget())) {
      return;
    }
    RefreshFailureDecision decision = refreshSessions.failOrRetry(
        appName,
        event.getFailureType(),
        lifecycle -> lifecycle.matchesPendingRefresh(event)
    );
    if (!decision.matched) {
      return;
    }
    appDevHelperLogService.appendSystemLog(appName, "开发助手元数据快照发布被拒绝: " + event.getReason());
    if (decision.retryRequest != null) {
      try {
        sendMetadataRefresh(decision.retryRequest);
      } catch (RuntimeException exception) {
        appDevHelperLogService.appendSystemLog(appName, "重试发送元数据刷新命令失败: " + exception.getMessage());
      }
      return;
    }
    publishHelperStatusChanged(appName);
  }





  private void publishMetadataSnapshotRejected(
      RefreshRequest request,
      String reason,
      CommandFailureType failureType
  ) {
    eventPublisher.publishEvent(new MetadataSnapshotRejectedEvent(
        request.appName,
        request.runtimeGeneration,
        request.helperIdentity.getInstanceId(),
        request.helperIdentity.getStartedAtEpochMillis(),
        request.helperIdentity.getAppIncarnationId(),
        request.refreshOperationId,
        reason,
        failureType,
        request.runtimeTarget
    ));
  }

  private void advanceHelperLifecycle(String appName, boolean acceptingActivations) {
    var retired = refreshSessions.advance(appName, acceptingActivations);
    publishHelperRetired(appName, retired.identity(), retired.target());
  }

  private void publishHelperRetired(
      String appName,
      HelperIdentity identity,
      HelperRuntimeTarget runtimeTarget
  ) {
    if (identity == null || runtimeTarget == null) {
      return;
    }
    eventPublisher.publishEvent(new HelperRuntimeRetiredEvent(
        appName,
        identity.getInstanceId(),
        identity.getStartedAtEpochMillis(),
        identity.getAppIncarnationId(),
        runtimeTarget
    ));
  }

  private HelperRuntimeTarget runtimeTarget(RuntimeRecord record) {
    if (record == null || record.spec == null || record.instance == null || record.spec.getBackendType() == null) {
      return null;
    }
    return new HelperRuntimeTarget(
        record.spec.getBackendType().name(),
        record.spec.getProfile(),
        record.instance.getRuntimeId()
    );
  }



  private boolean matchesCurrentGeneration(
      String appName,
      Long runtimeGeneration,
      HelperRuntimeTarget runtimeTarget
  ) {
    return resolveRuntime(appName, runtimeTarget)
        .map(record -> {
          Long currentGeneration = record.instance == null ? null : record.instance.getRuntimeGeneration();
          return Objects.equals(currentGeneration, runtimeGeneration);
        })
        .orElse(false);
  }

  private Optional<RuntimeRecord> resolveRuntimeForHelper(
      List<RuntimeRecord> runtimeRecords,
      HelperIdentity helperIdentity
  ) {
    List<RuntimeRecord> matches = runtimeRecords.stream()
        .filter(record -> Objects.equals(appIncarnationId(record.spec), helperIdentity.getAppIncarnationId()))
        .collect(Collectors.toList());
    if (matches.size() != 1) {
      return Optional.empty();
    }
    RuntimeRecord refreshed = refreshRecord(matches.get(0));
    return refreshed.instance != null && isActiveRuntimeStatus(refreshed.instance.getStatus())
        ? Optional.of(refreshed)
        : Optional.empty();
  }

  private Optional<RuntimeRecord> resolveRuntime(String appName, HelperRuntimeTarget runtimeTarget) {
    if (runtimeTarget == null) {
      return Optional.empty();
    }
    List<RuntimeRecord> matches = resolveRuntimes(appName).stream()
        .filter(record -> Objects.equals(runtimeTarget(record), runtimeTarget))
        .collect(Collectors.toList());
    if (matches.size() != 1) {
      return Optional.empty();
    }
    RuntimeRecord refreshed = refreshRecord(matches.get(0));
    return Objects.equals(runtimeTarget(refreshed), runtimeTarget)
        ? Optional.of(refreshed)
        : Optional.empty();
  }

  private Optional<RuntimeRecord> resolveRuntime(String appName) {
    List<RuntimeRecord> runtimeRecords = resolveRuntimes(appName);
    if (runtimeRecords.isEmpty()) {
      return Optional.empty();
    }
    return runtimeRecords.stream()
        .sorted(Comparator.comparing(this::runtimePriority).reversed())
        .findFirst();
  }

  private List<RuntimeRecord> resolveRuntimes(String appName) {
    List<RuntimeRecord> runtimeRecords = storedRuntimeRecords(appName);
    if (!runtimeRecords.isEmpty()) {
      return runtimeRecords;
    }
    Try<List<RuntimeInstance>> recovery = devRuntimeService.recover(appName, RuntimeKind.DEV_HELPER);
    if (recovery.isFailure()) {
      appDevHelperLogService.appendSystemLog(
          appName,
          "恢复开发助手运行时状态失败: " + recovery.getCause().getMessage()
      );
      return new ArrayList<RuntimeRecord>();
    }
    return storedRuntimeRecords(appName);
  }

  private List<RuntimeRecord> storedRuntimeRecords(String appName) {
    return runtimeStateStore.allSpecs(RuntimeKind.DEV_HELPER).entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> Objects.equals(appName, entry.getValue().getAppName()))
        .map(entry -> new RuntimeRecord(entry.getKey(), entry.getValue(), runtimeStateStore.allInstances().get(entry.getKey())))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private String containerNameOf(String appName) {
    return "ouroboros-dev-helper-" + appName;
  }

  private int runtimePriority(RuntimeRecord record) {
    RuntimeStatus status = record.instance == null ? null : record.instance.getStatus();
    if (status == RuntimeStatus.RUNNING) {
      return 4;
    }
    if (status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING) {
      return 3;
    }
    if (status == RuntimeStatus.STOPPED) {
      return 2;
    }
    if (status == RuntimeStatus.NOT_FOUND) {
      return 0;
    }
    return 1;
  }

  private Try<Void> stopAndDelete(RuntimeRecord record) {
    Try<RuntimeInstance> stopResult = record.instance != null && record.instance.getStatus() != RuntimeStatus.STOPPED
        ? devRuntimeService.stop(record.key)
        : Try.success(record.instance);
    return stopResult.flatMap(instance -> devRuntimeService.delete(record.key).map(v -> (Void) null));
  }

  private RuntimeRecord refreshRecord(RuntimeRecord record) {
    RuntimeInstance refreshed = devRuntimeService.getStatus(record.key).getOrElse(record.instance);
    return new RuntimeRecord(record.key, record.spec, refreshed);
  }

  private Map<String, Object> buildStatusPayload(RuntimeRecord record) {
    ContainerStatus status = resolveStatus(record);
    boolean refreshPending = ContainerStatus.LAUNCHING.equals(refreshSessions.status(record.spec.getAppName()));
    RuntimeImageViewDTO imageState = runtimeImageViewService.describe(
        record.spec.getBackendType(),
        resolveImageReference(record),
        record.spec.getBackendType() == BackendType.DOCKER
            ? devRuntimeService.listImages(record.key, devContainerConfig.getMothershipImageName()).getOrElse(Collections::emptyList)
            : null
    );
    if (imageState != null
        && record.spec.getBackendType() == BackendType.DOCKER
        && StringUtils.isNotBlank(devContainerConfig.getMothershipImageName())
        && DevHelperRuntimeSpecFactory.shouldFollowConfiguredMothershipImage(record.spec, devContainerConfig.getMothershipImageName())
        && !Objects.equals(devContainerConfig.getMothershipImageName(), record.spec.getImage())) {
      imageState.setUpdateRecommended(Boolean.TRUE);
    }
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", record.spec.getAppName());
    payload.put("containerId", record.instance == null ? null : record.instance.getRuntimeId());
    payload.put("containerName", containerNameOf(record));
    payload.put("status", status);
    payload.put("runtimeGeneration", record.instance == null ? null : record.instance.getRuntimeGeneration());
    payload.put("refreshPending", refreshPending);
    payload.put("imageState", imageState);
    payload.put("imageReference", imageState == null ? null : imageState.getReference());
    payload.put("imageRepository", imageState == null ? null : imageState.getRepository());
    payload.put("imageTag", imageState == null ? null : imageState.getTag());
    payload.put("imageDigest", imageState == null ? null : imageState.getDigest());
    payload.put("imageInputMode", imageState == null ? null : imageState.getInputMode());
    payload.put("imageCapabilities", imageState == null ? null : imageState.getCapabilities());
    payload.put("updateRecommended", imageState == null ? null : imageState.getUpdateRecommended());
    payload.put("imageVersion", imageState == null ? null : imageState.getTag());
    payload.put("lastLoadTime", refreshSessions.lastLoadTime(record.spec.getAppName()));
    return payload;
  }

  private Map<String, Object> notFoundPayload(String appName) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", appName);
    payload.put("containerId", null);
    payload.put("containerName", null);
    payload.put("status", ContainerStatus.NOT_FOUND);
    payload.put("runtimeGeneration", null);
    payload.put("refreshPending", Boolean.FALSE);
    payload.put("imageState", null);
    payload.put("imageReference", null);
    payload.put("imageRepository", null);
    payload.put("imageTag", null);
    payload.put("imageDigest", null);
    payload.put("imageInputMode", null);
    payload.put("imageCapabilities", null);
    payload.put("updateRecommended", null);
    payload.put("imageVersion", null);
    payload.put("lastLoadTime", null);
    return payload;
  }

  private ContainerStatus resolveStatus(RuntimeRecord record) {
    RuntimeStatus runtimeStatus = record.instance == null ? null : record.instance.getStatus();
    ContainerStatus runtimeContainerStatus = toContainerStatus(runtimeStatus);
    ContainerStatus override = refreshSessions.status(record.spec.getAppName());
    if (override != null && runtimeContainerStatus == ContainerStatus.RUNNING) {
      return override;
    }
    return runtimeContainerStatus;
  }

  private ContainerStatus toContainerStatus(RuntimeStatus runtimeStatus) {
    if (runtimeStatus == null) {
      return ContainerStatus.UNKNOWN;
    }
    switch (runtimeStatus) {
      case NOT_FOUND:
        return ContainerStatus.NOT_FOUND;
      case CREATING:
      case STARTING:
      case RELOADING:
        return ContainerStatus.LAUNCHING;
      case RUNNING:
        return ContainerStatus.RUNNING;
      case STOPPED:
        return ContainerStatus.STOPPED;
      default:
        return ContainerStatus.UNKNOWN;
    }
  }

  private String resolveImageReference(RuntimeRecord record) {
    return Optional.ofNullable(record.instance)
        .map(RuntimeInstance::getBackendMetadata)
        .map(metadata -> metadata.get("imageReference"))
        .filter(StringUtils::isNotBlank)
        .orElse(record.spec.getImage());
  }

  private String containerNameOf(RuntimeRecord record) {
    if (record.instance != null
        && record.instance.getBackendMetadata() != null
        && StringUtils.isNotBlank(record.instance.getBackendMetadata().get("containerName"))) {
      return record.instance.getBackendMetadata().get("containerName");
    }
    return containerNameOf(record.spec.getAppName());
  }



  private void publishHelperStatusChanged(String appName) {
    if (StringUtils.isBlank(appName)) {
      return;
    }
    eventPublisher.publishEvent(new DevHelperStatusChangedEvent(appName));
  }

  private static final class RuntimeRecord {
    private final RuntimeIdentityKey key;
    private final RuntimeSpec spec;
    private final RuntimeInstance instance;

    private RuntimeRecord(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
      this.key = key;
      this.spec = spec;
      this.instance = instance;
    }
  }
}
