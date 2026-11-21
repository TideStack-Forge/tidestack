package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class ConfigurationMetadataCollector implements MetadataCollector {

  private final List<ConfigurationMetadataProvider> providers;
  private final static String METADATA_TYPE = "configuration";

  public ConfigurationMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(ConfigurationMetadataProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getAllConfigurationMetadata().stream().map(origin -> {
          var metadata = new Metadata();
          metadata.setName(origin.getKey());
          metadata.setType("configuration");
          metadata.setLabel(origin.getDescription());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(origin)));

          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
