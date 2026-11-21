package com.ouroboros.businessmodel.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.util.EditorSchemaTemplates;
import com.ouroboros.uischema.schema.SchemaNode;

public class CompositeViewBusinessModelEditorSchemaGenerator implements EditorSchemaGenerator {
  private final SchemaNode schemaNode;

  public CompositeViewBusinessModelEditorSchemaGenerator() {
    this(Collections.emptyMap());
  }

  public CompositeViewBusinessModelEditorSchemaGenerator(Map<String, Object> context) {
    Map<String, Object> finalContext = new HashMap<>();
    if (context != null) {
      finalContext.putAll(context);
    }
    finalContext.putIfAbsent("appName", "${appName}");
    finalContext.put("modelType", BusinessModelTypes.COMPOSITE_VIEW);
    this.schemaNode = EditorSchemaTemplates.generate("composite-view/index", finalContext, SchemaNode.class);
  }

  @Override
  public SchemaNode generate() {
    return schemaNode;
  }
}
