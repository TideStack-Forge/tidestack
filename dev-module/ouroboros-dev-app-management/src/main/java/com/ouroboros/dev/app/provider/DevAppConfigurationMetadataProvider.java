package com.ouroboros.dev.app.provider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.dev.app.constant.ConfigurationConstant;

@Priority(1)
public class DevAppConfigurationMetadataProvider implements ConfigurationMetadataProvider {
  private static final String VERSION_SOURCE = "/META-INF/maven/com.ouroboros/ouroboros-app-runtime/pom.properties";
  private static final String VERSION = getVersion();
  private static final String CPU_ARCH = getCpuArch();
  private static final String ARM64_TAG = "-arm64";
  private static final String ARM64_KEYWORD = "aarch64";
  private static final String IMAGE_REGISTRY = "registry.cn-hangzhou.aliyuncs.com";
  private static final String IMAGE_NAMESPACE = "ouroboros";
  private static final String IMAGE_NAME = "ouroboros-mothership";
  private static final String HELPER_IMG_CFG_DESC = "开发助手基座镜像名";
  private static final String IMG_CFG_DESC = "测试应用基座镜像名";
  private static final String BACKEND_CFG_DESC = "测试应用默认运行时后端";
  private static final String EXPOSURE_CFG_DESC = "测试应用默认暴露策略";
  private static final String LOG_CACHE_CFG_DESC = "测试应用日志缓存行数";
  private static final int DEFAULT_LOG_CACHE_LINES = 200;

  @Override
  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    List<ConfigurationMetadata> result = new ArrayList<ConfigurationMetadata>();
    if (!VERSION.isEmpty()) {
      var isArm64 = CPU_ARCH.equalsIgnoreCase(ARM64_KEYWORD);
      var tag = isArm64 ? VERSION + ARM64_TAG : VERSION;
      result.add(buildHelperImageConfig(tag));
      result.add(buildTestAppImageConfig(tag));
    }
    result.add(buildPreferredRuntimeBackendConfig());
    result.add(buildDefaultExposureStrategyConfig());
    result.add(buildLogCacheLinesConfig());
    return result;
  }

  private ConfigurationMetadata buildHelperImageConfig(String tag) {
    var imageName = Stream.of(IMAGE_REGISTRY, IMAGE_NAMESPACE, IMAGE_NAME + ":" + tag)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.joining("/"));
    var configurationMetadata = new ConfigurationMetadata();
    configurationMetadata.setKey(ConfigurationConstant.DEV_HELPER_CONTAINER_IMAGE);
    configurationMetadata.setLabel(HELPER_IMG_CFG_DESC);
    configurationMetadata.setDefaultValue(imageName);
    configurationMetadata.setModifiable(true);
    configurationMetadata.setNullable(false);
    configurationMetadata.setValueType(ConfigValueType.STRING);
    configurationMetadata.setScope(ConfigScope.BACKEND);
    return configurationMetadata;
  }

  private ConfigurationMetadata buildTestAppImageConfig(String tag) {
    var imageName = Stream.of(IMAGE_REGISTRY, IMAGE_NAMESPACE, IMAGE_NAME + ":" + tag)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.joining("/"));
    var configurationMetadata = new ConfigurationMetadata();
    configurationMetadata.setKey(ConfigurationConstant.TEST_APP_CONTAINER_IMAGE);
    configurationMetadata.setLabel(IMG_CFG_DESC);
    configurationMetadata.setDefaultValue(imageName);
    configurationMetadata.setModifiable(true);
    configurationMetadata.setNullable(false);
    configurationMetadata.setValueType(ConfigValueType.STRING);
    configurationMetadata.setScope(ConfigScope.BACKEND);
    return configurationMetadata;
  }

  private ConfigurationMetadata buildPreferredRuntimeBackendConfig() {
    var configurationMetadata = new ConfigurationMetadata();
    configurationMetadata.setKey(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND);
    configurationMetadata.setLabel(BACKEND_CFG_DESC);
    configurationMetadata.setDescription("可选值：DOCKER、KUBERNETES");
    configurationMetadata.setDefaultValue("DOCKER");
    configurationMetadata.setModifiable(true);
    configurationMetadata.setNullable(false);
    configurationMetadata.setValueType(ConfigValueType.STRING);
    configurationMetadata.setScope(ConfigScope.BACKEND);
    return configurationMetadata;
  }

  private ConfigurationMetadata buildDefaultExposureStrategyConfig() {
    var configurationMetadata = new ConfigurationMetadata();
    configurationMetadata.setKey(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY);
    configurationMetadata.setLabel(EXPOSURE_CFG_DESC);
    configurationMetadata.setDescription("可选值：TEMP_PORT、STABLE_DOMAIN");
    configurationMetadata.setDefaultValue("TEMP_PORT");
    configurationMetadata.setModifiable(true);
    configurationMetadata.setNullable(false);
    configurationMetadata.setValueType(ConfigValueType.STRING);
    configurationMetadata.setScope(ConfigScope.BACKEND);
    return configurationMetadata;
  }

  private ConfigurationMetadata buildLogCacheLinesConfig() {
    var configurationMetadata = new ConfigurationMetadata();
    configurationMetadata.setKey(ConfigurationConstant.TEST_APP_LOG_CACHE_LINES);
    configurationMetadata.setLabel(LOG_CACHE_CFG_DESC);
    configurationMetadata.setDescription("测试应用日志在后台缓存的最近行数，默认200行");
    configurationMetadata.setDefaultValue(String.valueOf(DEFAULT_LOG_CACHE_LINES));
    configurationMetadata.setModifiable(true);
    configurationMetadata.setNullable(false);
    configurationMetadata.setValueType(ConfigValueType.INT);
    configurationMetadata.setScope(ConfigScope.BACKEND);
    return configurationMetadata;
  }

  private static String getVersion() {
    var props = new Properties();
    try {
      var inStream = DevAppConfigurationMetadataProvider.class.getResourceAsStream(VERSION_SOURCE);
      if (inStream == null) {
        return "";
      }
      props.load(inStream);
      var version = props.getProperty("version");
      return version == null ? "" : version.trim();
    } catch (IOException e) {
      return "";
    }
  }

  private static String getCpuArch() {
    try {
      var arch = System.getProperty("os.arch");
      if (arch == null) {
        return "";
      }
      return arch.trim();
    } catch (Exception e) {
      return "";
    }
  }
}
