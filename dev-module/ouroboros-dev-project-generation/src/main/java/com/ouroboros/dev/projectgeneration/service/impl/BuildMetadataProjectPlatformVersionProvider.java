package com.ouroboros.dev.projectgeneration.service.impl;

import org.springframework.boot.info.BuildProperties;
import org.springframework.util.StringUtils;

import com.ouroboros.dev.projectgeneration.service.ProjectPlatformVersionProvider;

public class BuildMetadataProjectPlatformVersionProvider implements ProjectPlatformVersionProvider {

  private final BuildProperties buildProperties;
  private final String configuredVersion;

  public BuildMetadataProjectPlatformVersionProvider(BuildProperties buildProperties, String configuredVersion) {
    this.buildProperties = buildProperties;
    this.configuredVersion = configuredVersion;
  }

  @Override
  public String getCurrentVersion() {
    if (buildProperties != null && StringUtils.hasText(buildProperties.getVersion())) {
      return buildProperties.getVersion();
    }
    if (StringUtils.hasText(configuredVersion)) {
      return configuredVersion;
    }
    return "unknown";
  }
}
