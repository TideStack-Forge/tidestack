package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class DataModelMetadataCollector implements MetadataCollector {

  private final List<DataModelMetaProvider> providers;
  private final static String METADATA_TYPE = "data-model";

  public DataModelMetadataCollector() {
    providers = SPI
        .getSortedServiceStream(DataModelMetaProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    return providers.stream().flatMap(provider ->
        provider.getDataModelMetaMap().entrySet().stream().map(entry -> {
          String key = entry.getKey();
          DataModelMeta meta = entry.getValue();

          var metadata = new Metadata();
          metadata.setName(key);
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(meta.getLabel());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(provider.getClass()));
          metadata.setSourceClassName(provider.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(provider.getClass()));
          var details = new java.util.LinkedHashMap<>(Json.toMap(Json.toJsonString(meta)));
          com.ouroboros.data.model.DataModelCenter.getDataModel(key).ifPresent(model -> {
            var reflector = new com.ouroboros.codeintelligence.type.JavaShapeIntrospector();
            var fields = new java.util.LinkedHashMap<String, com.ouroboros.codeintelligence.type.TypeShape>();
            model.getFields().forEach(field -> fields.put(field.getName(), reflector.describe(field.getValueType().getType())));
            details.put("recordSchema", com.ouroboros.codeintelligence.type.TypeShape.object(fields, "map").schema());
            details.put("instanceSchema", reflector.describe(model.getClass()).schema());
          });
          metadata.setDetails(details);

          return metadata;
        })
    ).collect(Collectors.toList());
  }
}
