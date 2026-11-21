package com.ouroboros.dev.runtime.broker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;

public class DebugSessionReaper {
  private final DebugSessionStore debugSessionStore;
  private final TunnelBroker tunnelBroker;

  public DebugSessionReaper(DebugSessionStore debugSessionStore, TunnelBroker tunnelBroker) {
    this.debugSessionStore = debugSessionStore;
    this.tunnelBroker = tunnelBroker;
  }

  public List<DebugSession> reap(Instant now) {
    if (now == null) {
      return Collections.emptyList();
    }
    List<DebugSession> expiredSessions = new ArrayList<DebugSession>();
    for (DebugSession session : debugSessionStore.findAll()) {
      if (!isActive(session) || !isExpired(session, now)) {
        continue;
      }
      tunnelBroker.close(session.getSessionId());
      DebugSession expired = transition(session, now);
      debugSessionStore.save(expired);
      expiredSessions.add(expired);
    }
    return Collections.unmodifiableList(expiredSessions);
  }

  private static boolean isExpired(DebugSession session, Instant now) {
    if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) {
      return true;
    }
    if (session.getIdleTimeoutSeconds() == null) {
      return false;
    }
    Instant lastActiveAt = session.getLastActiveAt() == null ? session.getOpenedAt() : session.getLastActiveAt();
    if (lastActiveAt == null) {
      return false;
    }
    return !lastActiveAt.plusSeconds(session.getIdleTimeoutSeconds().intValue()).isAfter(now);
  }

  private static boolean isActive(DebugSession session) {
    return session.getStatus() == DebugSessionStatus.REQUESTED
        || session.getStatus() == DebugSessionStatus.OPENING
        || session.getStatus() == DebugSessionStatus.ACTIVE
        || session.getStatus() == DebugSessionStatus.CLOSING;
  }

  private static DebugSession transition(DebugSession session, Instant now) {
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
        .status(DebugSessionStatus.EXPIRED)
        .debugAccessStrategy(session.getDebugAccessStrategy())
        .binding(session.getBinding())
        .requestedAt(session.getRequestedAt())
        .openedAt(session.getOpenedAt())
        .firstConnectedAt(session.getFirstConnectedAt())
        .lastActiveAt(session.getLastActiveAt())
        .expiresAt(session.getExpiresAt())
        .closedAt(now)
        .idleTimeoutSeconds(session.getIdleTimeoutSeconds())
        .maxLifetimeSeconds(session.getMaxLifetimeSeconds())
        .reason(session.getReason())
        .closeReason(DebugSessionCloseReason.EXPIRED)
        .metadata(session.getMetadata())
        .build();
  }
}
