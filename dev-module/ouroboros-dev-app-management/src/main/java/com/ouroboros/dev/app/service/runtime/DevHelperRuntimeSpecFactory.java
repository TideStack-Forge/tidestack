package com.ouroboros.dev.app.service.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.model.AppInfo;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;

@Component
public class DevHelperRuntimeSpecFactory {
  private static final String HELPER_PROFILE = "dev-helper";
  private static final String HELPER_METADATA_PATH = "/app/deploy/metadata/";
  public static final String HELPER_IMAGE_SOURCE_LABEL = "ouroboros.dev/helper-image-source";
  public static final String HELPER_IMAGE_SOURCE_REFERENCE_LABEL = "ouroboros.dev/helper-image-source-reference";
  public static final String HELPER_IMAGE_SOURCE_EXPLICIT = "explicit";
  public static final String HELPER_IMAGE_SOURCE_DEFAULT = "default";

  private final AppRuntimePreferenceResolver preferenceResolver;
  private final AppManagementService appManagementService;
  private final DevContainerConfig devContainerConfig;

  public DevHelperRuntimeSpecFactory(AppRuntimePreferenceResolver preferenceResolver,
                                     AppManagementService appManagementService,
                                     DevContainerConfig devContainerConfig) {
    this.preferenceResolver = preferenceResolver;
    this.appManagementService = appManagementService;
    this.devContainerConfig = devContainerConfig;
  }

  public RuntimeSpec create(AppDevHelperRequest request) {
    if (request == null) {
      throw new BusinessException("开发助手运行时请求不能为空");
    }
    String appName = request.getAppName();
    if (StringUtils.isBlank(appName)) {
      throw new BusinessException("应用名称不能为空");
    }

    BackendType backendType = preferenceResolver.resolveBackendType(request.getBackendType());
    ResolvedHelperImage resolvedImage = resolveImage(request.getImage());
    return buildSpec(appName, backendType, resolvedImage);
  }

  public RuntimeSpec recreate(RuntimeSpec currentSpec) {
    if (currentSpec == null) {
      throw new BusinessException("开发助手运行时规格不能为空");
    }
    String appName = currentSpec.getAppName();
    if (StringUtils.isBlank(appName)) {
      throw new BusinessException("应用名称不能为空");
    }

    BackendType backendType = currentSpec.getBackendType() == null
        ? preferenceResolver.resolveBackendType(null)
        : currentSpec.getBackendType();
    ResolvedHelperImage resolvedImage = resolveImageForRecreate(currentSpec);
    return buildSpec(appName, backendType, resolvedImage);
  }

  private RuntimeSpec buildSpec(String appName, BackendType backendType, ResolvedHelperImage resolvedImage) {

    AppInfo appInfo = appManagementService.getAppInfo(appName).getOrElseThrow(this::toRuntimeException);

    return RuntimeSpec.builder()
        .appName(appName)
        .profile(HELPER_PROFILE)
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(backendType)
        .workspaceMode(workspaceModeOf(backendType))
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image(resolvedImage.reference)
        .env(buildEnv(appName, UUID.randomUUID().toString()))
        .labels(buildLabels(appInfo, appName, resolvedImage.source,
            HELPER_IMAGE_SOURCE_EXPLICIT.equals(resolvedImage.source) ? resolvedImage.reference : null))
        .network(devContainerConfig.getNetworkName())
        .build();
  }

  private ResolvedHelperImage resolveImage(String requestedImage) {
    if (StringUtils.isNotBlank(requestedImage)) {
      return new ResolvedHelperImage(requestedImage.trim(), HELPER_IMAGE_SOURCE_EXPLICIT);
    }
    return new ResolvedHelperImage(devContainerConfig.getMothershipImageName(), HELPER_IMAGE_SOURCE_DEFAULT);
  }

  private ResolvedHelperImage resolveImageForRecreate(RuntimeSpec currentSpec) {
    if (shouldFollowConfiguredMothershipImage(currentSpec, devContainerConfig.getMothershipImageName())) {
      return new ResolvedHelperImage(devContainerConfig.getMothershipImageName(), HELPER_IMAGE_SOURCE_DEFAULT);
    }
    return new ResolvedHelperImage(resolveCanonicalImageReference(currentSpec), HELPER_IMAGE_SOURCE_EXPLICIT);
  }

