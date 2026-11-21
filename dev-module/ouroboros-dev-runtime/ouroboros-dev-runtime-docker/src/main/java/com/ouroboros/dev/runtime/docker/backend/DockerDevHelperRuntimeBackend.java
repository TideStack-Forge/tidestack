package com.ouroboros.dev.runtime.docker.backend;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vavr.control.Try;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

public class DockerDevHelperRuntimeBackend implements RuntimeBackend {
  private static final Logger LOGGER = LoggerFactory.getLogger(DockerDevHelperRuntimeBackend.class);
  private static final Integer HELPER_LOG_TAIL_LINES = Integer.valueOf(200);
  private static final long HELPER_LOG_POLL_INTERVAL_SECONDS = 1L;
  private static final String HELPER_IMAGE_SOURCE_LABEL = "ouroboros.dev/helper-image-source";
  private static final String HELPER_IMAGE_SOURCE_REFERENCE_LABEL = "ouroboros.dev/helper-image-source-reference";
  private static final String HELPER_IMAGE_SOURCE_EXPLICIT = "explicit";
  private static final String FAILURE_CODE = "failureCode";
  private static final String FAILURE_MESSAGE = "failureMessage";
  private static final String REFRESH_FAILED = "HELPER_REFRESH_FAILED";
  private static final ApplicationEventPublisher NOOP_APPLICATION_EVENT_PUBLISHER = event -> {
  };

  private final ContainerService containerService;
  private final DevRuntimeEventPublisher eventPublisher;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final DockerRuntimeGenerationStore runtimeGenerationStore = new DockerRuntimeGenerationStore();
  private final ScheduledExecutorService helperLogPollExecutor;
  private final ConcurrentMap<RuntimeIdentityKey, LogStreamState> helperLogStates = new ConcurrentHashMap<RuntimeIdentityKey, LogStreamState>();
  private final ConcurrentMap<String, HelperRuntimeCompletion> helperRuntimeCompletions =
      new ConcurrentHashMap<String, HelperRuntimeCompletion>();
  private final ConcurrentMap<RuntimeIdentityKey, Long> runtimeGenerations = new ConcurrentHashMap<RuntimeIdentityKey, Long>();
  private final ConcurrentMap<String, HelperIdentity> helperIdentities = new ConcurrentHashMap<String, HelperIdentity>();
  private final ConcurrentMap<String, HelperRuntimeTarget> helperRuntimeTargets =
      new ConcurrentHashMap<String, HelperRuntimeTarget>();
  private final ConcurrentMap<String, HelperRetirementFence> helperRetirementFences =
      new ConcurrentHashMap<String, HelperRetirementFence>();
  private final ConcurrentMap<RuntimeIdentityKey, String> pendingRefreshOperations =
      new ConcurrentHashMap<RuntimeIdentityKey, String>();
  private final Object helperLifecycleMonitor = new Object();

  public DockerDevHelperRuntimeBackend(ContainerService containerService) {
    this(containerService, DevRuntimeEventPublisher.noop(), NOOP_APPLICATION_EVENT_PUBLISHER);
  }

  public DockerDevHelperRuntimeBackend(ContainerService containerService,
                                       DevRuntimeEventPublisher eventPublisher) {
    this(containerService, eventPublisher, NOOP_APPLICATION_EVENT_PUBLISHER);
  }

