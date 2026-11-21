package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.LocalDateTime;
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

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class AppDevHelperControllerRuntimeWebTest {

  @Mock
  private AppDevHelperContainerService appDevHelperContainerService;

  @Mock
  private AppRuntimeInitService appRuntimeInitService;

  private AppDevHelperController controller;

  @BeforeEach
  void setUp() {
    controller = new AppDevHelperController();
    ReflectionTestUtils.setField(controller, "devHelperContainerService", appDevHelperContainerService);
    ReflectionTestUtils.setField(controller, "appRuntimeInitService", appRuntimeInitService);
  }

  @Test
  void shouldReturnRuntimeBackedHelperStatus() {
    Map<String, Object> payload = statusPayload();
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(payload));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(payload, result.getData());
    assertEquals("registry.example.com/helper:1.0.0", ((Map<?, ?>) result.getData()).get("imageReference"));
    assertFalse(((Map<?, ?>) result.getData()).containsKey("logTopic"));
    verify(appDevHelperContainerService).getStatus("crm");
    verify(appDevHelperContainerService, never()).updateImage("crm");
  }

  @Test
  void shouldDelegateManualUpdateImage() {
    Map<String, Object> stale = statusPayload();
    stale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(0, result.getCode());
    assertEquals("rt-crm-dev-helper", result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldDelegateManualUpdateImageWhileStopped() {
    Map<String, Object> stale = statusPayload(ContainerStatus.STOPPED);
    stale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(0, result.getCode());
    assertEquals("rt-crm-dev-helper", result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldDelegateReloadToHelperService() {
    when(appDevHelperContainerService.reload("crm")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.reload("crm");

    assertEquals(0, result.getCode());
    verify(appDevHelperContainerService).reload("crm");
  }

  @Test
  void shouldReturnHelperCreateInit() {
    RuntimeCreateInitDTO init = new RuntimeCreateInitDTO();
    init.setDefaultBackendType("KUBERNETES");
    init.setImageInputMode("INPUT_IMAGE_REFERENCE");
    init.setAvailableBackends(Collections.singletonList(new SelectOption("KUBERNETES", "Kubernetes")));
    when(appRuntimeInitService.buildDevHelperCreateInit("crm")).thenReturn(init);

    WebApiResult<RuntimeCreateInitDTO> result = controller.createInit("crm");

    assertEquals(0, result.getCode());
    assertEquals("KUBERNETES", result.getData().getDefaultBackendType());
    assertEquals("INPUT_IMAGE_REFERENCE", result.getData().getImageInputMode());
    assertEquals("KUBERNETES", result.getData().getAvailableBackends().get(0).getValue());
    assertNull(result.getData().getAvailableExposureStrategies());
    verify(appRuntimeInitService).buildDevHelperCreateInit("crm");
  }

  @Test
  void shouldAutoUpdateHelperOnceDuringStatusPollingWhenImageStateRequestsUpdate() {
    Map<String, Object> stale = statusPayload();
    stale.put("imageState", imageState(true));
    Map<String, Object> refreshed = statusPayload();
    refreshed.put("imageState", imageState(false));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale), Try.success(refreshed));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(refreshed, result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldAutoUpdateHelperWhenStatusUsesRuntimeImageViewDto() {
    Map<String, Object> stale = statusPayload();
    stale.put("imageState", imageStateDto("registry.example.com/helper:1.0.0", true));
    Map<String, Object> refreshed = statusPayload();
    refreshed.put("imageState", imageStateDto("registry.example.com/helper:1.1.0", false));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale), Try.success(refreshed));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(refreshed, result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldAutoUpdateHelperWhileLaunchingWhenImageStateRequestsUpdate() {
    Map<String, Object> launching = statusPayload(ContainerStatus.LAUNCHING);
    launching.put("imageState", imageState(true));
    Map<String, Object> refreshed = statusPayload(ContainerStatus.LAUNCHING);
    refreshed.put("imageState", imageState(false));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(launching), Try.success(refreshed));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(refreshed, result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldNotAutoUpdateStoppedHelperWhenImageStateRequestsUpdate() {
    Map<String, Object> stale = statusPayload(ContainerStatus.STOPPED);
    stale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(stale, result.getData());
    verify(appDevHelperContainerService, never()).updateImage("crm");
  }

  @Test
  void shouldClearStaleAutoUpdateFingerprintWhenHelperStopsAndAllowAutoUpdateAfterRestart() {
    Map<String, Object> runningStale = statusPayload(ContainerStatus.RUNNING);
    runningStale.put("imageState", imageState(true));
    Map<String, Object> stoppedStale = statusPayload(ContainerStatus.STOPPED);
    stoppedStale.put("imageState", imageState(true));
    Map<String, Object> restartedStale = statusPayload(ContainerStatus.RUNNING);
    restartedStale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(
        Try.success(runningStale),
        Try.success(runningStale),
        Try.success(stoppedStale),
        Try.success(restartedStale),
        Try.success(restartedStale)
    );
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    controller.status("crm", false);
    controller.status("crm", false);
    controller.status("crm", false);
    controller.status("crm", false);

    verify(appDevHelperContainerService, times(2)).updateImage("crm");
  }

  @Test
  void shouldRejectManualUpdateImageWhenHelperIsNotFound() {
    Map<String, Object> missing = statusPayload(ContainerStatus.NOT_FOUND);
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(missing));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(-4000, result.getCode());
    assertEquals("当前没有可更新的镜像", result.getMsg());
    verify(appDevHelperContainerService, never()).updateImage("crm");
  }

  @Test
  void shouldPropagateStatusLookupFailureForManualUpdateImage() {
    when(appDevHelperContainerService.getStatus("crm"))
        .thenReturn(Try.failure(new BusinessException("status lookup failed")));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(-4000, result.getCode());
    assertEquals("status lookup failed", result.getMsg());
    verify(appDevHelperContainerService, never()).updateImage("crm");
  }

  @Test
  void shouldNotRepeatedlyAutoUpdateHelperAcrossPollingWhenImageStateRemainsUnchanged() {
    Map<String, Object> stale = statusPayload();
    stale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<?> first = controller.status("crm", false);
    WebApiResult<?> second = controller.status("crm", false);
    WebApiResult<?> third = controller.status("crm", false);

    assertEquals(stale, first.getData());
    assertEquals(stale, second.getData());
    assertEquals(stale, third.getData());
    assertEquals(((Map<?, ?>) first.getData()).get("containerId"), ((Map<?, ?>) second.getData()).get("containerId"));
    assertEquals(((Map<?, ?>) second.getData()).get("containerId"), ((Map<?, ?>) third.getData()).get("containerId"));
    verify(appDevHelperContainerService, times(4)).getStatus("crm");
    verify(appDevHelperContainerService, times(1)).updateImage("crm");
  }

  @Test
  void shouldAllowAutoUpdateAgainAfterImageStateResolvesAndBecomesOutdatedLater() {
    Map<String, Object> staleV1 = statusPayload();
    staleV1.put("imageState", imageState(true));
    Map<String, Object> healthy = statusPayload();
    healthy.put("imageState", imageState(false));
    Map<String, Object> staleV2 = statusPayload();
    staleV2.put("imageState", imageStateWithReference("registry.example.com/helper:2.0.0", true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(
        Try.success(staleV1),
        Try.success(healthy),
        Try.success(healthy),
        Try.success(staleV2),
        Try.success(healthy)
    );
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    controller.status("crm", false);
    controller.status("crm", false);
    controller.status("crm", false);

    verify(appDevHelperContainerService, times(2)).updateImage("crm");
  }

  @Test
  void shouldAutoUpdateAgainWhenOutdatedImageFingerprintChanges() {
    Map<String, Object> staleV1 = statusPayload();
    staleV1.put("imageState", imageStateWithReference("registry.example.com/helper:1.0.0", true));
    Map<String, Object> staleV2 = statusPayload();
    staleV2.put("imageState", imageStateWithReference("registry.example.com/helper:2.0.0", true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(
        Try.success(staleV1),
        Try.success(staleV1),
        Try.success(staleV2),
        Try.success(staleV2)
    );
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    controller.status("crm", false);
    controller.status("crm", false);

    verify(appDevHelperContainerService, times(2)).updateImage("crm");
  }

  @Test
  void shouldKeepStatusContractWhenAutoUpdateFails() {
    Map<String, Object> stale = statusPayload();
    stale.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(stale));
    when(appDevHelperContainerService.updateImage("crm"))
        .thenReturn(Try.failure(new BusinessException("update failed")));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(stale, result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldRejectManualUpdateImageWhileLaunching() {
    Map<String, Object> launching = statusPayload(ContainerStatus.LAUNCHING);
    launching.put("imageState", imageState(true));
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(launching));
    when(appDevHelperContainerService.updateImage("crm")).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(0, result.getCode());
    assertEquals("rt-crm-dev-helper", result.getData());
    verify(appDevHelperContainerService).updateImage("crm");
  }

  @Test
  void shouldRejectManualUpdateImageWhenStatusIsUnknown() {
    Map<String, Object> unknown = statusPayload(ContainerStatus.UNKNOWN);
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(unknown));

    WebApiResult<String> result = controller.updateImage("crm");

    assertEquals(-4000, result.getCode());
    assertEquals("当前没有可更新的镜像", result.getMsg());
    verify(appDevHelperContainerService, never()).updateImage("crm");
  }

  @Test
  void shouldReturnNotFoundStatusWithoutCreatingHelper() {
    Map<String, Object> missing = new LinkedHashMap<String, Object>();
    missing.put("appName", "crm");
    missing.put("status", ContainerStatus.NOT_FOUND);
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(missing));

    WebApiResult<?> result = controller.status("crm", false);

    assertEquals(0, result.getCode());
    assertEquals(missing, result.getData());
    verify(appDevHelperContainerService, never()).createAndStart(any());
    verify(appDevHelperContainerService, never()).start("crm");
  }

  @Test
  void shouldAutoCreateHelperWhenTopbarStatusSyncRequestsIt() {
    Map<String, Object> missing = new LinkedHashMap<String, Object>();
    missing.put("appName", "crm");
    missing.put("status", ContainerStatus.NOT_FOUND);
    Map<String, Object> launching = statusPayload(ContainerStatus.LAUNCHING);
    RuntimeCreateInitDTO init = new RuntimeCreateInitDTO();
    init.setImage("registry.example.com/mothership:latest");
    init.setDefaultBackendType("KUBERNETES");
    when(appDevHelperContainerService.getStatus("crm")).thenReturn(Try.success(missing), Try.success(launching));
    when(appRuntimeInitService.buildDevHelperCreateInit("crm")).thenReturn(init);
    when(appDevHelperContainerService.createAndStart(any())).thenReturn(Try.success("rt-crm-dev-helper"));

    WebApiResult<?> result = controller.status("crm", true);

    assertEquals(0, result.getCode());
    assertEquals(launching, result.getData());
    verify(appRuntimeInitService).buildDevHelperCreateInit("crm");
    verify(appDevHelperContainerService).createAndStart(argThat(request ->
        request instanceof AppDevHelperRequest
            && "crm".equals(request.getAppName())
            && "registry.example.com/mothership:latest".equals(request.getImage())
            && "KUBERNETES".equals(request.getBackendType())));
  }

  private static Map<String, Object> statusPayload() {
    return statusPayload(ContainerStatus.RUNNING);
  }

  private static Map<String, Object> statusPayload(ContainerStatus status) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", "crm");
    payload.put("containerId", "rt-crm-dev-helper");
    payload.put("containerName", "rt-crm-dev-helper");
    payload.put("status", status);
    payload.put("imageReference", "registry.example.com/helper:1.0.0");
    payload.put("imageRepository", "registry.example.com/helper");
    payload.put("imageTag", "1.0.0");
    payload.put("imageDigest", null);
    payload.put("imageInputMode", "INPUT_IMAGE_REFERENCE");
    payload.put("imageCapabilities", null);
    payload.put("updateRecommended", Boolean.FALSE);
    payload.put("imageVersion", "1.0.0");
    payload.put("imageState", imageState(false));
    payload.put("lastLoadTime", LocalDateTime.of(2026, 3, 13, 18, 0));
    return payload;
  }

  private static Map<String, Object> imageState(boolean updateRecommended) {
    return imageStateWithReference("registry.example.com/helper:1.0.0", updateRecommended);
  }

  private static Map<String, Object> imageStateWithReference(String reference, boolean updateRecommended) {
    Map<String, Object> imageState = new LinkedHashMap<String, Object>();
    imageState.put("reference", reference);
    imageState.put("tag", reference.substring(reference.lastIndexOf(':') + 1));
    imageState.put("updateRecommended", Boolean.valueOf(updateRecommended));
    return imageState;
  }

  private static RuntimeImageViewDTO imageStateDto(String reference, boolean updateRecommended) {
    RuntimeImageViewDTO imageState = new RuntimeImageViewDTO();
    imageState.setReference(reference);
    imageState.setTag(reference.substring(reference.lastIndexOf(':') + 1));
    imageState.setUpdateRecommended(Boolean.valueOf(updateRecommended));
    return imageState;
  }
}
