package com.ouroboros.devhelper.metadata.collector;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.util.MetadataAnnotationUtil;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.Json;

/**
 * @author liansz
 **/
public class FieldTypeDefinitionMetadataCollector implements MetadataCollector {
  private static final String METADATA_TYPE = "field";

  @Override
  public String getMetadataType() {
    return METADATA_TYPE;
  }

  @Override
  public List<Metadata> getAllMetadata() {
    var fieldTypeCatalog = SpringContextUtil.getBean(FieldTypeCatalog.class);
    return fieldTypeCatalog.listMetadata(FieldTypeCatalogContext.runtime()).stream()
        .map(origin -> {
          var metadata = new Metadata();
          metadata.setName(origin.getName());
          metadata.setType(METADATA_TYPE);
          metadata.setLabel(origin.getLabel());
          metadata.setPriority(MetadataAnnotationUtil.getPriority(fieldTypeCatalog.getClass()));
          metadata.setSourceClassName(fieldTypeCatalog.getClass().getName());
          metadata.setSourceType(MetadataAnnotationUtil.getSourceType(fieldTypeCatalog.getClass()));
          metadata.setDetails(Json.toMap(Json.toJsonString(origin)));

          return metadata;
        })
        .collect(Collectors.toList());
  }
}
