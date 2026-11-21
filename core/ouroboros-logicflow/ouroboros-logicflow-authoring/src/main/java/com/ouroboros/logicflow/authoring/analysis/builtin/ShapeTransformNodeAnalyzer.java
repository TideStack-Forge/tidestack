package com.ouroboros.logicflow.authoring.analysis.builtin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;

/** Conservative signatures for structural builtins; values and methods are never executed. */
public final class ShapeTransformNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("pick", "omit", "take", "merge", "return", "validate", "mutex"); }

  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var type = String.valueOf(input.node().getType()).toLowerCase(Locale.ROOT);
    var scopes = new LinkedHashMap<String, CodeScope>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    TypeShape output;
    switch (type) {
      case "pick" -> output = pick(input, diagnostics);
      case "omit" -> output = omit(input);
      case "take" -> output = take(input);
      case "merge" -> output = merge(input, diagnostics);
      case "validate" -> {
        var declared = input.node().getExtraProp("schema").orElse(null);
        var schema = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(declared);
        output = schema != null && schema.isObject() && !schema.isEmpty()
            ? TypeShape.mapSchema(schema).annotated("x-evidence", "validated") : input.firstInput();
      }
      default -> output = input.firstInput();
    }
    return new NodeAnalysis(Map.of(0, PortShape.always(output)), scopes, diagnostics);
  }

  private TypeShape pick(NodeAnalysisInput input, List<AnalysisDiagnostic> diagnostics) {
    var source = input.firstInput();
    var key = input.node().getExtraProp(String.class, "key").orElse("");
    if (!key.isBlank()) {
      var each = input.node().getExtraProp(Boolean.class, "pickEachItem").orElse(false);
      if ("array".equals(source.type()) && !each) return TypeShape.unknown("pick-requires-map");
      var item = each && "array".equals(source.type()) ? source.element() : source;
      var selected = path(item, key);
      if (!selected.isUnknown()) {
        if (each) return TypeShape.array(selected);
        return selected;
      }
    }
    diagnostics.add(new AnalysisDiagnostic(input.node().getName(), "/key", "unknown-output", "Pick key or source shape is unknown"));
    return TypeShape.unknown("pick-result");
  }

  private TypeShape path(TypeShape source, String key) {
    var current = source;
    for (var segment : key.split("\\.")) {
      if (segment.isBlank()) return TypeShape.unknown("invalid-pick-path");
      current = current.property(segment);
      if (current.isUnknown()) return current;
    }
    return current;
  }

  private TypeShape omit(NodeAnalysisInput input) {
    var source = input.firstInput();
    if (!"map".equals(source.representation())) return source;
    var omitted = new LinkedHashMap<>(source.properties());
    input.node().getExtraProp("omitFields").ifPresent(value -> {
      if (value instanceof List<?> keys) keys.forEach(key -> omitted.remove(String.valueOf(key)));
    });
    return TypeShape.object(omitted, "map");
  }

  private TypeShape take(NodeAnalysisInput input) {
    var source = input.firstInput();
    return "array".equals(source.type()) ? source.element() : source;
  }

  private TypeShape merge(NodeAnalysisInput input, List<AnalysisDiagnostic> diagnostics) {
    var shapes = input.inputs().stream().map(PortShape::shape).toList();
    if (shapes.isEmpty()) return TypeShape.unknown("merge-no-input");
    if (shapes.stream().anyMatch(shape -> !"map".equals(shape.representation()))) {
      diagnostics.add(new AnalysisDiagnostic(input.node().getName(), "", "invalid-merge-input", "Merge inputs must be known map shapes"));
      return TypeShape.unknown("merge-input-not-map");
    }
    var deep = input.node().getExtraProp(Boolean.class, "deepMerge").orElse(false);
    var fields = new LinkedHashMap<String, TypeShape>();
    for (var shape : shapes) shape.properties().forEach((name, value) -> fields.merge(name,
        value, (left, right) -> deep ? overlay(left, right, true) : right));
    return TypeShape.object(fields, "map");
  }

  private TypeShape overlay(TypeShape left, TypeShape right, boolean deep) {
    if (deep && "map".equals(left.representation()) && "map".equals(right.representation())) {
      var fields = new LinkedHashMap<>(left.properties());
      right.properties().forEach((name, value) -> fields.merge(name, value, (previous, replacement) -> overlay(previous, replacement, true)));
      return TypeShape.object(fields, "map");
    }
    return right;
  }
}
