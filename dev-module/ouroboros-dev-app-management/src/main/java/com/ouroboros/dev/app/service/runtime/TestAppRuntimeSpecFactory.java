package com.ouroboros.dev.app.service.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.initializer.DataSourceInitializer;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.model.AppInfo;

@Component
public class TestAppRuntimeSpecFactory {
  private final AppRuntimePreferenceResolver preferenceResolver;
  private final AppManagementService appManagementService;
  private final ConfigurationService configurationService;
  private final DevContainerConfig devContainerConfig;
  private final Map<String, DataSourceInitializer> dataSourceInitializers;
  private final RuntimeLockStore runtimeLockStore;
  private final RuntimeLockOptInInspector runtimeLockOptInInspector;

  public TestAppRuntimeSpecFactory(AppRuntimePreferenceResolver preferenceResolver,
                                   AppManagementService appManagementService,
                                   ConfigurationService configurationService,
                                   DevContainerConfig devContainerConfig,
                                   List<DataSourceInitializer> dataSourceInitializers,
                                   RuntimeLockStore runtimeLockStore,
                                   RuntimeLockOptInInspector runtimeLockOptInInspector) {
    this.preferenceResolver = preferenceResolver;
    this.appManagementService = appManagementService;
    this.configurationService = configurationService;
    this.devContainerConfig = devContainerConfig;
    this.runtimeLockStore = runtimeLockStore;
    this.runtimeLockOptInInspector = runtimeLockOptInInspector;
    this.dataSourceInitializers = new TreeMap<String, DataSourceInitializer>(String::compareToIgnoreCase);
    if (dataSourceInitializers != null) {
      dataSourceInitializers.forEach(initializer -> this.dataSourceInitializers.put(initializer.getTypeName(), initializer));
    }
  }

  public RuntimeSpec create(AppContainerRequest request, String profile) {
    if (request == null) {
      throw new BusinessException("测试应用运行时请求不能为空");
    }
    String appName = request.getAppName();
    if (StringUtils.isBlank(appName)) {
      throw new BusinessException("应用名称不能为空");
    }
    if (request.getDatasource() == null) {
      throw new BusinessException("数据库配置不能为空！");
    }

    AppInfo appInfo = appManagementService.getAppInfo(appName).getOrElseThrow(this::toRuntimeException);
    BackendType backendType = preferenceResolver.resolveBackendType(request.getBackendType());
    ExposureStrategy exposureStrategy = preferenceResolver.resolveTestAppExposureStrategy(request.getExposureStrategy());
    ContainerDatasource datasource = resolveDatasource(appName, profile, request);
    Optional<ResolvedRuntimeLock> latestLock = requireLatestLockIfOptedIn(appName, profile, RuntimeKind.TEST_APP);

    return RuntimeSpec.builder()
        .appName(appName)
        .profile(profile)
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(backendType)
        .workspaceMode(workspaceModeOf(backendType))
        .exposureStrategy(exposureStrategy)
        .requestedPorts(requestedPortsOf(request, backendType, exposureStrategy))
        .image(resolveImage(request))
        .env(buildEnv(appName, profile, datasource, request.getEnv(), latestLock.orElse(null)))
        .labels(buildLabels(appInfo, appName, profile, request.getEnvironmentName()))
        .resourceLimits(Collections.<String, String>emptyMap())
        .network(devContainerConfig.getNetworkName())
        .build();
  }

  public RuntimeSpec recreate(RuntimeSpec currentSpec, String image) {
    if (currentSpec == null) {
      throw new BusinessException("测试应用运行时规格不能为空");
    }
    if (StringUtils.isBlank(currentSpec.getAppName())) {
      throw new BusinessException("应用名称不能为空");
    }
    if (StringUtils.isBlank(currentSpec.getProfile())) {
      throw new BusinessException("运行时 profile 不能为空");
    }
    if (StringUtils.isBlank(image)) {
      throw new BusinessException("测试应用运行时镜像不能为空");
    }

    return RuntimeSpec.builder()
        .appName(currentSpec.getAppName())
        .profile(currentSpec.getProfile())
        .runtimeKind(currentSpec.getRuntimeKind())
        .backendType(currentSpec.getBackendType())
        .workspaceMode(currentSpec.getWorkspaceMode())
        .exposureStrategy(currentSpec.getExposureStrategy())
        .requestedPorts(currentSpec.getRequestedPorts())
        .image(image.trim())
        .env(withTestAppIdentity(currentSpec.getEnv()))
        .labels(currentSpec.getLabels())
        .resourceLimits(currentSpec.getResourceLimits())
        .ttl(currentSpec.getTtl())
        .workspaceRef(currentSpec.getWorkspaceRef())
        .network(currentSpec.getNetwork())
        .build();
  }

  private ContainerDatasource resolveDatasource(String appName, String profile, AppContainerRequest request) {
    AppContainerRequest.DataSourceDefine datasourceDefine = request.getDatasource();
    DataSourceInitializer initializer = dataSourceInitializers.get(datasourceDefine.getType());
    if (initializer == null) {
      throw new BusinessException("不支持的数据源类型: " + datasourceDefine.getType());
    }
    return initializer.init(appName, profile, request.getEnvironmentName(), datasourceDefine).getOrElseThrow(this::toRuntimeException);
  }

