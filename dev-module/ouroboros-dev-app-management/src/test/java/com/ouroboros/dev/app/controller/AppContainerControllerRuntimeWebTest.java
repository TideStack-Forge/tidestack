package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.runtime.TestAppProfileResolver;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class AppContainerControllerRuntimeWebTest {

  @Mock
  private AppContainerService appContainerService;

  @Mock
  private TestAppProfileResolver testAppProfileResolver;

  private AppContainerController controller;

  @BeforeEach
  void setUp() {
    controller = new AppContainerController();
    ReflectionTestUtils.setField(controller, "appContainerService", appContainerService);
    ReflectionTestUtils.setField(controller, "testAppProfileResolver", testAppProfileResolver);
  }

  @Test
  void shouldCreateRuntimeUsingResolvedProfile() {
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName("crm");
    when(testAppProfileResolver.resolveForCreate(request)).thenReturn("alice");
    when(appContainerService.createAndStart(request, "alice")).thenReturn(Try.success("rt-crm-alice"));

    WebApiResult<String> result = controller.create(request);

    assertEquals(0, result.getCode());
    assertEquals("rt-crm-alice", result.getData());
    verify(testAppProfileResolver).resolveForCreate(request);
    verify(appContainerService).createAndStart(request, "alice");
  }

  @Test
  void shouldReturnRuntimeBackedStatusByRuntimeId() {
    Map<String, Object> payload = statusPayload();
    when(appContainerService.getStatus("rt-crm-alice")).thenReturn(Try.success(payload));

    WebApiResult<?> result = controller.status("rt-crm-alice");

    assertEquals(0, result.getCode());
    assertEquals(payload, result.getData());
    verify(appContainerService).getStatus("rt-crm-alice");
  }

  @Test
  void shouldReturnRuntimeBackedStatusByAppAndProfile() {
    Map<String, Object> payload = statusPayload();
    when(appContainerService.getStatus("crm", "alice")).thenReturn(Try.success(payload));

    WebApiResult<?> result = controller.status("crm", "alice");

    assertEquals(0, result.getCode());
    assertEquals(payload, result.getData());
    verify(appContainerService).getStatus("crm", "alice");
  }

  @Test
  void shouldReturnEmptyImageOptionsForKubernetesBackend() {
    assertTrue(controller.getImages("KUBERNETES").getData().getItems().isEmpty());
  }

  private static Map<String, Object> statusPayload() {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", "crm");
    payload.put("containerId", "rt-crm-alice");
    payload.put("containerName", "rt-crm-alice");
    payload.put("status", ContainerStatus.RUNNING);
    payload.put("webPort", 32080);
    payload.put("apiPort", 32081);
    payload.put("debugPort", 32082);
    payload.put("imageVersion", "v1");
    payload.put("imageState", imageState(false));
    return payload;
  }

  private static Map<String, Object> imageState(boolean updateRecommended) {
    Map<String, Object> imageState = new LinkedHashMap<String, Object>();
    imageState.put("reference", "registry.example.com/crm:v1");
    imageState.put("tag", "v1");
    imageState.put("updateRecommended", Boolean.valueOf(updateRecommended));
    return imageState;
  }
}
