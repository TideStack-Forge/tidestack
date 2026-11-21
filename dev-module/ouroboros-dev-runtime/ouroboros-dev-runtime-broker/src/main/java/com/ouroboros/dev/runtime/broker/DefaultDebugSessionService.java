package com.ouroboros.dev.runtime.broker;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;

public class DefaultDebugSessionService implements DebugSessionService {
  private final RuntimeStateStore runtimeStateStore;
  private final DevRuntimeService devRuntimeService;
  private final DebugSessionStore debugSessionStore;
  private final List<DebugConnector> debugConnectors;
  private final TunnelBroker tunnelBroker;
  private final DevRuntimeProperties properties;
  private final Clock clock;

  public DefaultDebugSessionService(
      RuntimeStateStore runtimeStateStore,
      DevRuntimeService devRuntimeService,
      DebugSessionStore debugSessionStore,
      List<DebugConnector> debugConnectors,
      TunnelBroker tunnelBroker,
      DevRuntimeProperties properties,
      Clock clock
  ) {
    this.runtimeStateStore = runtimeStateStore;
    this.devRuntimeService = devRuntimeService;
    this.debugSessionStore = debugSessionStore;
    this.debugConnectors = debugConnectors;
    this.tunnelBroker = tunnelBroker;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Try<DebugSession> open(OpenDebugSessionCommand command) {
    return Try.of(() -> runtimeStateStore.findInstance(command.getRuntimeKey())
        .orElseThrow(() -> new IllegalStateException("runtime not found")))
        .flatMap(runtime -> refreshRuntime(command.getRuntimeKey()))
        .flatMap(runtime -> validateRuntime(command, runtime).map(ignored -> runtime))
        .flatMap(runtime -> rejectDuplicateSession(command.getRuntimeKey(), command.getTargetType()).map(ignored -> runtime))
        .flatMap(runtime -> openSession(command, runtime));
  }

  @Override
  public Try<DebugSession> get(String sessionId) {
    return Try.of(() -> debugSessionStore.findBySessionId(sessionId)
        .orElseThrow(() -> new IllegalStateException("debug session not found")));
  }

  @Override
  public Try<Void> close(String sessionId) {
    Instant now = Instant.now(clock);
    return Try.of(() -> debugSessionStore.findBySessionId(sessionId)
        .orElseThrow(() -> new IllegalStateException("debug session not found")))
        .flatMap(session -> {
          DebugSession closing = transition(session, DebugSessionStatus.CLOSING, null, now, session.getBinding());
          debugSessionStore.save(closing);
          return tunnelBroker.close(sessionId)
              .map(ignored -> {
                debugSessionStore.close(sessionId, DebugSessionCloseReason.USER_CLOSED, now);
                return ignored;
              })
              .recoverWith(error -> {
                debugSessionStore.save(transition(closing, DebugSessionStatus.FAILED, DebugSessionCloseReason.FAILED, now, closing.getBinding()));
                return Try.failure(error);
              });
        });
  }

  private Try<DebugSession> openSession(OpenDebugSessionCommand command, RuntimeInstance runtime) {
    Instant now = Instant.now(clock);
    DebugConnector connector = resolveConnector(command.getRuntimeKey());
    DebugSession requested = requestedSession(command, runtime, now);
    DebugSession opening = transition(requested, DebugSessionStatus.OPENING, null, null, null);
    debugSessionStore.save(requested);
    debugSessionStore.save(opening);
    return tunnelBroker.open(opening, connector, opening.getTransportMode())
        .map(binding -> activate(opening, binding, now))
        .map(session -> {
          debugSessionStore.save(session);
          return session;
        })
        .recoverWith(error -> {
          tunnelBroker.close(opening.getSessionId());
          DebugSession failed = transition(opening, DebugSessionStatus.FAILED, DebugSessionCloseReason.FAILED, now, opening.getBinding());
          debugSessionStore.save(failed);
          return Try.failure(error);
        });
  }

  private DebugSession activate(DebugSession session, DebugSessionBinding binding, Instant now) {
    return DebugSession.builder()
        .sessionId(session.getSessionId())
        .runtimeKey(session.getRuntimeKey())
        .targetType(session.getTargetType())
        .transportMode(session.getTransportMode())
        .runtimeIdAtOpen(session.getRuntimeIdAtOpen())
        .runtimeGeneration(session.getRuntimeGeneration())
        .uploadBatchId(session.getUploadBatchId())
        .preparationSessionId(session.getPreparationSessionId())
        .ownerUserId(session.getOwnerUserId())
        .ownerUserName(session.getOwnerUserName())
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(session.getDebugAccessStrategy())
        .binding(binding)
        .requestedAt(session.getRequestedAt())
        .openedAt(now)
        .firstConnectedAt(session.getFirstConnectedAt())
        .lastActiveAt(now)
        .expiresAt(session.getExpiresAt())
        .closedAt(null)
        .idleTimeoutSeconds(session.getIdleTimeoutSeconds())
        .maxLifetimeSeconds(session.getMaxLifetimeSeconds())
        .reason(session.getReason())
        .closeReason(null)
        .metadata(session.getMetadata())
        .build();
  }

  private DebugSession requestedSession(OpenDebugSessionCommand command, RuntimeInstance runtime, Instant now) {
    Integer maxLifetimeSeconds = command.getMaxLifetimeSeconds() == null
        ? properties.getDebug().getMaxLifetimeSeconds()
        : command.getMaxLifetimeSeconds();
    Integer idleTimeoutSeconds = command.getIdleTimeoutSeconds() == null
        ? properties.getDebug().getIdleTimeoutSeconds()
        : command.getIdleTimeoutSeconds();
    Instant expiresAt = maxLifetimeSeconds == null ? null : now.plusSeconds(maxLifetimeSeconds.intValue());
    String sessionId = command.getRequestedSessionId() == null || command.getRequestedSessionId().trim().isEmpty()
        ? "dbg-" + UUID.randomUUID().toString()
        : command.getRequestedSessionId().trim();
    return DebugSession.builder()
        .sessionId(sessionId)
        .runtimeKey(command.getRuntimeKey())
        .targetType(command.getTargetType())
        .transportMode(resolveTransportMode(command))
        .runtimeIdAtOpen(runtime.getRuntimeId())
        .runtimeGeneration(runtime.getRuntimeGeneration())
        .uploadBatchId(command.getUploadBatchId())
        .preparationSessionId(command.getPreparationSessionId())
        .ownerUserId(command.getOwnerUserId())
        .ownerUserName(command.getOwnerUserName())
        .status(DebugSessionStatus.REQUESTED)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .requestedAt(now)
        .expiresAt(expiresAt)
        .idleTimeoutSeconds(idleTimeoutSeconds)
        .maxLifetimeSeconds(maxLifetimeSeconds)
        .reason(command.getReason())
        .metadata(command.getMetadata())
        .build();
  }

  private DebugSession transition(
      DebugSession session,
      DebugSessionStatus status,
      DebugSessionCloseReason closeReason,
      Instant closedAt,
      DebugSessionBinding binding
  ) {
    return DebugSession.builder()
        .sessionId(session.getSessionId())
        .runtimeKey(session.getRuntimeKey())
        .targetType(session.getTargetType())
        .transportMode(session.getTransportMode())
        .runtimeIdAtOpen(session.getRuntimeIdAtOpen())
        .runtimeGeneration(session.getRuntimeGeneration())
        .uploadBatchId(session.getUploadBatchId())
        .preparationSessionId(session.getPreparationSessionId())
        .ownerUserId(session.getOwnerUserId())
        .ownerUserName(session.getOwnerUserName())
        .status(status)
        .debugAccessStrategy(session.getDebugAccessStrategy())
        .binding(binding == null ? session.getBinding() : binding)
        .requestedAt(session.getRequestedAt())
        .openedAt(session.getOpenedAt())
        .firstConnectedAt(session.getFirstConnectedAt())
        .lastActiveAt(session.getLastActiveAt())
        .expiresAt(session.getExpiresAt())
        .closedAt(closedAt)
        .idleTimeoutSeconds(session.getIdleTimeoutSeconds())
        .maxLifetimeSeconds(session.getMaxLifetimeSeconds())
        .reason(session.getReason())
        .closeReason(closeReason)
        .metadata(session.getMetadata())
        .build();
  }

  private Try<RuntimeInstance> refreshRuntime(RuntimeIdentityKey runtimeKey) {
    return devRuntimeService.getStatus(runtimeKey);
  }

  private Try<Void> validateRuntime(OpenDebugSessionCommand command, RuntimeInstance runtime) {
    if (runtime == null || runtime.getStatus() != RuntimeStatus.RUNNING) {
      return Try.failure(new IllegalStateException("runtime is not running"));
    }
    if (command.getExpectedGeneration() != null && !command.getExpectedGeneration().equals(runtime.getRuntimeGeneration())) {
      return Try.failure(new IllegalStateException("runtime generation mismatch"));
    }
    return Try.success(null);
  }

  private Try<Void> rejectDuplicateSession(RuntimeIdentityKey runtimeKey, DebugTunnelTargetType targetType) {
    if (!debugSessionStore.findActiveByRuntimeKeyAndTargetType(runtimeKey, targetType).isEmpty()) {
      return Try.failure(new IllegalStateException("debug session already active for target " + targetType.name()));
    }
    return Try.success(null);
  }

  private DebugConnector resolveConnector(RuntimeIdentityKey runtimeKey) {
    return debugConnectors.stream()
        .filter(connector -> connector.getBackendType() == runtimeKey.getBackendType())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no debug connector for " + runtimeKey.getBackendType()));
  }

  private TransportMode resolveTransportMode(OpenDebugSessionCommand command) {
    DebugTunnelTargetType targetType = command == null ? null : command.getTargetType();
    if (targetType != null) {
      return targetType.getTransportMode();
    }
    return DebugTunnelTargetType.defaultType().getTransportMode();
  }
}
