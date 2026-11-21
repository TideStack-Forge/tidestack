package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;

class AppRuntimeViewAssemblerTest {

  @Test
  void shouldPreserveRuntimeEndpointsAndCapabilityFlags() {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("OUROBOROS_APP_NAME", "crm");
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(ExposureStrategy.TEMP_PORT)
        .image("registry.example.com/crm:v1")
        .env(Collections.singletonMap("SPRING_APPLICATION_NAME", "crm"))
        .labels(labels)
        .build();
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .runtimeGeneration(Long.valueOf(3L))
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("http://127.0.0.1:32080")
        .apiEndpoint("http://127.0.0.1:32081")
        .capabilities(RuntimeCapability.builder()
            .supportsPause(false)
            .supportsDebugPort(true)
            .supportsDebugSession(true)
            .supportsWorkspaceHotReload(true)
            .supportsNodePortExposure(true)
            .supportsIngressExposure(false)
            .build())
        .backendMetadata(kubernetesMetadata())
        .build();
    DebugSession activeSession = DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES))
        .status(DebugSessionStatus.ACTIVE)
        .debugAccessStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .openedAt(Instant.parse("2026-03-13T08:00:00Z"))
        .binding(DebugSessionBinding.builder()
            .host("127.0.0.1")
            .port(Integer.valueOf(5005))
            .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
            .clientAccessHint("ssh -L 5005:127.0.0.1:5005 develop.example.com")
            .build())
        .build();
    AppRuntimeViewAssembler assembler = new AppRuntimeViewAssembler(new DebugSessionViewAssembler());

    AppContainersDTO dto = assembler.assemble(
        RuntimeFactoryTestSupport.appInfo("crm", "CRM"),
        Collections.singletonList(AppRuntimeViewAssembler.RuntimeView.of(spec, instance, activeSession))
    );

    AppContainersDTO.Container containerDto = dto.getChildren().get(0);
    assertEquals(ContainerStatus.RUNNING, containerDto.getStatus());
    assertEquals("alice", containerDto.getProfile());
    assertEquals("TEST_APP", containerDto.getRuntimeKind());
    assertEquals(Long.valueOf(3L), containerDto.getRuntimeGeneration());
    assertEquals("KUBERNETES", containerDto.getBackendType());
    assertEquals("TEMP_PORT", containerDto.getExposureStrategy());
    assertEquals("service=devrt-test-app-crm-alice-svc", containerDto.getBackendSummary());
    assertEquals("devrt-test-app-crm-alice-svc", containerDto.getBackendDetails().get("service"));
    assertEquals("devrt-test-app-crm-alice", containerDto.getBackendDetails().get("deployment"));
    assertEquals("devrt-test-app-crm-alice-ing", containerDto.getBackendDetails().get("ingress"));
    assertEquals("http://127.0.0.1:32080", containerDto.getWebEndpoint());
    assertEquals("http://127.0.0.1:32081", containerDto.getApiEndpoint());
    assertEquals("tcp://127.0.0.1:5005", containerDto.getDebugEndpoint());
    assertEquals(Integer.valueOf(32080), containerDto.getPortBindings().get("web"));
    assertFalse(containerDto.getPortBindings().containsKey("apiDebug"));
    assertEquals(Boolean.FALSE, containerDto.getCapabilities().get("supportsPause"));
    assertEquals(Boolean.TRUE, containerDto.getCapabilities().get("supportsDebugSession"));
    assertEquals(Boolean.TRUE, containerDto.getCapabilities().get("supportsNodePortExposure"));
    assertEquals("dbg-001", containerDto.getActiveDebugSessionId());
    assertEquals("SESSION_TUNNEL", containerDto.getDebugCapability().getStrategy());
    assertEquals("LOOPBACK_ONLY", containerDto.getDebugCapability().getBindingScope());
    assertEquals(Boolean.FALSE, containerDto.getDebugCapability().getSessionOpenable());
    assertNotNull(containerDto.getEnvironmentalVariables());
  }

  private static Map<String, String> kubernetesMetadata() {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("deployment", "devrt-test-app-crm-alice");
    metadata.put("service", "devrt-test-app-crm-alice-svc");
    metadata.put("serviceType", "NodePort");
    metadata.put("ingress", "devrt-test-app-crm-alice-ing");
    return metadata;
  }
}
