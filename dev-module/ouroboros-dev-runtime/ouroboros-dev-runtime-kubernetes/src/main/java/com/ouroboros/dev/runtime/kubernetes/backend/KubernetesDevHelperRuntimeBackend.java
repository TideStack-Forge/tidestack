package com.ouroboros.dev.runtime.kubernetes.backend;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vavr.control.Try;

import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

public class KubernetesDevHelperRuntimeBackend implements RuntimeBackend {
  static final String FAILURE_CODE = "failureCode";
  static final String FAILURE_MESSAGE = "failureMessage";
  static final String HANDSHAKE_TIMEOUT = "HELPER_HANDSHAKE_TIMEOUT";
  static final String HANDSHAKE_FAILED = "HELPER_HANDSHAKE_FAILED";
  static final String REFRESH_FAILED = "HELPER_REFRESH_FAILED";
  static final String APPLY_FAILED = "HELPER_APPLY_FAILED";
  private static final String HELPER_IMAGE_SOURCE_LABEL = "ouroboros.dev/helper-image-source";
  private static final String HELPER_IMAGE_SOURCE_REFERENCE_LABEL = "ouroboros.dev/helper-image-source-reference";
  private static final String HELPER_IMAGE_SOURCE_EXPLICIT = "explicit";
  private static final ApplicationEventPublisher NOOP_APPLICATION_EVENT_PUBLISHER = event -> {
  };
  private static final String HELPER_CONTAINER_NAME = "helper";
  private static final long HELPER_LOG_POLL_INTERVAL_SECONDS = 1L;
  private static final int MAX_METADATA_REFRESH_RETRIES = 1;

  private final KubernetesClient client;
  private final KubernetesRuntimeResourceFactory resourceFactory;
  private final KubernetesRuntimeProperties properties;
  private final DevHelperCommandService commandService;
  private final KubernetesRuntimeStatusSynchronizer statusSynchronizer;
  private final DevRuntimeEventPublisher eventPublisher;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ScheduledExecutorService helperLogPollExecutor;
  private final Function<Pod, List<String>> podLogReader;
  private final ConcurrentMap<RuntimeIdentityKey, HelperRuntimeState> states = new ConcurrentHashMap<RuntimeIdentityKey, HelperRuntimeState>();
  private final ConcurrentMap<RuntimeIdentityKey, LogStreamState> helperLogStates = new ConcurrentHashMap<RuntimeIdentityKey, LogStreamState>();

