package com.ouroboros.dev.runtime.broker;

import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventListener;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;

public class DebugSessionRuntimeEventListener implements DevRuntimeEventListener {
  private final DebugSessionStore debugSessionStore;
  private final TunnelBroker tunnelBroker;

  public DebugSessionRuntimeEventListener(DebugSessionStore debugSessionStore, TunnelBroker tunnelBroker) {
    this.debugSessionStore = debugSessionStore;
    this.tunnelBroker = tunnelBroker;
  }

  @Override
  public void onEvent(DevRuntimeEvent event) {
    if (event == null || event.getAppName() == null || event.getProfile() == null || event.getRuntimeKind() == null || event.getBackendType() == null) {
      return;
    }
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of(
        event.getAppName(),
        event.getProfile(),
        event.getRuntimeKind(),
        event.getBackendType()
    );
    List<DebugSession> sessions = debugSessionStore.findActiveByRuntimeKey(runtimeKey);
    if (sessions.isEmpty()) {
      return;
    }
    DebugSessionCloseReason closeReason = resolveCloseReason(event, sessions);
    if (closeReason == null) {
      return;
    }
    for (DebugSession session : sessions) {
      tunnelBroker.close(session.getSessionId());
    }
    debugSessionStore.invalidate(runtimeKey, closeReason, Instant.now());
  }

  @EventListener
  public void onApplicationEvent(DevRuntimeEvent event) {
    onEvent(event);
  }

  private DebugSessionCloseReason resolveCloseReason(DevRuntimeEvent event, List<DebugSession> sessions) {
    if (event.getType() == DevRuntimeEventType.RUNTIME_DELETED) {
      return DebugSessionCloseReason.RUNTIME_DELETED;
    }
    if ((event.getType() == DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED || event.getType() == DevRuntimeEventType.RUNTIME_STATUS_CHANGED)
        && isGenerationChanged(event, sessions)) {
      return DebugSessionCloseReason.RUNTIME_GENERATION_CHANGED;
    }
    if (event.getType() == DevRuntimeEventType.RUNTIME_STATUS_CHANGED && event.getStatus() != RuntimeStatus.RUNNING) {
      return event.getStatus() == RuntimeStatus.FAILED ? DebugSessionCloseReason.FAILED : DebugSessionCloseReason.RUNTIME_RESTARTED;
    }
    return null;
  }

  private boolean isGenerationChanged(DevRuntimeEvent event, List<DebugSession> sessions) {
    if (event.getRuntimeGeneration() == null) {
      return false;
    }
    return sessions.stream()
        .map(DebugSession::getRuntimeGeneration)
        .filter(generation -> generation != null)
        .anyMatch(generation -> !event.getRuntimeGeneration().equals(generation));
  }
}
