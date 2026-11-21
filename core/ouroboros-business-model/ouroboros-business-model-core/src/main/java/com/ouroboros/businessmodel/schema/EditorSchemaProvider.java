package com.ouroboros.businessmodel.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * SPI for loading the base editor schema of a business model type.
 * <p>
 * Implementations are typically discovered through Java SPI and ordered by
 * {@code @Priority}. When multiple providers support the same model type, the
 * provider with the higher priority should win.
 */
public interface EditorSchemaProvider {

  /**
   * Whether the current provider supports the given business model type.
   *
   * @param modelType business model type
   * @return {@code true} when the provider can generate the base schema
   */
  boolean support(String modelType);

  /**
   * Generate the base editor schema for the current business model type.
   *
   * @param context generation context
   * @return generated base schema
   */
  SchemaNode generateBaseSchema(Map<String, Object> context);
}
