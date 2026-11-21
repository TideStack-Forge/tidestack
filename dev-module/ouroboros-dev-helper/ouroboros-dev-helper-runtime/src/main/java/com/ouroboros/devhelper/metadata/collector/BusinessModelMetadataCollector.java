package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.BusinessModelMetaProvider;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class BusinessModelMetadataCollector implements MetadataCollector {

  private final List<BusinessModelMetaProvider> providers;
  private final static String METADATA_TYPE = "business-model";

  public BusinessModelMetadataCollector() {
    providers = SPI
        .getCachedSortedServiceStream(BusinessModelMetaProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getBusinessModelMetaMap().entrySet().stream().map(entry -> {
          String key = entry.getKey();
          BusinessModelMeta model = entry.getValue();

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
