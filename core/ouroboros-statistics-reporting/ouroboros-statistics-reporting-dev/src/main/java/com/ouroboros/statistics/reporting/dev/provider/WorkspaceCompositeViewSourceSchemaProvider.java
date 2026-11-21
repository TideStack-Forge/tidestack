package com.ouroboros.statistics.reporting.dev.provider;

import java.util.Optional;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.validation.CompositeViewSourceSchema;
import com.ouroboros.businessmodel.validation.CompositeViewSourceSchemaProvider;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetCompositeViewSourceSchemaProvider;

@Priority(200)
public class WorkspaceCompositeViewSourceSchemaProvider implements CompositeViewSourceSchemaProvider {
  @Override
  public boolean supportsCurrentContext() {
    return DevAppHolder.getAppName() != null && !DevAppHolder.getAppName().isBlank();
  }

  @Override
  public Optional<CompositeViewSourceSchema> resolveEmbedded(Object query, String owner) {
    return Optional.of(DatasetCompositeViewSourceSchemaProvider.schema(com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition.resolve(query, owner, true)));
  }

  @Override
  public Optional<CompositeViewSourceSchema> resolve(String datasetName) {
    return SpringContextUtil.getBean(DatasetDevService.class).getOne(datasetName)
        .map(DatasetCompositeViewSourceSchemaProvider::schema);
  }
}