  public DockerDevHelperRuntimeBackend(
      ContainerService containerService,
      DevRuntimeEventPublisher eventPublisher,
      ApplicationEventPublisher applicationEventPublisher
  ) {
    this.containerService = containerService;
    this.eventPublisher = eventPublisher == null ? DevRuntimeEventPublisher.noop() : eventPublisher;
    this.applicationEventPublisher = applicationEventPublisher == null
        ? NOOP_APPLICATION_EVENT_PUBLISHER
        : applicationEventPublisher;
    this.helperLogPollExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      private final AtomicInteger sequence = new AtomicInteger();

      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "docker-dev-helper-log-poller-" + sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      }
    });
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.DOCKER;
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.DEV_HELPER;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.DOCKER && spec.getRuntimeKind() == RuntimeKind.DEV_HELPER;
  }

  @Override
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    Container container = buildContainer(spec);
    retireActiveHelper(spec.getAppName());
    runtimeGenerations.compute(key, (ignored, current) -> current);
    helperRuntimeCompletions.remove(spec.getAppName());
    Try<RuntimeInstance> reused = reuseExistingContainer(spec);
    if (reused != null) {
      return reused.map(instance -> {
        trackHelperLogStreaming(instance);
        return instance;
      });
    }
    return containerService.create(container)
        .fold(Try::failure, id -> {
          container.setId(id);
          return Try.success(DockerRuntimeMapper.toRuntime(container, RuntimeKind.DEV_HELPER, null, RuntimeStatus.STOPPED));
        });
  }

  @Override
  public Try<RuntimeInstance> start(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    retireActiveHelper(spec.getAppName());
    helperRuntimeCompletions.remove(spec.getAppName());
    return reserveNextRuntimeGeneration(spec).flatMap(runtimeGeneration -> containerService.start(containerName(spec))
        .fold(Try::failure, value -> inspect(spec.getAppName(), containerName(spec), null, runtimeGeneration)
            .map(instance -> {
              trackHelperLogStreaming(instance);
              return instance;
            })));
  }

  @Override
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
    retireActiveHelper(key.getAppName());
    runtimeGenerations.compute(key, (ignored, current) -> current);
    helperRuntimeCompletions.remove(key.getAppName());
    stopHelperLogStreaming(key);
    return containerService.stop(containerName(key))
        .fold(Try::failure, value -> inspect(
            key.getAppName(),
            containerName(key),
            RuntimeStatus.STOPPED,
            runtimeGenerations.get(key)
        ));
  }

  @Override
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration) {
    retireActiveHelper(spec.getAppName());
    helperRuntimeCompletions.remove(spec.getAppName());
    return persistRuntimeGeneration(spec, Long.valueOf(runtimeGeneration))
        .flatMap(effectiveGeneration -> containerService.restart(containerName(spec))
            .fold(Try::failure, value -> inspect(spec.getAppName(), containerName(spec), null, effectiveGeneration)
                .map(instance -> {
                  trackHelperLogStreaming(instance);
                  return instance;
                })));
  }

  @Override
  public Try<Void> delete(RuntimeIdentityKey key) {
    retireActiveHelper(key.getAppName());
    runtimeGenerations.remove(key);
    helperRuntimeCompletions.remove(key.getAppName());
    stopHelperLogStreaming(key);
    return containerService.delete(containerName(key)).fold(Try::failure, Try::success);
  }

  @Override
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration) {
    return persistRuntimeGeneration(spec, Long.valueOf(runtimeGeneration))
        .flatMap(effectiveGeneration -> inspect(spec.getAppName(), containerName(spec), null, effectiveGeneration)
            .map(instance -> {
              trackHelperLogStreaming(instance);
              return instance;
            }));
  }

  @Override
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return inspect(key.getAppName(), containerName(key), null, runtimeGenerations.get(key))
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
        BackendType.DOCKER
    );
    return containerService.getContainerInfoByName(containerName(key))
        .fold(
            error -> isContainerMissing(error) ? Try.success(Optional.empty()) : Try.failure(error),
            container -> Try.success(discoveredSpec(normalizedAppName, container))
        );
  }

  @Override
  public Try<List<String>> listImages(String reference) {
    return containerService.getAllImageNames(reference).fold(Try::failure, Try::success);
  }

  @Override
  public Try<List<String>> getLogs(RuntimeIdentityKey key) {
    return containerService.getLogs(containerName(key), HELPER_LOG_TAIL_LINES)
        .fold(error -> isContainerMissing(error) ? Try.success(Collections.<String>emptyList()) : Try.failure(error), Try::success);
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
    Try<RuntimeInstance> runtimeResult = inspect(
        key.getAppName(),
        containerName(key),
        null,
        runtimeGenerations.get(key)
    );
    if (runtimeResult.isFailure()) {
      stopHelperLogStreaming(key);
      return;
    }
    trackHelperLogStreaming(runtimeResult.get());
    RuntimeInstance runtime = runtimeResult.get();
    if (!isActiveHelperStatus(runtime.getStatus())) {
      stopHelperLogStreaming(key);
      return;
    }
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
        state.lastSnapshot = new java.util.ArrayList<String>(logs);
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

  private Try<RuntimeInstance> inspect(String appName, String containerName, RuntimeStatus overrideStatus) {
    return inspect(appName, containerName, overrideStatus, null);
  }

  private Try<RuntimeInstance> inspect(String appName, String containerName, RuntimeStatus overrideStatus, Long runtimeGeneration) {
    return containerService.getContainerInfoByName(containerName)
        .fold(error -> {
          if (isContainerMissing(error)) {
            retireActiveHelper(appName);
            return Try.success(RuntimeInstance.builder()
                .runtimeId(null)
                .runtimeKind(RuntimeKind.DEV_HELPER)
                .backendType(BackendType.DOCKER)
                .appName(appName)
                .profile("dev-helper")
                .runtimeGeneration(runtimeGeneration)
                .status(RuntimeStatus.NOT_FOUND)
                .build());
          }
          return Try.failure(error);
        }, container -> {
          RuntimeIdentityKey key = RuntimeIdentityKey.of(
              appName,
              "dev-helper",
              RuntimeKind.DEV_HELPER,
              BackendType.DOCKER
          );
          return resolveRuntimeGeneration(key, container, runtimeGeneration).map(resolvedGeneration -> {
            RuntimeStatus helperStatus = overrideStatus == null ? helperStatus(appName, container) : overrideStatus;
            if (!isActiveHelperStatus(helperStatus)) {
              retireActiveHelper(appName);
            }
            RuntimeInstance runtime = DockerRuntimeMapper.toRuntime(
                container,
                RuntimeKind.DEV_HELPER,
                resolvedGeneration,
                helperStatus
            );
            return withCompletionMetadata(runtime, helperRuntimeCompletions.get(appName));
          });
        });
  }

  private boolean isContainerMissing(Throwable error) {
    if (!(error instanceof PlatformException)) {
      return false;
    }
    Throwable cause = error.getCause();
    String message = cause == null ? error.getMessage() : cause.getMessage();
    return message != null && message.contains("No such container");
  }

  private RuntimeStatus helperStatus(String appName, Container container) {
    if (container.getStatus() == ContainerStatus.RUNNING) {
      HelperRuntimeCompletion completion = helperRuntimeCompletions.get(appName);
      return completion != null && completion.matches(container.getId())
          ? completion.status
          : RuntimeStatus.STARTING;
    }
    return DockerRuntimeMapper.toRuntimeStatus(container.getStatus());
  }

  private RuntimeInstance withCompletionMetadata(
      RuntimeInstance runtime,
      HelperRuntimeCompletion completion
  ) {
    if (runtime == null
        || completion == null
        || completion.status != RuntimeStatus.FAILED
        || !completion.matches(runtime.getRuntimeId())) {
      return runtime;
    }
    Map<String, String> backendMetadata = new LinkedHashMap<String, String>(runtime.getBackendMetadata());
    backendMetadata.put(FAILURE_CODE, REFRESH_FAILED);
    backendMetadata.put(FAILURE_MESSAGE, completion.failureMessage);
    return RuntimeInstance.builder()
        .runtimeId(runtime.getRuntimeId())
        .appName(runtime.getAppName())
        .profile(runtime.getProfile())
        .runtimeKind(runtime.getRuntimeKind())
        .backendType(runtime.getBackendType())
        .runtimeGeneration(runtime.getRuntimeGeneration())
        .status(runtime.getStatus())
        .webEndpoint(runtime.getWebEndpoint())
        .apiEndpoint(runtime.getApiEndpoint())
        .debugEndpoint(runtime.getDebugEndpoint())
        .workspaceRef(runtime.getWorkspaceRef())
        .accessRef(runtime.getAccessRef())
        .capabilities(runtime.getCapabilities())
        .backendMetadata(backendMetadata)
        .build();
  }

  @EventListener(HelperRuntimeActivatedEvent.class)
  public void onHelperRuntimeActivated(HelperRuntimeActivatedEvent event) {
    if (event == null
        || event.getAppName() == null
        || event.getAppName().trim().isEmpty()
        || event.getHelperIdentity() == null
        || !isDockerHelperTarget(event.getRuntimeTarget())) {
      return;
    }
    String appName = event.getAppName().trim();
    HelperIdentity candidate = event.getHelperIdentity();
    HelperRuntimeTarget runtimeTarget = event.getRuntimeTarget();
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        appName,
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.DOCKER
    );
    if (!matchesCurrentContainerId(containerName(key), runtimeTarget.getRuntimeId())) {
      return;
    }
    synchronized (helperLifecycleMonitor) {
      HelperIdentity current = helperIdentities.get(appName);
      if (helperRetirementFences.getOrDefault(appName, HelperRetirementFence.empty()).rejects(candidate)
          || current != null
              && current.matches(
                  candidate.getInstanceId(),
                  candidate.getStartedAtEpochMillis(),
                  candidate.getAppIncarnationId()
              )) {
        return;
      }
      if (!MetadataRefreshProtocol.canActivateHelper(candidate, current)) {
        return;
      }
      retireHelperIdentity(appName, current);
      helperIdentities.put(appName, candidate);
      helperRuntimeTargets.put(appName, runtimeTarget);
      helperRuntimeCompletions.remove(appName);
    }
  }

  @EventListener(MetadataRefreshRequestedEvent.class)
  public void onMetadataRefreshRequested(MetadataRefreshRequestedEvent event) {
    if (event == null
        || event.getAppName() == null
        || event.getAppName().trim().isEmpty()
        || event.getHelperIdentity() == null
        || event.getRefreshOperationId() == null
        || event.getRefreshOperationId().trim().isEmpty()
        || !isDockerHelperTarget(event.getRuntimeTarget())) {
      return;
    }
    String appName = event.getAppName().trim();
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        appName,
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.DOCKER
    );
    synchronized (helperLifecycleMonitor) {
      if (!matchesCurrentRefresh(
          key,
          event.getHelperIdentity(),
          event.getRuntimeGeneration(),
          event.getRuntimeTarget()
      )) {
        return;
      }
      pendingRefreshOperations.put(key, event.getRefreshOperationId());
      helperRuntimeCompletions.remove(appName);
    }
  }

  @EventListener(MetadataSnapshotPublishedEvent.class)
  public void onMetadataSnapshotPublished(MetadataSnapshotPublishedEvent event) {
    if (event == null || event.getAppName() == null) {
      return;
    }
    String appName = event.getAppName().trim();
    RuntimeIdentityKey key = RuntimeIdentityKey.of(appName, "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER);
    synchronized (helperLifecycleMonitor) {
      if (!matchesCurrentPublication(key, event)) {
        return;
      }
    }
    inspect(appName, containerName(key), null, event.getRuntimeGeneration())
        .onSuccess(runtime -> {
          if (runtime.getRuntimeId() == null
              || runtime.getStatus() != RuntimeStatus.STARTING
                  && runtime.getStatus() != RuntimeStatus.RUNNING) {
            return;
          }
          synchronized (helperLifecycleMonitor) {
            if (!matchesCurrentPublication(key, event)
                || !isCurrentRunningContainer(containerName(key), event.getRuntimeTarget().getRuntimeId())) {
              retireActiveHelper(appName);
              return;
            }
            helperRuntimeCompletions.put(
                appName,
                HelperRuntimeCompletion.ready(event.getRuntimeTarget().getRuntimeId())
            );
            pendingRefreshOperations.remove(key, event.getRefreshOperationId());
            if (event.getRuntimeGeneration() != null) {
              runtimeGenerations.put(key, event.getRuntimeGeneration());
            }
            eventPublisher.publish(DevRuntimeEvent.builder()
                .type(DevRuntimeEventType.RUNTIME_STATUS_CHANGED)
                .runtimeId(runtime.getRuntimeId())
                .appName(runtime.getAppName())
                .profile(runtime.getProfile())
                .runtimeKind(runtime.getRuntimeKind())
                .backendType(runtime.getBackendType())
                .runtimeGeneration(event.getRuntimeGeneration())
                .status(RuntimeStatus.RUNNING)
                .backendMetadata(runtime.getBackendMetadata())
                .build());
          }
        });
  }

  @EventListener(MetadataSnapshotRejectedEvent.class)
  public void onMetadataSnapshotRejected(MetadataSnapshotRejectedEvent event) {
    if (event == null || event.getAppName() == null || event.getAppName().trim().isEmpty()) {
      return;
    }
    if (!isDockerHelperTarget(event.getRuntimeTarget())) {
      return;
    }
    String appName = event.getAppName().trim();
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        appName,
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.DOCKER
    );
    synchronized (helperLifecycleMonitor) {
      if (matchesCurrentRefresh(
          key,
          new HelperIdentity(
              event.getHelperInstanceId(),
              event.getHelperStartedAtEpochMillis(),
              event.getAppIncarnationId()
          ),
          event.getRuntimeGeneration(),
          event.getRuntimeTarget()
      ) && pendingRefreshOperations.remove(key, event.getRefreshOperationId())) {
        helperRuntimeCompletions.put(
            appName,
            HelperRuntimeCompletion.failed(event.getRuntimeTarget().getRuntimeId(), event.getReason())
        );
      }
    }
  }

  private boolean matchesCurrentPublication(RuntimeIdentityKey key, MetadataSnapshotPublishedEvent event) {
    Long currentGeneration = runtimeGenerations.get(key);
    HelperIdentity identity = helperIdentities.get(event.getAppName().trim());
    HelperRuntimeTarget runtimeTarget = helperRuntimeTargets.get(event.getAppName().trim());
    Long publishedGeneration = event.getRuntimeGeneration();
    return identity != null
        && identity.matches(
            event.getHelperInstanceId(),
            event.getHelperStartedAtEpochMillis(),
            event.getAppIncarnationId()
        )
        && java.util.Objects.equals(currentGeneration, publishedGeneration)
        && java.util.Objects.equals(runtimeTarget, event.getRuntimeTarget())
        && java.util.Objects.equals(pendingRefreshOperations.get(key), event.getRefreshOperationId());
  }

  private boolean matchesCurrentRefresh(
      RuntimeIdentityKey key,
      HelperIdentity candidate,
      Long runtimeGeneration,
      HelperRuntimeTarget runtimeTarget
  ) {
    HelperIdentity current = helperIdentities.get(key.getAppName());
    return current != null
        && candidate != null
        && current.matches(
            candidate.getInstanceId(),
            candidate.getStartedAtEpochMillis(),
            candidate.getAppIncarnationId()
        )
        && java.util.Objects.equals(helperRuntimeTargets.get(key.getAppName()), runtimeTarget)
        && adoptRecoveredGeneration(key, runtimeGeneration);
  }

  private boolean adoptRecoveredGeneration(RuntimeIdentityKey key, Long runtimeGeneration) {
    if (runtimeGeneration == null) {
      return false;
    }
    Long currentGeneration = runtimeGenerations.get(key);
    if (java.util.Objects.equals(currentGeneration, runtimeGeneration)) {
      return true;
    }
    if (currentGeneration != null && runtimeGeneration.longValue() < currentGeneration.longValue()) {
      return false;
    }
    Try<Boolean> adoption = containerService.getContainerInfoByName(containerName(key))
        .fold(Try::failure, container -> runtimeGenerationStore.read(container).flatMap(persisted -> {
          Long knownGeneration = highestGeneration(runtimeGenerations.get(key), persisted.orElse(null));
          if (knownGeneration != null && runtimeGeneration.longValue() < knownGeneration.longValue()) {
            return Try.success(Boolean.FALSE);
          }
          return runtimeGenerationStore.write(container, runtimeGeneration.longValue()).map(ignored -> {
            runtimeGenerations.put(key, runtimeGeneration);
            return Boolean.TRUE;
          });
        }));
    if (adoption.isFailure()) {
      LOGGER.warn("Unable to persist recovered Docker runtime generation for app: {}", key.getAppName(), adoption.getCause());
      return false;
    }
    return adoption.get().booleanValue();
  }

  private boolean isCurrentRunningContainer(String name, String runtimeId) {
    return containerService.getContainerInfoByName(name)
        .fold(error -> false, container -> container.getStatus() == ContainerStatus.RUNNING
            && java.util.Objects.equals(runtimeId, container.getId()));
  }

  private boolean matchesCurrentContainerId(String name, String runtimeId) {
    return containerService.getContainerInfoByName(name)
        .fold(error -> false, container -> java.util.Objects.equals(runtimeId, container.getId()));
  }

  private boolean isDockerHelperTarget(HelperRuntimeTarget runtimeTarget) {
    return runtimeTarget != null
        && BackendType.DOCKER.name().equals(runtimeTarget.getBackendType())
        && "dev-helper".equals(runtimeTarget.getProfile())
        && runtimeTarget.getRuntimeId() != null
        && !runtimeTarget.getRuntimeId().trim().isEmpty();
  }

  private void retireActiveHelper(String appName) {
    HelperIdentity retiredIdentity;
    HelperRuntimeTarget retiredRuntimeTarget;
    synchronized (helperLifecycleMonitor) {
      retiredIdentity = helperIdentities.remove(appName);
      retiredRuntimeTarget = helperRuntimeTargets.remove(appName);
      retireHelperIdentity(appName, retiredIdentity);
      pendingRefreshOperations.remove(RuntimeIdentityKey.of(
          appName,
          "dev-helper",
          RuntimeKind.DEV_HELPER,
          BackendType.DOCKER
      ));
      helperRuntimeCompletions.remove(appName);
    }
    if (retiredIdentity != null && retiredRuntimeTarget != null) {
      applicationEventPublisher.publishEvent(new HelperRuntimeRetiredEvent(
          appName,
          retiredIdentity.getInstanceId(),
          retiredIdentity.getStartedAtEpochMillis(),
          retiredIdentity.getAppIncarnationId(),
          retiredRuntimeTarget
      ));
    }
  }

  private void retireHelperIdentity(String appName, HelperIdentity identity) {
    if (identity != null) {
      helperRetirementFences.compute(
          appName,
          (ignored, current) -> (current == null ? HelperRetirementFence.empty() : current).retire(identity)
      );
    }
  }

  private Try<RuntimeInstance> reuseExistingContainer(RuntimeSpec spec) {
    return containerService.getContainerInfoByName(containerName(spec))
        .fold(error -> null, container -> {
          if (!matchesSpec(spec, container)) {
            return Try.failure(new IllegalStateException("dev helper container name conflict"));
          }
          RuntimeStatus status = container.getStatus() == ContainerStatus.RUNNING
              ? helperStatus(spec.getAppName(), container)
              : RuntimeStatus.STOPPED;
          RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
          return resolveRuntimeGeneration(key, container, null)
              .map(runtimeGeneration -> DockerRuntimeMapper.toRuntime(
                  container,
                  RuntimeKind.DEV_HELPER,
                  runtimeGeneration,
                  status
              ));
        });
  }

  private boolean matchesSpec(RuntimeSpec spec, Container container) {
    if (container == null || container.getLabels() == null) {
      return false;
    }
    String helperLabel = container.getLabels().get(DockerRuntimeMapper.DEV_HELPER_LABEL);
    String appNameLabel = container.getLabels().get(DockerRuntimeMapper.APP_NAME_LABEL);
    String expectedAppIncarnationId = spec.getEnv() == null
        ? null
        : spec.getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    String actualAppIncarnationId = container.getEnvironmentalVariables() == null
        ? null
        : container.getEnvironmentalVariables().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    return spec.getAppName().equals(helperLabel)
        && spec.getAppName().equals(appNameLabel)
        && expectedAppIncarnationId != null
        && !expectedAppIncarnationId.trim().isEmpty()
        && expectedAppIncarnationId.equals(actualAppIncarnationId);
  }

  private Optional<RuntimeSpec> discoveredSpec(String appName, Container container) {
    if (container == null
        || container.getLabels() == null
        || container.getEnvironmentalVariables() == null
        || container.getImage() == null
        || container.getImage().trim().isEmpty()
        || !appName.equals(container.getLabels().get(DockerRuntimeMapper.DEV_HELPER_LABEL))
        || !appName.equals(container.getLabels().get(DockerRuntimeMapper.APP_NAME_LABEL))) {
      return Optional.empty();
    }
    String appIncarnationId = container.getEnvironmentalVariables().get(
        MetadataRefreshProtocol.APP_INCARNATION_ID_ENV
    );
    String imageSource = container.getLabels().get(HELPER_IMAGE_SOURCE_LABEL);
    if (appIncarnationId == null
        || appIncarnationId.trim().isEmpty()
        || imageSource == null
        || imageSource.trim().isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> labels = new LinkedHashMap<String, String>(container.getLabels());
    if (HELPER_IMAGE_SOURCE_EXPLICIT.equals(imageSource)
        && !labels.containsKey(HELPER_IMAGE_SOURCE_REFERENCE_LABEL)) {
      labels.put(HELPER_IMAGE_SOURCE_REFERENCE_LABEL, container.getImage());
    }
    return Optional.of(RuntimeSpec.builder()
        .appName(appName)
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image(container.getImage())
        .env(container.getEnvironmentalVariables())
        .labels(labels)
        .workspaceRef(discoveredWorkspace(container))
        .network(container.getNetwork())
        .build());
  }

  private WorkspaceRef discoveredWorkspace(Container container) {
    if (container.getVolumeBindings() == null) {
      return null;
    }
    String hostPath = container.getVolumeBindings().get(DockerRuntimeGenerationStore.CONTAINER_WORKSPACE_PATH);
    return hostPath == null || hostPath.trim().isEmpty()
        ? null
        : WorkspaceRef.builder().workspaceMode(WorkspaceMode.HOST_PATH).hostPath(hostPath).build();
  }

  private Try<Long> reserveNextRuntimeGeneration(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return containerService.getContainerInfoByName(containerName(spec))
        .fold(Try::failure, container -> runtimeGenerationStore.read(container).flatMap(persisted -> {
          Long knownGeneration = highestGeneration(runtimeGenerations.get(key), persisted.orElse(null));
          long nextGeneration = knownGeneration == null ? 1L : knownGeneration.longValue() + 1L;
          return runtimeGenerationStore.write(container, nextGeneration).map(ignored -> {
            Long reserved = Long.valueOf(nextGeneration);
            runtimeGenerations.put(key, reserved);
            return reserved;
          });
        }));
  }

  private Try<Long> persistRuntimeGeneration(RuntimeSpec spec, Long requestedGeneration) {
    if (requestedGeneration == null) {
      return Try.failure(new IllegalArgumentException("runtimeGeneration is required"));
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return containerService.getContainerInfoByName(containerName(spec))
        .fold(Try::failure, container -> runtimeGenerationStore.read(container).flatMap(persisted -> {
          Long effectiveGeneration = highestGeneration(
              requestedGeneration,
              highestGeneration(runtimeGenerations.get(key), persisted.orElse(null))
          );
          return runtimeGenerationStore.write(container, effectiveGeneration.longValue()).map(ignored -> {
            runtimeGenerations.put(key, effectiveGeneration);
            return effectiveGeneration;
          });
        }));
  }

  private Try<Long> resolveRuntimeGeneration(
      RuntimeIdentityKey key,
      Container container,
      Long observedGeneration
  ) {
    return runtimeGenerationStore.read(container).map(persisted -> {
      Long resolvedGeneration = highestGeneration(
          observedGeneration,
          highestGeneration(runtimeGenerations.get(key), persisted.orElse(null))
      );
      if (resolvedGeneration != null) {
        runtimeGenerations.put(key, resolvedGeneration);
      }
      return resolvedGeneration;
    });
  }

  private Long highestGeneration(Long left, Long right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return left.longValue() >= right.longValue() ? left : right;
  }

  private Container buildContainer(RuntimeSpec spec) {
    Container container = new Container();
    container.setName(containerName(spec));
    container.setImage(spec.getImage());
    container.setEnvironmentalVariables(spec.getEnv());
    container.setLabels(labels(spec));
    container.setVolumeBindings(volumeBindings(spec));
    container.setNetwork(spec.getNetwork());
    return container;
  }

  private Map<String, String> labels(RuntimeSpec spec) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    if (spec.getLabels() != null) {
      labels.putAll(spec.getLabels());
    }
    labels.put(DockerRuntimeMapper.DEV_HELPER_LABEL, spec.getAppName());
    labels.put(DockerRuntimeMapper.APP_NAME_LABEL, spec.getAppName());
    return labels;
  }

  private Map<String, String> volumeBindings(RuntimeSpec spec) {
    if (spec.getWorkspaceRef() == null || spec.getWorkspaceRef().getHostPath() == null) {
      return null;
    }
    Map<String, String> bindings = new LinkedHashMap<String, String>();
    bindings.put("/app/deploy", spec.getWorkspaceRef().getHostPath());
    return bindings;
  }

  private String containerName(RuntimeSpec spec) {
    return "ouroboros-dev-helper-" + spec.getAppName();
  }

  private String containerName(RuntimeIdentityKey key) {
    return "ouroboros-dev-helper-" + key.getAppName();
  }

  private boolean isActiveHelperStatus(RuntimeStatus status) {
    return status == RuntimeStatus.RUNNING
        || status == RuntimeStatus.STARTING
        || status == RuntimeStatus.RELOADING
        || status == RuntimeStatus.FAILED;
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

  private static final class HelperRuntimeCompletion {
    private final String runtimeId;
    private final RuntimeStatus status;
    private final String failureMessage;

    private HelperRuntimeCompletion(String runtimeId, RuntimeStatus status, String failureMessage) {
      this.runtimeId = runtimeId;
      this.status = status;
      this.failureMessage = failureMessage;
    }

    static HelperRuntimeCompletion ready(String runtimeId) {
      return new HelperRuntimeCompletion(runtimeId, RuntimeStatus.RUNNING, null);
    }

    static HelperRuntimeCompletion failed(String runtimeId, String failureMessage) {
      String message = failureMessage == null || failureMessage.trim().isEmpty()
          ? "元数据刷新失败"
          : failureMessage;
      return new HelperRuntimeCompletion(runtimeId, RuntimeStatus.FAILED, message);
    }

    boolean matches(String candidateRuntimeId) {
      return Objects.equals(runtimeId, candidateRuntimeId);
    }
  }

  private static class LogStreamState {
    private List<String> lastSnapshot = Collections.emptyList();
    private ScheduledFuture<?> future;
  }
}