  public KubernetesDevHelperRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeProperties properties,
      DevHelperCommandService commandService,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer
  ) {
    this(
        client,
        resourceFactory,
        properties,
        commandService,
        statusSynchronizer,
        DevRuntimeEventPublisher.noop(),
        null,
        NOOP_APPLICATION_EVENT_PUBLISHER
    );
  }

  public KubernetesDevHelperRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeProperties properties,
      DevHelperCommandService commandService,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      Function<Pod, List<String>> podLogReader
  ) {
    this(
        client,
        resourceFactory,
        properties,
        commandService,
        statusSynchronizer,
        DevRuntimeEventPublisher.noop(),
        podLogReader,
        NOOP_APPLICATION_EVENT_PUBLISHER
    );
  }

  public KubernetesDevHelperRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeProperties properties,
      DevHelperCommandService commandService,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      DevRuntimeEventPublisher eventPublisher,
      Function<Pod, List<String>> podLogReader
  ) {
    this(
        client,
        resourceFactory,
        properties,
        commandService,
        statusSynchronizer,
        eventPublisher,
        podLogReader,
        NOOP_APPLICATION_EVENT_PUBLISHER
    );
  }

  public KubernetesDevHelperRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeProperties properties,
      DevHelperCommandService commandService,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      DevRuntimeEventPublisher eventPublisher,
      Function<Pod, List<String>> podLogReader,
      ApplicationEventPublisher applicationEventPublisher
  ) {
    this.client = client;
    this.resourceFactory = resourceFactory;
    this.properties = properties;
    this.commandService = commandService;
    this.statusSynchronizer = statusSynchronizer;
    this.eventPublisher = eventPublisher == null ? DevRuntimeEventPublisher.noop() : eventPublisher;
    this.applicationEventPublisher = applicationEventPublisher == null
        ? NOOP_APPLICATION_EVENT_PUBLISHER
        : applicationEventPublisher;
    this.helperLogPollExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      private final AtomicInteger sequence = new AtomicInteger();

      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "kubernetes-dev-helper-log-poller-" + sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      }
    });
    this.podLogReader = podLogReader == null ? this::readPodLogs : podLogReader;
    this.commandService.registerGlobalResultListener(this::onCommandResult);
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.KUBERNETES;
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.DEV_HELPER;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.KUBERNETES && spec.getRuntimeKind() == RuntimeKind.DEV_HELPER;
  }

  @Override
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    return activate(spec, nextGeneration(RuntimeIdentityKey.from(spec), null), RuntimeStatus.STARTING)
        .map(instance -> {
          trackHelperLogStreaming(instance);
          return instance;
        });
  }

  @Override
  public Try<RuntimeInstance> start(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    HelperRuntimeState current = states.get(key);
    if (current != null && (current.status == RuntimeStatus.STARTING || current.status == RuntimeStatus.RUNNING)) {
      trackHelperLogStreaming(current.toRuntimeInstance());
      return Try.success(current.toRuntimeInstance());
    }
    return activate(spec, nextGeneration(key, null), RuntimeStatus.STARTING)
        .map(instance -> {
          trackHelperLogStreaming(instance);
          return instance;
        });
  }

  @Override
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
    return Try.of(() -> {
      scale(key, 0);
      stopHelperLogStreaming(key);
      HelperRuntimeState stopped = states.compute(key, (ignored, current) -> current == null
          ? HelperRuntimeState.of(key, null, 0L, RuntimeStatus.STOPPED, null, null)
          : current.retire(RuntimeStatus.STOPPED, current.runtimeGeneration, current.spec));
      return stopped.toRuntimeInstance();
    });
  }

  @Override
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration) {
    return activate(spec, runtimeGeneration, RuntimeStatus.STARTING)
        .map(instance -> {
          trackHelperLogStreaming(instance);
          return instance;
        });
  }

  @Override
  public Try<Void> delete(RuntimeIdentityKey key) {
    return Try.run(() -> {
      client.apps().deployments()
          .inNamespace(properties.getNamespace())
          .withName(KubernetesResourceNameFactory.deploymentName(key))
          .delete();
      stopHelperLogStreaming(key);
      states.compute(key, (ignored, current) -> current == null
          ? HelperRuntimeState.of(key, null, 0L, RuntimeStatus.NOT_FOUND, null, null)
          : current.retire(RuntimeStatus.NOT_FOUND, current.runtimeGeneration, current.spec));
    }).map(value -> null);
  }

  @Override
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return Try.of(() -> {
      if (deployment(key) == null) {
        throw new IllegalStateException("runtime not found");
      }
      HelperRuntimeState reloading = states.compute(
          key,
          (ignored, current) -> current == null
              ? HelperRuntimeState.of(key, spec, runtimeGeneration, RuntimeStatus.RELOADING, null, null)
              : current.retire(RuntimeStatus.RELOADING, Long.valueOf(runtimeGeneration), spec)
      );
      try {
        apply(resourceFactory.deployment(spec, Long.valueOf(runtimeGeneration)));
      } catch (RuntimeException exception) {
        markApplyFailed(key, reloading, exception);
        throw exception;
      }
      return reloading.toRuntimeInstance();
    }).map(instance -> {
      trackHelperLogStreaming(instance);
      return instance;
    });
  }

  @Override
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return Try.success(resolveStatus(key))
        .map(instance -> {
          trackHelperLogStreaming(instance);
          return instance;
        });
  }

  @Override
  public Try<Optional<RuntimeSpec>> discover(String appName) {
    if (appName == null || appName.trim().isEmpty()) {
      return Try.success(Optional.empty());
    }
    String normalizedAppName = appName.trim();
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        normalizedAppName,
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.KUBERNETES
    );
    return Try.of(() -> discoveredSpec(key, deployment(key)));
  }

  @Override
  public Try<List<String>> getLogs(RuntimeIdentityKey key) {
    return Try.of(() -> helperPods(key).stream()
        .flatMap(pod -> podLogReader.apply(pod).stream())
        .collect(Collectors.toList()));
  }

  @Override
  public void resetLogStream(RuntimeIdentityKey key) {
    LogStreamState state = helperLogStates.get(key);
    if (state == null) {
      return;
    }
    synchronized (state) {
      state.lastSnapshot = Collections.<String>emptyList();
    }
  }

  void refreshHelperLogStreaming(RuntimeIdentityKey key) {
    RuntimeInstance runtime = resolveStatus(key);
    if (runtime == null || !isActiveHelperStatus(runtime.getStatus())) {
      stopHelperLogStreaming(key);
      return;
    }
    trackHelperLogStreaming(runtime);
    Try<List<String>> logsResult = getLogs(key);
    if (logsResult.isFailure()) {
      return;
    }
    List<String> logs = logsResult.get();
    if (logs == null || logs.isEmpty()) {
      return;
    }
    LogStreamState state = helperLogStates.get(key);
    if (state == null) {
      return;
    }
    synchronized (state) {
      if (!logs.equals(state.lastSnapshot)) {
        state.lastSnapshot = new ArrayList<String>(logs);
        eventPublisher.publish(DevRuntimeEvent.builder()
            .type(DevRuntimeEventType.RUNTIME_LOG_OUTPUT)
            .runtimeId(runtime.getRuntimeId())
            .appName(runtime.getAppName())
            .profile(runtime.getProfile())
            .runtimeKind(runtime.getRuntimeKind())
            .backendType(runtime.getBackendType())
            .runtimeGeneration(runtime.getRuntimeGeneration())
            .status(runtime.getStatus())
            .logLines(logs)
            .build());
      }
    }
  }

  public void onCommandResult(CommandResult result) {
    if (result == null || result.getAppName() == null || result.getAppName().trim().isEmpty()) {
      return;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.of(result.getAppName().trim(), "dev-helper", RuntimeKind.DEV_HELPER, BackendType.KUBERNETES);
    if (result.getSuccess() || !Commands.REFRESH_METADATA.getCommand().equalsIgnoreCase(result.getCommand())) {
      return;
    }
    AtomicReference<RefreshRequest> failedRequest = new AtomicReference<RefreshRequest>();
    AtomicReference<RefreshRequest> retryRequest = new AtomicReference<RefreshRequest>();
    states.computeIfPresent(key, (ignored, current) -> {
      if (!current.isAwaitingRefresh()
          || !current.matchesRefreshResult(result)) {
        return current;
      }
      if (current.canRetry(result.getFailureType())) {
        failedRequest.set(current.currentRefreshRequest());
        return current.retryRefresh(retryRequest);
      }
      if (current.status == RuntimeStatus.RELOADING) {
        return current.rejectRefresh(REFRESH_FAILED, result.getMessage());
      }
      String failureCode = isTimeout(result) ? HANDSHAKE_TIMEOUT : HANDSHAKE_FAILED;
      return current.rejectRefresh(failureCode, result.getMessage());
    });
    if (retryRequest.get() != null) {
      publishRefreshRejected(failedRequest.get(), result.getMessage(), result.getFailureType());
      sendRefresh(key, retryRequest.get());
    }
  }

  @EventListener(HelperRuntimeActivatedEvent.class)
  @Order(Ordered.LOWEST_PRECEDENCE)
  public void onHelperRuntimeActivated(HelperRuntimeActivatedEvent event) {
    if (event == null
        || event.getAppName() == null
        || event.getAppName().trim().isEmpty()
        || event.getHelperIdentity() == null) {
      return;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        event.getAppName().trim(),
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.KUBERNETES
    );
    if (!matchesRuntimeTarget(key, event.getRuntimeTarget())) {
      return;
    }
    if (!states.containsKey(key)) {
      resolveStatus(key);
    }
    AtomicReference<RefreshRequest> refreshRequest = new AtomicReference<RefreshRequest>();
    states.computeIfPresent(key, (ignored, current) -> {
      HelperRuntimeState activated = current.activate(event.getHelperIdentity());
      return prepareRefresh(activated, refreshRequest);
    });
    sendRefresh(key, refreshRequest.get());
  }

  @EventListener(MetadataSnapshotPublishedEvent.class)
  public void onMetadataSnapshotPublished(MetadataSnapshotPublishedEvent event) {
    if (event == null || event.getAppName() == null) {
      return;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        event.getAppName().trim(),
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.KUBERNETES
    );
    if (!matchesRuntimeTarget(key, event.getRuntimeTarget())) {
      return;
    }
    states.computeIfPresent(key, (ignored, current) -> {
      if (!current.matchesPublication(event)) {
        return current;
      }
      return current.completeRefresh();
    });
  }

  @EventListener(MetadataSnapshotRejectedEvent.class)
  public void onMetadataSnapshotRejected(MetadataSnapshotRejectedEvent event) {
    if (event == null || event.getAppName() == null || event.getAppName().trim().isEmpty()) {
      return;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        event.getAppName().trim(),
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.KUBERNETES
    );
    if (!matchesRuntimeTarget(key, event.getRuntimeTarget())) {
      return;
    }
    AtomicReference<RefreshRequest> retryRequest = new AtomicReference<RefreshRequest>();
    states.computeIfPresent(key, (ignored, current) -> {
      if (!current.matchesRejection(event)) {
        return current;
      }
      if (current.canRetry(event.getFailureType())) {
        return current.retryRefresh(retryRequest);
      }
      return current.rejectRefresh(REFRESH_FAILED, event.getReason());
    });
    sendRefresh(key, retryRequest.get());
  }

  private Try<RuntimeInstance> activate(RuntimeSpec spec, long runtimeGeneration, RuntimeStatus status) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return Try.of(() -> {
      HelperRuntimeState state = states.compute(
          key,
          (ignored, current) -> current == null
              ? HelperRuntimeState.of(key, spec, runtimeGeneration, status, null, null)
              : current.retire(status, Long.valueOf(runtimeGeneration), spec)
      );
      try {
        apply(resourceFactory.deployment(spec, Long.valueOf(runtimeGeneration)));
      } catch (RuntimeException exception) {
        markApplyFailed(key, state, exception);
        throw exception;
      }
      return state.toRuntimeInstance();
    });
  }

  private void markApplyFailed(
      RuntimeIdentityKey key,
      HelperRuntimeState transition,
      RuntimeException exception
  ) {
    states.computeIfPresent(
        key,
        (ignored, current) -> current == transition
            ? current.withStatus(RuntimeStatus.FAILED, APPLY_FAILED, exception.getMessage())
            : current
    );
  }

  private RuntimeInstance resolveStatus(RuntimeIdentityKey key) {
    Deployment deployment = deployment(key);
    if (deployment == null) {
      return retireObservedHelper(key, RuntimeStatus.NOT_FOUND);
    }
    Integer replicas = deployment.getSpec() == null ? null : deployment.getSpec().getReplicas();
    if (replicas == null || replicas.intValue() == 0) {
      return retireObservedHelper(key, RuntimeStatus.STOPPED);
    }
    HelperRuntimeState current = states.get(key);
    RuntimeInstance synchronizedRuntime = statusSynchronizer == null ? null : statusSynchronizer.reconcile(key);
    if (current != null && current.status != RuntimeStatus.NOT_FOUND) {
      return current.toRuntimeInstance();
    }
    HelperRuntimeState observed = HelperRuntimeState.of(
        key,
        null,
        synchronizedRuntime == null || synchronizedRuntime.getRuntimeGeneration() == null
            ? 1L
            : synchronizedRuntime.getRuntimeGeneration(),
        RuntimeStatus.STARTING,
        null,
        null
    );
    states.compute(key, (ignored, existing) -> existing == null ? observed : existing.reobserve(observed));
    return states.get(key).toRuntimeInstance();
  }

  private Optional<RuntimeSpec> discoveredSpec(RuntimeIdentityKey key, Deployment deployment) {
    if (deployment == null
        || deployment.getMetadata() == null
        || deployment.getMetadata().getLabels() == null
        || deployment.getSpec() == null
        || deployment.getSpec().getTemplate() == null
        || deployment.getSpec().getTemplate().getSpec() == null
        || deployment.getSpec().getTemplate().getSpec().getContainers() == null
        || !KubernetesResourceNameFactory.labels(key).entrySet().stream().allMatch(
            entry -> Objects.equals(deployment.getMetadata().getLabels().get(entry.getKey()), entry.getValue())
        )) {
      return Optional.empty();
    }
    io.fabric8.kubernetes.api.model.Container helperContainer = deployment.getSpec()
        .getTemplate()
        .getSpec()
        .getContainers()
        .stream()
        .filter(container -> HELPER_CONTAINER_NAME.equals(container.getName()))
        .findFirst()
        .orElse(null);
    if (helperContainer == null || helperContainer.getImage() == null || helperContainer.getImage().trim().isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> env = new LinkedHashMap<String, String>();
    if (helperContainer.getEnv() != null) {
      helperContainer.getEnv().stream()
          .filter(value -> value != null && value.getName() != null && value.getValue() != null)
          .forEach(value -> env.put(value.getName(), value.getValue()));
    }
    String appIncarnationId = env.get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    if (appIncarnationId == null || appIncarnationId.trim().isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> labels = new LinkedHashMap<String, String>(deployment.getMetadata().getLabels());
    labels.put(HELPER_IMAGE_SOURCE_LABEL, HELPER_IMAGE_SOURCE_EXPLICIT);
    labels.put(HELPER_IMAGE_SOURCE_REFERENCE_LABEL, helperContainer.getImage());
    return Optional.of(RuntimeSpec.builder()
        .appName(key.getAppName())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind())
        .backendType(key.getBackendType())
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image(helperContainer.getImage())
        .env(env)
        .labels(labels)
        .build());
  }

  private RuntimeInstance retireObservedHelper(RuntimeIdentityKey key, RuntimeStatus status) {
    AtomicReference<HelperIdentity> retiredIdentity = new AtomicReference<HelperIdentity>();
    HelperRuntimeState retired = states.compute(key, (ignored, current) -> {
      if (current == null) {
        return HelperRuntimeState.of(key, null, 0L, status, null, null);
      }
      retiredIdentity.set(current.helperIdentity);
      return current.retire(status, current.runtimeGeneration, current.spec);
    });
    publishHelperRetired(key.getAppName(), retiredIdentity.get());
    return retired.toRuntimeInstance();
  }

  private void publishHelperRetired(String appName, HelperIdentity identity) {
    if (identity != null) {
      applicationEventPublisher.publishEvent(new HelperRuntimeRetiredEvent(
          appName,
          identity.getInstanceId(),
          identity.getStartedAtEpochMillis(),
          identity.getAppIncarnationId(),
          runtimeTarget(RuntimeIdentityKey.of(appName, "dev-helper", RuntimeKind.DEV_HELPER, BackendType.KUBERNETES))
      ));
    }
  }

  private HelperRuntimeState prepareRefresh(
      HelperRuntimeState current,
      AtomicReference<RefreshRequest> refreshRequest
  ) {
    if (!current.canStartRefresh()) {
      return current;
    }
    String refreshOperationId = UUID.randomUUID().toString();
    HelperRuntimeState pending = current.withPendingRefresh(refreshOperationId, 0);
    refreshRequest.set(new RefreshRequest(
        current.key,
        current.key.getAppName(),
        current.runtimeGeneration.longValue(),
        current.helperIdentity,
        refreshOperationId
    ));
    return pending;
  }

  private void sendRefresh(RuntimeIdentityKey key, RefreshRequest request) {
    if (request == null) {
      return;
    }
    try {
      applicationEventPublisher.publishEvent(new MetadataRefreshRequestedEvent(
          request.appName,
          Long.valueOf(request.runtimeGeneration),
          request.helperIdentity,
          request.refreshOperationId,
          runtimeTarget(key)
      ));
      commandService.exec(
          request.appName,
          Commands.REFRESH_METADATA.getCommand(),
          MetadataRefreshProtocol.refreshParameters(
              Long.valueOf(request.runtimeGeneration),
              request.helperIdentity,
              request.refreshOperationId
          ),
          helperReadyTimeout().toMillis()
      );
    } catch (RuntimeException exception) {
      String failureMessage = exception.getMessage() == null
          ? "发送元数据刷新命令失败"
          : exception.getMessage();
      AtomicReference<RefreshRequest> retryRequest = new AtomicReference<RefreshRequest>();
      states.computeIfPresent(key, (ignored, current) -> {
        if (!current.matchesRefreshRequest(request)) {
          return current;
        }
        if (current.canRetry(CommandFailureType.TRANSPORT)) {
          return current.retryRefresh(retryRequest);
        }
        return current.rejectRefresh(REFRESH_FAILED, failureMessage);
      });
      publishRefreshRejected(request, failureMessage, CommandFailureType.TRANSPORT);
      sendRefresh(key, retryRequest.get());
    }
  }

  private void publishRefreshRejected(
      RefreshRequest request,
      String failureMessage,
      CommandFailureType failureType
  ) {
    applicationEventPublisher.publishEvent(new MetadataSnapshotRejectedEvent(
        request.appName,
        Long.valueOf(request.runtimeGeneration),
        request.helperIdentity.getInstanceId(),
        request.helperIdentity.getStartedAtEpochMillis(),
        request.helperIdentity.getAppIncarnationId(),
        request.refreshOperationId,
        failureMessage == null || failureMessage.trim().isEmpty()
            ? "元数据刷新失败"
            : failureMessage,
        failureType,
        runtimeTarget(request.key)
    ));
  }

  private void scale(RuntimeIdentityKey key, int replicas) {
    Deployment current = deployment(key);
    if (current == null) {
      return;
    }
    apply(new DeploymentBuilder(current)
        .editSpec()
        .withReplicas(Integer.valueOf(replicas))
        .endSpec()
        .build());
  }

  private Deployment deployment(RuntimeIdentityKey key) {
    return client.apps().deployments()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .get();
  }

  private List<Pod> helperPods(RuntimeIdentityKey key) {
    List<Pod> pods = client.pods()
        .inNamespace(properties.getNamespace())
        .withLabels(KubernetesResourceNameFactory.labels(key))
        .list()
        .getItems();
    if (pods == null || pods.isEmpty()) {
      return Collections.<Pod>emptyList();
    }
    List<Pod> sortedPods = new ArrayList<Pod>(pods);
    sortedPods.sort((left, right) -> podName(left).compareTo(podName(right)));
    return sortedPods;
  }

  private List<String> readPodLogs(Pod pod) {
    String logOutput = client.pods()
        .inNamespace(properties.getNamespace())
        .withName(podName(pod))
        .inContainer(HELPER_CONTAINER_NAME)
        .getLog();
    if (logOutput == null || logOutput.trim().isEmpty()) {
      return Collections.<String>emptyList();
    }
    String[] rawLines = logOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    List<String> lines = new ArrayList<String>(rawLines.length);
    for (String rawLine : rawLines) {
      if (!rawLine.isEmpty()) {
        lines.add(rawLine);
      }
    }
    return lines;
  }

  private String podName(Pod pod) {
    return pod == null || pod.getMetadata() == null || pod.getMetadata().getName() == null
        ? ""
        : pod.getMetadata().getName();
  }

  private void apply(io.fabric8.kubernetes.api.model.HasMetadata resource) {
    client.resource(resource).createOrReplace();
  }

  private boolean isActiveHelperStatus(RuntimeStatus status) {
    return status == RuntimeStatus.RUNNING
        || status == RuntimeStatus.STARTING
        || status == RuntimeStatus.RELOADING;
  }

  private boolean matchesRuntimeTarget(RuntimeIdentityKey key, HelperRuntimeTarget runtimeTarget) {
    return runtimeTarget != null
        && BackendType.KUBERNETES.name().equals(runtimeTarget.getBackendType())
        && Objects.equals(key.getProfile(), runtimeTarget.getProfile())
        && Objects.equals(KubernetesResourceNameFactory.deploymentName(key), runtimeTarget.getRuntimeId());
  }

  private HelperRuntimeTarget runtimeTarget(RuntimeIdentityKey key) {
    return new HelperRuntimeTarget(
        BackendType.KUBERNETES.name(),
        key.getProfile(),
        KubernetesResourceNameFactory.deploymentName(key)
    );
  }

  private void trackHelperLogStreaming(RuntimeInstance runtime) {
    if (runtime == null) {
      return;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.from(runtime);
    if (!isActiveHelperStatus(runtime.getStatus())) {
      stopHelperLogStreaming(key);
      return;
    }
    helperLogStates.compute(key, (runtimeKey, state) -> {
      LogStreamState current = state == null ? new LogStreamState() : state;
      if (current.future == null || current.future.isCancelled() || current.future.isDone()) {
        current.lastSnapshot = Collections.<String>emptyList();
        current.future = helperLogPollExecutor.scheduleWithFixedDelay(
            () -> refreshHelperLogStreaming(runtimeKey),
            0L,
            HELPER_LOG_POLL_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
      }
      return current;
    });
  }

  private void stopHelperLogStreaming(RuntimeIdentityKey key) {
    LogStreamState state = helperLogStates.remove(key);
    if (state != null && state.future != null) {
      state.future.cancel(false);
    }
  }

  private long nextGeneration(RuntimeIdentityKey key, Long runtimeGeneration) {
    if (runtimeGeneration != null) {
      return runtimeGeneration.longValue();
    }
    HelperRuntimeState current = states.get(key);
    if (current == null || current.runtimeGeneration == null) {
      return 1L;
    }
    return current.runtimeGeneration.longValue() + 1L;
  }

  private boolean isTimeout(CommandResult result) {
    String message = result.getMessage();
    return message != null && (message.contains("超时") || message.toLowerCase().contains("timeout"));
  }

  private static class LogStreamState {
    private List<String> lastSnapshot = Collections.emptyList();
    private ScheduledFuture<?> future;
  }

  private Duration helperReadyTimeout() {
    return properties.getHelperReadyTimeout() == null ? Duration.ofSeconds(30) : properties.getHelperReadyTimeout();
  }

  private static class HelperRuntimeState {
    private final RuntimeIdentityKey key;
    private final RuntimeSpec spec;
    private final Long runtimeGeneration;
    private final RuntimeStatus status;
    private final String failureCode;
    private final String failureMessage;
    private final HelperIdentity helperIdentity;
    private final HelperRetirementFence helperRetirementFence;
    private final String pendingRefreshOperationId;
    private final int pendingRefreshAttempt;

    private HelperRuntimeState(
        RuntimeIdentityKey key,
        RuntimeSpec spec,
        Long runtimeGeneration,
        RuntimeStatus status,
        String failureCode,
        String failureMessage,
        HelperIdentity helperIdentity,
        HelperRetirementFence helperRetirementFence,
        String pendingRefreshOperationId,
        int pendingRefreshAttempt
    ) {
      this.key = key;
      this.spec = spec;
      this.runtimeGeneration = runtimeGeneration;
      this.status = status;
      this.failureCode = failureCode;
      this.failureMessage = failureMessage;
      this.helperIdentity = helperIdentity;
      this.helperRetirementFence = helperRetirementFence;
      this.pendingRefreshOperationId = pendingRefreshOperationId;
      this.pendingRefreshAttempt = pendingRefreshAttempt;
    }

    static HelperRuntimeState of(
        RuntimeIdentityKey key,
        RuntimeSpec spec,
        Long runtimeGeneration,
        RuntimeStatus status,
        String failureCode,
        String failureMessage
    ) {
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          status,
          failureCode,
          failureMessage,
          null,
          HelperRetirementFence.empty(),
          null,
          0
      );
    }

    HelperRuntimeState withStatus(RuntimeStatus status, String failureCode, String failureMessage) {
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          status,
          failureCode,
          failureMessage,
          helperIdentity,
          helperRetirementFence,
          pendingRefreshOperationId,
          pendingRefreshAttempt
      );
    }

    HelperRuntimeState completeRefresh() {
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          RuntimeStatus.RUNNING,
          null,
          null,
          helperIdentity,
          helperRetirementFence,
          null,
          0
      );
    }

    HelperRuntimeState retire(RuntimeStatus status, Long generation, RuntimeSpec nextSpec) {
      return new HelperRuntimeState(
          key,
          nextSpec,
          generation,
          status,
          null,
          null,
          null,
          helperRetirementFence.retire(helperIdentity),
          null,
          0
      );
    }

    HelperRuntimeState reobserve(HelperRuntimeState observed) {
      return new HelperRuntimeState(
          key,
          observed.spec,
          observed.runtimeGeneration,
          observed.status,
          observed.failureCode,
          observed.failureMessage,
          helperIdentity,
          helperRetirementFence,
          null,
          0
      );
    }

    HelperRuntimeState activate(HelperIdentity candidate) {
      if (helperRetirementFence.rejects(candidate)) {
        return this;
      }
      if (helperIdentity != null
          && helperIdentity.matches(
              candidate.getInstanceId(),
              candidate.getStartedAtEpochMillis(),
              candidate.getAppIncarnationId()
          )) {
        return this;
      }
      if (!MetadataRefreshProtocol.canActivateHelper(candidate, helperIdentity)) {
        return this;
      }
      RuntimeStatus nextStatus = status == RuntimeStatus.RUNNING
          || status == RuntimeStatus.FAILED && !APPLY_FAILED.equals(failureCode)
              ? RuntimeStatus.STARTING
              : status;
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          nextStatus,
          null,
          null,
          candidate,
          helperRetirementFence.retire(helperIdentity),
          null,
          0
      );
    }

    boolean canStartRefresh() {
      return (status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING)
          && runtimeGeneration != null
          && helperIdentity != null
          && pendingRefreshOperationId == null;
    }

    boolean isAwaitingRefresh() {
      return status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING;
    }

    HelperRuntimeState withPendingRefresh(String refreshOperationId, int attempt) {
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          status,
          failureCode,
          failureMessage,
          helperIdentity,
          helperRetirementFence,
          refreshOperationId,
          attempt
      );
    }

    boolean canRetry(CommandFailureType failureType) {
      return (failureType == CommandFailureType.TRANSPORT
          || failureType == CommandFailureType.RESPONSE_TIMEOUT)
          && pendingRefreshAttempt < MAX_METADATA_REFRESH_RETRIES;
    }

    HelperRuntimeState retryRefresh(AtomicReference<RefreshRequest> retryRequest) {
      String refreshOperationId = UUID.randomUUID().toString();
      retryRequest.set(new RefreshRequest(
          key,
          key.getAppName(),
          runtimeGeneration.longValue(),
          helperIdentity,
          refreshOperationId
      ));
      return withPendingRefresh(refreshOperationId, pendingRefreshAttempt + 1);
    }

    RefreshRequest currentRefreshRequest() {
      return new RefreshRequest(
          key,
          key.getAppName(),
          runtimeGeneration.longValue(),
          helperIdentity,
          pendingRefreshOperationId
      );
    }

    boolean matchesRefreshResult(CommandResult result) {
      return helperIdentity != null
          && helperIdentity.matches(
              result.getHelperInstanceId(),
              result.getHelperStartedAtEpochMillis() == null
                  ? 0L
                  : result.getHelperStartedAtEpochMillis().longValue(),
              result.getAppIncarnationId()
          )
          && matches(result.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, result.getRefreshOperationId());
    }

    boolean matchesPublication(MetadataSnapshotPublishedEvent event) {
      return helperIdentity != null
          && helperIdentity.matches(
              event.getHelperInstanceId(),
              event.getHelperStartedAtEpochMillis(),
              event.getAppIncarnationId()
          )
          && matches(event.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, event.getRefreshOperationId())
          && (status == RuntimeStatus.STARTING
              || status == RuntimeStatus.RELOADING
              || status == RuntimeStatus.FAILED);
    }

    boolean matchesRejection(MetadataSnapshotRejectedEvent event) {
      return helperIdentity != null
          && helperIdentity.matches(
              event.getHelperInstanceId(),
              event.getHelperStartedAtEpochMillis(),
              event.getAppIncarnationId()
          )
          && matches(event.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, event.getRefreshOperationId())
          && (status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING);
    }

    boolean matchesRefreshRequest(RefreshRequest request) {
      return request != null
          && helperIdentity != null
          && helperIdentity.matches(
              request.helperIdentity.getInstanceId(),
              request.helperIdentity.getStartedAtEpochMillis(),
              request.helperIdentity.getAppIncarnationId()
          )
          && Objects.equals(runtimeGeneration, Long.valueOf(request.runtimeGeneration))
          && Objects.equals(pendingRefreshOperationId, request.refreshOperationId)
          && (status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING);
    }

    HelperRuntimeState rejectRefresh(String failureCode, String failureMessage) {
      return new HelperRuntimeState(
          key,
          spec,
          runtimeGeneration,
          RuntimeStatus.FAILED,
          failureCode,
          failureMessage,
          helperIdentity,
          helperRetirementFence,
          null,
          0
      );
    }

    boolean matches(Long generation) {
      return Objects.equals(runtimeGeneration, generation);
    }

    RuntimeInstance toRuntimeInstance() {
      Map<String, String> backendMetadata = new LinkedHashMap<String, String>();
      backendMetadata.put("containerName", KubernetesResourceNameFactory.deploymentName(key));
      backendMetadata.put("deployment", KubernetesResourceNameFactory.deploymentName(key));
      if (failureCode != null) {
        backendMetadata.put(FAILURE_CODE, failureCode);
      }
      if (failureMessage != null) {
        backendMetadata.put(FAILURE_MESSAGE, failureMessage);
      }
      return RuntimeInstance.builder()
          .runtimeId(KubernetesResourceNameFactory.deploymentName(key))
          .appName(key.getAppName())
          .profile(key.getProfile())
          .runtimeKind(key.getRuntimeKind())
          .backendType(key.getBackendType())
          .runtimeGeneration(runtimeGeneration)
          .status(status)
          .workspaceRef(spec == null ? null : spec.getWorkspaceRef())
          .capabilities(RuntimeCapability.builder()
              .supportsPause(false)
              .supportsDebugPort(false)
              .supportsWorkspaceHotReload(true)
              .supportsNodePortExposure(false)
              .supportsIngressExposure(false)
              .build())
          .backendMetadata(backendMetadata)
          .build();
    }
  }

  private static final class RefreshRequest {
    private final RuntimeIdentityKey key;
    private final String appName;
    private final long runtimeGeneration;
    private final HelperIdentity helperIdentity;
    private final String refreshOperationId;

    private RefreshRequest(
        RuntimeIdentityKey key,
        String appName,
        long runtimeGeneration,
        HelperIdentity helperIdentity,
        String refreshOperationId
    ) {
      this.key = key;
      this.appName = appName;
      this.runtimeGeneration = runtimeGeneration;
      this.helperIdentity = helperIdentity;
      this.refreshOperationId = refreshOperationId;
    }
  }
}
