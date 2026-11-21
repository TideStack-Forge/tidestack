package com.ouroboros.logicflow.authoring.analysis.builtin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;

public final class MappingNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("mapping"); }

  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var each = input.node().getExtraProp(Boolean.class, "mappingEachItem").orElse(false);
    var bindings = each ? NodeScopes.item(input, true) : NodeScopes.ordinary(input);
    var scopes = new LinkedHashMap<String, CodeScope>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var config = input.node().getExtraProp("mapping").orElse(Map.of());
    var strategy = input.node().getExtraProp(String.class, "conflictStrategy").orElse("OVERWRITE").toUpperCase(java.util.Locale.ROOT);
    scopes.put("/mapping", new CodeScope("spel", bindings, TypeShape.unknown("mapping-result")));
    var output = config instanceof Map<?, ?> map
        ? mapping(map, "/mapping", bindings, expressions, scopes, diagnostics, input.node().getName(), strategy, true, 0)
        : TypeShape.unknown("invalid-mapping");
    if (each) output = TypeShape.array(output);
    return new NodeAnalysis(Map.of(0, PortShape.always(output)), scopes, diagnostics);
  }

  private TypeShape mapping(Map<?, ?> config, String pointer, Map<String, TypeShape> bindings, ExpressionTypeQuery expressions,
      Map<String, CodeScope> scopes, List<AnalysisDiagnostic> diagnostics, String node, String strategy, boolean hierarchical, int depth) {
    if (depth > 32) return TypeShape.unknown("mapping-depth-budget");
    var fields = new LinkedHashMap<String, TypeShape>();
    boolean open = false;
    for (var entry : config.entrySet()) {
      var key = String.valueOf(entry.getKey());
      var path = pointer + "/" + NodeScopes.pointer(key);
      var rule = entry.getValue();
      TypeShape value;
      if (rule instanceof String expression) {
        scopes.put(path, new CodeScope("spel", bindings, TypeShape.unknown("mapping-result")));
        value = expressions.infer(expression, bindings);
      } else if (rule instanceof Map<?, ?> nested) value = mapping(nested, path, bindings, expressions, scopes, diagnostics, node, strategy, false, depth + 1);
      else value = TypeShape.literal(rule);
      if ("&".equals(key) || key.endsWith(".&")) {
        if (!(rule instanceof String)) continue;
        if (!"map".equals(value.representation())) {
          open = true;
          diagnostics.add(new AnalysisDiagnostic(node, path, "unknown-expansion", "Expansion requires a known Map shape"));
          continue;
        }
        var prefix = "&".equals(key) ? "" : key.substring(0, key.length() - 1);
        for (var field : value.properties().entrySet()) put(fields, prefix + field.getKey(), field.getValue(), strategy, diagnostics, node, path);
        open |= value.schema().path("additionalProperties").asBoolean(true);
      } else put(fields, key, value, strategy, diagnostics, node, path);
    }
    var result = TypeShape.object(fields, "map");
    if (hierarchical) result = TypeShape.hierarchicalObject(fields);
    if (open) {
      var schema = (ObjectNode) result.schema(); schema.put("additionalProperties", true); result = new TypeShape(schema);
    }
    return result;
  }

  private static void put(Map<String, TypeShape> fields, String key, TypeShape value, String strategy,
      List<AnalysisDiagnostic> diagnostics, String node, String pointer) {
    var previous = fields.get(key);
    if (previous == null || "null".equals(previous.type())) { fields.put(key, value); return; }
    switch (strategy) {
      case "SKIP" -> { }
      case "ERROR" -> {
        diagnostics.add(new AnalysisDiagnostic(node, pointer, "mapping-conflict", "Conflicting mapping key: " + key));
        fields.put(key, previous.merge(value));
      }
      case "MERGE" -> {
        if ("map".equals(previous.representation()) && "map".equals(value.representation())) {
          var merged = new LinkedHashMap<>(previous.properties()); merged.putAll(value.properties()); fields.put(key, TypeShape.object(merged, "map"));
        } else fields.put(key, value);
      }
      default -> fields.put(key, value);
    }
  }

}
