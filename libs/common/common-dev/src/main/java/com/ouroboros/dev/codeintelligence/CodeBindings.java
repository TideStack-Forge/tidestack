package com.ouroboros.dev.codeintelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;

/** Common DTO construction for domain providers; stores descriptions, never runtime values. */
public final class CodeBindings {
  private CodeBindings() { }
  public static ScriptIntelligenceGlobal binding(String name, TypeShape shape, String source) {
    var binding = new ScriptIntelligenceGlobal();
    binding.setName(name); binding.setKind("variable"); binding.setShape(shape);
    binding.setTypeDisplayName(shape.type()); binding.setSourceTag(source);
    return binding;
  }
  public static TypeShape literalShape(JsonNode value) { return literalShape(value, 0); }
  private static TypeShape literalShape(JsonNode value, int depth) {
    if (depth > 32) return TypeShape.unknown("literal-depth-budget");
    if (value.isObject()) {
      var fields = new java.util.LinkedHashMap<String, TypeShape>();
      value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), literalShape(entry.getValue(), depth + 1)));
      return TypeShape.object(fields, "map");
    }
    if (value.isArray()) {
      TypeShape element = null;
      for (var item : value) { var shape = literalShape(item, depth + 1); element = element == null ? shape : element.merge(shape); }
      return TypeShape.array(element == null ? TypeShape.unknown("empty-collection") : element);
    }
    return value.isMissingNode() ? TypeShape.unknown("absent-value") : TypeShape.scalar(value.isTextual() ? "string"
        : value.isBoolean() ? "boolean" : value.isIntegralNumber() ? "integer" : value.isNumber() ? "number" : "null");
  }
}
