package com.ouroboros.dev.runtime.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;

class DebugSessionRuntimeEventListenerTest {

  @Test
  void shouldInvalidateActiveSessionsWhenRuntimeIsDeleted() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(activeSession("dbg-deleted", runtimeKey, Long.valueOf(12L)));
    sessionStore.save(activeSession(
        "dbg-other",
        RuntimeIdentityKey.of("crm", "bob", RuntimeKind.TEST_APP, BackendType.DOCKER),
        Long.valueOf(12L)
    ));
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker();
    DebugSessionRuntimeEventListener listener = new DebugSessionRuntimeEventListener(sessionStore, broker);

    listener.onEvent(event(runtimeKey, DevRuntimeEventType.RUNTIME_DELETED, Long.valueOf(12L), RuntimeStatus.STOPPED));

    assertEquals(DebugSessionStatus.INVALIDATED, sessionStore.findBySessionId("dbg-deleted").get().getStatus());
    assertEquals(DebugSessionCloseReason.RUNTIME_DELETED, sessionStore.findBySessionId("dbg-deleted").get().getCloseReason());
    assertEquals(DebugSessionStatus.ACTIVE, sessionStore.findBySessionId("dbg-other").get().getStatus());
    assertTrue(broker.closedSessionIds.contains("dbg-deleted"));
  }

  @Test
  void shouldInvalidateActiveSessionsWhenRuntimeGenerationChanges() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(activeSession("dbg-generation", runtimeKey, Long.valueOf(12L)));
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker();
    DebugSessionRuntimeEventListener listener = new DebugSessionRuntimeEventListener(sessionStore, broker);

    listener.onEvent(event(runtimeKey, DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED, Long.valueOf(13L), RuntimeStatus.RUNNING));

    assertEquals(DebugSessionStatus.INVALIDATED, sessionStore.findBySessionId("dbg-generation").get().getStatus());
    assertEquals(DebugSessionCloseReason.RUNTIME_GENERATION_CHANGED, sessionStore.findBySessionId("dbg-generation").get().getCloseReason());
    assertEquals("upl-001", sessionStore.findBySessionId("dbg-generation").get().getUploadBatchId());
    assertEquals("dbg-prep-001", sessionStore.findBySessionId("dbg-generation").get().getPreparationSessionId());
    assertTrue(broker.closedSessionIds.contains("dbg-generation"));
  }

  @Test
  void shouldInvalidateActiveSessionsWhenRuntimeLeavesRunningState() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(activeSession("dbg-stopped", runtimeKey, Long.valueOf(12L)));
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker();
    DebugSessionRuntimeEventListener listener = new DebugSessionRuntimeEventListener(sessionStore, broker);

    listener.onEvent(event(runtimeKey, DevRuntimeEventType.RUNTIME_STATUS_CHANGED, Long.valueOf(12L), RuntimeStatus.STOPPED));

    assertEquals(DebugSessionStatus.INVALIDATED, sessionStore.findBySessionId("dbg-stopped").get().getStatus());
    assertEquals(DebugSessionCloseReason.RUNTIME_RESTARTED, sessionStore.findBySessionId("dbg-stopped").get().getCloseReason());
    assertTrue(broker.closedSessionIds.contains("dbg-stopped"));
  }

  @Test
  void shouldInvalidateAllTargetTypesForSameRuntimeKey() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(activeSession("dbg-debug", runtimeKey, Long.valueOf(12L), DebugTunnelTargetType.DEBUG));
    sessionStore.save(activeSession("dbg-backend-api", runtimeKey, Long.valueOf(12L), DebugTunnelTargetType.BACKEND_API));
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker();
    DebugSessionRuntimeEventListener listener = new DebugSessionRuntimeEventListener(sessionStore, broker);

    listener.onEvent(event(runtimeKey, DevRuntimeEventType.RUNTIME_DELETED, Long.valueOf(12L), RuntimeStatus.STOPPED));

    assertEquals(DebugSessionStatus.INVALIDATED, sessionStore.findBySessionId("dbg-debug").get().getStatus());
    assertEquals(DebugSessionStatus.INVALIDATED, sessionStore.findBySessionId("dbg-backend-api").get().getStatus());
    assertTrue(broker.closedSessionIds.contains("dbg-debug"));
    assertTrue(broker.closedSessionIds.contains("dbg-backend-api"));
  }

  private static DebugSession activeSession(String sessionId, RuntimeIdentityKey runtimeKey, Long runtimeGeneration) {
    return activeSession(sessionId, runtimeKey, runtimeGeneration, DebugTunnelTargetType.DEBUG);
  }

  private static DebugSession activeSession(String sessionId,
                                            RuntimeIdentityKey runtimeKey,
                                            Long runtimeGeneration,
                                            DebugTunnelTargetType targetType) {
    return DebugSession.builder()
        .sessionId(sessionId)
        .runtimeKey(runtimeKey)
        .targetType(targetType)
        .runtimeGeneration(runtimeGeneration)
        .uploadBatchId("upl-001")
        .preparationSessionId("dbg-prep-001")
        .status(DebugSessionStatus.ACTIVE)
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(43125))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .targetType(targetType)
            .build())
        .build();
  }

  private static DevRuntimeEvent event(
      RuntimeIdentityKey runtimeKey,
      DevRuntimeEventType eventType,
      Long runtimeGeneration,
      RuntimeStatus status
  ) {
    return DevRuntimeEvent.builder()
        .type(eventType)
        .appName(runtimeKey.getAppName())
        .profile(runtimeKey.getProfile())
        .runtimeKind(runtimeKey.getRuntimeKind())
        .backendType(runtimeKey.getBackendType())
        .runtimeGeneration(runtimeGeneration)
        .status(status)
        .build();
  }

  static class RecordingDebugTunnelBroker implements com.ouroboros.dev.runtime.service.TunnelBroker {
    private final List<String> closedSessionIds = new ArrayList<String>();

    @Override
    public Try<com.ouroboros.dev.runtime.model.DebugSessionBinding> open(
        DebugSession session,
        com.ouroboros.dev.runtime.service.DebugConnector connector,
        com.ouroboros.dev.runtime.model.TransportMode transportMode
    ) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Void> close(String sessionId) {
      closedSessionIds.add(sessionId);
      return Try.success(null);
    }
  }
}
