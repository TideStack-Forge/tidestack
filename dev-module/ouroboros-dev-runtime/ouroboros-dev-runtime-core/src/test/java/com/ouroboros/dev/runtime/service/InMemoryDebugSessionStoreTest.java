package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class InMemoryDebugSessionStoreTest {

  @Test
  void shouldSaveFindAndListActiveSessionsByRuntime() {
    DebugSessionStore store = new InMemoryDebugSessionStore();
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSession session = DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .build();

    store.save(session);

    assertEquals("dbg-001", store.findBySessionId("dbg-001").get().getSessionId());
    assertEquals(1, store.findActiveByRuntimeKey(runtimeKey).size());
  }

  @Test
  void shouldMarkSessionClosedAndInvalidateRuntimeSessions() {
    DebugSessionStore store = new InMemoryDebugSessionStore();
    RuntimeIdentityKey dockerKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    RuntimeIdentityKey k8sKey = RuntimeIdentityKey.of("crm", "bob", RuntimeKind.TEST_APP, BackendType.KUBERNETES);
    store.save(DebugSession.builder()
        .sessionId("dbg-closed")
        .runtimeKey(dockerKey)
        .status(DebugSessionStatus.ACTIVE)
        .build());
    store.save(DebugSession.builder()
        .sessionId("dbg-invalidated")
        .runtimeKey(k8sKey)
        .status(DebugSessionStatus.OPENING)
        .build());

    store.close("dbg-closed", DebugSessionCloseReason.USER_CLOSED, Instant.parse("2026-03-13T07:30:00Z"));
    List<DebugSession> invalidated = store.invalidate(k8sKey, DebugSessionCloseReason.RUNTIME_DELETED, Instant.parse("2026-03-13T07:31:00Z"));

    assertEquals(DebugSessionStatus.CLOSED, store.findBySessionId("dbg-closed").get().getStatus());
    assertEquals(DebugSessionCloseReason.USER_CLOSED, store.findBySessionId("dbg-closed").get().getCloseReason());
    assertTrue(store.findActiveByRuntimeKey(dockerKey).isEmpty());
    assertEquals(1, invalidated.size());
    assertEquals(DebugSessionStatus.INVALIDATED, store.findBySessionId("dbg-invalidated").get().getStatus());
    assertEquals(DebugSessionCloseReason.RUNTIME_DELETED, store.findBySessionId("dbg-invalidated").get().getCloseReason());
    assertTrue(store.findActiveByRuntimeKey(k8sKey).isEmpty());
  }
}
