package com.ouroboros.dev.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;

public class InMemoryDebugSessionStore implements DebugSessionStore {
  private final Map<String, DebugSession> sessionsById = new ConcurrentHashMap<String, DebugSession>();
  private final Map<RuntimeTargetKey, Map<String, DebugSession>> activeSessionsByRuntimeTarget = new ConcurrentHashMap<RuntimeTargetKey, Map<String, DebugSession>>();

  @Override
  public DebugSession save(DebugSession session) {
    DebugSession previous = sessionsById.put(session.getSessionId(), session);
    if (previous != null) {
      removeFromActiveIndex(previous);
    }
    if (isActive(session.getStatus())) {
      activeSessionsByRuntimeTarget
          .computeIfAbsent(RuntimeTargetKey.of(session.getRuntimeKey(), session.getTargetType()), key -> new ConcurrentHashMap<String, DebugSession>())
          .put(session.getSessionId(), session);
    } else {
      removeFromActiveIndex(session);
    }
    return session;
  }

  @Override
  public Optional<DebugSession> findBySessionId(String sessionId) {
    return Optional.ofNullable(sessionsById.get(sessionId));
  }

  @Override
  public List<DebugSession> findActiveByRuntimeKey(RuntimeIdentityKey runtimeKey) {
    if (runtimeKey == null) {
      return Collections.emptyList();
    }
    List<DebugSession> sessions = new ArrayList<DebugSession>();
    for (Map.Entry<RuntimeTargetKey, Map<String, DebugSession>> entry : activeSessionsByRuntimeTarget.entrySet()) {
      if (!runtimeKey.equals(entry.getKey().runtimeKey)) {
        continue;
      }
      sessions.addAll(entry.getValue().values());
    }
    sessions.sort(Comparator.comparing(DebugSession::getSessionId));
    return Collections.unmodifiableList(sessions);
  }

  @Override
  public List<DebugSession> findActiveByRuntimeKeyAndTargetType(RuntimeIdentityKey runtimeKey, DebugTunnelTargetType targetType) {
    if (runtimeKey == null) {
      return Collections.emptyList();
    }
    Map<String, DebugSession> sessions = activeSessionsByRuntimeTarget.get(RuntimeTargetKey.of(runtimeKey, targetType));
    if (sessions == null || sessions.isEmpty()) {
      return Collections.emptyList();
    }
    List<DebugSession> matches = new ArrayList<DebugSession>(sessions.values());
    matches.sort(Comparator.comparing(DebugSession::getSessionId));
    return Collections.unmodifiableList(matches);
  }

  @Override
  public List<DebugSession> findAll() {
    List<DebugSession> sessions = new ArrayList<DebugSession>(sessionsById.values());
    sessions.sort(Comparator.comparing(DebugSession::getSessionId));
    return Collections.unmodifiableList(sessions);
  }

  @Override
  public Optional<DebugSession> close(String sessionId, DebugSessionCloseReason closeReason, Instant closedAt) {
    return update(sessionId, DebugSessionStatus.CLOSED, closeReason, closedAt);
  }

  @Override
  public List<DebugSession> invalidate(RuntimeIdentityKey runtimeKey, DebugSessionCloseReason closeReason, Instant closedAt) {
    if (runtimeKey == null) {
      return Collections.emptyList();
    }
    List<DebugSession> invalidatedSessions = new ArrayList<DebugSession>();
    for (DebugSession session : sessionsById.values()) {
      if (!runtimeKey.equals(session.getRuntimeKey()) || !isActive(session.getStatus())) {
        continue;
      }
      DebugSession updated = transition(session, DebugSessionStatus.INVALIDATED, closeReason, closedAt);
      save(updated);
      invalidatedSessions.add(updated);
    }
    invalidatedSessions.sort(Comparator.comparing(DebugSession::getSessionId));
    return Collections.unmodifiableList(invalidatedSessions);
  }

  private Optional<DebugSession> update(String sessionId, DebugSessionStatus status, DebugSessionCloseReason closeReason, Instant closedAt) {
    DebugSession current = sessionsById.get(sessionId);
    if (current == null) {
      return Optional.empty();
    }
    DebugSession updated = transition(current, status, closeReason, closedAt);
    save(updated);
    return Optional.of(updated);
  }

  private void removeFromActiveIndex(DebugSession session) {
    if (session == null || session.getRuntimeKey() == null) {
      return;
    }
    RuntimeTargetKey key = RuntimeTargetKey.of(session.getRuntimeKey(), session.getTargetType());
    Map<String, DebugSession> sessions = activeSessionsByRuntimeTarget.get(key);
    if (sessions == null) {
      return;
    }
    sessions.remove(session.getSessionId());
    if (sessions.isEmpty()) {
      activeSessionsByRuntimeTarget.remove(key, sessions);
    }
  }

  private static DebugSession transition(DebugSession session, DebugSessionStatus status, DebugSessionCloseReason closeReason, Instant closedAt) {
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
        .binding(session.getBinding())
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

  private static boolean isActive(DebugSessionStatus status) {
    return status == DebugSessionStatus.REQUESTED
        || status == DebugSessionStatus.OPENING
        || status == DebugSessionStatus.ACTIVE
        || status == DebugSessionStatus.CLOSING;
  }

  private static class RuntimeTargetKey {
    private final RuntimeIdentityKey runtimeKey;
    private final DebugTunnelTargetType targetType;

    private RuntimeTargetKey(RuntimeIdentityKey runtimeKey, DebugTunnelTargetType targetType) {
      this.runtimeKey = runtimeKey;
      this.targetType = targetType == null ? DebugTunnelTargetType.defaultType() : targetType;
    }

    private static RuntimeTargetKey of(RuntimeIdentityKey runtimeKey, DebugTunnelTargetType targetType) {
      return new RuntimeTargetKey(runtimeKey, targetType);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof RuntimeTargetKey)) {
        return false;
      }
      RuntimeTargetKey that = (RuntimeTargetKey) other;
      return runtimeKey.equals(that.runtimeKey) && targetType == that.targetType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(runtimeKey, targetType);
    }
  }
}
