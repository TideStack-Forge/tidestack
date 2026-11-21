package com.ouroboros.businessmodel.schema;

import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;

/**
 * Contributes editor schema fragments for a business model feature plugin.
 * <p>
 * Contributors are composed in ascending {@link #getOrder()} order so earlier
 * contributors can establish structure that later contributors extend.
 */
public interface PluginEditorSchemaContributor {

  /**
   * Unique plugin identifier.
   *
   * @return plugin id
   */
  String getPluginId();

  /**
   * Build a schema fragment contributed by the plugin.
   *
   * @param context generation context
   * @return contributed schema fragment
   */
  SchemaNode contributeEditorFragment(Map<String, Object> context);

  /**
   * Merge order of the contributed fragment.
   *
   * @return contributor order, lower values are merged earlier
   */
  int getOrder();
}
