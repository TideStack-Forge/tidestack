package com.ouroboros.businessmodel.schema;

import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.uischema.schema.SchemaNode;

@Priority(0)
public class DefaultEditorSchemaProvider implements EditorSchemaProvider {
  @Override
  public boolean support(String modelType) {
    return "default".equalsIgnoreCase(modelType);
  }

  @Override
  public SchemaNode generateBaseSchema(Map<String, Object> context) {
    return new DefaultBusinessModelEditorSchemaGenerator(context).generate();
  }
}
