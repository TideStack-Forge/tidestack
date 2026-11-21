package com.ouroboros.businessmodel.plugin;

import java.util.List;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;

/**
 * Unified business model feature plugin contract.
 * <p>
 * A feature plugin can contribute editor UI, projection behavior, or both.
 */
public interface BusinessModelFeaturePlugin {

  /** Prepare declarations without projecting a bundle or resolving another model's behavior. */
  default BusinessModelFeaturePlan prepare(BusinessModelMeta meta, BusinessModel model) {
    var projector = getProjector();
    return new BusinessModelFeaturePlan(getPluginId(), List.of(), context -> {
      if (projector != null) { projector.project(meta, context); }
    });
  }

  /**
   * Unique plugin identifier.
   *
   * @return plugin id
   */
  String getPluginId();

  /**
   * Human-readable plugin display name.
   *
   * @return display name
   */
  String getDisplayName();

  /**
   * Editor fragment contributor of the plugin.
   *
   * @return editor contributor, or {@code null} when the plugin does not
   * contribute editor schema
   */
  PluginEditorSchemaContributor getEditorContributor();

  /**
   * Projection logic of the plugin.
   *
   * @return projector, or {@code null} when the plugin does not contribute
   * projection behavior
   */
  PluginProjector getProjector();

  /**
   * Supported business model types of this plugin.
   *
   * @return model type list
   */
  List<String> getSupportedModelTypes();

  /**
   * Metadata of the plugin.
   *
   * @return plugin metadata
   */
  PluginMetadata getMetadata();
}
