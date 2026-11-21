package com.ouroboros.dev.app.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.event.DevHelperStatusChangedEvent;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.websocket.StompMessageSender;

import io.vavr.control.Try;

@ExtendWith(MockitoExtension.class)
class DevRuntimeStatusPushBridgeTest {

  @Mock
  private StompMessageSender stompMessageSender;

  @Mock
  private AppContainerService appContainerService;

  @Mock
  private AppDevHelperContainerService appDevHelperContainerService;

  @Test
  void shouldPushResolvedTestAppStatusPayloadToAppContainerTopic() {
    when(appContainerService.getStatus("crm", "alice")).thenReturn(Try.success(testAppStatusPayload(false)));
    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onRuntimeEvent(runtimeEvent(RuntimeKind.TEST_APP, "crm", "alice"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stompMessageSender).send(eq("/topic/app-container/crm-alice"), payloadCaptor.capture());
    verify(stompMessageSender).sendToUser(eq("alice"), eq("/queue/app/crm/status"), eq(payloadCaptor.getValue()));
    assertEquals(ContainerStatus.RUNNING, payloadCaptor.getValue().get("status"));
    assertEquals("DOCKER", payloadCaptor.getValue().get("backendType"));
    assertEquals("rt-crm-alice", payloadCaptor.getValue().get("containerId"));
    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payloadCaptor.getValue().get("imageState");
    assertEquals("registry.example.com/crm:v2", imageState.getReference());
    assertEquals(Boolean.FALSE, imageState.getUpdateRecommended());
  }

  @Test
  void shouldFallbackToRuntimePayloadWhenTestAppStatusLookupFails() {
    when(appContainerService.getStatus("crm", "alice"))
        .thenReturn(Try.failure(new IllegalStateException("test app status unavailable")));
    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onRuntimeEvent(runtimeEvent(RuntimeKind.TEST_APP, "crm", "alice"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stompMessageSender).send(eq("/topic/app-container/crm-alice"), payloadCaptor.capture());
    assertEquals(ContainerStatus.RUNNING, payloadCaptor.getValue().get("status"));
    assertEquals("KUBERNETES", payloadCaptor.getValue().get("backendType"));
    assertEquals("rt-crm-alice", payloadCaptor.getValue().get("containerId"));
  }

  @Test
  void shouldPushHelperRuntimeEventsToHelperTopic() {
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(helperStatusPayload()));

    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onRuntimeEvent(runtimeEvent(RuntimeKind.DEV_HELPER, "crm", "dev-helper"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stompMessageSender).send(eq("/topic/dev-helper/crm"), payloadCaptor.capture());
    assertEquals(ContainerStatus.RUNNING, payloadCaptor.getValue().get("status"));
    assertEquals("registry.example.com/helper:1.1.0", payloadCaptor.getValue().get("imageReference"));
    assertEquals(Boolean.FALSE, payloadCaptor.getValue().get("updateRecommended"));
    RuntimeImageViewDTO imageState = (RuntimeImageViewDTO) payloadCaptor.getValue().get("imageState");
    assertEquals(Boolean.FALSE, imageState.getUpdateRecommended());
    assertFalse(payloadCaptor.getValue().containsKey("runtimeId"));
  }

  @Test
  void shouldFallbackToRuntimePayloadWhenHelperStatusLookupFails() {
    when(appDevHelperContainerService.getStatus("crm"))
        .thenReturn(Try.failure(new IllegalStateException("helper status unavailable")));

    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onRuntimeEvent(runtimeEvent(RuntimeKind.DEV_HELPER, "crm", "dev-helper"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stompMessageSender).send(eq("/topic/dev-helper/crm"), payloadCaptor.capture());
    assertEquals(ContainerStatus.RUNNING, payloadCaptor.getValue().get("status"));
    assertEquals("rt-crm-dev-helper", payloadCaptor.getValue().get("runtimeId"));
    assertEquals("KUBERNETES", payloadCaptor.getValue().get("backendType"));
  }

  @Test
  void shouldIgnoreHelperRuntimeLogOutputEvents() {
    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onRuntimeEvent(runtimeLogOutputEvent());

    verify(stompMessageSender, never()).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldPushHelperStatusChangedEventsToHelperTopic() {
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(helperStatusPayload()));

    DevRuntimeStatusPushBridge bridge = bridge();

    bridge.onHelperStatusChanged(new DevHelperStatusChangedEvent("crm"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stompMessageSender).send(eq("/topic/dev-helper/crm"), payloadCaptor.capture());
    assertEquals(ContainerStatus.RUNNING, payloadCaptor.getValue().get("status"));
    assertEquals("registry.example.com/helper:1.1.0", payloadCaptor.getValue().get("imageReference"));
  }

  private DevRuntimeStatusPushBridge bridge() {
    return new DevRuntimeStatusPushBridge(stompMessageSender, appContainerService, appDevHelperContainerService);
  }

  private static DevRuntimeEvent runtimeEvent(RuntimeKind runtimeKind, String appName, String profile) {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_STATUS_CHANGED)
        .runtimeId("rt-" + appName + "-" + profile)
        .appName(appName)
        .profile(profile)
        .runtimeKind(runtimeKind)
        .backendType(BackendType.KUBERNETES)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("http://127.0.0.1:32080")
        .apiEndpoint("http://127.0.0.1:32081")
        .debugEndpoint("tcp://127.0.0.1:32082")
        .capabilities(RuntimeCapability.builder()
            .supportsPause(false)
            .supportsDebugPort(true)
            .supportsWorkspaceHotReload(true)
            .supportsNodePortExposure(true)
            .build())
        .backendMetadata(Collections.singletonMap("containerName", "rt-" + appName + "-" + profile))
        .build();
  }

  private static DevRuntimeEvent runtimeLogOutputEvent() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_LOG_OUTPUT)
        .runtimeId("rt-crm-dev-helper")
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .build();
  }

  private static Map<String, Object> helperStatusPayload() {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", "crm");
    payload.put("containerId", "rt-crm-dev-helper");
    payload.put("containerName", "ouroboros-dev-helper-crm");
    payload.put("status", ContainerStatus.RUNNING);
    payload.put("runtimeGeneration", Long.valueOf(7L));
    payload.put("refreshPending", Boolean.FALSE);
    payload.put("imageState", helperImageState(false));
    payload.put("imageReference", "registry.example.com/helper:1.1.0");
    payload.put("imageRepository", "registry.example.com/helper");
    payload.put("imageTag", "1.1.0");
    payload.put("imageDigest", "sha256:1234");
    payload.put("imageInputMode", "INPUT_IMAGE_REFERENCE");
    payload.put("imageCapabilities", Collections.singletonMap("supportsAutoUpdate", Boolean.TRUE));
    payload.put("updateRecommended", Boolean.FALSE);
    payload.put("imageVersion", "1.1.0");
    payload.put("lastLoadTime", "2026-06-05T10:00:00");
    return payload;
  }

  private static Map<String, Object> testAppStatusPayload(boolean updateRecommended) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", "crm");
    payload.put("profile", "alice");
    payload.put("containerId", "rt-crm-alice");
    payload.put("containerName", "ouroboros-test-app-crm-alice");
    payload.put("status", ContainerStatus.RUNNING);
    payload.put("runtimeGeneration", Long.valueOf(8L));
    payload.put("backendType", "DOCKER");
    payload.put("imageState", testAppImageState(updateRecommended));
    payload.put("imageVersion", "v2");
    return payload;
  }

  private static RuntimeImageViewDTO testAppImageState(boolean updateRecommended) {
    RuntimeImageViewDTO imageState = new RuntimeImageViewDTO();
    imageState.setReference("registry.example.com/crm:v2");
    imageState.setRepository("registry.example.com/crm");
    imageState.setTag("v2");
    imageState.setInputMode("SELECT_LOCAL_IMAGE");
    imageState.setUpdateRecommended(Boolean.valueOf(updateRecommended));
    imageState.setCapabilities(Collections.singletonMap("supportsLocalCatalog", Boolean.TRUE));
    return imageState;
  }

  private static RuntimeImageViewDTO helperImageState(boolean updateRecommended) {
    RuntimeImageViewDTO imageState = new RuntimeImageViewDTO();
    imageState.setReference("registry.example.com/helper:1.1.0");
    imageState.setRepository("registry.example.com/helper");
    imageState.setTag("1.1.0");
    imageState.setDigest("sha256:1234");
    imageState.setInputMode("INPUT_IMAGE_REFERENCE");
    imageState.setUpdateRecommended(Boolean.valueOf(updateRecommended));
    imageState.setCapabilities(Collections.singletonMap("supportsAutoUpdate", Boolean.TRUE));
    return imageState;
  }
}
