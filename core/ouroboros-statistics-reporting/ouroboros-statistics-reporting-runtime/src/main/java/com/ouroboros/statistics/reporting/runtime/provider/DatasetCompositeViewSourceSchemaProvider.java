package com.ouroboros.statistics.reporting.runtime.provider;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.validation.CompositeViewSourceSchema;
import com.ouroboros.businessmodel.validation.CompositeViewSourceSchemaProvider;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;

@Priority(100)
public class DatasetCompositeViewSourceSchemaProvider implements CompositeViewSourceSchemaProvider {
  @Override
  public boolean supportsCurrentContext() {
    return SpringContextUtil.getApplicationContext() != null;
  }

  @Override
  public Optional<CompositeViewSourceSchema> resolveEmbedded(Object query, String owner) {
    return Optional.of(schema(com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition.resolve(query, owner)));
  }

  @Override
  public Optional<CompositeViewSourceSchema> resolve(String datasetName) {
    return SpringContextUtil.getBean(DefaultDatasetDefinitionCenter.class)
        .getDefinition(datasetName).map(DatasetCompositeViewSourceSchemaProvider::schema);
  }

  public static CompositeViewSourceSchema schema(DatasetDefinition dataset) {
    var fields = dataset.getSchema() == null || dataset.getSchema().getFields() == null
        ? List.<CompositeViewSourceSchema.Field>of()
        : dataset.getSchema().getFields().stream().map(field -> new CompositeViewSourceSchema.Field(
            field.getName(), field.getDataType(), field.isNullable(), field.isFilterable(), field.isSortable())).toList();
    return new CompositeViewSourceSchema(Boolean.TRUE.equals(dataset.getIsEnabled()), fields);
  }
}
