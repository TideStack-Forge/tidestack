package com.ouroboros.businessmodel.plugin;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * Projection callback of a business model feature plugin.
 */
public interface PluginProjector {

  /**
   * Apply plugin-specific projection logic.
   *
   * @param meta business model metadata
   * @param context projection pipeline context
   */
  void project(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context);
}
