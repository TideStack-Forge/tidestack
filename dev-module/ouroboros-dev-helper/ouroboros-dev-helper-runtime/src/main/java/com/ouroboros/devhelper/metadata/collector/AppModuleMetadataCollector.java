package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.appmodule.AppModuleProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

public class AppModuleMetadataCollector implements MetadataCollector {
  List<AppModuleProvider> providers;
  private static final String METADATA_TYPE = "app-module";

  public AppModuleMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(AppModuleProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getAppModules().stream().map(origin -> {
          var metadata = new Metadata();
          metadata.setName(origin.getName());
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(origin.getTitle());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(origin)));
          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
