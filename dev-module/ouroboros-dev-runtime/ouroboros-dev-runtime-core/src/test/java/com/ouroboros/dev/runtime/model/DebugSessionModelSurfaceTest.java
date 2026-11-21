package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DebugSessionModelSurfaceTest {

  @Test
  void shouldExposeLifecycleOwnershipAndBindingFields() {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("ticketNo", "DEV-1024");
    DebugSessionBinding binding = DebugSessionBinding.builder()
        .host("127.0.0.1")
        .port(Integer.valueOf(43125))
        .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
        .clientAccessHint("ssh -L 5005:127.0.0.1:43125 develop.example.com")
        .metadata(metadata)
        .build();

    DebugSession session = DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER))
        .runtimeIdAtOpen("rt-crm-alice")
        .runtimeGeneration(Long.valueOf(12))
        .uploadBatchId("upl-001")
        .preparationSessionId("dbg-prep-001")
        .ownerUserId("u-001")
        .ownerUserName("alice")
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .binding(binding)
        .requestedAt(Instant.parse("2026-03-13T07:00:00Z"))
        .openedAt(Instant.parse("2026-03-13T07:00:02Z"))
        .firstConnectedAt(Instant.parse("2026-03-13T07:00:05Z"))
        .lastActiveAt(Instant.parse("2026-03-13T07:01:00Z"))
        .expiresAt(Instant.parse("2026-03-13T07:30:00Z"))
        .idleTimeoutSeconds(Integer.valueOf(600))
        .maxLifetimeSeconds(Integer.valueOf(1800))
        .reason("排查启动期接口异常")
        .closeReason(DebugSessionCloseReason.USER_CLOSED)
        .metadata(metadata)
        .build();

    assertEquals("dbg-001", session.getSessionId());
    assertEquals("crm", session.getRuntimeKey().getAppName());
    assertEquals(Long.valueOf(12), session.getRuntimeGeneration());
    assertEquals("upl-001", session.getUploadBatchId());
    assertEquals("dbg-prep-001", session.getPreparationSessionId());
    assertEquals("alice", session.getOwnerUserName());
    assertEquals(DebugSessionStatus.ACTIVE, session.getStatus());
    assertEquals(DebugAccessStrategy.SESSION_TUNNEL, session.getDebugAccessStrategy());
    assertNotNull(session.getBinding());
    assertEquals(Integer.valueOf(43125), session.getBinding().getPort());
    assertEquals(DebugSessionBindingScope.LOOPBACK_ONLY, session.getBinding().getBindingScope());
    assertEquals("DEV-1024", session.getMetadata().get("ticketNo"));
  }

  @Test
  void shouldDefensivelyCopyMetadataMaps() {
    Map<String, String> sessionMetadata = new LinkedHashMap<String, String>();
    sessionMetadata.put("ticketNo", "DEV-1024");
    Map<String, String> bindingMetadata = new LinkedHashMap<String, String>();
    bindingMetadata.put("brokerInstanceId", "broker-01");

    DebugSessionBinding binding = DebugSessionBinding.builder()
        .host("127.0.0.1")
        .port(Integer.valueOf(43125))
        .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
        .metadata(bindingMetadata)
        .build();
    DebugSession session = DebugSession.builder()
        .sessionId("dbg-002")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES))
        .uploadBatchId("upl-002")
        .preparationSessionId("dbg-prep-002")
        .status(DebugSessionStatus.REQUESTED)
        .binding(binding)
        .metadata(sessionMetadata)
        .build();

    sessionMetadata.put("ticketNo", "MUTATED");
    bindingMetadata.put("brokerInstanceId", "broker-99");

    assertEquals("DEV-1024", session.getMetadata().get("ticketNo"));
    assertEquals("broker-01", session.getBinding().getMetadata().get("brokerInstanceId"));
    assertEquals("upl-002", session.getUploadBatchId());
    assertEquals("dbg-prep-002", session.getPreparationSessionId());
    assertThrows(UnsupportedOperationException.class, () -> session.getMetadata().put("x", "y"));
    assertThrows(UnsupportedOperationException.class, () -> session.getBinding().getMetadata().put("x", "y"));
  }

  @Test
  void shouldExposeAllDebugSessionStatuses() {
    assertNotNull(DebugSessionStatus.valueOf("REQUESTED"));
    assertNotNull(DebugSessionStatus.valueOf("OPENING"));
    assertNotNull(DebugSessionStatus.valueOf("ACTIVE"));
    assertNotNull(DebugSessionStatus.valueOf("CLOSING"));
    assertNotNull(DebugSessionStatus.valueOf("CLOSED"));
    assertNotNull(DebugSessionStatus.valueOf("EXPIRED"));
    assertNotNull(DebugSessionStatus.valueOf("INVALIDATED"));
    assertNotNull(DebugSessionStatus.valueOf("FAILED"));
  }
}
