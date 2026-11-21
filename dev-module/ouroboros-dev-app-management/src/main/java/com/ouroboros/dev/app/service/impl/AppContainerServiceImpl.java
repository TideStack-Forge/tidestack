package com.ouroboros.dev.app.service.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.BaseImagePullRequest;
import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;

@Service
public class AppContainerServiceImpl implements AppContainerService {
  static final Logger logger = LoggerFactory.getLogger(AppContainerServiceImpl.class);

  private final AppLifecycleService appLifecycleService;
  private final AppEndpointService appEndpointService;
  private final AppImageService appImageService;
  private final AppRuntimeQueryService appRuntimeQueryService;
  private final AppConfigService appConfigService;

  private final ConfigurationService configurationService;
  private final ContainerService containerService;
  private final DevRuntimeService devRuntimeService;

  @Autowired
  public AppContainerServiceImpl(AppLifecycleService appLifecycleService,
                                 AppEndpointService appEndpointService,
                                 AppImageService appImageService,
                                 AppRuntimeQueryService appRuntimeQueryService,
                                 AppConfigService appConfigService,
                                 ConfigurationService configurationService,
                                 ContainerService containerService,
                                 DevRuntimeService devRuntimeService) {
    this.appLifecycleService = appLifecycleService;
    this.appEndpointService = appEndpointService;
    this.appImageService = appImageService;
    this.appRuntimeQueryService = appRuntimeQueryService;
    this.appConfigService = appConfigService;
    this.configurationService = configurationService;
    this.containerService = containerService;
    this.devRuntimeService = devRuntimeService;
  }

  public AppContainerServiceImpl(AppManagementService appManagementService,
                                 ConfigurationService configurationService,
                                 DevelopWorkspaceContext developWorkspaceContext,
                                 ContainerService containerService,
                                 DevContainerConfig devContainerConfig,
                                 AppDataSourceService appDataSourceService,
                                 PlatformDataSourceService platformDataSourceService,
                                 DevRuntimeService devRuntimeService,
                                 RuntimeStateStore runtimeStateStore,
                                 DebugSessionStore debugSessionStore,
                                 TestAppRuntimeSpecFactory runtimeSpecFactory,
                                 AppRuntimeViewAssembler runtimeViewAssembler,
                                 RuntimeImageViewService runtimeImageViewService,
                                 BaseImageQuerySupport baseImageQuerySupport) {
    AppConfigService appConfigService = new AppConfigService(
        developWorkspaceContext,
        appDataSourceService,
        platformDataSourceService
    );
    AppEndpointService endpointService = new AppEndpointService(containerService, configurationService);
    AppRuntimeQueryService runtimeQueryService = new AppRuntimeQueryService(
        appManagementService,
        runtimeStateStore,
        debugSessionStore,
        devRuntimeService,
        containerService,
        runtimeViewAssembler,
        runtimeImageViewService,
        devContainerConfig,
        endpointService
    );
    this.appLifecycleService = new AppLifecycleService(
        appConfigService,
        runtimeQueryService,
        runtimeSpecFactory,
        devRuntimeService,
        runtimeStateStore,
        containerService
    );
    this.appEndpointService = endpointService;
    this.appImageService = new AppImageService(
        runtimeQueryService,
        devRuntimeService,
        appConfigService,
        configurationService,
        containerService,
        devContainerConfig,
        baseImageQuerySupport,
        runtimeSpecFactory
    );
    this.appRuntimeQueryService = runtimeQueryService;
    this.appConfigService = appConfigService;
    this.configurationService = configurationService;
    this.containerService = containerService;
    this.devRuntimeService = devRuntimeService;
  }

  @Override
  public Try<Map<String, Integer>> generateAvailablePortMap() {
    return appEndpointService.generateAvailablePortMap();
  }

  @Override
  public Try<List<String>> getImages() {
    return appImageService.getImages();
  }

  @Override
  public Try<List<BaseImageReference>> getBaseImages() {
    return appImageService.getBaseImages();
  }

  @Override
  public Try<String> getDefaultImage() {
    return appImageService.getDefaultImage();
  }

  @Override
  public Try<Void> pullBaseImage(BaseImagePullRequest request) {
    return appImageService.pullBaseImage(request);
  }

  @Override
  public Try<String> create(AppContainerRequest request, String profileName) {
    return appLifecycleService.create(request, profileName);
  }

  @Override
  public Try<String> createAndStart(AppContainerRequest request, String profileName) {
    return appLifecycleService.createAndStart(request, profileName);
  }

  @Override
  public Try<Void> delete(String containerId) {
    return appLifecycleService.delete(containerId);
  }

  @Override
  public Try<Void> start(String containerId) {
    return appLifecycleService.start(containerId);
  }

  @Override
  public Try<Void> stop(String containerId) {
    return appLifecycleService.stop(containerId);
  }

  @Override
  public Try<Void> pause(String containerId) {
    return appLifecycleService.pause(containerId);
  }

  @Override
  public Try<Void> unpause(String containerId) {
    return appLifecycleService.unpause(containerId);
  }

  @Override
  public Try<Void> kill(String containerId) {
    return appLifecycleService.kill(containerId);
  }

  @Override
  public Try<Void> restart(String containerId) {
    return appLifecycleService.restart(containerId);
  }

  @Override
  public Try<Void> forceRestart(String containerId) {
    return appLifecycleService.forceRestart(containerId);
  }

  @Override
  public Try<Void> reloadWorkspace(String containerId) {
    return appLifecycleService.reloadWorkspace(containerId);
  }

  @Override
  public Try<List<AppContainersDTO>> getAll() {
    return appRuntimeQueryService.getAll();
  }

  @Override
  public Try<String> updateImage(String containerId) {
    return appImageService.updateImage(containerId);
  }

  @Override
  public Try<Map<String, Object>> getStatus(String containerId) {
    return appRuntimeQueryService.getStatus(containerId);
  }

  @Override
  public Try<Map<String, Object>> getStatus(String appName, String profile) {
    return appRuntimeQueryService.getStatus(appName, profile);
  }

  @EventListener(DeleteAppEvent.class)
  private void handleDeleteAppEvent(DeleteAppEvent event) {
    String appName = event.getAppName();
    appRuntimeQueryService.deleteRuntimeRecords(appName);
    appConfigService.moveRunWorkspaceToTrash(appName);
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  private void handleApplicationStartedEvent(OuroborosContextRefreshedEvent event) {
    if (!(event.getSource() instanceof ApplicationStartedEvent)) {
      return;
    }
    String imageName = configurationService
        .get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE)
        .map(String::trim)
        .orElse("");
    if (imageName.isEmpty()) {
      logger.warn("未配置基座容器镜像名称，跳过自动拉取镜像");
      return;
    }
    containerService.pullImageAsync(imageName)
        .whenComplete((value, throwable) -> {
          if (throwable != null) {
            logger.error("自动拉取基座容器镜像失败: {}", imageName, throwable);
            return;
          }
          logger.info("自动拉取基座容器镜像成功: {}", imageName);
        });
  }
}
