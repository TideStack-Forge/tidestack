package com.ouroboros.dev.app.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.vavr.control.Try;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppDevHelperContainerService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/helper")
public class AppDevHelperController {

  @Autowired
  private AppDevHelperContainerService devHelperContainerService;

  @Autowired
  private AppRuntimeInitService appRuntimeInitService;

  private final ConcurrentMap<String, String> appHelperAutoUpdateFingerprints = new java.util.concurrent.ConcurrentHashMap<String, String>();

  @GetMapping("/create-init")
  public WebApiResult<RuntimeCreateInitDTO> createInit(@PathVariable("appName") String appName) {
    return WebApiResult.of(Try.of(() -> appRuntimeInitService.buildDevHelperCreateInit(appName)));
  }

  @PostMapping
  public WebApiResult<String> create(@PathVariable("appName") String appName, @RequestBody Map<String, Object> params) {
    AppDevHelperRequest request = new AppDevHelperRequest();
    request.setAppName(appName);
    request.setImage(params == null ? null : text(params.get("image")));
    request.setBackendType(params == null ? null : text(params.get("backendType")));
    return WebApiResult.of(
        devHelperContainerService.createAndStart(request)
    );
  }

  @DeleteMapping
  public WebApiResult<Void> delete(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.delete(appName));
  }

  @PostMapping("/start")
  public WebApiResult<Void> start(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.start(appName));
  }

  @PostMapping("/stop")
  public WebApiResult<Void> stop(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.stop(appName));
  }

  @PostMapping("/kill")
  public WebApiResult<Void> kill(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.kill(appName));
  }

  @PostMapping("/reload")
  public WebApiResult<Void> reload(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.reload(appName));
  }

  @PostMapping("/restart")
  public WebApiResult<Void> restart(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.restart(appName));
  }

  @PostMapping("/update-image")
  public WebApiResult<String> updateImage(@PathVariable("appName") String appName) {
    return WebApiResult.of(devHelperContainerService.getStatus(appName).flatMap(status -> {
      if (!hasOutdatedImage(status)) {
        return Try.failure(new BusinessException("当前没有可更新的镜像"));
      }
      return devHelperContainerService.updateImage(appName);
    }));
  }

  @GetMapping("/status")
  public WebApiResult<?> status(@PathVariable("appName") String appName,
                                @RequestParam(name = "autoCreate", required = false, defaultValue = "false") boolean autoCreate) {
    Map<String, Object> status = devHelperContainerService.getStatus(appName).getOrElseGet(error -> notFoundPayload(appName));
    boolean noContainer = ContainerStatus.NOT_FOUND.equals(status.get("status"));
    if (noContainer) {
      appHelperAutoUpdateFingerprints.remove(appName);
      if (autoCreate) {
        return WebApiResult.of(createAndStartWithDefaults(appName)
            .flatMap(runtimeId -> devHelperContainerService.getStatus(appName)));
      }
      return WebApiResult.ok(status);
    }
    if (autoUpdateDevHelperImage(appName, status)) {
      return WebApiResult.ok(devHelperContainerService.getStatus(appName).getOrElse(status));
    }
    clearAutoUpdateMarkerWhenResolved(appName, status);
    return WebApiResult.ok(status);
  }

  private Try<String> createAndStartWithDefaults(String appName) {
    return Try.of(() -> appRuntimeInitService.buildDevHelperCreateInit(appName))
        .map(init -> {
          AppDevHelperRequest request = new AppDevHelperRequest();
          request.setAppName(appName);
          request.setImage(init == null ? null : init.getImage());
          request.setBackendType(init == null ? null : init.getDefaultBackendType());
          return request;
        })
        .flatMap(devHelperContainerService::createAndStart);
  }

  private boolean autoUpdateDevHelperImage(String appName, Map<String, Object> status) {
    if (!safeToAutoUpdateImage(status)) {
      return false;
    }
    String fingerprint = autoUpdateFingerprint(status);
    if (fingerprint == null) {
      return false;
    }
    String existing = appHelperAutoUpdateFingerprints.putIfAbsent(appName, fingerprint);
    if (fingerprint.equals(existing)) {
      return false;
    }
    if (existing != null && !appHelperAutoUpdateFingerprints.replace(appName, existing, fingerprint)) {
      return false;
    }
    Try<String> updateResult = devHelperContainerService.updateImage(appName);
    if (updateResult.isFailure()) {
      appHelperAutoUpdateFingerprints.remove(appName, fingerprint);
      return false;
    }
    return true;
  }

  private void clearAutoUpdateMarkerWhenResolved(String appName, Map<String, Object> status) {
    if (!safeToAutoUpdateImage(status) || autoUpdateFingerprint(status) == null) {
      appHelperAutoUpdateFingerprints.remove(appName);
    }
  }

  private boolean hasOutdatedImage(Map<String, Object> status) {
    return status != null
        && Boolean.TRUE.equals(readImageStateFlag(status));
  }

  private boolean safeToAutoUpdateImage(Map<String, Object> status) {
    return status != null
        && (ContainerStatus.RUNNING.equals(status.get("status"))
            || ContainerStatus.LAUNCHING.equals(status.get("status")));
  }

  private String autoUpdateFingerprint(Map<String, Object> status) {
    if (status == null || !Boolean.TRUE.equals(readImageStateFlag(status))) {
      return null;
    }
    Object reference = readImageStateReference(status);
    return text(reference) == null ? "<missing-image-reference>" : text(reference);
  }

  private Boolean readImageStateFlag(Map<String, Object> status) {
    Object imageState = status.get("imageState");
    if (imageState instanceof RuntimeImageViewDTO) {
      return ((RuntimeImageViewDTO) imageState).getUpdateRecommended();
    }
    if (imageState instanceof Map) {
      Object value = ((Map<?, ?>) imageState).get("updateRecommended");
      return value instanceof Boolean ? (Boolean) value : null;
    }
    return null;
  }

  private Object readImageStateReference(Map<String, Object> status) {
    Object imageState = status.get("imageState");
    if (imageState instanceof RuntimeImageViewDTO) {
      return ((RuntimeImageViewDTO) imageState).getReference();
    }
    if (imageState instanceof Map) {
      return ((Map<?, ?>) imageState).get("reference");
    }
    return null;
  }

  private Map<String, Object> notFoundPayload(String appName) {
    Map<String, Object> payload = new java.util.concurrent.ConcurrentHashMap<String, Object>();
    payload.put("appName", appName);
    payload.put("status", ContainerStatus.NOT_FOUND);
    return payload;
  }

  private String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return StringUtils.isBlank(text) ? null : text;
  }

}
