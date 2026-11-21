package com.ouroboros.dev.app.service.impl;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

/** Owns refresh requests, retries, generation fences and their observable status. */
final class DevHelperRefreshSessions {
  private static final int MAX_METADATA_REFRESH_RETRIES = 1;
  private final ConcurrentMap<String, ContainerStatus> helperStatusOverrides = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LocalDateTime> lastLoadTimes = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, HelperLifecycleState> helperLifecycles = new ConcurrentHashMap<>();
  private final Object helperLifecycleMonitor = new Object();
  record RetiredHelper(HelperIdentity identity, HelperRuntimeTarget target) {}
  Long activationEpoch(String appName) {
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.getOrDefault(appName, HelperLifecycleState.empty());
      return current.isAcceptingActivations() ? Long.valueOf(current.getActivationEpoch()) : null;
    }
  }

  RefreshFailureDecision failOrRetry(
      String appName,
      CommandFailureType failureType,
      Predicate<HelperLifecycleState> matcher
  ) {
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.get(appName);
      if (current == null || !matcher.test(current)) {
        return RefreshFailureDecision.unmatched();
      }
      RefreshRequest failed = current.toRefreshRequest(appName);
      if (isRetryableRefreshFailure(failureType)
          && current.pendingRefreshAttempt < MAX_METADATA_REFRESH_RETRIES) {
        RefreshRequest retry = failed.retry();
        helperLifecycles.put(
            appName,
            current.withPendingRefresh(
                retry.runtimeGeneration,
                retry.refreshOperationId,
                retry.attempt
            )
        );
        return RefreshFailureDecision.retry(failed, retry);
      }
      helperLifecycles.put(appName, current.clearPendingRefresh());
      helperStatusOverrides.put(appName, ContainerStatus.UNKNOWN);
      return RefreshFailureDecision.terminal(failed);
    }
  }

  private boolean isRetryableRefreshFailure(CommandFailureType failureType) {
    return failureType == CommandFailureType.TRANSPORT
        || failureType == CommandFailureType.RESPONSE_TIMEOUT;
  }

  HelperIdentity identity(String appName) {
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState state = helperLifecycles.get(appName);
      return state == null ? null : state.getHelperIdentity();
    }
  }

  void clear(String appName) {
    synchronized (helperLifecycleMonitor) {
      helperStatusOverrides.remove(appName);
      helperLifecycles.computeIfPresent(appName, (ignored, lifecycle) -> lifecycle.clearPendingRefresh());
      lastLoadTimes.remove(appName);
    }
  }

  RetiredHelper activate(
      String appName,
      HelperIdentity candidate,
      HelperRuntimeTarget runtimeTarget,
      long expectedActivationEpoch
  ) {
    HelperIdentity replaced;
    HelperRuntimeTarget replacedRuntimeTarget;
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.getOrDefault(appName, HelperLifecycleState.empty());
      HelperLifecycleState activated = current.activate(candidate, runtimeTarget, expectedActivationEpoch);
      if (activated == current) {
        return null;
      }
      replaced = current.getHelperIdentity();
      replacedRuntimeTarget = current.getRuntimeTarget();
      helperLifecycles.put(appName, activated);
    }
    return new RetiredHelper(replaced, replacedRuntimeTarget);
  }

  RetiredHelper advance(String appName, boolean acceptingActivations) {
    HelperIdentity active;
    HelperRuntimeTarget runtimeTarget;
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.getOrDefault(appName, HelperLifecycleState.empty());
      active = current.getHelperIdentity();
      runtimeTarget = current.getRuntimeTarget();
      helperLifecycles.put(appName, current.advanceLifecycle(acceptingActivations));
    }
    return new RetiredHelper(active, runtimeTarget);
  }

  RefreshRequest request(
      String appName,
      Long runtimeGeneration,
      HelperIdentity helperIdentity,
      HelperRuntimeTarget runtimeTarget
  ) {
    RefreshRequest request;
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.get(appName);
      if (current == null
          || !current.matchesActiveHelper(helperIdentity)
          || !current.matchesRuntimeTarget(runtimeTarget)) {
        return null;
      }
      String refreshOperationId = UUID.randomUUID().toString();
      request = new RefreshRequest(
          appName,
          runtimeGeneration,
          helperIdentity,
          runtimeTarget,
          refreshOperationId,
          0
      );
      HelperLifecycleState pending = current.withPendingRefresh(runtimeGeneration, refreshOperationId, 0);
      helperLifecycles.put(appName, pending);
      helperStatusOverrides.put(appName, ContainerStatus.LAUNCHING);
    }
    return request;
  }

  boolean complete(MetadataSnapshotPublishedEvent event, boolean dockerRuntime) {
    String appName = event.getAppName().trim();
    synchronized (helperLifecycleMonitor) {
      HelperLifecycleState current = helperLifecycles.get(appName);
      if (current == null
          || !current.matchesActiveHelper(
              event.getHelperInstanceId(),
              event.getHelperStartedAtEpochMillis(),
              event.getAppIncarnationId()
          )) {
        return false;
      }
      if (!current.matchesRuntimeTarget(event.getRuntimeTarget())) {
        return false;
      }
      if (dockerRuntime) {
        if (!current.matchesPendingRefresh(event)) {
          return false;
        }
        helperLifecycles.put(appName, current.clearPendingRefresh(event.getRefreshOperationId()));
      }
      helperStatusOverrides.remove(appName);
      lastLoadTimes.put(appName, LocalDateTime.now());
    }
    return true;
  }
  void clearStatus(String appName) { helperStatusOverrides.remove(appName); }
  void setStatus(String appName, ContainerStatus status) { helperStatusOverrides.put(appName, status); }
  ContainerStatus status(String appName) { return helperStatusOverrides.get(appName); }
  LocalDateTime lastLoadTime(String appName) { return lastLoadTimes.get(appName); }
  static final class HelperLifecycleState {
    final HelperIdentity helperIdentity;
    final HelperRuntimeTarget runtimeTarget;
    final HelperRetirementFence helperRetirementFence;
    final Long runtimeGeneration;
    final String pendingRefreshOperationId;
    final int pendingRefreshAttempt;
    final long activationEpoch;
    final boolean acceptingActivations;

    private HelperLifecycleState(
        HelperIdentity helperIdentity,
        HelperRuntimeTarget runtimeTarget,
        HelperRetirementFence helperRetirementFence,
        Long runtimeGeneration,
        String pendingRefreshOperationId,
        int pendingRefreshAttempt,
        long activationEpoch,
        boolean acceptingActivations
    ) {
      this.helperIdentity = helperIdentity;
      this.runtimeTarget = runtimeTarget;
      this.helperRetirementFence = helperRetirementFence;
      this.runtimeGeneration = runtimeGeneration;
      this.pendingRefreshOperationId = pendingRefreshOperationId;
      this.pendingRefreshAttempt = pendingRefreshAttempt;
      this.activationEpoch = activationEpoch;
      this.acceptingActivations = acceptingActivations;
    }

    static HelperLifecycleState empty() {
      return new HelperLifecycleState(null, null, HelperRetirementFence.empty(), null, null, 0, 0L, true);
    }

    HelperIdentity getHelperIdentity() {
      return helperIdentity;
    }

    HelperRuntimeTarget getRuntimeTarget() {
      return runtimeTarget;
    }

    long getActivationEpoch() {
      return activationEpoch;
    }

    boolean isAcceptingActivations() {
      return acceptingActivations;
    }

    HelperLifecycleState activate(
        HelperIdentity candidate,
        HelperRuntimeTarget candidateRuntimeTarget,
        long expectedActivationEpoch
    ) {
      if (!acceptingActivations
          || activationEpoch != expectedActivationEpoch
          || candidateRuntimeTarget == null
          || helperRetirementFence.rejects(candidate)
          || matchesActiveHelper(candidate)
          || !MetadataRefreshProtocol.canActivateHelper(candidate, helperIdentity)) {
        return this;
      }
      return new HelperLifecycleState(
          candidate,
          candidateRuntimeTarget,
          helperRetirementFence.retire(helperIdentity),
          null,
          null,
          0,
          activationEpoch,
          true
      );
    }

    HelperLifecycleState advanceLifecycle(boolean nextAcceptingActivations) {
      return new HelperLifecycleState(
          null,
          null,
          helperRetirementFence.retire(helperIdentity),
          null,
          null,
          0,
          activationEpoch + 1L,
          nextAcceptingActivations
      );
    }

    HelperLifecycleState withPendingRefresh(Long generation, String operationId, int attempt) {
      return new HelperLifecycleState(
          helperIdentity,
          runtimeTarget,
          helperRetirementFence,
          generation,
          operationId,
          attempt,
          activationEpoch,
          acceptingActivations
      );
    }

    HelperLifecycleState clearPendingRefresh() {
      return new HelperLifecycleState(
          helperIdentity,
          runtimeTarget,
          helperRetirementFence,
          null,
          null,
          0,
          activationEpoch,
          acceptingActivations
      );
    }

    HelperLifecycleState clearPendingRefresh(String operationId) {
      return Objects.equals(pendingRefreshOperationId, operationId) ? clearPendingRefresh() : this;
    }

    boolean matchesActiveHelper(HelperIdentity candidate) {
      return candidate != null
          && matchesActiveHelper(
              candidate.getInstanceId(),
              candidate.getStartedAtEpochMillis(),
              candidate.getAppIncarnationId()
          );
    }

    boolean matchesActiveHelper(String instanceId, long startedAtEpochMillis, String appIncarnationId) {
      return helperIdentity != null
          && helperIdentity.matches(instanceId, startedAtEpochMillis, appIncarnationId);
    }

    boolean matchesRuntimeTarget(HelperRuntimeTarget candidate) {
      return Objects.equals(runtimeTarget, candidate);
    }

    boolean matchesPendingRefresh(CommandResult result) {
      return matchesActiveHelper(
          result.getHelperInstanceId(),
          result.getHelperStartedAtEpochMillis() == null
              ? 0L
              : result.getHelperStartedAtEpochMillis().longValue(),
          result.getAppIncarnationId()
      )
          && Objects.equals(runtimeGeneration, result.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, result.getRefreshOperationId());
    }

    boolean matchesPendingRefresh(RefreshRequest request) {
      return request != null
          && matchesActiveHelper(request.helperIdentity)
          && Objects.equals(runtimeGeneration, request.runtimeGeneration)
          && Objects.equals(pendingRefreshOperationId, request.refreshOperationId)
          && matchesRuntimeTarget(request.runtimeTarget);
    }

    boolean matchesPendingRefresh(MetadataSnapshotPublishedEvent event) {
      return matchesActiveHelper(
          event.getHelperInstanceId(),
          event.getHelperStartedAtEpochMillis(),
          event.getAppIncarnationId()
      )
          && Objects.equals(runtimeGeneration, event.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, event.getRefreshOperationId())
          && matchesRuntimeTarget(event.getRuntimeTarget());
    }

    boolean matchesPendingRefresh(MetadataSnapshotRejectedEvent event) {
      return matchesActiveHelper(
          event.getHelperInstanceId(),
          event.getHelperStartedAtEpochMillis(),
          event.getAppIncarnationId()
      )
          && Objects.equals(runtimeGeneration, event.getRuntimeGeneration())
          && Objects.equals(pendingRefreshOperationId, event.getRefreshOperationId())
          && matchesRuntimeTarget(event.getRuntimeTarget());
    }

    RefreshRequest toRefreshRequest(String appName) {
      return new RefreshRequest(
          appName,
          runtimeGeneration,
          helperIdentity,
          runtimeTarget,
          pendingRefreshOperationId,
          pendingRefreshAttempt
      );
    }
  }

  static final class RefreshRequest {
    final String appName;
    final Long runtimeGeneration;
    final HelperIdentity helperIdentity;
    final HelperRuntimeTarget runtimeTarget;
    final String refreshOperationId;
    final int attempt;

    private RefreshRequest(
        String appName,
        Long runtimeGeneration,
        HelperIdentity helperIdentity,
        HelperRuntimeTarget runtimeTarget,
        String refreshOperationId,
        int attempt
    ) {
      this.appName = appName;
      this.runtimeGeneration = runtimeGeneration;
      this.helperIdentity = helperIdentity;
      this.runtimeTarget = runtimeTarget;
      this.refreshOperationId = refreshOperationId;
      this.attempt = attempt;
    }

    MetadataRefreshRequestedEvent toRequestedEvent() {
      return new MetadataRefreshRequestedEvent(
          appName,
          runtimeGeneration,
          helperIdentity,
          refreshOperationId,
          runtimeTarget
      );
    }

    RefreshRequest retry() {
      return new RefreshRequest(
          appName,
          runtimeGeneration,
          helperIdentity,
          runtimeTarget,
          UUID.randomUUID().toString(),
          attempt + 1
      );
    }
  }

  static final class RefreshFailureDecision {
    final boolean matched;
    final RefreshRequest failedRequest;
    final RefreshRequest retryRequest;

    private RefreshFailureDecision(
        boolean matched,
        RefreshRequest failedRequest,
        RefreshRequest retryRequest
    ) {
      this.matched = matched;
      this.failedRequest = failedRequest;
      this.retryRequest = retryRequest;
    }

    static RefreshFailureDecision unmatched() {
      return new RefreshFailureDecision(false, null, null);
    }

    static RefreshFailureDecision retry(RefreshRequest failedRequest, RefreshRequest retryRequest) {
      return new RefreshFailureDecision(true, failedRequest, retryRequest);
    }

    static RefreshFailureDecision terminal(RefreshRequest failedRequest) {
      return new RefreshFailureDecision(true, failedRequest, null);
    }
  }

}
