package com.ouroboros.businessmodel.projection.data.feature;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan;
import com.ouroboros.businessmodel.plugins.FeatureQueryPlans;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaDecorator;
import com.ouroboros.data.model.PluginDescriptor;

/** Resolves decorator-owned soft-delete field identities before behavior publication. */
@Priority(55)
public final class FeatureQueryMPS extends AbstractDataMPS {
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var source = context.getModel(DataModelMeta.class, context.getSourceModel().getFullName()).orElse(null);
    if (source == null || !context.getSourceModel().getBehavior().getActions().containsKey("searchDeleted")) { return; }
    var decorated = DataModelMetaDecorator.applyDecorators(source);
    var descriptor = decorated.getPluginDescriptors().stream().filter(plugin -> "LogicalDelete".equalsIgnoreCase(plugin.getName()))
        .findFirst().orElseThrow(() -> new IllegalArgumentException("Soft-delete Action requires an effective LogicalDelete strategy"));
    String field = String.valueOf(descriptor.getConfig().get("isDeletedField"));
    if (decorated.getFields().stream().noneMatch(candidate -> candidate.getName().equals(field))) {
      throw new IllegalArgumentException("Soft-delete marker is missing from the projected source model");
    }
    var mappings = new LinkedHashMap<String, String>(); decorated.getFields().forEach(value -> mappings.put(value.getName(), value.getName()));
    String key = FeatureQueryPlans.sourceKey(decorated);
    var plan = new BusinessActionFeatureQueryPlan(BusinessActionFeatureQueryPlan.Kind.DELETED,
        source.getFullName(), source.getFullName(), key, key, mappings, Map.of(field, true));
    FeatureQueryPlans.publish(context, decorated, decorated, plan);
    source.addPluginDescriptor(new PluginDescriptor("AccessControl", Map.of(
        "readAuthority", source.getFullName() + ".search", "deletedReadAuthority", source.getFullName() + ".searchDeleted",
        "deletedField", field)));
  }
}
