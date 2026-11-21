package com.ouroboros.dev.app.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;

class DevAppConfigurationMetadataProviderTest {

  private final DevAppConfigurationMetadataProvider provider = new DevAppConfigurationMetadataProvider();

  @Test
  void shouldExposeMothershipDefaultImageForHelperSemantics() throws Exception {
    Path configurationFile = Paths.get("src/main/metadata/configurations.json");
    String configuration = new String(Files.readAllBytes(configurationFile), StandardCharsets.UTF_8);

    assertTrue(configuration.contains(ConfigurationConstant.DEV_HELPER_CONTAINER_IMAGE));
    assertTrue(configuration.contains(ConfigurationConstant.TEST_APP_CONTAINER_IMAGE));
    assertTrue(configuration.contains("ouroboros-mothership"));
    assertTrue(provider.getAllConfigurationMetadata().stream()
        .allMatch(metadata -> metadata.getKey() != null && !metadata.getKey().isEmpty()));
  }
}
