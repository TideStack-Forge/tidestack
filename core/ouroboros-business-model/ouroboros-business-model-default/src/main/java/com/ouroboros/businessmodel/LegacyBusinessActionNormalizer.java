package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.BehaviorMemberSource;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventReference;
import com.ouroboros.businessmodel.action.BusinessActionMutationEventPlan;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.custom.CustomConfig;

/** Converts the existing CRUD and custom API configuration into Action definitions. */
final class LegacyBusinessActionNormalizer {
  private static final List<CrudAction> CRUD_ACTIONS = List.of(
      new CrudAction("add", false),
      new CrudAction("getOne", true),
      new CrudAction("search", true),
      new CrudAction("update", false),
      new CrudAction("delete", false));

  private LegacyBusinessActionNormalizer() {
  }

  static NormalizedBehavior normalize(DefaultBusinessModelMeta meta, DefaultBusinessModel model) {
    List<ActionDefinition> actions = new ArrayList<>();
    var workflow = WorkflowBusinessActionNormalizer.normalize(meta, model);
    var sources = new ArrayList<BehaviorMemberSource>(workflow.sources());
    var executionAuthorities = new java.util.LinkedHashMap<>(workflow.executionAuthorities());
    if (!meta.getFields().isEmpty()) {
      CRUD_ACTIONS.forEach(crud -> {
        actions.add(normalizeCrud(meta, model, crud));
        sources.add(generatedSource(meta, "action", crud.name, "apis." + crud.name));
      });
      normalizeFeature(meta, model, actions, "batchDelete", "isEnabledBatchDelete");
      normalizeFeature(meta, model, actions, "import", "isEnabledImport");
      model.getFeaturePlans().forEach(plan -> {
        actions.addAll(plan.getActions());
        plan.getActions().forEach(action -> sources.add(generatedSource(meta, "action", action.getName(), plan.getConfigurationPath())));
      });
      for (String feature : List.of("batchDelete", "import")) {
        if (actions.stream().anyMatch(action -> feature.equals(action.getName()))) {
          String flag = "batchDelete".equals(feature) ? "isEnabledBatchDelete" : "isEnabledImport";
          sources.add(generatedSource(meta, "action", feature, "features." + flag));
        }
      }
      if (meta.getExtraPropDeep(Boolean.class, "features.isEnabledServerSideExport").orElse(false)) {
        var export = baseAction("export", "导出", true, Map.of());
        export.setReadOnly(true);
        export.setInputSchema(crudInputSchema(meta, model, "search"));
        export.setOutputSchema(Map.of("type", "string", "format", "binary", "contentMediaType",
            com.ouroboros.businessmodel.action.BusinessModelExportHandler.MEDIA_TYPE));
        var binding = new ActionBinding(); binding.setKind("handler");
        binding.setHandlerName(com.ouroboros.businessmodel.action.BusinessModelExportHandler.NAME);
        export.setBinding(binding); actions.add(export);
        sources.add(generatedSource(meta, "action", "export", "features.isEnabledServerSideExport"));
        executionAuthorities.put("export", meta.getFullName() + ".serverSideExport");
      }
    }
    List<ActionDefinition> defaultActions = List.copyOf(actions);
    actions.addAll(workflow.actions());
    model.getCustomConfigs().stream().filter(config -> !config.getApi().isActionReference())
        .filter(config -> config.getApi().isActionBindingEnabled())
        .forEach(config -> normalizeCustom(meta, config.getExtraProps()).ifPresent(action -> {
          actions.add(action);
          sources.add(new BehaviorMemberSource("action", action.getName(), "definition", "legacy", new BehaviorDefinitionSource(
              meta.getFullName(), "custom." + action.getName() + ".api", null)));
        }));
    var primaryKeys = model.getFields().stream().filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKey())).toList();
    if (meta.getFields().isEmpty() || primaryKeys.size() != 1 || !"id".equalsIgnoreCase(primaryKeys.get(0).getName())) {
      return new NormalizedBehavior(List.copyOf(actions), workflow.events(), Map.of(), Map.copyOf(executionAuthorities), List.copyOf(sources));
    }
    Map<String, String> names = Map.of("INSERT", "recordsCreated", "UPDATE", "recordsUpdated", "DELETE", "recordsDeleted");
    Map<String, Object> idSchema = com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport.toJsonSchemaMap(
        com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport.primaryKeySchema(model));
    var events = new ArrayList<EventDefinition>();
    for (String operation : List.of("INSERT", "UPDATE", "DELETE")) {
      var event = new EventDefinition();
      event.setName(names.get(operation));
      event.setTitle(names.get(operation));
      event.setOccurrenceCondition("本模型 " + operation + " 操作随源事务成功提交");
      event.setSubject("model");
      event.setAllowedProducerKinds(List.of("action"));
      event.setRequireUnfilteredModelRead(true);
      event.setPayloadSchema(Map.of("type", "object", "additionalProperties", false,
          "required", List.of("operation", "objectIds", "affectedCount"), "properties", Map.of(
              "operation", Map.of("type", "string", "enum", List.of(operation)),
              "objectIds", Map.of("type", "array", "minItems", 1, "uniqueItems", true, "items", idSchema),
              "affectedCount", Map.of("type", "integer", "minimum", 1))));
      events.add(event);
      sources.add(generatedSource(meta, "event", event.getName(), "fields"));
    }
    List<EventDefinition> mutationEvents = List.copyOf(events);
    events.addAll(workflow.events());
    var plans = new java.util.LinkedHashMap<String, BusinessActionMutationEventPlan>();
    defaultActions.stream().filter(action -> List.of("add", "update", "delete", "batchDelete", "import").contains(action.getName()))
        .filter(action -> !Boolean.TRUE.equals(action.getReadOnly()))
        .forEach(action -> {
          action.setEmits(mutationEvents.stream().map(event -> {
            var reference = new EventReference();
            reference.setModelFullName(meta.getFullName());
            reference.setEventName(event.getName());
            return reference;
          }).toList());
          plans.put(action.getName(), new BusinessActionMutationEventPlan(names));
        });
    return new NormalizedBehavior(List.copyOf(actions), List.copyOf(events), Map.copyOf(plans), Map.copyOf(executionAuthorities), List.copyOf(sources));
  }

  record NormalizedBehavior(List<ActionDefinition> actions, List<EventDefinition> events,
                            Map<String, BusinessActionMutationEventPlan> plans, Map<String, String> executionAuthorities,
                            List<BehaviorMemberSource> sources) {
    NormalizedBehavior(List<ActionDefinition> actions, List<EventDefinition> events,
        Map<String, BusinessActionMutationEventPlan> plans, Map<String, String> executionAuthorities) {
      this(actions, events, plans, executionAuthorities, List.of());
    }
  }

  static BehaviorMemberSource generatedSource(DefaultBusinessModelMeta meta, String memberType, String name, String path) {
    return new BehaviorMemberSource(memberType, name, "definition", "generated",
        new BehaviorDefinitionSource(meta.getFullName(), path, "2.0"));
  }

  private static void normalizeFeature(DefaultBusinessModelMeta meta, DefaultBusinessModel model, List<ActionDefinition> actions,
                                       String name, String enabledFlag) {
    if (!meta.getExtraPropDeep(Boolean.class, "features", enabledFlag).orElse(false)) {
      return;
    }
    ActionDefinition action = baseAction(name, name, true, Map.of());
    action.setReadOnly(false);
    action.setInputSchema(crudInputSchema(meta, model, name));
    ActionBinding binding = logicFlowBinding(meta.getFullName() + "." + name);
    binding.setInputMapping(crudInputMapping(name));
    action.setBinding(binding);
    actions.add(action);
  }

  private static ActionDefinition normalizeCrud(DefaultBusinessModelMeta meta, DefaultBusinessModel model, CrudAction crud) {
    Map<?, ?> config = meta.getExtraPropDeep(Map.class, "apis", crud.name).orElse(Map.of());
    ActionDefinition action = baseAction(crud.name, crud.name,
        Boolean.TRUE.equals(config.get("isEnabled")), config);
    action.setReadOnly(config.containsKey("readOnly")
        ? Boolean.TRUE.equals(config.get("readOnly")) : crud.readOnly);
    if (action.getInputSchema().isEmpty()) {
      action.setInputSchema(crudInputSchema(meta, model, crud.name));
    }
    ActionBinding binding = logicFlowBinding(meta.getFullName() + "." + crud.name);
    binding.setInputMapping(crudInputMapping(crud.name));
    action.setBinding(binding);
    return action;
  }

  private static java.util.Optional<ActionDefinition> normalizeCustom(DefaultBusinessModelMeta meta,
                                                                         Map<?, ?> definition) {
    String name = stringValue(definition.get("name"));
    if (name.isBlank()) {
      return java.util.Optional.empty();
    }
    Map<?, ?> api = mapValue(definition.get("api"));
    if (!Boolean.TRUE.equals(api.get("isEnabled")) || !CustomConfig.CustomApiConfig.isActionBindingEnabled(api)) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(customDefinition(meta, definition));
  }

  /** Also used by explicit format upgrade so disabled inline definitions are not lost. */
  static ActionDefinition customDefinition(DefaultBusinessModelMeta meta, Map<?, ?> definition) {
    String name = stringValue(definition.get("name"));
    Map<?, ?> api = mapValue(definition.get("api"));
    ActionDefinition action = baseAction(name, stringValue(definition.get("title")),
        Boolean.TRUE.equals(api.get("isEnabled")), Map.of());
    action.setInputSchema(schema(customProperty(definition, api, "inputSchema")));
    action.setOutputSchema(schema(customProperty(definition, api, "outputSchema")));
    action.setDescription(stringValue(definition.get("description")));
    action.setReadOnly(Boolean.TRUE.equals(customProperty(definition, api, "readOnly")));
    action.setPrecondition(customProperty(definition, api, "precondition"));
    action.setAuthorization(customProperty(definition, api, "authorization"));
    Object emits = customProperty(definition, api, "emits");
    if (emits != null) {
      action.setEmits(com.ouroboros.util.Json.toBean(new com.fasterxml.jackson.core.type.TypeReference<List<EventReference>>() { },
          com.ouroboros.util.Json.toJsonString(emits)));
    }
    if (Boolean.TRUE.equals(api.get("isGenerateImplementation"))) {
      ActionBinding binding = logicFlowBinding(meta.getFullName() + "." + name);
      action.setBinding(binding);
    }
    return action;
  }

  private static Object customProperty(Map<?, ?> definition, Map<?, ?> api, String key) {
    return api.containsKey(key) ? api.get(key) : definition.get(key);
  }

  private static ActionDefinition baseAction(String name,
                                            String title,
                                            boolean enabled,
                                            Map<?, ?> schemaSource) {
    ActionDefinition action = new ActionDefinition();
    action.setName(name);
    action.setTitle(title.isBlank() ? name : title);
    action.setIsEnabled(enabled);
    action.setInputSchema(schema(schemaSource.get("inputSchema")));
    action.setOutputSchema(schema(schemaSource.get("outputSchema")));
    return action;
  }

  private static Map<String, Object> schema(Object value) {
    if (!(value instanceof Map<?, ?> source)) {
      return Map.of();
    }
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    source.forEach((key, item) -> {
      if (key instanceof String name) {
        result.put(name, item);
      }
    });
    return result;
  }

  private static ActionBinding logicFlowBinding(String flowName) {
    ActionBinding binding = new ActionBinding();
    binding.setKind("logicflow");
    binding.setFlowName(flowName);
    return binding;
  }

  private static Map<String, Object> crudInputMapping(String actionName) {
    return switch (actionName) {
      case "add", "import" -> Map.of("data", "${ input.data }", "context", "${ context }");
      case "batchDelete" -> Map.of("data.ids", "${ input.params.records?.![id] }", "context", "${ context }");
      case "getOne", "delete" -> Map.of("params.id", "${ input.params.record.id }", "context", "${ context }");
      case "search" -> Map.of("params", "${ input.params.query }", "context", "${ context }");
      case "update" -> Map.of("params.id", "${ input.params.record.id }", "data", "${ input.data }", "context", "${ context }");
      default -> Map.of();
    };
  }

  private static Map<String, Object> crudInputSchema(DefaultBusinessModelMeta meta, DefaultBusinessModel model, String name) {
    Map<String, Object> id = com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport
        .toJsonSchemaMap(com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport.primaryKeySchema(model));
    Map<String, Object> reference = Map.of("type", "object", "x-business-object-ref", meta.getFullName(),
        "required", List.of("id"), "additionalProperties", false, "properties", Map.of("id", id));
    Map<String, Object> properties = new java.util.LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    switch (name) {
      case "getOne", "delete", "update" -> {
        properties.put("params", Map.of("type", "object", "required", List.of("record"),
            "additionalProperties", false, "properties", Map.of("record", reference)));
        required.add("params");
        if ("update".equals(name)) {
          var payloadId = new java.util.LinkedHashMap<>(id);
          payloadId.put("x-business-object-identity", "/params/record/id");
          properties.put("data", Map.of("type", "object", "properties", Map.of("id", payloadId)));
          required.add("data");
        }
      }
      case "search" -> properties.put("params", Map.of("type", "object", "default", Map.of("query", Map.of()),
          "additionalProperties", false, "properties", Map.of("query", Map.of("type", "object", "default", Map.of()))));
      case "batchDelete" -> {
        properties.put("params", Map.of("type", "object", "required", List.of("records"),
            "additionalProperties", false, "properties", Map.of("records", Map.of("type", "array", "minItems", 1, "items", reference))));
        required.add("params");
      }
      case "import" -> {
        properties.put("data", Map.of("type", "object", "required", List.of("file"), "properties", Map.of("file", Map.of())));
        required.add("data");
      }
      default -> {
        properties.put("data", Map.of("type", "object"));
        required.add("data");
      }
    }
    return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
  }

  private static Map<?, ?> mapValue(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static String stringValue(Object value) {
    return value instanceof String string ? string : "";
  }

  private record CrudAction(String name, boolean readOnly) {
  }
}
