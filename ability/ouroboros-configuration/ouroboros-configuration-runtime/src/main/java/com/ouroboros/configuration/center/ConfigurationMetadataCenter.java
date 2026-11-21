package com.ouroboros.configuration.center;

import java.util.*;
import java.util.stream.Collectors;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class ConfigurationMetadataCenter {

  private static Map<String, ConfigurationMetadata> metadataMap;

  private ConfigurationMetadataCenter() {
  }

  /**
   * 按key获取元数据
   *
   * @param key
   * @return
   */
  public static Optional<ConfigurationMetadata> getConfigurationMetadata(String key) {
    return Optional.ofNullable(metadataMap).map(map -> map.get(key));
  }

  /**
   * 获取所有元数据
   *
   * @return
   */
  public static Map<String, ConfigurationMetadata> getConfigurationMetadataMap() {
    return Optional.ofNullable(metadataMap).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  /**
   * 刷新元数据
   */
  public static void refresh() {
    metadataMap = SPI.getCachedSortedServiceStream(ConfigurationMetadataProvider.class)
        .map(ConfigurationMetadataProvider::getAllConfigurationMetadata)
        .flatMap(Collection::stream)
        .collect(
            Collectors.toMap(
                ConfigurationMetadata::getKey,
                metadata -> metadata,
                ConfigurationMetadata::merge,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );
  }
}
