package com.ouroboros.businessmodel.schema;

import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.uischema.schema.SchemaNode;

@Priority(10)
public class DashboardEditorSchemaProvider implements EditorSchemaProvider {
  @Override
  public boolean support(String modelType) {
    return BusinessModelTypes.DASHBOARD.equalsIgnoreCase(modelType);
  }

  @Override
  public SchemaNode generateBaseSchema(Map<String, Object> context) {
    return new DashboardBusinessModelEditorSchemaGenerator(context).generate();
  }
}
