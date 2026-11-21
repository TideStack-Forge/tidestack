package com.ouroboros.businessmodel.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.businessmodel.BusinessModelScriptAction;
import com.ouroboros.logicflow.dev.scriptintelligence.MetadataFlowTypeContracts;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;

/** Projects formal business action, event, and generated-flow schemas into editor scopes. */
public final class BusinessModelCodeContextProvider implements CodeContextProvider {
  private final BusinessModelDevService businessModelDevService;
  private final LogicFlowCodeContextProvider logicFlowProvider;

  public BusinessModelCodeContextProvider(BusinessModelDevService businessModelDevService) {
    this.businessModelDevService = businessModelDevService;
    this.logicFlowProvider = new LogicFlowCodeContextProvider();
  }
  public Set<String> resourceTypes() { return Set.of("business-model"); }

  public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    var pointer = request.location().pointer();
    var draft = request.draft();
    if (pointer.matches("/fields/\\d+/defaultValue")) return fieldDefault(request, metadata);
    var projected = projectedFlowContext(request, pointer);
    if (projected != null) {
      return logicFlowProvider.describeFlow(projected.flow(), projected.nodeName(), projected.nodePointer(), request.language(),
          projected.inputs(), metadata.environment(), new MetadataFlowTypeContracts(metadata.application(), projected.contracts()));
    }
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    var expected = TypeShape.unknown("unspecified-result");
    if (!"spel".equals(request.language())) throw new BusinessException("此业务模型位置需要 spel 语言");
    var allowGlobals = true;
    var mode = "template";
    var current = "2.0".equals(draft.path("formatVersion").asText());
    if (pointer.startsWith("/actions/")) {
      var action = indexedObject(draft.path("actions"), pointer, 1);
      if (pointer.endsWith("/precondition/expression")) {
        allowGlobals = false;
        add(bindings, "input", shape(action, "inputSchema"), "action-input");
        add(bindings, "objectStates", TypeShape.unknown("authorized-object-roles"), "action-object-states");
        expected = TypeShape.scalar("boolean");
      } else if (pointer.contains("/binding/inputMapping")) {
        add(bindings, "input", shape(action, "inputSchema"), "action-input");
        add(bindings, "context", actionContext(), "action-context");
        if (!current) mode = "legacy-input";
      } else if (pointer.contains("/binding/outputMapping")) {
        add(bindings, "result", new MetadataFlowTypeContracts(metadata.application())
            .find("logic-flow", action.path("binding").path("flowName").asText(), "output"), "flow-output");
        if (!current) mode = "legacy-output";
        expected = shape(action, "outputSchema");
      } else throw new BusinessException("不支持的 Action 代码位置: " + pointer);
    } else if (pointer.startsWith("/eventSubscriptions/")) {
      var subscription = indexedObject(draft.path("eventSubscriptions"), pointer, 1);
      var sourceModel = subscription.path("sourceEvent").path("modelFullName").asText();
      var localName = draft.path("namespace").asText("").isBlank() ? draft.path("name").asText()
          : draft.path("namespace").asText() + "." + draft.path("name").asText();
      var event = draft.path("events");
      if (!sourceModel.isBlank() && !sourceModel.equals(localName)) {
        var resources = metadata.application().findModuleMetadata("business-model", sourceModel);
        event = resources.size() == 1 ? com.ouroboros.util.Json.toBean(JsonNode.class,
            com.ouroboros.util.Json.toJsonString(resources.getFirst().getDetails())).path("events")
            : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      }
      var payload = TypeShape.unknown("event-payload-schema-unavailable");
      if (event.isArray()) {
        for (var definition : event) {
          if (definition.path("name").asText().equals(subscription.path("sourceEvent").path("eventName").asText())) payload = shape(definition, "payloadSchema");
        }
      }
      add(bindings, "event", BusinessEventTypeShapes.envelope(payload, TypeShape.unknown("subject-id")), "event-envelope");
      if (pointer.endsWith("/filter")) { allowGlobals = false; expected = TypeShape.scalar("boolean"); }
      else if (!pointer.contains("/inputMapping")) {
        throw new BusinessException("不支持的事件代码位置: " + pointer);
      }

    } else if (pointer.matches("/custom/\\d+/api/actionInputMapping(/.*)?")) {
      var unknownMap = TypeShape.object(Map.of(), "map");
      var open = (com.fasterxml.jackson.databind.node.ObjectNode) unknownMap.schema(); open.put("additionalProperties", true);
      unknownMap = new TypeShape(open);
      var requestShape = TypeShape.object(Map.of("body", TypeShape.unknown("http-request-body-contract"),
          "params", unknownMap, "pathVariables", unknownMap), "map");
      add(bindings, "request", requestShape, "http-request");
      add(bindings, "query", unknownMap, "http-query"); add(bindings, "headers", unknownMap, "http-headers");
      allowGlobals = false;
      if (!current) { mode = "legacy-http"; add(bindings, "body", requestShape.property("body"), "http-body");
        add(bindings, "pathVariables", unknownMap, "http-path"); }
    } else throw new BusinessException("业务模型代码位置未声明: " + pointer);
    var diagnostics = bindings.stream().filter(binding -> binding.getShape().isUnknown())
        .map(binding -> Map.<String, Object>of("code", "unknown-binding-shape", "message", "上下文形状未声明: " + binding.getName())).toList();
    return new CodeContextDescription(request.language(), bindings, expected, diagnostics, allowGlobals, Map.of("embeddedExpressionMode", mode));
  }

  private CodeContextDescription fieldDefault(CodeContextRequest request, ReadOnlyMetadataView metadata) {
    if (!"spel".equals(request.language())) throw new BusinessException("字段默认值需要 spel 语言");
    var draft = request.draft();
    var name = draft.path("namespace").asText().isBlank() ? draft.path("name").asText()
        : draft.path("namespace").asText() + "." + draft.path("name").asText();
    var known = new MetadataFlowTypeContracts(metadata.application()).find("data-model", name, "record");
    var originals = metadata.application().findModuleMetadata("data-model", name);
    var original = originals.size() == 1 ? com.ouroboros.util.Json.toBean(JsonNode.class,
        com.ouroboros.util.Json.toJsonString(originals.getFirst().getDetails())) : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    var fields = new java.util.LinkedHashMap<String, TypeShape>();
    for (var field : draft.path("fields")) {
      var key = field.path("name").asText();
      TypeShape shape = TypeShape.unknown("draft-field-type");
      for (var previous : original.path("fields")) {
        if (key.equals(previous.path("name").asText()) && field.path("type").equals(previous.path("type"))) shape = known.property(key);
      }
      fields.put(key, shape);
    }
    var bindings = new ArrayList<ScriptIntelligenceGlobal>();
    fields.forEach((key, shape) -> add(bindings, key, shape, "record-field"));
    add(bindings, "$record", TypeShape.object(fields, "map"), "record-view");
    var field = indexedObject(draft.path("fields"), request.location().pointer(), 1);
    return new CodeContextDescription("spel", bindings, fields.getOrDefault(field.path("name").asText(), TypeShape.unknown("field-type")),
        List.of(), true, Map.of("embeddedExpressionMode", "bare"));
  }

  private ProjectedFlow projectedFlowContext(CodeContextRequest request, String pointer) {
    if ((!pointer.startsWith("/apis/") && !pointer.startsWith("/features/import/dataMap")
        && !pointer.startsWith("/workflow/formDataMapping") && !pointer.matches("/custom/\\d+/api/script")
        && !request.codeSite().equals("business-model.script-action")) || request.draft() == null || !request.draft().has("name")) return null;
    var model = businessModelDevService.previewBusinessModel(request.draft().toString());
    var bundle = model.buildModelBundle();
    var dataModels = new java.util.LinkedHashMap<String, JsonNode>();
    bundle.getModelMap(DataModelMeta.class).forEach((name, definition) -> dataModels.put(name,
        com.ouroboros.util.Json.toBean(JsonNode.class, com.ouroboros.util.Json.toJsonString(definition))));
    FlowModelMeta flow;
    TypeShape input;
    boolean implicitContext = true;
    if (request.codeSite().equals("business-model.script-action")) {
      flow = com.ouroboros.util.SPI.getCachedSortedServiceStream(BusinessModelScriptAction.class).findFirst()
          .orElseThrow(() -> new BusinessException("业务模型脚本投影能力未注册")).scriptFlow(model.getFullName() + ".__codePreview", model.getFullName(), model,
          request.language(), request.draft().path("script").asText(""));
      input = TypeShape.mapSchema(com.ouroboros.util.Json.toBean(JsonNode.class,
          com.ouroboros.util.Json.toJsonString(BusinessModelScriptAction.inputSchema())));
    } else {
      var parts = pointer.split("/");
      if (parts.length < 4) return null;
      var operation = pointer.startsWith("/features/import/") ? "import" : pointer.startsWith("/workflow/") ? "startProcess"
          : pointer.startsWith("/custom/") ? request.draft().path("custom").path(Integer.parseInt(parts[2])).path("name").asText() : parts[2];
      var flowName = model.getFullName() + "." + operation;
      flow = bundle.getModelMap(FlowModelMeta.class).get(flowName);
      if (flow == null) throw new BusinessException("当前业务模型没有生成此脚本所属的逻辑流: " + flowName);
      var action = model.getBehavior().getActions().get(operation);
      input = action == null ? TypeShape.unknown("missing-action-input-contract")
          : TypeShape.mapSchema(com.ouroboros.util.Json.toBean(JsonNode.class, com.ouroboros.util.Json.toJsonString(action.getInputSchema())));
      if (action != null && action.getBinding() != null && !action.getBinding().getInputMapping().isEmpty()) {
        implicitContext = false;
        var mapping = action.getBinding().getInputMapping();
        var source = model.getBehavior().getActionSources().get(operation);
        if (source != null && !source.current()) mapping = com.ouroboros.businessmodel.behavior.BusinessBehaviorMappingPlan
            .upgradeFlowInput(mapping, action.getInputSchema(), source);
        var mappedFields = new java.util.LinkedHashMap<String, TypeShape>();
        var inputBindings = Map.of("input", input, "context", actionContext());
        mapping.forEach((name, rule) -> mappedFields.put(name, mappingType(
            com.ouroboros.util.Json.toBean(JsonNode.class, com.ouroboros.util.Json.toJsonString(rule)), inputBindings, 0)));
        input = TypeShape.hierarchicalObject(mappedFields);
      }
    }
    var fields = new java.util.LinkedHashMap<>(input.properties());
    if (implicitContext) fields.put("context", actionContext());
    var flowInput = TypeShape.object(fields, "map");
    var node = flow.getNodes().stream()
        .filter(candidate -> {
          var source = candidate.getExtraProp(String.class, "codeSourcePointer").orElse("");
          return !source.isEmpty() && (pointer.equals(source) || pointer.startsWith(source + "/"));
        })
        .findFirst().orElse(null);
    if (node == null) throw new BusinessException("当前脚本槽位未参与正式逻辑流投影: " + pointer);
    var source = node.getExtraProp(String.class, "codeSourcePointer").orElseThrow();
    var nodePointer = "mapping".equalsIgnoreCase(node.getType()) ? "/mapping" + pointer.substring(source.length()) : "/script";
    return new ProjectedFlow(flow, node.getName(), nodePointer, List.of(flowInput), Map.of("data-model", dataModels));
  }

  private record ProjectedFlow(FlowModelMeta flow, String nodeName, String nodePointer, List<TypeShape> inputs, Map<String, Map<String, JsonNode>> contracts) { }

  private static TypeShape mappingType(JsonNode mapping, Map<String, TypeShape> bindings, int depth) {
    if (depth > 32) return TypeShape.unknown("mapping-depth-budget");
    if (mapping.isTextual()) return mapping.asText().startsWith("${")
        ? new com.ouroboros.expression.intelligence.SpelTypeQuery().infer(mapping.asText(), bindings) : TypeShape.scalar("string");
    if (mapping.isObject()) {
      var fields = new java.util.LinkedHashMap<String, TypeShape>();
      mapping.fields().forEachRemaining(entry -> fields.put(entry.getKey(), mappingType(entry.getValue(), bindings, depth + 1)));
      return TypeShape.object(fields, "map");
    }
    if (mapping.isArray()) {
      TypeShape element = null;
      for (var item : mapping) { var shape = mappingType(item, bindings, depth + 1); element = element == null ? shape : element.merge(shape); }
      return TypeShape.array(element == null ? TypeShape.unknown("empty-collection") : element);
    }
    return TypeShape.scalar(mapping.isBoolean() ? "boolean" : mapping.isIntegralNumber() ? "integer" : mapping.isNumber() ? "number" : "null");
  }

  private static TypeShape actionContext() {
    var fields = new java.util.LinkedHashMap<String, TypeShape>();
    for (var name : List.of("modelFullName", "actionName", "authorityName", "executionId", "parentExecutionId")) fields.put(name, TypeShape.scalar("string"));
    fields.put("actor", TypeShape.unknown("security-principal"));
    fields.put("readOnly", TypeShape.scalar("boolean"));
    fields.put("objectStates", TypeShape.unknown("authorized-object-roles"));
    fields.put("request", TypeShape.unknown("optional-http-request"));
    return TypeShape.object(fields, "map");
  }

  private static JsonNode indexedObject(JsonNode array, String pointer, int segmentIndex) {
    var parts = pointer.split("/");
    if (parts.length <= segmentIndex + 1 || !parts[segmentIndex + 1].matches("\\d{1,6}")) {
      throw new BusinessException("业务模型成员位置必须包含有效数组下标");
    }
    var index = Integer.parseInt(parts[segmentIndex + 1]);
    if (!array.isArray() || index >= array.size()) throw new BusinessException("业务模型成员不存在");
    return array.get(index);
  }

  private static TypeShape shape(JsonNode source, String... names) {
    for (var name : names) {
      var value = source == null ? null : source.get(name);
      if (value != null && value.isObject() && (value.has("type") || value.has("properties") || value.has("$schema"))) {
        return TypeShape.mapSchema(value);
      }
    }
    return TypeShape.unknown("missing-business-schema");
  }

  private static void add(List<ScriptIntelligenceGlobal> list, String name, TypeShape shape, String source) {
    var global = new ScriptIntelligenceGlobal(); global.setName(name); global.setKind("variable"); global.setShape(shape);
    global.setTypeDisplayName(shape.type()); global.setSourceTag(source); list.add(global);
  }
}
