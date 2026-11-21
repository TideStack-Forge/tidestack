package com.ouroboros.businessmodel.plugins;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan;
import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport;

/** Compiles feature declarations and their resolved, model-owned read bindings. */
public final class FeatureQueryPlans {
  private FeatureQueryPlans() { }

  public static List<ActionDefinition> actions(DefaultBusinessModel model, Kind kind) {
    if (model.getFields().isEmpty()) { return List.of(); }
    var params = new LinkedHashMap<String, Object>();
    params.put("query", Map.of("type", "object", "additionalProperties", false, "default", Map.of(), "properties", Map.of(
        "where", Map.of(), "order", Map.of(), "limit", Map.of("type", "integer", "minimum", 0),
        "skip", Map.of("type", "integer", "minimum", 0))));
    if (kind == Kind.HISTORY) {
      params.put("recordId", SchemaModelJsonSchemaSupport.toJsonSchemaMap(BusinessModelSchemaSupport.primaryKeySchema(model)));
    }
    var paramsSchema = new LinkedHashMap<String, Object>();
    paramsSchema.put("type", "object"); paramsSchema.put("additionalProperties", false); paramsSchema.put("properties", params);
    if (kind == Kind.HISTORY) { paramsSchema.put("required", List.of("recordId")); }
    else { paramsSchema.put("default", Map.of("query", Map.of())); }
    var input = new LinkedHashMap<String, Object>(); input.put("type", "object"); input.put("additionalProperties", false);
    input.put("properties", Map.of("params", paramsSchema));
    if (kind == Kind.HISTORY) { input.put("required", List.of("params")); }
    var action = new ActionDefinition(); action.setName(kind.actionName()); action.setTitle(kind.title());
    action.setReadOnly(true); action.setIsEnabled(true); action.setInputSchema(input);
    action.setOutputSchema(Map.of("type", "object", "required", List.of("items", "total"), "additionalProperties", false,
        "properties", Map.of("items", Map.of("type", "array", "items", Map.of("type", "object")),
            "total", Map.of("type", "integer", "minimum", 0))));
    var binding = new ActionBinding(); binding.setKind("handler"); binding.setHandlerName(BusinessActionFeatureQueryPlan.HANDLER_NAME);
    action.setBinding(binding);
    return List.of(action);
  }

  public static String sourceKey(DataModelMeta source) {
    var keys = source.getPrimaryKeys();
    if (keys == null || keys.isEmpty()) {
      keys = source.getFields().stream().filter(field -> field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false))
          .map(com.ouroboros.data.model.DataModelFieldMeta::getName).toList();
    }
    if (keys == null || keys.size() != 1) {
      throw new IllegalArgumentException("Feature queries require one source primary key: " + source.getFullName());
    }
    return keys.getFirst();
  }

  public static void publish(ModelProjectionPipelineContext<?> context, DataModelMeta source,
                             DataModelMeta target, BusinessActionFeatureQueryPlan plan) {
    if (!(context.getSourceModel() instanceof DefaultBusinessModel business)
        || !business.getBehavior().getActions().containsKey(plan.kind().actionName())) { return; }
    if (!source.getFullName().equals(plan.sourceModel()) || !target.getFullName().equals(plan.queryModel())
        || plan.sourceFieldMappings().values().stream().anyMatch(name -> target.getFields().stream().noneMatch(field -> field.getName().equals(name)))) {
      throw new IllegalArgumentException("Feature query projection is inconsistent: " + plan.kind().actionName());
    }
    if (context.getModel(BusinessActionFeatureQueryPlan.class, plan.kind().actionName()).isPresent()) {
      throw new IllegalArgumentException("Duplicate feature query projection: " + plan.kind().actionName());
    }
    if (plan.kind() != Kind.DELETED) {
      var config = new LinkedHashMap<String, Object>(); config.put("readAuthority", source.getFullName() + ".search");
      config.put("requiredReadAuthority", source.getFullName() + "." + plan.kind().actionName());
      config.put("sourceModel", plan.sourceModel()); config.put("sourceKeyField", plan.sourceKeyField());
      config.put("snapshotKeyField", plan.businessKeyField()); config.put("sourceFieldMappings", plan.sourceFieldMappings());
      config.put("fixedReadConditions", plan.fixedConditions());
      target.addPluginDescriptor(new PluginDescriptor("AccessControl", config));
    }
    context.putModel(BusinessActionFeatureQueryPlan.class, plan.kind().actionName(), plan);
  }
}
