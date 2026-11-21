package com.ouroboros.dev.runtime.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;

public interface DebugSessionStore {
  DebugSession save(DebugSession session);

  Optional<DebugSession> findBySessionId(String sessionId);

  List<DebugSession> findActiveByRuntimeKey(RuntimeIdentityKey runtimeKey);

  List<DebugSession> findActiveByRuntimeKeyAndTargetType(RuntimeIdentityKey runtimeKey, DebugTunnelTargetType targetType);

  List<DebugSession> findAll();

  Optional<DebugSession> close(String sessionId, DebugSessionCloseReason closeReason, Instant closedAt);

  List<DebugSession> invalidate(RuntimeIdentityKey runtimeKey, DebugSessionCloseReason closeReason, Instant closedAt);
}
