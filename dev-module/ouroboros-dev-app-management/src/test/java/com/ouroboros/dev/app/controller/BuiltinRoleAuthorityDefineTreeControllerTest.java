package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class BuiltinRoleAuthorityDefineTreeControllerTest {

  @Mock
  private RuntimeStateStore runtimeStateStore;
  @Mock
  private DebugSessionService debugSessionService;
  @Captor
  private ArgumentCaptor<OpenDebugSessionCommand> commandCaptor;

  private HttpServer server;
  private String requestedPath;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/sys/authority/define-tree", exchange -> {
      requestedPath = exchange.getRequestURI().getPath();
      byte[] responseBody = "{\"code\":0,\"msg\":\"success\",\"data\":[{\"title\":\"应用权限\",\"fullName\":\"workflow.assign\"}]}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, responseBody.length);
      try (OutputStream outputStream = exchange.getResponseBody()) {
        outputStream.write(responseBody);
      }
    });
    server.start();
  }

  @Test
  void shouldLoadAuthorityTreeThroughHelperBackendApiSession() {
    RuntimeIdentityKey runtimeKey = RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.KUBERNETES);
    Map<RuntimeIdentityKey, RuntimeSpec> specs = new LinkedHashMap<RuntimeIdentityKey, RuntimeSpec>();
    specs.put(runtimeKey, RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .build());
    Map<RuntimeIdentityKey, RuntimeInstance> instances = new LinkedHashMap<RuntimeIdentityKey, RuntimeInstance>();
    instances.put(runtimeKey, RuntimeInstance.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .status(RuntimeStatus.RUNNING)
        .build());
    when(runtimeStateStore.allSpecs(RuntimeKind.DEV_HELPER)).thenReturn(specs);
    when(runtimeStateStore.allInstances()).thenReturn(instances);
    when(debugSessionService.open(any(OpenDebugSessionCommand.class))).thenReturn(Try.success(activeSession(server.getAddress().getPort())));
    when(debugSessionService.close("bapi-001")).thenReturn(Try.success(null));

    BuiltinRoleAuthorityDefineTreeController controller = new BuiltinRoleAuthorityDefineTreeController(runtimeStateStore, debugSessionService, new ObjectMapper());
    WebApiResult<Object> result = controller.authorityDefineTree("crm");

    assertEquals(0, result.getCode());
    assertEquals("/api/sys/authority/define-tree", requestedPath);
    assertTrue(result.getData() instanceof java.util.List);
    verify(debugSessionService).open(commandCaptor.capture());
    assertEquals(DebugTunnelTargetType.BACKEND_API, commandCaptor.getValue().getTargetType());
    assertEquals(RuntimeKind.DEV_HELPER, commandCaptor.getValue().getRuntimeKind());
    assertEquals("dev-helper", commandCaptor.getValue().getProfile());
    verify(debugSessionService).close("bapi-001");
  }

  @Test
  void shouldReturnClearErrorWhenHelperIsNotRunning() {
    when(runtimeStateStore.allSpecs(RuntimeKind.DEV_HELPER)).thenReturn(Collections.emptyMap());

    BuiltinRoleAuthorityDefineTreeController controller = new BuiltinRoleAuthorityDefineTreeController(runtimeStateStore, debugSessionService, new ObjectMapper());
    BusinessException exception = assertThrows(BusinessException.class, () -> controller.authorityDefineTree("crm"));

    assertTrue(exception.getMessage().contains("开发助手未运行"));
  }

  private static DebugSession activeSession(int port) {
    return DebugSession.builder()
        .sessionId("bapi-001")
        .status(DebugSessionStatus.ACTIVE)
        .targetType(DebugTunnelTargetType.BACKEND_API)
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(port))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .targetType(DebugTunnelTargetType.BACKEND_API)
            .build())
        .build();
  }
}
