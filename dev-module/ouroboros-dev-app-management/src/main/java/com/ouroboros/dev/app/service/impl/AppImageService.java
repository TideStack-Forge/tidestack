package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.BaseImagePullRequest;
import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.exception.BusinessException;

@Service
public class AppImageService {

  private final AppRuntimeQueryService runtimeQueryService;
  private final DevRuntimeService devRuntimeService;
  private final AppConfigService appConfigService;
  private final ConfigurationService configurationService;
  private final ContainerService containerService;
  private final DevContainerConfig devContainerConfig;
  private final BaseImageQuerySupport baseImageQuerySupport;
  private final TestAppRuntimeSpecFactory runtimeSpecFactory;

  public AppImageService(AppRuntimeQueryService runtimeQueryService,
                         DevRuntimeService devRuntimeService,
                         AppConfigService appConfigService,
                         ConfigurationService configurationService,
                         ContainerService containerService,
                         DevContainerConfig devContainerConfig,
                         BaseImageQuerySupport baseImageQuerySupport,
                         TestAppRuntimeSpecFactory runtimeSpecFactory) {
    this.runtimeQueryService = runtimeQueryService;
    this.devRuntimeService = devRuntimeService;
    this.appConfigService = appConfigService;
    this.configurationService = configurationService;
    this.containerService = containerService;
    this.devContainerConfig = devContainerConfig;
    this.baseImageQuerySupport = baseImageQuerySupport;
    this.runtimeSpecFactory = runtimeSpecFactory;
  }

  public Try<String> updateImage(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> {
          boolean shouldStart = runtimeQueryService.isActive(record.getInstance() == null ? null : record.getInstance().getStatus());
          Try<RuntimeInstance> stopResult = shouldStart
              ? devRuntimeService.stop(record.getKey())
              : Try.success(record.getInstance());
          return stopResult
              .flatMap(instance -> devRuntimeService.delete(record.getKey()))
              .flatMap(v -> appConfigService.buildRunWorkspace(record.getSpec().getAppName(), record.getSpec().getProfile()))
              .flatMap(v -> rebuildSpecWithCurrentDefaultImage(record.getSpec()))
              .flatMap(spec -> devRuntimeService.create(spec))
              .flatMap(created -> shouldStart
                  ? devRuntimeService.start(record.getKey()).map(RuntimeInstance::getRuntimeId)
                  : Try.success(created.getRuntimeId()));
        });
  }

  private Try<RuntimeSpec> rebuildSpecWithCurrentDefaultImage(RuntimeSpec currentSpec) {
    return getDefaultImage()
        .flatMap(image -> StringUtils.isBlank(image)
            ? Try.failure(new BusinessException("本地未找到可用于更新测试容器的基座镜像"))
            : Try.of(() -> runtimeSpecFactory.recreate(currentSpec, image)));
  }

  public Try<List<String>> getImages() {
    return getBaseImages()
        .map(baseImages -> baseImages.stream()
            .map(BaseImageReference::getImageName)
            .collect(Collectors.toList()));
  }

  public Try<List<BaseImageReference>> getBaseImages() {
    return getConfiguredBaseImage()
        .flatMap(configuredImageName -> getAllTaggedImageNames()
            .flatMap(localTaggedImageNames -> baseImageQuerySupport.getBaseImages(configuredImageName, localTaggedImageNames)));
  }

  public Try<String> getDefaultImage() {
    return getConfiguredBaseImage()
        .flatMap(configuredImageName -> getAllTaggedImageNames()
            .flatMap(localTaggedImageNames -> baseImageQuerySupport.getDefaultImage(configuredImageName, localTaggedImageNames)));
  }

  public Try<Void> pullBaseImage(BaseImagePullRequest request) {
    String rawImageName = Optional.ofNullable(request).map(BaseImagePullRequest::getImageName).orElse(null);
    String rawTag = Optional.ofNullable(request).map(BaseImagePullRequest::getTag).orElse(null);
    String imageName = StringUtils.trimToNull(rawImageName);
    String tag = StringUtils.trimToNull(rawTag);
    if (rawImageName != null && imageName == null && rawTag == null) {
      return Try.failure(new BusinessException("镜像名不能为空"));
    }
    if (rawTag != null && tag == null && rawImageName == null) {
      return Try.failure(new BusinessException("tag 不能为空"));
    }
    if (imageName == null && tag == null) {
      return Try.failure(new BusinessException("imageName 与 tag 必须二选一"));
    }
    if (imageName != null && tag != null) {
      return Try.failure(new BusinessException("imageName 与 tag 必须二选一"));
    }

    if (imageName != null) {
      return getConfiguredBaseImage()
          .flatMap(configuredImageName -> getAllTaggedImageNames()
              .flatMap(localTaggedImageNames -> baseImageQuerySupport.requirePulledBaseImage(
                  configuredImageName,
                  localTaggedImageNames,
                  imageName
              )))
          .flatMap(this::startPullImage);
    }

    return getConfiguredBaseImage()
        .flatMap(configuredImageName -> baseImageQuerySupport.buildImageNameFromTag(configuredImageName, tag))
        .flatMap(this::startPullImage);
  }

  private List<String> dockerLocalImageNames() {
    return containerService.getAllImageNames(devContainerConfig.getMothershipImageName())
        .getOrElse(Collections::emptyList);
  }

  private Try<String> getConfiguredBaseImage() {
    return Try.of(() -> configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE).orElse(null))
        .flatMap(imageName -> StringUtils.isBlank(imageName)
            ? Try.failure(new BusinessException("默认基座镜像配置不能为空"))
            : Try.success(imageName));
  }

  private Try<List<String>> getAllTaggedImageNames() {
    return containerService.getAllTaggedImageNames().toTry();
  }

  private Try<Void> startPullImage(String imageName) {
    return Try.of(() -> containerService.pullImageAsync(imageName))
        .map(ignore -> (Void) null);
  }
}
