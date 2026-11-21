package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.RuntimeAccessViewDTO;
import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.model.AppInfo;

@Service
public class AppRuntimeQueryService {

  private static final Logger logger = LoggerFactory.getLogger(AppRuntimeQueryService.class);

  private static final String APP_CONTAINER_PREFIX = "ouroboros-test-app-";
  private static final String APP_NAME_LABEL = "OUROBOROS_APP_NAME";
  private static final String PROFILE_LABEL = "OUROBOROS_APP_PROFILE";

  private final AppManagementService appManagementService;
  private final RuntimeStateStore runtimeStateStore;
  private final DebugSessionStore debugSessionStore;
  private final DevRuntimeService devRuntimeService;
  private final ContainerService containerService;
  private final AppRuntimeViewAssembler runtimeViewAssembler;
  private final RuntimeImageViewService runtimeImageViewService;
  private final DevContainerConfig devContainerConfig;
  private final AppEndpointService appEndpointService;

  public AppRuntimeQueryService(AppManagementService appManagementService,
                                RuntimeStateStore runtimeStateStore,
                                DebugSessionStore debugSessionStore,
                                DevRuntimeService devRuntimeService,
                                ContainerService containerService,
                                AppRuntimeViewAssembler runtimeViewAssembler,
                                RuntimeImageViewService runtimeImageViewService,
                                DevContainerConfig devContainerConfig,
                                AppEndpointService appEndpointService) {
    this.appManagementService = appManagementService;
    this.runtimeStateStore = runtimeStateStore;
    this.debugSessionStore = debugSessionStore;
    this.devRuntimeService = devRuntimeService;
    this.containerService = containerService;
    this.runtimeViewAssembler = runtimeViewAssembler;
    this.runtimeImageViewService = runtimeImageViewService;
    this.devContainerConfig = devContainerConfig;
    this.appEndpointService = appEndpointService;
  }

  public Try<List<AppContainersDTO>> getAll() {
    Map<RuntimeIdentityKey, RuntimeSpec> specs = runtimeStateStore.allSpecs(RuntimeKind.TEST_APP);
    Map<RuntimeIdentityKey, RuntimeInstance> instances = runtimeStateStore.allInstances();
    List<String> dockerImageNames = dockerLocalImageNames();
    return appManagementService.listApps()
        .map(apps -> apps.stream()
            .map(AppInfoWithMeta::getAppInfo)
            .map(appInfo -> assembleListView(appInfo, specs, instances, dockerImageNames))
            .collect(Collectors.toList()));
  }

  public Try<Map<String, Object>> getStatus(String containerId) {
    return resolveRuntimeById(containerId)
        .map(this::refreshRecord)
        .map(record -> buildStatusPayload(record, false))
        .recover(throwable -> notFoundPayload(containerId));
  }

  public Try<Map<String, Object>> getStatus(String appName, String profile) {
    return findRuntimeByAppAndProfile(appName, profile)
        .map(this::refreshRecord)
        .map(record -> Try.success(buildStatusPayload(record, false)))
        .orElseGet(() -> Try.success(notFoundPayload(appName)));
  }

  public Try<RuntimeRecord> resolveRuntimeById(String runtimeId) {
    return findRuntimeById(runtimeId)
        .map(Try::success)
        .orElseGet(() -> Try.failure(new BusinessException("容器未找到！")));
  }

