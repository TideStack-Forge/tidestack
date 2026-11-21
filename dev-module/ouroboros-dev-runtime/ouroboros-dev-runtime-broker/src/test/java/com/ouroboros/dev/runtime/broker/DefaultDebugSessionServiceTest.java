package com.ouroboros.dev.runtime.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionCloseReason;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;

class DefaultDebugSessionServiceTest {

  @Test
  void shouldOpenSessionForRunningRuntimeWithMatchingGeneration() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    runtimeStateStore.saveInstance(runtimeKey, runtimeInstance(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING));
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        runtimeStateStore,
        new RecordingDevRuntimeService(runtime(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING)),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:00:00Z"), ZoneOffset.UTC)
    );

    DebugSession session = service.open(openCommand(runtimeKey, Long.valueOf(12L))).get();

    assertEquals(DebugSessionStatus.ACTIVE, session.getStatus());
    assertEquals(Long.valueOf(12L), session.getRuntimeGeneration());
    assertEquals(Integer.valueOf(43125), session.getBinding().getPort());
    assertEquals(Arrays.asList(DebugSessionStatus.OPENING), broker.openObservedStatuses);
    assertEquals(DebugSessionStatus.ACTIVE, sessionStore.findBySessionId(session.getSessionId()).get().getStatus());
  }

  @Test
  void shouldRejectOpenWhenRuntimeMissingStoppedOrGenerationMismatched() {
    RuntimeIdentityKey stoppedKey = RuntimeIdentityKey.of("crm", "stopped", RuntimeKind.TEST_APP, BackendType.DOCKER);
    RuntimeIdentityKey mismatchKey = RuntimeIdentityKey.of("crm", "mismatch", RuntimeKind.TEST_APP, BackendType.DOCKER);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    runtimeStateStore.saveInstance(stoppedKey, runtimeInstance(stoppedKey, Long.valueOf(9L), RuntimeStatus.STOPPED));
    runtimeStateStore.saveInstance(mismatchKey, runtimeInstance(mismatchKey, Long.valueOf(10L), RuntimeStatus.RUNNING));
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    Map<RuntimeIdentityKey, RuntimeInstance> refreshed = new LinkedHashMap<RuntimeIdentityKey, RuntimeInstance>();
    refreshed.put(stoppedKey, runtime(stoppedKey, Long.valueOf(9L), RuntimeStatus.STOPPED));
    refreshed.put(mismatchKey, runtime(mismatchKey, Long.valueOf(11L), RuntimeStatus.RUNNING));
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        runtimeStateStore,
        new RecordingDevRuntimeService(refreshed),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:00:00Z"), ZoneOffset.UTC)
    );

    Try<DebugSession> missing = service.open(openCommand(
        RuntimeIdentityKey.of("crm", "missing", RuntimeKind.TEST_APP, BackendType.DOCKER),
        Long.valueOf(7L)
    ));
    Try<DebugSession> stopped = service.open(openCommand(stoppedKey, Long.valueOf(9L)));
    Try<DebugSession> mismatched = service.open(openCommand(mismatchKey, Long.valueOf(10L)));

    assertTrue(missing.isFailure());
    assertTrue(stopped.isFailure());
    assertTrue(mismatched.isFailure());
    assertEquals(0, broker.openInvocationCount);
  }

  @Test
  void shouldRejectSecondActiveSessionForSameRuntime() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    runtimeStateStore.saveInstance(runtimeKey, runtimeInstance(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING));
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(DebugSession.builder()
        .sessionId("dbg-existing")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .build());
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        runtimeStateStore,
        new RecordingDevRuntimeService(runtime(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING)),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:00:00Z"), ZoneOffset.UTC)
    );

    Try<DebugSession> secondOpen = service.open(openCommand(runtimeKey, Long.valueOf(12L)));

    assertTrue(secondOpen.isFailure());
    assertEquals(0, broker.openInvocationCount);
  }

  @Test
  void shouldAllowConcurrentSessionsForDifferentTargetTypesOnSameRuntime() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    runtimeStateStore.saveInstance(runtimeKey, runtimeInstance(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING));
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        runtimeStateStore,
        new RecordingDevRuntimeService(runtime(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING)),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:00:00Z"), ZoneOffset.UTC)
    );

    Try<DebugSession> debugOpen = service.open(openCommand(runtimeKey, Long.valueOf(12L), "DEBUG"));
    Try<DebugSession> backendApiOpen = service.open(openCommand(runtimeKey, Long.valueOf(12L), "BACKEND_API"));

    assertTrue(debugOpen.isSuccess());
    assertTrue(backendApiOpen.isSuccess());
    assertEquals(2, sessionStore.findActiveByRuntimeKey(runtimeKey).size());
    assertEquals(2, broker.openInvocationCount);
  }

  @Test
  void shouldUseRequestedSessionIdWhenPreparationProvidesOne() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    InMemoryRuntimeStateStore runtimeStateStore = new InMemoryRuntimeStateStore();
    runtimeStateStore.saveInstance(runtimeKey, runtimeInstance(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING));
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        runtimeStateStore,
        new RecordingDevRuntimeService(runtime(runtimeKey, Long.valueOf(12L), RuntimeStatus.RUNNING)),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:00:00Z"), ZoneOffset.UTC)
    );

    DebugSession session = service.open(OpenDebugSessionCommand.builder()
        .appName(runtimeKey.getAppName())
        .profile(runtimeKey.getProfile())
        .runtimeKind(runtimeKey.getRuntimeKind())
        .backendType(runtimeKey.getBackendType())
        .expectedGeneration(Long.valueOf(12L))
        .ownerUserId("u-001")
        .ownerUserName("alice")
        .uploadBatchId("upl-001")
        .preparationSessionId("dbg-prep-001")
        .requestedSessionId("dbg-prep-001")
        .build()).get();

    assertEquals("dbg-prep-001", session.getSessionId());
    assertEquals("upl-001", session.getUploadBatchId());
    assertEquals("dbg-prep-001", session.getPreparationSessionId());
  }

  @Test
  void shouldTransitionActiveSessionToClosingBeforeBrokerCloseAndThenClosed() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(DebugSession.builder()
        .sessionId("dbg-close")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(43125))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .build())
        .build());
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DefaultDebugSessionService service = new DefaultDebugSessionService(
        new InMemoryRuntimeStateStore(),
        new RecordingDevRuntimeService(Collections.emptyMap()),
        sessionStore,
        Arrays.asList(new StaticDebugConnector(BackendType.DOCKER)),
        broker,
        properties(),
        Clock.fixed(Instant.parse("2026-03-13T08:05:00Z"), ZoneOffset.UTC)
    );

    service.close("dbg-close").get();

    DebugSession closed = sessionStore.findBySessionId("dbg-close").get();
    assertEquals(Arrays.asList(DebugSessionStatus.CLOSING), broker.closeObservedStatuses);
    assertEquals(DebugSessionStatus.CLOSED, closed.getStatus());
    assertEquals(DebugSessionCloseReason.USER_CLOSED, closed.getCloseReason());
    assertEquals(Instant.parse("2026-03-13T08:05:00Z"), closed.getClosedAt());
  }

  @Test
  void shouldExpireSessionsWhenLifetimeOrIdleTimeoutIsExceeded() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER);
    DebugSessionStore sessionStore = new InMemoryDebugSessionStore();
    sessionStore.save(DebugSession.builder()
        .sessionId("dbg-expire-lifetime")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .expiresAt(Instant.parse("2026-03-13T08:10:00Z"))
        .build());
    sessionStore.save(DebugSession.builder()
        .sessionId("dbg-expire-idle")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .lastActiveAt(Instant.parse("2026-03-13T08:00:00Z"))
        .idleTimeoutSeconds(Integer.valueOf(600))
        .build());
    sessionStore.save(DebugSession.builder()
        .sessionId("dbg-still-active")
        .runtimeKey(runtimeKey)
        .status(DebugSessionStatus.ACTIVE)
        .expiresAt(Instant.parse("2026-03-13T08:30:00Z"))
        .lastActiveAt(Instant.parse("2026-03-13T08:14:59Z"))
        .idleTimeoutSeconds(Integer.valueOf(600))
        .build());
    RecordingDebugTunnelBroker broker = new RecordingDebugTunnelBroker(sessionStore);
    DebugSessionReaper reaper = new DebugSessionReaper(sessionStore, broker);

    List<DebugSession> expired = reaper.reap(Instant.parse("2026-03-13T08:15:00Z"));

    assertEquals(2, expired.size());
    assertEquals(DebugSessionStatus.EXPIRED, sessionStore.findBySessionId("dbg-expire-lifetime").get().getStatus());
    assertEquals(DebugSessionStatus.EXPIRED, sessionStore.findBySessionId("dbg-expire-idle").get().getStatus());
    assertEquals(DebugSessionStatus.ACTIVE, sessionStore.findBySessionId("dbg-still-active").get().getStatus());
    assertTrue(broker.closedSessionIds.contains("dbg-expire-lifetime"));
    assertTrue(broker.closedSessionIds.contains("dbg-expire-idle"));
  }

  private static DevRuntimeProperties properties() {
    return new DevRuntimeProperties();
  }

  private static OpenDebugSessionCommand openCommand(RuntimeIdentityKey runtimeKey, Long expectedGeneration) {
    return openCommand(runtimeKey, expectedGeneration, null);
  }

  private static OpenDebugSessionCommand openCommand(RuntimeIdentityKey runtimeKey, Long expectedGeneration, String targetType) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    if (targetType != null) {
      metadata.put("targetType", targetType);
    }
    return OpenDebugSessionCommand.builder()
        .appName(runtimeKey.getAppName())
        .profile(runtimeKey.getProfile())
        .runtimeKind(runtimeKey.getRuntimeKind())
        .backendType(runtimeKey.getBackendType())
        .expectedGeneration(expectedGeneration)
        .ownerUserId("u-001")
        .ownerUserName("alice")
        .reason("Investigate runtime")
        .metadata(metadata)
        .build();
  }

  private static RuntimeInstance runtime(RuntimeIdentityKey runtimeKey, Long generation, RuntimeStatus status) {
    return RuntimeInstance.builder()
        .runtimeId("rt-" + runtimeKey.getAppName() + "-" + runtimeKey.getProfile())
        .appName(runtimeKey.getAppName())
        .profile(runtimeKey.getProfile())
        .runtimeKind(runtimeKey.getRuntimeKind())
        .backendType(runtimeKey.getBackendType())
        .runtimeGeneration(generation)
        .status(status)
        .build();
  }

  private static RuntimeInstance runtimeInstance(RuntimeIdentityKey runtimeKey, Long generation, RuntimeStatus status) {
    return runtime(runtimeKey, generation, status);
  }

  static class RecordingDevRuntimeService implements DevRuntimeService {
    private final Map<RuntimeIdentityKey, RuntimeInstance> runtimes;

    RecordingDevRuntimeService(RuntimeInstance runtimeInstance) {
      this(Collections.singletonMap(
          RuntimeIdentityKey.of(
              runtimeInstance.getAppName(),
              runtimeInstance.getProfile(),
              runtimeInstance.getRuntimeKind(),
              runtimeInstance.getBackendType()
          ),
          runtimeInstance
      ));
    }

    RecordingDevRuntimeService(Map<RuntimeIdentityKey, RuntimeInstance> runtimes) {
      this.runtimes = runtimes;
    }

    @Override
    public Try<RuntimeInstance> create(com.ouroboros.dev.runtime.model.RuntimeSpec spec) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> start(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> restart(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Void> delete(RuntimeIdentityKey key) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> reloadWorkspace(com.ouroboros.dev.runtime.model.RuntimeSpec spec) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
      RuntimeInstance runtimeInstance = runtimes.get(key);
      return runtimeInstance == null ? Try.failure(new IllegalStateException("runtime not found")) : Try.success(runtimeInstance);
    }

    @Override
    public Try<List<String>> getLogs(RuntimeIdentityKey key) {
      return Try.success(Collections.<String>emptyList());
    }
  }

  static class RecordingDebugTunnelBroker implements TunnelBroker {
    private final DebugSessionStore sessionStore;
    private final List<DebugSessionStatus> openObservedStatuses = new ArrayList<DebugSessionStatus>();
    private final List<DebugSessionStatus> closeObservedStatuses = new ArrayList<DebugSessionStatus>();
    private final List<String> closedSessionIds = new ArrayList<String>();
    private int openInvocationCount;

    RecordingDebugTunnelBroker(DebugSessionStore sessionStore) {
      this.sessionStore = sessionStore;
    }

    @Override
    public Try<DebugSessionBinding> open(DebugSession session, DebugConnector connector, TransportMode transportMode) {
      openInvocationCount++;
      openObservedStatuses.add(sessionStore.findBySessionId(session.getSessionId()).get().getStatus());
      return Try.success(DebugSessionBinding.builder()
          .host("127.0.0.1")
          .port(Integer.valueOf(43125))
          .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
          .build());
    }

    @Override
    public Try<Void> close(String sessionId) {
      closedSessionIds.add(sessionId);
      closeObservedStatuses.add(sessionStore.findBySessionId(sessionId).get().getStatus());
      return Try.success(null);
    }
  }

  static class StaticDebugConnector implements DebugConnector {
    private final BackendType backendType;

    StaticDebugConnector(BackendType backendType) {
      this.backendType = backendType;
    }

    @Override
    public BackendType getBackendType() {
      return backendType;
    }

    @Override
    public Try<Socket> connect(DebugSession session) {
      return Try.failure(new UnsupportedOperationException());
    }
  }
}
