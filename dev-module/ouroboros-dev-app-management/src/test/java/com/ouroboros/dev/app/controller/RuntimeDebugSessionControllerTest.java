package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class RuntimeDebugSessionControllerTest {

  @Mock
  private DebugSessionService debugSessionService;
  @Mock
  private DebugRuntimePreparationService debugRuntimePreparationService;
  @Captor
  private ArgumentCaptor<OpenDebugSessionCommand> commandCaptor;

  private RuntimeDebugSessionController controller;

  @BeforeEach
  void setUp() {
    controller = new RuntimeDebugSessionController(debugSessionService, new DebugSessionViewAssembler(), debugRuntimePreparationService);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "n/a"));
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldOpenDebugSessionThroughService() {
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession()));
    OpenDebugSessionRequest request = new OpenDebugSessionRequest();
    request.setAppName("crm");
    request.setProfile("alice");
    request.setRuntimeKind("TEST_APP");
    request.setBackendType("KUBERNETES");
    request.setExpectedGeneration(Long.valueOf(7L));
    request.setReason("attach debugger");

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(0, result.getCode());
    assertEquals("dbg-001", result.getData().getSessionId());
    assertEquals("upl-001", result.getData().getUploadBatchId());
    assertEquals("dbg-prep-001", result.getData().getPreparationSessionId());
    assertEquals("DEBUG", result.getData().getTargetType());
    assertEquals("tcp://127.0.0.1:5005", result.getData().getEndpoint());
    verify(debugSessionService).open(any(OpenDebugSessionCommand.class));
  }

  @Test
  void shouldPrepareRuntimeBeforeOpeningDebugSessionWhenUploadBatchPresent() {
    OpenDebugSessionRequest request = new OpenDebugSessionRequest();
    request.setAppName("crm");
    request.setProfile("alice");
    request.setRuntimeKind("TEST_APP");
    request.setBackendType("KUBERNETES");
    request.setUploadBatchId("upl-001");
    OpenDebugSessionCommand preparedCommand = OpenDebugSessionCommand.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .ownerUserId("alice")
        .ownerUserName("alice")
        .requestedSessionId("dbg-prep-001")
        .build();
    when(debugRuntimePreparationService.prepare(org.mockito.ArgumentMatchers.anyString(), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001")))
        .thenReturn(Try.success(preparedCommand));
    when(debugSessionService.open(preparedCommand)).thenReturn(Try.success(activeSession()));

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(0, result.getCode());
    verify(debugRuntimePreparationService).prepare(org.mockito.ArgumentMatchers.anyString(), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001"));
    verify(debugSessionService).open(preparedCommand);
  }

  @Test
  void shouldNotOpenDebugSessionWhenPreparationFails() {
    OpenDebugSessionRequest request = new OpenDebugSessionRequest();
    request.setAppName("crm");
    request.setProfile("alice");
    request.setRuntimeKind("TEST_APP");
    request.setBackendType("KUBERNETES");
    request.setUploadBatchId("upl-001");
    when(debugRuntimePreparationService.prepare(org.mockito.ArgumentMatchers.anyString(), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001")))
        .thenReturn(Try.failure(new IllegalStateException("temporary resolution failed")));

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(false, Integer.valueOf(0).equals(result.getCode()));
    verify(debugRuntimePreparationService).prepare(org.mockito.ArgumentMatchers.anyString(), any(OpenDebugSessionCommand.class), org.mockito.ArgumentMatchers.eq("upl-001"));
  }

  @Test
  void shouldCaptureOwnerFromSeamAuthentication() {
    CurrentSecurityContextHolder.setDelegate(seamAuthenticationContext("alice"));
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession()));
    controller.open(buildDefaultRequest());

    verify(debugSessionService).open(commandCaptor.capture());
    OpenDebugSessionCommand captured = commandCaptor.getValue();
    assertEquals("alice", captured.getOwnerUserId());
    assertEquals("alice", captured.getOwnerUserName());
  }

  @Test
  void shouldForceDebugTargetTypeWhenRequestMetadataContainsAnotherTarget() {
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession()));
    OpenDebugSessionRequest request = buildDefaultRequest();
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("targetType", "BACKEND_API");
    request.setMetadata(metadata);

    WebApiResult<DebugSessionDTO> result = controller.open(request);

    assertEquals(0, result.getCode());
    assertEquals("DEBUG", result.getData().getTargetType());
    verify(debugSessionService).open(commandCaptor.capture());
    assertEquals(DebugTunnelTargetType.DEBUG, commandCaptor.getValue().getTargetType());
    assertTrue("BACKEND_API".equals(commandCaptor.getValue().getMetadata().get("targetType")));
  }

  @Test
  void shouldNotUseLegacyPrincipalWhenSeamAuthenticationEmpty() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("legacy-principal", "n/a"));
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession()));
    controller.open(buildDefaultRequest());

    verify(debugSessionService).open(commandCaptor.capture());
    OpenDebugSessionCommand captured = commandCaptor.getValue();
    assertEquals(null, captured.getOwnerUserId());
    assertEquals(null, captured.getOwnerUserName());
    assertNotEquals("legacy-principal", captured.getOwnerUserId());
  }

  @Test
  void shouldGetDebugSessionById() {
    when(debugSessionService.get("dbg-001")).thenReturn(Try.success(activeSession()));

    WebApiResult<DebugSessionDTO> result = controller.get("dbg-001");

    assertEquals(0, result.getCode());
    assertEquals("dbg-001", result.getData().getSessionId());
    assertEquals("ACTIVE", result.getData().getStatus());
    assertEquals("DEBUG", result.getData().getTargetType());
  }

  @Test
  void shouldCloseDebugSessionById() {
    when(debugSessionService.close("dbg-001")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.close("dbg-001");

    assertEquals(0, result.getCode());
    verify(debugSessionService).close("dbg-001");
  }

  private static DebugSession activeSession() {
    return DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES))
        .targetType(DebugTunnelTargetType.DEBUG)
        .uploadBatchId("upl-001")
        .preparationSessionId("dbg-prep-001")
        .ownerUserId("alice")
        .ownerUserName("alice")
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .requestedAt(Instant.parse("2026-03-13T08:00:00Z"))
        .openedAt(Instant.parse("2026-03-13T08:00:05Z"))
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(5005))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .targetType(DebugTunnelTargetType.DEBUG)
            .clientAccessHint("ssh -L 5005:127.0.0.1:5005 develop.example.com")
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
    request.setReason("attach debugger");
    return request;
  }

  private static CurrentSecurityContext seamAuthenticationContext(String userIdentity) {
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userIdentity, "n/a");
    return new CurrentSecurityContext() {
      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(OuroborosAuthenticationHolder.adapt(authentication));
      }

      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }
    };
  }
}