  public List<RuntimeRecord> findRuntimeRecords(String appName) {
    Map<RuntimeIdentityKey, RuntimeInstance> instances = runtimeStateStore.allInstances();
    return runtimeStateStore.allSpecs(RuntimeKind.TEST_APP).entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> Objects.equals(appName, entry.getValue().getAppName()))
        .map(entry -> new RuntimeRecord(entry.getKey(), entry.getValue(), instances.get(entry.getKey())))
        .collect(Collectors.toList());
  }

  public void deleteRuntimeRecords(String appName) {
    findRuntimeRecords(appName).forEach(record -> devRuntimeService.delete(record.getKey()).onFailure(error ->
        logger.warn("删除应用 {} 时删除测试运行时失败: {}", appName, record.getKey().getBackendType(), error)
    ));
  }

  public String containerNameOf(RuntimeRecord record) {
    if (record.instance != null
        && record.instance.getBackendMetadata() != null
        && StringUtils.isNotBlank(record.instance.getBackendMetadata().get("containerName"))) {
      return record.instance.getBackendMetadata().get("containerName");
    }
    return APP_CONTAINER_PREFIX + record.spec.getAppName() + "-" + record.spec.getProfile();
  }

  public boolean isActive(RuntimeStatus status) {
    return status == RuntimeStatus.RUNNING || status == RuntimeStatus.STARTING || status == RuntimeStatus.RELOADING;
  }

  private List<AppRuntimeViewAssembler.RuntimeView> viewsForApp(Map<RuntimeIdentityKey, RuntimeSpec> specs,
                                                                 Map<RuntimeIdentityKey, RuntimeInstance> instances,
                                                                 String appName) {
    return runtimeRecordsForApp(specs, instances, appName).stream()
        .map(record -> AppRuntimeViewAssembler.RuntimeView.of(
            record.spec,
            record.instance,
            activeDebugSession(record.key).orElse(null),
            activeBackendApiSession(record.key).orElse(null)
        ))
        .collect(Collectors.toList());
  }

  private List<RuntimeRecord> runtimeRecordsForApp(Map<RuntimeIdentityKey, RuntimeSpec> specs,
                                                    Map<RuntimeIdentityKey, RuntimeInstance> instances,
                                                    String appName) {
    return specs.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> RuntimeKind.TEST_APP == entry.getValue().getRuntimeKind())
        .filter(entry -> Objects.equals(appName, entry.getValue().getAppName()))
        .map(entry -> new RuntimeRecord(entry.getKey(), entry.getValue(), instances.get(entry.getKey())))
        .sorted(Comparator.comparing((RuntimeRecord record) -> runtimePriority(record.instance), Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

  private AppContainersDTO assembleListView(AppInfo appInfo,
                                            Map<RuntimeIdentityKey, RuntimeSpec> specs,
                                            Map<RuntimeIdentityKey, RuntimeInstance> instances,
                                            List<String> dockerImageNames) {
    List<RuntimeRecord> records = runtimeRecordsForApp(specs, instances, appInfo.getName());
    AppContainersDTO dto = runtimeViewAssembler.assemble(appInfo, viewsForApp(specs, instances, appInfo.getName()), dockerImageNames);
    if (dto.getChildren() == null || dto.getChildren().isEmpty()) {
      return dto;
    }
    int size = Math.min(dto.getChildren().size(), records.size());
    for (int index = 0; index < size; index++) {
      RuntimeRecord record = records.get(index);
      dto.getChildren().get(index).setStatus(resolveDisplayedStatus(record, dockerContainer(record)));
    }
    return dto;
  }

  private Optional<DebugSession> activeDebugSession(RuntimeIdentityKey key) {
    return debugSessionStore.findActiveByRuntimeKeyAndTargetType(key, DebugTunnelTargetType.DEBUG).stream().findFirst();
  }

  private Optional<DebugSession> activeBackendApiSession(RuntimeIdentityKey key) {
    return debugSessionStore.findActiveByRuntimeKeyAndTargetType(key, DebugTunnelTargetType.BACKEND_API).stream().findFirst();
  }

  private int runtimePriority(RuntimeInstance instance) {
    if (instance == null || instance.getStatus() == null) {
      return 0;
    }
    switch (instance.getStatus()) {
      case RUNNING:
        return 4;
      case STARTING:
      case RELOADING:
        return 3;
      case STOPPED:
        return 2;
      default:
        return 1;
    }
  }

  private Optional<RuntimeRecord> findRuntimeById(String runtimeId) {
    if (StringUtils.isBlank(runtimeId)) {
      return Optional.empty();
    }
    Map<RuntimeIdentityKey, RuntimeSpec> specs = runtimeStateStore.allSpecs(RuntimeKind.TEST_APP);
    Map<RuntimeIdentityKey, RuntimeInstance> instances = runtimeStateStore.allInstances();
    Optional<RuntimeRecord> runtimeRecord = specs.entrySet().stream()
        .map(entry -> new RuntimeRecord(entry.getKey(), entry.getValue(), instances.get(entry.getKey())))
        .filter(record -> matchesRuntimeId(record, runtimeId))
        .findFirst();
    return runtimeRecord.isPresent() ? runtimeRecord : findDockerRuntimeById(runtimeId);
  }

  private boolean matchesRuntimeId(RuntimeRecord record, String runtimeId) {
    if (record.instance != null && Objects.equals(runtimeId, record.instance.getRuntimeId())) {
      return true;
    }
    return Objects.equals(runtimeId, containerNameOf(record));
  }

  private Optional<RuntimeRecord> findRuntimeByAppAndProfile(String appName, String profile) {
    Optional<RuntimeRecord> runtimeRecord = runtimeStateStore.allSpecs(RuntimeKind.TEST_APP).entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .filter(entry -> Objects.equals(appName, entry.getValue().getAppName()))
        .filter(entry -> Objects.equals(profile, entry.getValue().getProfile()))
        .map(entry -> new RuntimeRecord(entry.getKey(), entry.getValue(), runtimeStateStore.allInstances().get(entry.getKey())))
        .sorted(Comparator.comparing((RuntimeRecord record) -> runtimePriority(record.instance), Comparator.reverseOrder()))
        .findFirst();
    return runtimeRecord.isPresent() ? runtimeRecord : findDockerRuntimeByAppAndProfile(appName, profile);
  }

  private Optional<RuntimeRecord> findDockerRuntimeByAppAndProfile(String appName, String profile) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(profile)) {
      return Optional.empty();
    }
    String containerName = APP_CONTAINER_PREFIX + appName + "-" + profile;
    com.ouroboros.container.model.Container container = Optional.ofNullable(containerService.getContainerInfoByName(containerName))
        .map(either -> either.getOrNull())
        .orElse(null);
    if (container == null) {
      return Optional.empty();
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.of(appName, profile, RuntimeKind.TEST_APP, BackendType.DOCKER);
    RuntimeInstance instance = RuntimeInstance.builder()
        .runtimeId(container.getId())
        .appName(StringUtils.trimToNull(labelOf(container, APP_NAME_LABEL, appName)))
        .profile(StringUtils.trimToNull(labelOf(container, PROFILE_LABEL, profile)))
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .status(runtimeStatusFromContainer(container.getStatus()))
        .webEndpoint(appEndpointService.httpEndpoint(container, "WEB_PORT", 80))
        .apiEndpoint(appEndpointService.httpEndpoint(container, "API_PORT", 8080))
        .debugEndpoint(null)
        .capabilities(RuntimeCapability.builder()
            .supportsPause(true)
            .supportsDebugPort(true)
            .supportsDebugSession(true)
            .supportsWorkspaceHotReload(true)
            .supportsNodePortExposure(false)
            .supportsIngressExposure(false)
            .build())
        .backendMetadata(Collections.singletonMap("containerName", container.getName()))
        .build();
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName(appName)
        .profile(profile)
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .exposureStrategy(exposureStrategyFrom(container))
        .requestedPorts(requestedPortsFrom(container))
        .image(container.getImage())
        .env(container.getEnvironmentalVariables())
        .labels(container.getLabels())
        .network(StringUtils.trimToNull(container.getNetwork()))
        .build();
    runtimeStateStore.saveSpec(key, spec);
    runtimeStateStore.saveInstance(key, instance);
    return Optional.of(new RuntimeRecord(key, spec, instance));
  }

  private ExposureStrategy exposureStrategyFrom(com.ouroboros.container.model.Container container) {
    if (StringUtils.isNotBlank(labelOf(container, "WEB_PORT", null))
        || StringUtils.isNotBlank(labelOf(container, "API_DEBUG_PORT", null))) {
      return ExposureStrategy.TEMP_PORT;
    }
    return ExposureStrategy.STABLE_DOMAIN;
  }

  private Map<String, Integer> requestedPortsFrom(com.ouroboros.container.model.Container container) {
    Map<String, Integer> requestedPorts = new LinkedHashMap<String, Integer>();
    putRequestedPort(requestedPorts, "web", labelOf(container, "WEB_PORT", null));
    putRequestedPort(requestedPorts, "apiDebug", labelOf(container, "API_DEBUG_PORT", null));
    if (!requestedPorts.isEmpty()) {
      return requestedPorts;
    }
    Map<String, Map<Integer, Integer>> portBindings = container.getPortBindings();
    if (portBindings == null || portBindings.isEmpty()) {
      return requestedPorts;
    }
    Map<Integer, Integer> tcpBindings = portBindings.get("tcp");
    if (tcpBindings == null) {
      return requestedPorts;
    }
    putRequestedPort(requestedPorts, "web", tcpBindings.get(Integer.valueOf(80)));
    putRequestedPort(requestedPorts, "apiDebug", tcpBindings.get(Integer.valueOf(5005)));
    return requestedPorts;
  }

  private void putRequestedPort(Map<String, Integer> requestedPorts, String key, String value) {
    if (!StringUtils.isNumeric(value)) {
      return;
    }
    putRequestedPort(requestedPorts, key, Integer.valueOf(value));
  }

  private void putRequestedPort(Map<String, Integer> requestedPorts, String key, Integer port) {
    if (port != null && port.intValue() > 0) {
      requestedPorts.put(key, port);
    }
  }

  private Optional<RuntimeRecord> findDockerRuntimeById(String runtimeId) {
    if (StringUtils.isBlank(runtimeId)) {
      return Optional.empty();
    }
    com.ouroboros.container.model.Container container = Optional.ofNullable(containerService.getContainerInfoById(runtimeId))
        .map(either -> either.getOrNull())
        .orElse(null);
    if (container == null) {
      container = Optional.ofNullable(containerService.getContainerInfoByName(runtimeId))
          .map(either -> either.getOrNull())
          .orElse(null);
    }
    if (container == null) {
      return Optional.empty();
    }
    String appName = StringUtils.trimToNull(labelOf(container, APP_NAME_LABEL, null));
    String profile = StringUtils.trimToNull(labelOf(container, PROFILE_LABEL, null));
    if (appName == null || profile == null) {
      return Optional.empty();
    }
    return findDockerRuntimeByAppAndProfile(appName, profile);
  }

  private String labelOf(com.ouroboros.container.model.Container container, String key, String fallback) {
    if (container == null || container.getLabels() == null) {
      return fallback;
    }
    return StringUtils.defaultIfBlank(container.getLabels().get(key), fallback);
  }

  private RuntimeStatus runtimeStatusFromContainer(ContainerStatus status) {
    if (status == null) {
      return RuntimeStatus.UNKNOWN;
    }
    switch (status) {
      case NOT_FOUND:
        return RuntimeStatus.NOT_FOUND;
      case LAUNCHING:
        return RuntimeStatus.STARTING;
      case RUNNING:
      case PAUSED:
        return RuntimeStatus.RUNNING;
      case STOPPED:
        return RuntimeStatus.STOPPED;
      default:
        return RuntimeStatus.UNKNOWN;
    }
  }

  private RuntimeRecord refreshRecord(RuntimeRecord record) {
    RuntimeInstance refreshed = devRuntimeService.getStatus(record.key).getOrElse(record.instance);
    return new RuntimeRecord(record.key, record.spec, refreshed);
  }

  private Map<String, Object> buildStatusPayload(RuntimeRecord record, boolean includeNotFoundAsFailure) {
    DebugSession activeDebugSession = activeDebugSession(record.key).orElse(null);
    DebugSession activeBackendApiSession = activeBackendApiSession(record.key).orElse(null);
    RuntimeAccessViewDTO runtimeAccess = runtimeViewAssembler.toRuntimeAccess(record.spec, record.instance, activeDebugSession, activeBackendApiSession);
    String debugEndpoint = runtimeViewAssembler.debugEndpoint(activeDebugSession);
    Map<String, String> backendDetails = runtimeViewAssembler.backendDetails(record.instance);
    com.ouroboros.container.model.Container dockerContainer = dockerContainer(record);
    RuntimeImageViewDTO imageState = runtimeImageViewService.describe(
        record.spec.getBackendType(),
        resolveImageReference(record, dockerContainer),
        dockerLocalImageNames()
    );
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", record.spec.getAppName());
    payload.put("profile", record.spec.getProfile());
    payload.put("runtimeKind", record.spec.getRuntimeKind() == null ? null : record.spec.getRuntimeKind().name());
    payload.put("runtimeGeneration", record.instance == null ? null : record.instance.getRuntimeGeneration());
    payload.put("containerId", record.instance == null ? null : record.instance.getRuntimeId());
    payload.put("containerName", containerNameOf(record));
    payload.put("status", resolveDisplayedStatus(record, dockerContainer));
    payload.put("backendType", record.spec.getBackendType() == null ? null : record.spec.getBackendType().name());
    payload.put("exposureStrategy", record.spec.getExposureStrategy() == null ? null : record.spec.getExposureStrategy().name());
    payload.put("backendSummary", runtimeViewAssembler.backendSummary(record.instance));
    payload.put("backendDetails", backendDetails);
    payload.put("webEndpoint", record.instance == null ? null : record.instance.getWebEndpoint());
    payload.put("apiEndpoint", record.instance == null ? null : record.instance.getApiEndpoint());
    payload.put("debugEndpoint", debugEndpoint);
    payload.put("runtimeAccess", runtimeAccess);
    payload.put("debugCapability", runtimeAccess == null ? null : runtimeAccess.getDebugCapability());
    payload.put("backendApiCapability", runtimeAccess == null ? null : runtimeAccess.getBackendApiCapability());
    payload.put("activeDebugSessionId", activeDebugSession == null ? null : activeDebugSession.getSessionId());
    payload.put("activeBackendApiSessionId", activeBackendApiSession == null ? null : activeBackendApiSession.getSessionId());
    payload.put("capabilities", capabilities(record.instance == null ? null : record.instance.getCapabilities()));
    payload.put("imageState", imageState);
    appEndpointService.putPort(payload, "webPort", record.instance == null ? null : record.instance.getWebEndpoint());
    appEndpointService.putPort(payload, "apiPort", record.instance == null ? null : record.instance.getApiEndpoint());
    appEndpointService.putPort(payload, "debugPort", debugEndpoint);
    payload.put("imageVersion", imageState == null ? null : imageState.getTag());
    if (includeNotFoundAsFailure && ContainerStatus.NOT_FOUND == payload.get("status")) {
      throw new BusinessException("容器未找到！");
    }
    return payload;
  }

  private Map<String, Object> notFoundPayload(String appName) {
    RuntimeAccessViewDTO runtimeAccess = runtimeViewAssembler.toRuntimeAccess(null, null, null, null);
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("appName", appName);
    payload.put("status", ContainerStatus.NOT_FOUND);
    payload.put("profile", null);
    payload.put("runtimeKind", null);
    payload.put("runtimeGeneration", null);
    payload.put("backendSummary", null);
    payload.put("backendDetails", Collections.emptyMap());
    payload.put("webEndpoint", null);
    payload.put("apiEndpoint", null);
    payload.put("debugEndpoint", null);
    payload.put("runtimeAccess", runtimeAccess);
    payload.put("debugCapability", runtimeAccess == null ? null : runtimeAccess.getDebugCapability());
    payload.put("backendApiCapability", runtimeAccess == null ? null : runtimeAccess.getBackendApiCapability());
    payload.put("activeDebugSessionId", null);
    payload.put("activeBackendApiSessionId", null);
    payload.put("capabilities", capabilities(null));
    payload.put("imageState", null);
    return payload;
  }

  private Map<String, Boolean> capabilities(RuntimeCapability capability) {
    RuntimeCapability resolved = capability == null
        ? RuntimeCapability.builder().build()
        : capability;
    Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
    values.put("supportsPause", resolved.isSupportsPause());
    values.put("supportsDebugPort", resolved.isSupportsDebugPort());
    values.put("supportsDebugSession", resolved.isSupportsDebugSession());
    values.put("supportsWorkspaceHotReload", resolved.isSupportsWorkspaceHotReload());
    values.put("supportsNodePortExposure", resolved.isSupportsNodePortExposure());
    values.put("supportsIngressExposure", resolved.isSupportsIngressExposure());
    return values;
  }

  private ContainerStatus resolveDisplayedStatus(RuntimeRecord record, com.ouroboros.container.model.Container dockerContainer) {
    if (record.spec.getBackendType() == BackendType.DOCKER) {
      if (dockerContainer != null && dockerContainer.getStatus() != null) {
        return dockerContainer.getStatus();
      }
      return ContainerStatus.NOT_FOUND;
    }
    return toContainerStatus(record.instance == null ? null : record.instance.getStatus());
  }

  private String resolveImageReference(RuntimeRecord record, com.ouroboros.container.model.Container dockerContainer) {
    if (record.spec.getBackendType() != BackendType.DOCKER) {
      return record.spec.getImage();
    }
    return dockerContainer != null && StringUtils.isNotBlank(dockerContainer.getImage())
        ? dockerContainer.getImage()
        : record.spec.getImage();
  }

  private com.ouroboros.container.model.Container dockerContainer(RuntimeRecord record) {
    if (record.spec.getBackendType() != BackendType.DOCKER) {
      return null;
    }
    return Optional.ofNullable(containerService.getContainerInfoByName(containerNameOf(record)))
        .map(either -> either.getOrNull())
        .orElse(null);
  }

  private ContainerStatus toContainerStatus(RuntimeStatus status) {
    if (status == null) {
      return ContainerStatus.UNKNOWN;
    }
    switch (status) {
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

  private List<String> dockerLocalImageNames() {
    return containerService.getAllImageNames(devContainerConfig.getMothershipImageName())
        .getOrElse(Collections::emptyList);
  }

  public static final class RuntimeRecord {
    private final RuntimeIdentityKey key;
    private final RuntimeSpec spec;
    private final RuntimeInstance instance;

    public RuntimeRecord(RuntimeIdentityKey key, RuntimeSpec spec, RuntimeInstance instance) {
      this.key = key;
      this.spec = spec;
      this.instance = instance;
    }

    public RuntimeIdentityKey getKey() {
      return key;
    }

    public RuntimeSpec getSpec() {
      return spec;
    }

    public RuntimeInstance getInstance() {
      return instance;
    }
  }
}
