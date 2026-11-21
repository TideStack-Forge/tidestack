package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.BeforeEach;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.dev.app.service.runtime.TestAppProfileResolver;
import com.ouroboros.web.WebApiResult;
import io.vavr.control.Try;

@ExtendWith(MockitoExtension.class)
class AppContainerControllerRuntimeInitTest {

  @Mock
  private AppRuntimeInitService appRuntimeInitService;

  @Mock
  private TestAppProfileResolver testAppProfileResolver;

  @Mock
  private AppContainerService appContainerService;

  private AppContainerController controller;

  @BeforeEach
  void setUp() {
    controller = new AppContainerController();
    ReflectionTestUtils.setField(controller, "appContainerService", appContainerService);
    ReflectionTestUtils.setField(controller, "appRuntimeInitService", appRuntimeInitService);
    ReflectionTestUtils.setField(controller, "testAppProfileResolver", testAppProfileResolver);
  }

  @Test
  void shouldReturnServerDrivenRuntimeCreateInit() {
    RuntimeCreateInitDTO init = new RuntimeCreateInitDTO();
    Map<String, Integer> portBindings = new LinkedHashMap<String, Integer>();
    portBindings.put("web", Integer.valueOf(32080));
    portBindings.put("api", Integer.valueOf(32081));
    init.setDefaultBackendType("DOCKER");
    init.setDefaultExposureStrategy("TEMP_PORT");
    init.setImageInputMode("SELECT_LOCAL_IMAGE");
    init.setPortBindings(portBindings);
    when(testAppProfileResolver.resolveForCreate(org.mockito.ArgumentMatchers.any())).thenReturn("alice");
    when(appRuntimeInitService.buildTestAppCreateInit("crm", "alice")).thenReturn(init);

    WebApiResult<RuntimeCreateInitDTO> result = controller.getCreateInit("crm");

    assertEquals(0, result.getCode());
    assertEquals("DOCKER", result.getData().getDefaultBackendType());
    assertEquals("TEMP_PORT", result.getData().getDefaultExposureStrategy());
    assertEquals("SELECT_LOCAL_IMAGE", result.getData().getImageInputMode());
    Map<String, Integer> payload = result.getData().getPortBindings();
    assertEquals(Integer.valueOf(32080), payload.get("web"));
    assertEquals(Integer.valueOf(32081), payload.get("api"));
    assertFalse(payload.containsKey("apiDebug"));
    verify(appRuntimeInitService).buildTestAppCreateInit("crm", "alice");
  }

  @Test
  void shouldResolveBlankProfileWhenQueryingRuntimeStatus() {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("status", "notFound");
    when(testAppProfileResolver.resolveForCreate(org.mockito.ArgumentMatchers.any())).thenReturn("alice");
    when(appContainerService.getStatus("crm", "alice")).thenReturn(Try.success(payload));

    WebApiResult<?> result = controller.status("crm", "");

    assertEquals(0, result.getCode());
    assertEquals(payload, result.getData());
    verify(appContainerService).getStatus("crm", "alice");
  }

  @Test
  void shouldFallbackToCurrentProfileWhenContainerIdStatusMisses() {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("status", ContainerStatus.RUNNING);
    Map<String, Object> notFound = new LinkedHashMap<String, Object>();
    notFound.put("status", ContainerStatus.NOT_FOUND);
    when(appContainerService.getStatus("crm")).thenReturn(Try.success(notFound));
    when(testAppProfileResolver.resolveForCreate(org.mockito.ArgumentMatchers.any())).thenReturn("alice");
    when(appContainerService.getStatus("crm", "alice")).thenReturn(Try.success(payload));

    WebApiResult<?> result = controller.status("crm");

    assertEquals(0, result.getCode());
    assertEquals(payload, result.getData());
    verify(appContainerService).getStatus("crm");
    verify(appContainerService).getStatus("crm", "alice");
  }
}
