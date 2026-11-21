package com.ouroboros.businessmodel.projection.data.plugin;

import java.util.List;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.plugin.*;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;
import com.ouroboros.data.model.DataModelMeta;

/** A third-party feature fixture loaded through the same SPI as production plugins. */
public final class FeaturePlanTestPlugin implements BusinessModelFeaturePlugin {
  public String getPluginId() { return "featurePlanFixture"; }
  public String getDisplayName() { return "Feature plan fixture"; }
  public PluginEditorSchemaContributor getEditorContributor() { return null; }
  public PluginProjector getProjector() { return null; }
  public List<String> getSupportedModelTypes() { return List.of(BusinessModelTypes.DEFAULT); }
  public PluginMetadata getMetadata() { return null; }

  public BusinessModelFeaturePlan prepare(BusinessModelMeta meta, BusinessModel model) {
    if (!"featurePlanCases".equals(meta.getNamespace())) { return BusinessModelFeaturePlugin.super.prepare(meta, model); }
    var action = new ActionDefinition(); action.setName("featureQuery"); action.setIsEnabled(true); action.setReadOnly(true);
    var binding = new ActionBinding(); binding.setKind("handler"); binding.setHandlerName("featureQueryFixture");
    action.setBinding(binding);
    return new BusinessModelFeaturePlan(getPluginId(), List.of(action), context -> {
      if (meta.getExtraProp(Boolean.class, "failFeatureProjection").orElse(false)) {
        throw new IllegalStateException("feature fixture projection failed");
      }
      context.getModel(DataModelMeta.class, meta.getFullName()).orElseThrow()
          .setExtraProp("featurePlanApplied", true);
    });
  }
}
