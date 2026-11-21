package com.ouroboros.businessmodel.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ouroboros.businessmodel.util.EditorSchemaTemplates;
import com.ouroboros.uischema.schema.SchemaNode;

public class DefaultBusinessModelEditorSchemaGenerator implements EditorSchemaGenerator {
  private final SchemaNode schemaNode;

  public DefaultBusinessModelEditorSchemaGenerator() {
    this(Collections.emptyMap());
  }

  public DefaultBusinessModelEditorSchemaGenerator(Map<String, Object> context) {
    Map<String, Object> finalContext = new HashMap<>();
    if (context != null) {
      finalContext.putAll(context);
    }
    finalContext.putIfAbsent("appName", "${appName}");
    this.schemaNode = EditorSchemaTemplates.generate("default/index", finalContext, SchemaNode.class);
  }

  @Override
  public SchemaNode generate() {
    return schemaNode;
  }

}
