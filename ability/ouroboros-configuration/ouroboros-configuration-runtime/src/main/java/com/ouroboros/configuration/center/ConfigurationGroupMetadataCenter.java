package com.ouroboros.configuration.center;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider;
import com.ouroboros.util.SPI;

public class ConfigurationGroupMetadataCenter {

  private static Map<String, ConfigurationGroupMetadata> metadataMap;

  private ConfigurationGroupMetadataCenter() {
  }

  public static Optional<ConfigurationGroupMetadata> getConfigurationGroupMetadata(String path) {
    return Optional.ofNullable(metadataMap).map(map -> map.get(path));
  }

  public static Map<String, ConfigurationGroupMetadata> getConfigurationGroupMetadataMap() {
    return Optional.ofNullable(metadataMap).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  public static void refresh() {
    metadataMap = SPI.getCachedSortedServiceStream(ConfigurationGroupMetadataProvider.class)
        .map(ConfigurationGroupMetadataProvider::getAllConfigurationGroupMetadata)
        .flatMap(Collection::stream)
        .collect(Collectors.toMap(
            ConfigurationGroupMetadata::getPath,
            metadata -> metadata,
            ConfigurationGroupMetadata::merge,
            () -> new TreeMap<>(String::compareToIgnoreCase)
        ));
  }
}
