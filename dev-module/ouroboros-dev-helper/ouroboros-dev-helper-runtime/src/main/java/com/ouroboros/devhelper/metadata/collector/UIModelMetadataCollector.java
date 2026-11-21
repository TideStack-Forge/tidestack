package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class UIModelMetadataCollector implements MetadataCollector {

  private final List<UIModelProvider> providers;
  public static final String METADATA_TYPE = "ui-model";

  public UIModelMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(UIModelProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getUIModelMap().entrySet().stream().map(entry -> {
          String key = entry.getKey();
          UIModel model = entry.getValue();

          var metadata = new Metadata();
          metadata.setName(key);
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(model.getTitle());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(model)));

          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
