package com.ouroboros.dev.app.service.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ResolvedRuntimeLockDTO;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.SelectOption;

@Component
public class AppRuntimeInitService {
  private static final String IMAGE_INPUT_SELECT_LOCAL = "SELECT_LOCAL_IMAGE";
  private static final String IMAGE_INPUT_REFERENCE = "INPUT_IMAGE_REFERENCE";

  private final AppContainerService appContainerService;
  private final ConfigurationService configurationService;
  private final AppRuntimePreferenceResolver preferenceResolver;
  private final StableDomainHostResolver stableDomainHostResolver;
  private final DevContainerConfig devContainerConfig;
  private final RuntimeLockStore runtimeLockStore;
  private final RuntimeLockOptInInspector runtimeLockOptInInspector;

  public AppRuntimeInitService(AppContainerService appContainerService,
                               ConfigurationService configurationService,
                               AppRuntimePreferenceResolver preferenceResolver,
                               @Nullable StableDomainHostResolver stableDomainHostResolver,
                               DevContainerConfig devContainerConfig,
                               RuntimeLockStore runtimeLockStore,
                               RuntimeLockOptInInspector runtimeLockOptInInspector) {
    this.appContainerService = appContainerService;
    this.configurationService = configurationService;
    this.preferenceResolver = preferenceResolver;
    this.stableDomainHostResolver = stableDomainHostResolver;
    this.devContainerConfig = devContainerConfig;
    this.runtimeLockStore = runtimeLockStore;
    this.runtimeLockOptInInspector = runtimeLockOptInInspector;
  }

  public RuntimeCreateInitDTO buildTestAppCreateInit(String appName, String profile) {
    BackendType backendType = preferenceResolver.resolveBackendType(null);
    ExposureStrategy exposureStrategy = preferenceResolver.resolveTestAppExposureStrategy(null);
    List<String> imageNames = dockerImageNamesFor(backendType);

    RuntimeCreateInitDTO dto = new RuntimeCreateInitDTO();
    dto.setAvailableBackends(Collections.singletonList(backendOption(backendType.name())));
    dto.setAvailableExposureStrategies(Collections.singletonList(exposureOption(exposureStrategy.name())));
    dto.setBackendType(backendType.name());
    dto.setExposureStrategy(exposureStrategy.name());
    dto.setDefaultBackendType(backendType.name());
    dto.setDefaultExposureStrategy(exposureStrategy.name());
    dto.setImageInputMode(imageInputMode(backendType));
    dto.setImageOptions(imageOptions(backendType, imageNames));
    dto.setImage(resolveTestAppImage(imageNames, backendType));
    dto.setPortBindings(defaultPortBindings(backendType, exposureStrategy));
    dto.setConfigFile(defaultConfigFile());
    dto.setFieldVisibilityRules(testAppFieldVisibilityRules(backendType, exposureStrategy));
    dto.setActionCapabilities(testAppActionCapabilities());
    dto.setSuggestedValues(suggestedValues(dto));
    Optional<ResolvedRuntimeLockDTO> latestLock = requireLatestLockIfOptedIn(appName, profile, RuntimeKind.TEST_APP);
    dto.setRuntimeAccessPreview(testAppRuntimeAccessPreview(appName, profile, backendType, exposureStrategy, dto.getPortBindings(), latestLock));
    return dto;
  }

  public RuntimeCreateInitDTO buildDevHelperCreateInit(String appName) {
    BackendType backendType = preferenceResolver.resolveBackendType(null);
    List<String> imageNames = dockerImageNamesFor(backendType);

    RuntimeCreateInitDTO dto = new RuntimeCreateInitDTO();
    dto.setAvailableBackends(Collections.singletonList(backendOption(backendType.name())));
    dto.setBackendType(backendType.name());
    dto.setDefaultBackendType(backendType.name());
    dto.setImageInputMode(imageInputMode(backendType));
    dto.setImageOptions(imageOptions(backendType, imageNames));
    dto.setImage(resolveDevHelperImage(imageNames, backendType));
    dto.setFieldVisibilityRules(devHelperFieldVisibilityRules());
    dto.setActionCapabilities(devHelperActionCapabilities());
    dto.setSuggestedValues(suggestedValues(dto));
    dto.setRuntimeAccessPreview(Collections.<String, Object>emptyMap());
    return dto;
  }

  private List<SelectOption> backendOptions() {
    return preferenceResolver.availableBackendTypeNames().stream()
        .map(this::backendOption)
        .collect(Collectors.toList());
  }

  private List<SelectOption> testAppExposureOptions() {
    return preferenceResolver.availableTestAppExposureStrategyNames().stream()
        .map(this::exposureOption)
        .collect(Collectors.toList());
  }

  private SelectOption backendOption(String backendTypeName) {
    String label = BackendType.DOCKER.name().equals(backendTypeName) ? "Docker" : "Kubernetes";
    return new SelectOption(backendTypeName, label);
  }

  private SelectOption exposureOption(String exposureStrategyName) {
    String label = ExposureStrategy.TEMP_PORT.name().equals(exposureStrategyName) ? "临时端口" : "稳定域名";
    return new SelectOption(exposureStrategyName, label);
  }

