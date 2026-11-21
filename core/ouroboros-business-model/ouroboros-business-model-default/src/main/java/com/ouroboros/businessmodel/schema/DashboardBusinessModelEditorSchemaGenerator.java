package com.ouroboros.businessmodel.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.util.EditorSchemaTemplates;
import com.ouroboros.uischema.schema.SchemaNode;

public class DashboardBusinessModelEditorSchemaGenerator implements EditorSchemaGenerator {
  private final SchemaNode schemaNode;

  public DashboardBusinessModelEditorSchemaGenerator() {
    this(Collections.emptyMap());
  }

  public DashboardBusinessModelEditorSchemaGenerator(Map<String, Object> context) {
    Map<String, Object> finalContext = new HashMap<>();
    if (context != null) {
      finalContext.putAll(context);
    }
    finalContext.putIfAbsent("appName", "${appName}");
    finalContext.put("modelType", BusinessModelTypes.DASHBOARD);
    this.schemaNode = EditorSchemaTemplates.generate("dashboard/index", finalContext, SchemaNode.class);
  }

  @Override
  public SchemaNode generate() {
    return schemaNode;
  }
}
