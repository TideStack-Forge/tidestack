package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.DebugSessionDTO;
import com.ouroboros.dev.app.model.OpenDebugSessionRequest;
import com.ouroboros.dev.app.service.runtime.DebugRuntimePreparationService;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class RuntimeBackendApiSessionControllerTest {

  @Mock
  private DebugSessionService debugSessionService;
  @Mock
  private DebugRuntimePreparationService debugRuntimePreparationService;
  @Captor
  private ArgumentCaptor<OpenDebugSessionCommand> commandCaptor;
  @Captor
  private ArgumentCaptor<String> sessionIdCaptor;

  private RuntimeBackendApiSessionController controller;

  @BeforeEach
  void setUp() {
    controller = new RuntimeBackendApiSessionController(debugSessionService, new DebugSessionViewAssembler(), debugRuntimePreparationService);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "n/a"));
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldOpenBackendApiSessionThroughService() {
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession()));
    OpenDebugSessionRequest request = buildDefaultRequest();
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("targetType", "DEBUG");
    request.setMetadata(metadata);

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(0, result.getCode());
    assertEquals("api-001", result.getData().getSessionId());
    assertEquals("BACKEND_API", result.getData().getTargetType());
    assertEquals("http://127.0.0.1:38080", result.getData().getEndpoint());
    verify(debugSessionService).open(commandCaptor.capture());
    assertEquals(DebugTunnelTargetType.BACKEND_API, commandCaptor.getValue().getTargetType());
    assertEquals("DEBUG", commandCaptor.getValue().getMetadata().get("targetType"));
  }

  @Test
  void shouldPrepareRuntimeBeforeOpeningBackendApiSessionWhenUploadBatchPresent() {
    OpenDebugSessionRequest request = buildDefaultRequest();
    request.setUploadBatchId("upl-001");
    OpenDebugSessionCommand preparedCommand = OpenDebugSessionCommand.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .targetType(DebugTunnelTargetType.BACKEND_API)
        .ownerUserId("alice")
        .ownerUserName("alice")
        .requestedSessionId("bapi-prep-001")
        .build();
    when(debugRuntimePreparationService.prepare(any(String.class), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001")))
        .thenReturn(Try.success(preparedCommand));
    when(debugSessionService.open(preparedCommand)).thenReturn(Try.success(activeSession()));

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(0, result.getCode());
    verify(debugRuntimePreparationService).prepare(sessionIdCaptor.capture(), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001"));
    assertTrue(sessionIdCaptor.getValue().startsWith("bapi-"));
    verify(debugSessionService).open(preparedCommand);
  }

  @Test
  void shouldGetBackendApiSessionById() {
    when(debugSessionService.get("api-001")).thenReturn(Try.success(activeSession()));

    WebApiResult<DebugSessionDTO> result = controller.get("api-001");

    assertEquals(0, result.getCode());
    assertEquals("api-001", result.getData().getSessionId());
    assertEquals("BACKEND_API", result.getData().getTargetType());
    assertEquals("ACTIVE", result.getData().getStatus());
  }

  @Test
  void shouldCloseBackendApiSessionById() {
    when(debugSessionService.close("api-001")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.close("api-001");

    assertEquals(0, result.getCode());
    verify(debugSessionService).close("api-001");
  }

  private static DebugSession activeSession() {
    return DebugSession.builder()
        .sessionId("api-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES))
        .targetType(DebugTunnelTargetType.BACKEND_API)
        .uploadBatchId("upl-001")
        .preparationSessionId("bapi-prep-001")
        .ownerUserId("alice")
        .ownerUserName("alice")
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .requestedAt(Instant.parse("2026-03-13T08:00:00Z"))
        .openedAt(Instant.parse("2026-03-13T08:00:05Z"))
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(38080))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .targetType(DebugTunnelTargetType.BACKEND_API)
            .clientAccessHint("ssh -L 8080:127.0.0.1:38080 develop.example.com")
            .build())
        .build();
  }

  private static OpenDebugSessionRequest buildDefaultRequest() {
    OpenDebugSessionRequest request = new OpenDebugSessionRequest();
    request.setAppName("crm");
    request.setProfile("alice");
    request.setRuntimeKind("TEST_APP");
    request.setBackendType("KUBERNETES");
    request.setExpectedGeneration(Long.valueOf(7L));
    request.setReason("connect backend api");
    return request;
  }
}