  private String resolveImage(AppContainerRequest request) {
    if (StringUtils.isNotBlank(request.getImage())) {
      return request.getImage().trim();
    }
    return configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE).orElse(null);
  }

  private Map<String, String> buildEnv(String appName,
                                       String profile,
                                       ContainerDatasource datasource,
                                       Map<String, String> requestEnv,
                                       ResolvedRuntimeLock latestLock) {
    Map<String, String> env = new LinkedHashMap<String, String>();
    if (ObjectUtils.isNotEmpty(requestEnv)) {
      env.putAll(requestEnv);
    }
    if (StringUtils.isNotBlank(System.getenv("TZ"))) {
      env.put("TZ", System.getenv("TZ"));
    }
    env.put("ENABLE_DEBUG", "true");
    env.put("SPRING_APPLICATION_NAME", appName);
    env.put("SPRING_PROFILES_ACTIVE", profile);
    env.putAll(datasource.asEnvMap());
    env.putAll(devContainerConfig.getMQConfig());
    if (latestLock != null) {
      env.put("OUROBOROS_RUNTIME_LOCK_REQUIRED", "true");
      env.put("OUROBOROS_RUNTIME_LOCK_STATUS", latestLock.getStatus() == null ? "" : latestLock.getStatus().name());
      env.put("OUROBOROS_RUNTIME_LOCK_BASE_BOM", StringUtils.defaultString(latestLock.getBaseBomVersion()));
      env.put("OUROBOROS_RUNTIME_LOCK_ARTIFACTS", String.join(",", toCoordinates(latestLock.getTopLevelArtifacts())));
      env.put("OUROBOROS_RUNTIME_LOCK_RESOLVED_AT", latestLock.getResolvedAt() == null ? "" : latestLock.getResolvedAt().toString());
    }
    String owner = AuthenticationPrincipalTestAppProfileResolver.currentUserIdentity();
    env.remove("OUROBOROS_DEBUG_OWNER");
    if (!AnonymousAuthentication.getInstance().getPrincipal().equals(owner)) {
      env.put("OUROBOROS_DEBUG_OWNER", owner);
    }
    return withTestAppIdentity(env);
  }

  private Map<String, String> withTestAppIdentity(Map<String, String> current) {
    var env = new LinkedHashMap<>(current);
    env.put("OUROBOROS_DEV_HELPER_TEST_APP", "true");
    env.put(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, UUID.randomUUID().toString());
    return env;
  }

  private Optional<ResolvedRuntimeLock> requireLatestLockIfOptedIn(String appName, String profile, RuntimeKind runtimeKind) {
    if (!runtimeLockOptInInspector.isOptedIn(appName, profile)) {
      return Optional.empty();
    }
    RuntimeLockKey slot = RuntimeLockKey.of(appName, profile, runtimeKind);
    Optional<RuntimeLockRecord> latest = runtimeLockStore.findLatestSuccessful(slot);
    if (!latest.isPresent() || !isUsable(latest.get().getLock())) {
      throw new BusinessException(String.format("运行时依赖锁缺失或不可用: %s/%s (%s)", appName, profile, runtimeKind.name()));
    }
    return Optional.of(latest.get().getLock());
  }

  private boolean isUsable(ResolvedRuntimeLock lock) {
    return lock != null
        && lock.getStatus() == RuntimeLockStatus.READY;
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

  private Map<String, String> buildLabels(AppInfo appInfo,
                                          String appName,
                                          String profile,
                                          String environmentName) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("created", profile);
    labels.put("ouroboros-test-app", appInfo.getTitle());
    labels.put("OUROBOROS_APP_NAME", appName);
    labels.put("OUROBOROS_APP_PROFILE", profile);
    if (StringUtils.isNotBlank(environmentName)) {
      labels.put("OUROBOROS_APP_ENVIRONMENT", environmentName);
    }
    return labels;
  }

  private WorkspaceMode workspaceModeOf(BackendType backendType) {
    return backendType == BackendType.KUBERNETES ? WorkspaceMode.PVC : WorkspaceMode.HOST_PATH;
  }

  private Map<String, Integer> requestedPortsOf(AppContainerRequest request,
                                                BackendType backendType,
                                                ExposureStrategy exposureStrategy) {
    if (backendType != BackendType.DOCKER || exposureStrategy != ExposureStrategy.TEMP_PORT) {
      return Collections.emptyMap();
    }
    Map<String, Integer> requestedPorts = new LinkedHashMap<String, Integer>();
    if (request.getPortBindings() == null || request.getPortBindings().isEmpty()) {
      return requestedPorts;
    }
    putRequestedPort(requestedPorts, "web", request.getPortBindings().get("web"));
    putRequestedPort(requestedPorts, "apiDebug", request.getPortBindings().get("apiDebug"));
    return requestedPorts;
  }

  private void putRequestedPort(Map<String, Integer> requestedPorts, String key, Integer port) {
    if (port != null && port.intValue() > 0) {
      requestedPorts.put(key, port);
    }
  }

  private RuntimeException toRuntimeException(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new PlatformException("构建测试应用运行时规格失败", throwable);
  }
}
