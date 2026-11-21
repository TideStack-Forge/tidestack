package com.ouroboros.businessmodel.plugins;

import java.util.List;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.plugin.*;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;

/** Declares the read capability of the existing soft-delete projection. */
public final class SoftDeleteFeaturePlugin implements BusinessModelFeaturePlugin {
  public String getPluginId() { return "softDelete"; }
  public String getDisplayName() { return "数据软删除"; }
  public PluginEditorSchemaContributor getEditorContributor() { return null; }
  public PluginProjector getProjector() { return null; }
  public List<String> getSupportedModelTypes() { return List.of(BusinessModelTypes.DEFAULT); }
  public PluginMetadata getMetadata() { return new PluginMetadata("1.0.0", "已有软删除策略的读取能力", "Ouroboros", List.of()); }

  public BusinessModelFeaturePlan prepare(BusinessModelMeta meta, BusinessModel model) {
    boolean enabled = isEnabled(meta);
    var actions = enabled && model instanceof DefaultBusinessModel business ? FeatureQueryPlans.actions(business, Kind.DELETED)
        : List.<com.ouroboros.businessmodel.behavior.ActionDefinition>of();
    return new BusinessModelFeaturePlan(getPluginId(), actions, context -> { }, "features.isEnabledSoftDelete", enabled
        ? "实际删除本模型对象时执行软删除；业务写集仍为 DELETE，不因底层 UPDATE 另发 recordsUpdated" : null);
  }

  public static boolean isEnabled(BusinessModelMeta meta) {
    return meta.getExtraPropDeep(Boolean.class, "features.isEnabledSoftDelete").orElse(false)
        && !(meta instanceof DefaultBusinessModelMeta defaults && Boolean.TRUE.equals(defaults.getEnableArchiveDelete()));
  }
}
