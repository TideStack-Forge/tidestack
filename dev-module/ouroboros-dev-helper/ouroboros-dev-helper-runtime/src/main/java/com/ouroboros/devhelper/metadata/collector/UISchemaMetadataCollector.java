package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class UISchemaMetadataCollector implements MetadataCollector {

  private final List<UISchemaProvider> providers;
  private final String METADATA_TYPE = "ui-schema";

  public UISchemaMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(UISchemaProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getUISchemaMap().entrySet().stream().map(entry -> {
          String key = entry.getKey();
          Object schema = entry.getValue();

          var metadata = new Metadata();
          metadata.setName(key);
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(key);
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(schema)));

          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
