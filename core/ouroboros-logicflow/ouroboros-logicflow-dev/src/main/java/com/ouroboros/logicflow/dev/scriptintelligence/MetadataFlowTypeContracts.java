package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.logicflow.authoring.analysis.FlowTypeContracts;
import com.ouroboros.util.Json;

/** Uses one target-app snapshot plus unsaved projected metadata; no runtime resource is instantiated. */
public final class MetadataFlowTypeContracts implements FlowTypeContracts {
  private final java.util.function.Consumer<com.ouroboros.dev.codeintelligence.ScriptAnalysisQuery> queries;
  private final ApplicationMetadataSnapshot application;
  private final Map<String, TypeShape> scriptOutputs;
  private final Map<String, Map<String, JsonNode>> drafts;
  private final java.util.Set<String> callPath;
  private final java.util.concurrent.atomic.AtomicInteger remainingNodes;
  public MetadataFlowTypeContracts(ApplicationMetadataSnapshot application) { this(application, Map.of()); }
  public MetadataFlowTypeContracts(ApplicationMetadataSnapshot application, Map<String, Map<String, JsonNode>> drafts) {
    this(application, drafts, Map.of(), query -> {}, java.util.Set.of(), new java.util.concurrent.atomic.AtomicInteger(4000));
  }
  public MetadataFlowTypeContracts(ApplicationMetadataSnapshot application, Map<String, Map<String, JsonNode>> drafts, Map<String, TypeShape> outputs) {
    this(application, drafts, outputs, query -> {}, java.util.Set.of(), new java.util.concurrent.atomic.AtomicInteger(4000));
  }
  public MetadataFlowTypeContracts(ApplicationMetadataSnapshot application, Map<String, Map<String, JsonNode>> drafts, Map<String, TypeShape> outputs,
      java.util.function.Consumer<com.ouroboros.dev.codeintelligence.ScriptAnalysisQuery> queries) {
    this(application, drafts, outputs, queries, java.util.Set.of(), new java.util.concurrent.atomic.AtomicInteger(4000));
  }
  private MetadataFlowTypeContracts(ApplicationMetadataSnapshot application, Map<String, Map<String, JsonNode>> drafts,
      Map<String, TypeShape> outputs, java.util.function.Consumer<com.ouroboros.dev.codeintelligence.ScriptAnalysisQuery> queries,
      java.util.Set<String> callPath, java.util.concurrent.atomic.AtomicInteger remainingNodes) {
    this.queries = queries;
    this.application = application; this.scriptOutputs = Map.copyOf(outputs);
    this.callPath = java.util.Set.copyOf(callPath);
    this.remainingNodes = remainingNodes;
    var snapshot = new LinkedHashMap<String, Map<String, JsonNode>>();
    drafts.forEach((type, resources) -> {
      var copy = new LinkedHashMap<String, JsonNode>();
      resources.forEach((name, value) -> copy.put(name, value.deepCopy()));
      snapshot.put(type, Map.copyOf(copy));
    });
    this.drafts = Map.copyOf(snapshot);
  }
  public TypeShape scriptOutput(String language, String source, Map<String, TypeShape> bindings) {
    var query = new com.ouroboros.dev.codeintelligence.ScriptAnalysisQuery(language, source, bindings);
    queries.accept(query);
    return scriptOutputs.getOrDefault(query.key(), TypeShape.unknown("script-output-not-analyzed"));
  }
  public java.util.Optional<JsonNode> metadata(String type, String name) {
    var declared = drafts.getOrDefault(type, Map.of()).get(name);
    if (declared == null) {
      var matches = application.findModuleMetadata(type, name);
      if (matches.size() != 1 || matches.getFirst().getDetails() == null) return java.util.Optional.empty();
      declared = Json.toBean(JsonNode.class, Json.toJsonString(matches.getFirst().getDetails()));
    }
    return java.util.Optional.of(declared.deepCopy());
  }
  public TypeShape find(String type, String name, String role) {
    var declared = metadata(type, name).orElse(null);
    if (declared == null) return TypeShape.unknown("missing-or-ambiguous-contract:" + type + ":" + name);
    var schema = declared.path(role + "Schema");
    if (schema.isObject() && !schema.isEmpty()) return ("instance".equals(role) ? new TypeShape(schema) : TypeShape.mapSchema(schema)).annotated("x-evidence", "declared");
    if ("data-model".equals(type) && "record".equals(role)) {
      var fields = new LinkedHashMap<String, TypeShape>();
      for (var field : declared.path("fields")) {
        if (field.hasNonNull("name")) fields.put(field.path("name").asText(), TypeShape.unknown("data-field-type:" + field.path("type").asText()));
      }
      return TypeShape.object(fields, "map").annotated("x-evidence", "declared-field-names");
    }
    return TypeShape.unknown("missing-schema:" + type + ":" + name + ":" + role);
  }

  public TypeShape callFlow(String name, java.util.List<TypeShape> inputs, Map<String, TypeShape> globals,
      com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery expressions) {
    var declared = find("logic-flow", name, "output");
    if (!declared.isUnknown()) return declared;
    var key = name.toLowerCase(java.util.Locale.ROOT);
    if (callPath.contains(key)) return TypeShape.unknown("recursive-flow-reference:" + name);
    if (callPath.size() >= 12) return TypeShape.unknown("flow-call-depth-budget");
    var raw = metadata("logic-flow", name);
    if (raw.isEmpty()) return declared;
    var flow = Json.toBean(com.ouroboros.logicflow.model.FlowModelMeta.class, raw.get().toString());
    if (remainingNodes.addAndGet(-flow.getNodes().size()) < 0) return TypeShape.unknown("flow-call-node-budget");
    var path = new java.util.HashSet<>(callPath); path.add(key);
    var nested = new MetadataFlowTypeContracts(application, drafts, scriptOutputs, queries, path, remainingNodes);
    var analysis = new com.ouroboros.logicflow.authoring.analysis.LogicFlowDataflowAnalyzer().analyze(flow, inputs, globals, expressions, nested);
    TypeShape result = null;
    for (var terminalName : analysis.successfulTerminals()) {
      var analyzed = analysis.nodes().get(terminalName);
      if (analyzed == null || !analyzed.outputs().containsKey(0)) continue;
      var shape = analyzed.outputs().get(0).shape();
      result = result == null ? shape : result.merge(shape);
    }
    return result == null ? TypeShape.unknown("flow-without-successful-output:" + name) : result;
  }
}
