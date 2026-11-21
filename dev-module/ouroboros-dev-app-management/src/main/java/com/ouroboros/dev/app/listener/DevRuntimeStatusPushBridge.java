package com.ouroboros.dev.app.listener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.event.DevHelperStatusChangedEvent;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.websocket.StompMessageSender;

@Component
public class DevRuntimeStatusPushBridge {
  private final StompMessageSender stompMessageSender;
  private final AppContainerService appContainerService;
  private final AppDevHelperContainerService appDevHelperContainerService;

  public DevRuntimeStatusPushBridge(
      StompMessageSender stompMessageSender,
      AppContainerService appContainerService,
      AppDevHelperContainerService appDevHelperContainerService) {
    this.stompMessageSender = stompMessageSender;
    this.appContainerService = appContainerService;
    this.appDevHelperContainerService = appDevHelperContainerService;
  }

  @EventListener
  public void onRuntimeEvent(DevRuntimeEvent event) {
    if (event == null || event.getRuntimeKind() == null || event.getAppName() == null) {
      return;
    }
    if (event.getType() == DevRuntimeEventType.RUNTIME_LOG_OUTPUT) {
      return;
    }
    if (event.getRuntimeKind() == RuntimeKind.DEV_HELPER) {
      pushHelperStatus(event.getAppName(), buildPayload(event));
      return;
    }
    if (event.getProfile() != null) {
      var payload = resolveTestAppPayload(event);
      stompMessageSender.send("/topic/app-container/" + event.getAppName() + "-" + event.getProfile(), payload);
      stompMessageSender.sendToUser(event.getProfile(), "/queue/app/" + event.getAppName() + "/status", payload);
    }
  }

  @EventListener
  public void onHelperStatusChanged(DevHelperStatusChangedEvent event) {
    if (event == null || StringUtils.isBlank(event.getAppName())) {
      return;
    }
    pushHelperStatus(event.getAppName(), helperFallbackPayload(event.getAppName()));
  }

  private Map<String, Object> buildPayload(DevRuntimeEvent event) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("runtimeId", event.getRuntimeId());
    payload.put("containerId", event.getRuntimeId());
    payload.put("appName", event.getAppName());
    payload.put("profile", event.getProfile());
    payload.put("status", toContainerStatus(event.getStatus()));
    payload.put("backendType", event.getBackendType() == null ? null : event.getBackendType().name());
    payload.put("webEndpoint", event.getWebEndpoint());
    payload.put("apiEndpoint", event.getApiEndpoint());
    payload.put("debugEndpoint", event.getDebugEndpoint());
    payload.put("capabilities", buildCapabilities(event.getCapabilities()));
    return payload;
  }

  private void pushHelperStatus(String appName, Map<String, Object> fallbackPayload) {
    stompMessageSender.send("/topic/dev-helper/" + appName, resolveHelperPayload(appName, fallbackPayload));
  }

  private Map<String, Object> resolveHelperPayload(String appName, Map<String, Object> fallbackPayload) {
    return appDevHelperContainerService.getStatus(appName)
        .getOrElse(fallbackPayload);
  }

  private Map<String, Object> resolveTestAppPayload(DevRuntimeEvent event) {
    return appContainerService.getStatus(event.getAppName(), event.getProfile())
        .getOrElse(buildPayload(event));
  }

  private Map<String, Object> helperFallbackPayload(String appName) {
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", appName);
    payload.put("status", ContainerStatus.UNKNOWN);
    payload.put("imageState", null);
    payload.put("imageCapabilities", Collections.emptyMap());
    return payload;
  }

  private ContainerStatus toContainerStatus(RuntimeStatus runtimeStatus) {
    if (runtimeStatus == null) {
      return ContainerStatus.UNKNOWN;
    }
    switch (runtimeStatus) {
      case NOT_FOUND:
        return ContainerStatus.NOT_FOUND;
      case CREATING:
      case STARTING:
      case RELOADING:
        return ContainerStatus.LAUNCHING;
      case RUNNING:
        return ContainerStatus.RUNNING;
      case STOPPED:
        return ContainerStatus.STOPPED;
      default:
        return ContainerStatus.UNKNOWN;
    }
  }

  private Map<String, Boolean> buildCapabilities(RuntimeCapability capability) {
    RuntimeCapability resolved = capability == null ? RuntimeCapability.builder().build() : capability;
    Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
    values.put("supportsPause", resolved.isSupportsPause());
    values.put("supportsDebugPort", resolved.isSupportsDebugPort());
    values.put("supportsWorkspaceHotReload", resolved.isSupportsWorkspaceHotReload());
    values.put("supportsNodePortExposure", resolved.isSupportsNodePortExposure());
    values.put("supportsIngressExposure", resolved.isSupportsIngressExposure());
    return values;
  }
}
