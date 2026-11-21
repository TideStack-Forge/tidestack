package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeModelSurfaceTest {

  @Test
  void shouldExposeSpecAndInstanceFieldsRequiredByRuntimeLayer() {
    Map<String, String> env = new HashMap<String, String>();
    env.put("SPRING_PROFILES_ACTIVE", "alice");
    Map<String, String> labels = new HashMap<String, String>();
    labels.put("app", "crm");
    Map<String, Integer> requestedPorts = new HashMap<String, Integer>();
    requestedPorts.put("web", Integer.valueOf(30080));
    requestedPorts.put("api", Integer.valueOf(30081));
    Map<String, String> resourceLimits = new HashMap<String, String>();
    resourceLimits.put("cpu", "500m");
    resourceLimits.put("memory", "512Mi");
    Map<String, String> accessRef = new HashMap<String, String>();
    accessRef.put("web", "https://crm-alice.example.com");
    Map<String, String> backendMetadata = new HashMap<String, String>();
    backendMetadata.put("deployment", "devrt-test-app-crm-alice");

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .requestedPorts(requestedPorts)
        .image("crm:v1")
        .env(env)
        .labels(labels)
        .resourceLimits(resourceLimits)
        .ttl(Duration.ofMinutes(30))
        .build();
    WorkspaceRef provisionedWorkspace = WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.PVC)
        .pvcName("dev-runtime")
        .namespace("ouroboros-dev")
        .subPath("crm/alice")
        .build();
    WorkspaceRef hostWorkspace = WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .hostPath("/workspace/crm/alice")
        .build();
    RuntimeSpec resolvedSpec = spec.withWorkspaceRef(provisionedWorkspace);

    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .status(RuntimeStatus.RUNNING)
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName("dev-runtime")
            .namespace("ouroboros-dev")
            .subPath("crm/alice/runtime")
            .build())
        .accessRef(accessRef)
        .backendMetadata(backendMetadata)
        .build();

    assertEquals(ExposureStrategy.STABLE_DOMAIN, spec.getExposureStrategy());
    assertEquals(Integer.valueOf(30080), spec.getRequestedPorts().get("web"));
    assertEquals("crm:v1", spec.getImage());
    assertEquals("500m", spec.getResourceLimits().get("cpu"));
    assertEquals(provisionedWorkspace, resolvedSpec.getWorkspaceRef());
    assertEquals(Integer.valueOf(30081), resolvedSpec.getRequestedPorts().get("api"));
    assertEquals("/workspace/crm/alice", hostWorkspace.getHostPath());
    assertEquals(RuntimeStatus.RUNNING, instance.getStatus());
    assertNotNull(instance.getWorkspaceRef());
    assertEquals("https://crm-alice.example.com", instance.getAccessRef().get("web"));
    assertEquals("devrt-test-app-crm-alice", instance.getBackendMetadata().get("deployment"));
  }

  @Test
  void shouldRejectWorkspaceRefsThatDoNotMatchModeConstraints() {
    assertThrows(IllegalArgumentException.class, () -> WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .pvcName("dev-runtime")
        .namespace("ouroboros-dev")
        .subPath("crm/alice")
        .build());
    assertThrows(IllegalArgumentException.class, () -> WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.PVC)
        .build());

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .build();

    assertThrows(IllegalArgumentException.class, () -> spec.withWorkspaceRef(WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.PVC)
        .pvcName("dev-runtime")
        .namespace("ouroboros-dev")
        .subPath("crm/alice")
        .build()));
  }

  @Test
  void shouldDefensivelyCopyMapsAndExposeImmutableViews() {
    Map<String, String> env = new HashMap<String, String>();
    env.put("SPRING_PROFILES_ACTIVE", "alice");
    Map<String, Integer> requestedPorts = new HashMap<String, Integer>();
    requestedPorts.put("web", Integer.valueOf(30080));
    Map<String, String> accessRef = new HashMap<String, String>();
    accessRef.put("web", "https://crm-alice.example.com");
    Map<String, String> backendMetadata = new HashMap<String, String>();
    backendMetadata.put("deployment", "devrt-test-app-crm-alice");

    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .requestedPorts(requestedPorts)
        .env(env)
        .build();
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId("rt-crm-alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .status(RuntimeStatus.RUNNING)
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName("dev-runtime")
            .namespace("ouroboros-dev")
            .subPath("crm/alice/runtime")
            .build())
        .accessRef(accessRef)
        .backendMetadata(backendMetadata)
        .build();

    env.put("SPRING_PROFILES_ACTIVE", "bob");
    requestedPorts.put("web", Integer.valueOf(39999));
    accessRef.put("web", "https://crm-bob.example.com");
    backendMetadata.put("deployment", "mutated");

    assertEquals("alice", spec.getEnv().get("SPRING_PROFILES_ACTIVE"));
    assertEquals(Integer.valueOf(30080), spec.getRequestedPorts().get("web"));
    assertEquals("https://crm-alice.example.com", instance.getAccessRef().get("web"));
    assertEquals("devrt-test-app-crm-alice", instance.getBackendMetadata().get("deployment"));
    assertThrows(UnsupportedOperationException.class, () -> spec.getEnv().put("NEW_KEY", "value"));
    assertThrows(UnsupportedOperationException.class, () -> spec.getRequestedPorts().put("api", Integer.valueOf(30081)));
    assertThrows(UnsupportedOperationException.class, () -> instance.getAccessRef().put("api", "/api"));
    assertThrows(UnsupportedOperationException.class, () -> instance.getBackendMetadata().put("service", "svc"));
  }
}
