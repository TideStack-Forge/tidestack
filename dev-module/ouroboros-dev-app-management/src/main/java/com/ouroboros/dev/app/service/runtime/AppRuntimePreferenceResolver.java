package com.ouroboros.dev.app.service.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.exception.BusinessException;

@Component
public class AppRuntimePreferenceResolver {
  private final ConfigurationService configurationService;
  private final DevRuntimeProperties devRuntimeProperties;

  public AppRuntimePreferenceResolver(ConfigurationService configurationService,
                                      DevRuntimeProperties devRuntimeProperties) {
    this.configurationService = configurationService;
    this.devRuntimeProperties = devRuntimeProperties;
  }

  public BackendType resolveBackendType(String requestedBackendType) {
    String configuredBackendType = configurationService
        .get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND)
        .orElse(null);
    BackendType defaultBackendType = devRuntimeProperties == null ? BackendType.DOCKER : devRuntimeProperties.getDefaultBackend();
    String selected = firstNonBlank(requestedBackendType, configuredBackendType, defaultBackendType == null ? null : defaultBackendType.name());
    return parseBackendType(selected);
  }

  public ExposureStrategy resolveTestAppExposureStrategy(String requestedExposureStrategy) {
    String configuredExposureStrategy = configurationService
        .get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY)
        .orElse(null);
    String selected = firstNonBlank(requestedExposureStrategy, configuredExposureStrategy, ExposureStrategy.TEMP_PORT.name());
    return parseExposureStrategy(selected);
  }

  public List<String> availableBackendTypeNames() {
    return Arrays.asList(BackendType.DOCKER.name(), BackendType.KUBERNETES.name());
  }

  public List<String> availableTestAppExposureStrategyNames() {
    return Arrays.asList(ExposureStrategy.TEMP_PORT.name(), ExposureStrategy.STABLE_DOMAIN.name());
  }

  private BackendType parseBackendType(String value) {
    try {
      return BackendType.valueOf(normalize(value));
    } catch (Exception exception) {
      throw new BusinessException("不支持的运行时后端类型: " + value);
    }
  }

  private ExposureStrategy parseExposureStrategy(String value) {
    try {
      return ExposureStrategy.valueOf(normalize(value));
    } catch (Exception exception) {
      throw new BusinessException("不支持的暴露策略: " + value);
    }
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (StringUtils.isNotBlank(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private String normalize(String value) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .map(v -> v.toUpperCase(Locale.ROOT))
        .orElseThrow(() -> new BusinessException("运行时参数不能为空"));
  }
}