  public static boolean shouldFollowConfiguredMothershipImage(RuntimeSpec currentSpec, String configuredMothershipImageName) {
    if (currentSpec == null) {
      return false;
    }
    String imageSource = imageSourceOf(currentSpec);
    if (StringUtils.isBlank(imageSource) || HELPER_IMAGE_SOURCE_DEFAULT.equalsIgnoreCase(imageSource)) {
      return true;
    }
    if (!HELPER_IMAGE_SOURCE_EXPLICIT.equalsIgnoreCase(imageSource)) {
      return false;
    }
    return isSameImageRepository(configuredMothershipImageName, resolveCanonicalImageReference(currentSpec));
  }

  private Map<String, String> buildEnv(String appName, String appIncarnationId) {
    Map<String, String> env = new LinkedHashMap<String, String>();
    if (StringUtils.isNotBlank(System.getenv("TZ"))) {
      env.put("TZ", System.getenv("TZ"));
    }
    env.put("SPRING_APPLICATION_NAME", appName);
    env.put("ENABLE_DEPLOY_EXTEND", "true");
    env.put("OUROBOROS_METADATA_PATH", HELPER_METADATA_PATH);
    env.put(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, appIncarnationId);
    env.put("SPRING_DATASOURCE_DRIVERCLASSNAME", devContainerConfig.getTestAppMemoryDatasourceDriverClass());
    env.put("SPRING_DATASOURCE_URL", devContainerConfig.getTestAppMemoryDatasourceUrl());
    env.putAll(devContainerConfig.getMQConfig());
    return env;
  }

  private Map<String, String> buildLabels(AppInfo appInfo, String appName, String imageSource) {
    return buildLabels(appInfo, appName, imageSource, null);
  }

  private Map<String, String> buildLabels(AppInfo appInfo, String appName, String imageSource, String imageSourceReference) {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("ouroboros-dev-helper", appInfo.getTitle());
    labels.put("OUROBOROS_APP_NAME", appName);
    labels.put(HELPER_IMAGE_SOURCE_LABEL, imageSource);
    if (StringUtils.isNotBlank(imageSourceReference)) {
      labels.put(HELPER_IMAGE_SOURCE_REFERENCE_LABEL, imageSourceReference);
    }
    return labels;
  }

  private static String resolveCanonicalImageReference(RuntimeSpec currentSpec) {
    if (currentSpec != null && currentSpec.getLabels() != null) {
      String canonicalReference = currentSpec.getLabels().get(HELPER_IMAGE_SOURCE_REFERENCE_LABEL);
      if (StringUtils.isNotBlank(canonicalReference)) {
        return canonicalReference.trim();
      }
    }
    return currentSpec == null ? null : currentSpec.getImage();
  }

  private static String imageSourceOf(RuntimeSpec currentSpec) {
    if (currentSpec == null || currentSpec.getLabels() == null) {
      return null;
    }
    return currentSpec.getLabels().get(HELPER_IMAGE_SOURCE_LABEL);
  }

  private static boolean isSameImageRepository(String left, String right) {
    String leftRepository = imageRepositoryOf(left);
    String rightRepository = imageRepositoryOf(right);
    return StringUtils.isNotBlank(leftRepository) && leftRepository.equals(rightRepository);
  }

  private static String imageRepositoryOf(String imageReference) {
    String trimmed = StringUtils.trimToNull(imageReference);
    if (trimmed == null) {
      return null;
    }
    int digestSeparator = trimmed.indexOf('@');
    if (digestSeparator >= 0) {
      trimmed = trimmed.substring(0, digestSeparator);
    }
    int lastSlash = trimmed.lastIndexOf('/');
    int lastColon = trimmed.lastIndexOf(':');
    if (lastColon > lastSlash) {
      return trimmed.substring(0, lastColon);
    }
    return trimmed;
  }

  private WorkspaceMode workspaceModeOf(BackendType backendType) {
    return backendType == BackendType.KUBERNETES ? WorkspaceMode.PVC : WorkspaceMode.HOST_PATH;
  }

  private RuntimeException toRuntimeException(Throwable throwable) {
    if (throwable instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new PlatformException("构建开发助手运行时规格失败", throwable);
  }

  private static final class ResolvedHelperImage {
    private final String reference;
    private final String source;

    private ResolvedHelperImage(String reference, String source) {
      this.reference = reference;
      this.source = source;
    }
  }
}