  private String resolveTestAppImage(List<String> imageNames, BackendType backendType) {
    String configuredDefaultImage = configurationService
        .get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE)
        .orElse("");
    if (backendType == BackendType.KUBERNETES && StringUtils.isNotBlank(configuredDefaultImage)) {
      return configuredDefaultImage;
    }
    return preferredImage(imageNames, configuredDefaultImage);
  }

  private String resolveDevHelperImage(List<String> imageNames, BackendType backendType) {
    String configuredDefaultImage = devContainerConfig.getMothershipImageName();
    if (backendType == BackendType.KUBERNETES && StringUtils.isNotBlank(configuredDefaultImage)) {
      return configuredDefaultImage;
    }
    return preferredImage(imageNames, configuredDefaultImage);
  }

  private String preferredImage(List<String> imageNames, String configuredDefaultImage) {
    if (imageNames == null || imageNames.isEmpty()) {
      return StringUtils.defaultString(configuredDefaultImage);
    }
    if (StringUtils.isNotBlank(configuredDefaultImage) && imageNames.contains(configuredDefaultImage)) {
      return configuredDefaultImage;
    }
    return imageNames.get(0);
  }

  private List<SelectOption> imageOptions(BackendType backendType, List<String> imageNames) {
    if (backendType != BackendType.DOCKER || imageNames == null || imageNames.isEmpty()) {
      return Collections.emptyList();
    }
    return imageNames.stream()
        .map(this::imageOption)
        .collect(Collectors.toList());
  }

  private SelectOption imageOption(String imageName) {
    SelectOption option = new SelectOption(imageName, imageName);
    if (imageName != null && imageName.contains("/")) {
      option.setLabel(imageName.substring(imageName.lastIndexOf('/') + 1));
    }
    return option;
  }

  private String imageInputMode(BackendType backendType) {
    return backendType == BackendType.DOCKER ? IMAGE_INPUT_SELECT_LOCAL : IMAGE_INPUT_REFERENCE;
  }

  private List<String> dockerImageNamesFor(BackendType backendType) {
    if (backendType != BackendType.DOCKER) {
      return Collections.emptyList();
    }
    return appContainerService.getImages().getOrElseThrow(throwable -> {
      if (throwable instanceof RuntimeException) {
        return (RuntimeException) throwable;
      }
      return new IllegalStateException("查询本地基座镜像失败", throwable);
    });
  }

  private Map<String, Integer> defaultPortBindings(BackendType backendType, ExposureStrategy exposureStrategy) {
    if (backendType != BackendType.DOCKER || exposureStrategy != ExposureStrategy.TEMP_PORT) {
      return Collections.emptyMap();
    }
    Try<Map<String, Integer>> portBindings = appContainerService.generateAvailablePortMap();
    Map<String, Integer> generated = portBindings.getOrElseThrow(throwable -> {
      if (throwable instanceof RuntimeException) {
        return (RuntimeException) throwable;
      }
      return new IllegalStateException("生成默认端口失败", throwable);
    });
    Map<String, Integer> defaults = new LinkedHashMap<String, Integer>();
    Integer webPort = generated == null ? null : generated.get("web");
    if (webPort != null && webPort.intValue() > 0) {
      defaults.put("web", webPort);
    }
    Integer debugPort = generated == null ? null : generated.get("apiDebug");
    if (debugPort != null && debugPort.intValue() > 0) {
      defaults.put("apiDebug", debugPort);
    }
    return defaults;
  }

  private AppContainerRequest.ConfigFile defaultConfigFile() {
    AppContainerRequest.ConfigFile configFile = new AppContainerRequest.ConfigFile();
    configFile.setReset(false);
    return configFile;
  }

  private Map<String, Boolean> testAppFieldVisibilityRules(BackendType backendType, ExposureStrategy exposureStrategy) {
    Map<String, Boolean> rules = new LinkedHashMap<String, Boolean>();
    rules.put("backendType", Boolean.TRUE);
    rules.put("portBindings", Boolean.valueOf(backendType == BackendType.DOCKER && exposureStrategy == ExposureStrategy.TEMP_PORT));
    rules.put("exposureStrategy", Boolean.TRUE);
    rules.put("configFile", Boolean.TRUE);
    return rules;
  }

  private Map<String, Boolean> devHelperFieldVisibilityRules() {
    Map<String, Boolean> rules = new LinkedHashMap<String, Boolean>();
    rules.put("backendType", Boolean.FALSE);
    rules.put("portBindings", Boolean.FALSE);
    rules.put("exposureStrategy", Boolean.FALSE);
    rules.put("configFile", Boolean.FALSE);
    return rules;
  }

  private Map<String, Boolean> testAppActionCapabilities() {
    Map<String, Boolean> capabilities = new LinkedHashMap<String, Boolean>();
    capabilities.put("supportsExposureStrategySelection", Boolean.FALSE);
    capabilities.put("autoStartAfterCreate", Boolean.TRUE);
    return capabilities;
  }

  private Map<String, Boolean> devHelperActionCapabilities() {
    Map<String, Boolean> capabilities = new LinkedHashMap<String, Boolean>();
    capabilities.put("supportsExposureStrategySelection", Boolean.FALSE);
    capabilities.put("autoStartAfterCreate", Boolean.TRUE);
    return capabilities;
  }

  private Map<String, Object> suggestedValues(RuntimeCreateInitDTO dto) {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("backendType", dto.getDefaultBackendType());
    if (dto.getDefaultExposureStrategy() != null) {
      values.put("exposureStrategy", dto.getDefaultExposureStrategy());
    }
    values.put("image", dto.getImage());
    return values;
  }

  private Map<String, Object> testAppRuntimeAccessPreview(String appName,
                                                          String profile,
                                                          BackendType backendType,
                                                          ExposureStrategy exposureStrategy,
                                                          Map<String, Integer> portBindings,
                                                          Optional<ResolvedRuntimeLockDTO> latestLock) {
    Map<String, Object> preview = new LinkedHashMap<String, Object>();
    if (backendType == BackendType.DOCKER && exposureStrategy == ExposureStrategy.TEMP_PORT) {
      preview.putAll(loopbackPreview(portBindings));
    }
    if (exposureStrategy == ExposureStrategy.STABLE_DOMAIN) {
      preview.putAll(stableDomainPreview(appName, profile));
    }
    latestLock.ifPresent(lock -> preview.put("resolvedRuntimeLock", lock));
    return preview;
  }

  private Optional<ResolvedRuntimeLockDTO> requireLatestLockIfOptedIn(String appName, String profile, RuntimeKind runtimeKind) {
    if (!runtimeLockOptInInspector.isOptedIn(appName, profile)) {
      return Optional.empty();
    }
    RuntimeLockKey slot = RuntimeLockKey.of(appName, profile, runtimeKind);
    Optional<RuntimeLockRecord> latest = runtimeLockStore.findLatestSuccessful(slot);
    if (!latest.isPresent() || !isUsable(latest.get().getLock())) {
      throw new BusinessException(String.format("运行时依赖锁缺失或不可用: %s/%s (%s)", appName, profile, runtimeKind.name()));
    }
    return Optional.of(toDto(latest.get()));
  }

  private boolean isUsable(ResolvedRuntimeLock lock) {
    return lock != null
        && lock.getStatus() == RuntimeLockStatus.READY;
  }

  private ResolvedRuntimeLockDTO toDto(RuntimeLockRecord record) {
    ResolvedRuntimeLock lock = record.getLock();
    ResolvedRuntimeLockDTO dto = new ResolvedRuntimeLockDTO();
    dto.setRuntimeKind(lock.getRuntimeKind() == null ? null : lock.getRuntimeKind().name());
    dto.setStatus(lock.getStatus() == null ? null : lock.getStatus().name());
    dto.setResolvedAt(lock.getResolvedAt() == null ? null : lock.getResolvedAt().toString());
    dto.setSavedAt(record.getSavedAt() == null ? null : record.getSavedAt().toString());
    dto.setBaseBomVersion(lock.getBaseBomVersion());
    dto.setArtifacts(toCoordinates(lock.getTopLevelArtifacts()));
    return dto;
  }

  private List<String> toCoordinates(List<AppArtifactBinding> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> coordinates = new ArrayList<String>();
    for (AppArtifactBinding binding : bindings) {
      if (binding == null || binding.getGroupId() == null || binding.getArtifactId() == null || binding.getVersion() == null) {
        continue;
      }
      coordinates.add(binding.getGroupId() + ":" + binding.getArtifactId() + ":" + binding.getVersion());
    }
    return coordinates;
  }

  private Map<String, Object> loopbackPreview(Map<String, Integer> portBindings) {
    Map<String, Object> preview = new LinkedHashMap<String, Object>();
    putEndpoint(preview, "web", "http", portBindings.get("web"), "");
    putEndpoint(preview, "debug", "tcp", portBindings.get("apiDebug"), "");
    return preview;
  }

  private Map<String, Object> stableDomainPreview(String appName, String profile) {
    if (stableDomainHostResolver == null || StringUtils.isBlank(appName) || StringUtils.isBlank(profile)) {
      return Collections.emptyMap();
    }
    String host = stableDomainHostResolver.resolve(appName, profile);
    if (StringUtils.isBlank(host)) {
      return Collections.emptyMap();
    }
    Map<String, Object> preview = new LinkedHashMap<String, Object>();
    putEndpoint(preview, "web", "https", null, host);
    return preview;
  }

  private void putEndpoint(Map<String, Object> preview, String key, String scheme, Integer port, String host) {
    Map<String, Object> endpoint = new LinkedHashMap<String, Object>();
    if (StringUtils.isNotBlank(host)) {
      endpoint.put("endpoint", scheme + "://" + host);
    } else if (port != null) {
      endpoint.put("endpoint", scheme + "://127.0.0.1:" + port);
    }
    preview.put(key, endpoint);
  }
}
