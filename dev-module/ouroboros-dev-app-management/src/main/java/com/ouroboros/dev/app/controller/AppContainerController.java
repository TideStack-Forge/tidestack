package com.ouroboros.dev.app.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
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
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.BaseImagePullRequest;
import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.dev.app.service.runtime.TestAppProfileResolver;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/app/container")
public class AppContainerController {

  @Autowired
  private AppContainerService appContainerService;

  @Autowired
  private AppRuntimeInitService appRuntimeInitService;

  @Autowired
  private TestAppProfileResolver testAppProfileResolver;

  @GetMapping("/generate-port")
  public WebApiResult<Map<String, Integer>> generatePort() {
    return WebApiResult.of(appContainerService.generateAvailablePortMap());
  }

  @GetMapping("/create-init")
  public WebApiResult<RuntimeCreateInitDTO> getCreateInit(@RequestParam(name = "appName", required = false) String appName) {
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName(appName);
    String profileName = testAppProfileResolver.resolveForCreate(request);
    return WebApiResult.of(Try.of(() -> appRuntimeInitService.buildTestAppCreateInit(appName, profileName)));
  }

  @PostMapping("/create")
  public WebApiResult<String> create(@RequestBody AppContainerRequest appContainer) {
    String profileName = testAppProfileResolver.resolveForCreate(appContainer);
    return WebApiResult.of(
        appContainerService.createAndStart(appContainer, profileName)
    );
  }

  @DeleteMapping("/{containerId}")
  public WebApiResult<Void> delete(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.delete(containerId));
  }

  @PostMapping("/{containerId}/start")
  public WebApiResult<Void> start(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.start(containerId));
  }

  @PostMapping("/{containerId}/pause")
  public WebApiResult<Void> pause(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.pause(containerId));
  }

  @PostMapping("/{containerId}/unpause")
  public WebApiResult<Void> unpause(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.unpause(containerId));
  }

  @PostMapping("/{containerId}/stop")
  public WebApiResult<Void> stop(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.stop(containerId));
  }

  @PostMapping("/{containerId}/kill")
  public WebApiResult<Void> kill(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.kill(containerId));
  }

  @PostMapping("/{containerId}/reload")
  public WebApiResult<Void> reload(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.reloadWorkspace(containerId));
  }

  @PostMapping("/{containerId}/restart")
  public WebApiResult<Void> restart(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.restart(containerId));
  }

  @PostMapping("/{containerId}/update-image")
  public WebApiResult<String> updateImage(@PathVariable("containerId") String containerId) {
    return WebApiResult.of(appContainerService.updateImage(containerId));
  }

  @GetMapping("/{appName}/status")
  public WebApiResult<?> statusByAppName(@PathVariable("appName") String appName) {
    AppContainerRequest request = new AppContainerRequest();
    request.setAppName(appName);
    String profile = testAppProfileResolver.resolveForCreate(request);
    return WebApiResult.of(appContainerService.getStatus(appName, profile));
  }

  @GetMapping("/{appName}/{profile}/status")
  public WebApiResult<?> status(@PathVariable("appName") String appName, @PathVariable("profile") String profile) {
    String resolvedProfile = profile;
    if (StringUtils.isBlank(resolvedProfile)) {
      AppContainerRequest request = new AppContainerRequest();
      request.setAppName(appName);
      resolvedProfile = testAppProfileResolver.resolveForCreate(request);
    }
    return WebApiResult.of(appContainerService.getStatus(appName, resolvedProfile));
  }

  @GetMapping("/{containerId}/status")
  public WebApiResult<?> status(@PathVariable("containerId") String containerId) {
    Try<Map<String, Object>> status = appContainerService.getStatus(containerId)
        .flatMap(payload -> {
          if (!ContainerStatus.NOT_FOUND.equals(payload.get("status"))) {
            return Try.success(payload);
          }
          AppContainerRequest request = new AppContainerRequest();
          request.setAppName(containerId);
          String profileName = testAppProfileResolver.resolveForCreate(request);
          return appContainerService.getStatus(containerId, profileName);
        });
    return WebApiResult.of(status);
  }

  @GetMapping
  public ItemsWebApiResult<AppContainersDTO> getAll(@RequestParam(defaultValue = "", name = "keyword") String keyword) {
    Try<List<AppContainersDTO>> allTry = appContainerService.getAll()
        .map(list -> ObjectUtils.isEmpty(keyword) ? list : list.stream()
            .filter(app -> app.getAppName() != null && app.getAppName().contains(keyword)
                || app.getAppTitle() != null && app.getAppTitle().contains(keyword)
                || app.getAppDescription() != null && app.getAppDescription().contains(keyword))
            .collect(Collectors.toList()));
    return ItemsWebApiResult.of(allTry);
  }

  @GetMapping("/images")
  public ItemsWebApiResult<SelectOption> getImages(@RequestParam(name = "backendType", required = false) String backendType) {
    if (BackendType.KUBERNETES.name().equalsIgnoreCase(String.valueOf(backendType))) {
      return ItemsWebApiResult.ok(Collections.<SelectOption>emptyList());
    }
    return ItemsWebApiResult.of(appContainerService.getImages()
        .map(images -> images.stream()
            .map(this::buildImageNameOption)
            .collect(Collectors.toList())));
  }

  @GetMapping("/base-images")
  public ItemsWebApiResult<BaseImageReference> getBaseImages() {
    return ItemsWebApiResult.of(appContainerService.getBaseImages());
  }

  @PostMapping("/base-images/pull")
  public WebApiResult<Void> pullBaseImage(@RequestBody BaseImagePullRequest request) {
    return WebApiResult.of(appContainerService.pullBaseImage(request));
  }

  private SelectOption buildImageNameOption(String imageName) {
    SelectOption option = new SelectOption(imageName, imageName);
    if (!imageName.contains("/")) {
      return option;
    }
    option.setLabel(imageName.substring(imageName.lastIndexOf("/") + 1));
    return option;
  }
}
