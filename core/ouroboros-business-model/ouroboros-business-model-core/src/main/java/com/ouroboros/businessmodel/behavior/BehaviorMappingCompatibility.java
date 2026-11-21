package com.ouroboros.businessmodel.behavior;

import java.util.List;
import java.util.Map;

/** Converts the previous field-specific syntax once, before publishing EL plans. */
final class BehaviorMappingCompatibility {
  enum Field { FLOW_INPUT, FLOW_OUTPUT, SUBSCRIPTION_INPUT }

  private BehaviorMappingCompatibility() { }

  record Conversion(String expression, List<String> collectionGuards) { }

  static Conversion expression(String value, Field field, boolean current, Map<String, Object> inputSchema) {
    List<String> collectionGuards = new java.util.ArrayList<>();
    if (current || field == Field.SUBSCRIPTION_INPUT) {
      if (value.startsWith("$.")) {
        throw new IllegalArgumentException("2.0 mappings use the platform EL syntax");
      }
      return new Conversion(value, List.of());
    }
    if (value.startsWith("$.")) {
      String[] segments = value.substring(2).split("\\.", -1);
      if (java.util.Arrays.stream(segments).anyMatch(segment -> !segment.matches("[A-Za-z0-9_$-]+"))) {
        throw new IllegalArgumentException("Invalid legacy mapping selector");
      }
      if (field == Field.FLOW_OUTPUT) {
        String expression = "${ " + flowPath("result", segments, 0, Map.of(), collectionGuards, "result") + " }";
        return new Conversion(expression, List.copyOf(collectionGuards));
      }
      Map<String, Object> roots = Map.of("type", "object", "properties", Map.of(
          "input", inputSchema, "context", Map.of("type", "object")));
      String expression = "${ " + flowPath("#root", segments, 0, roots, collectionGuards, "#root") + " }";
      return new Conversion(expression, List.copyOf(collectionGuards));
    }
    return new Conversion(value.replace("${", "$${"), List.of());
  }

  private static String flowPath(String base, String[] segments, int offset, Map<?, ?> schema, List<String> collectionGuards, String safeBase) {
    if (offset == segments.length) { return base; }
    Object type = schema.get("type");
    boolean array = "array".equals(type) || type instanceof List<?> types && types.contains("array");
    if (array) {
      Map<?, ?> item = schema.get("items") instanceof Map<?, ?> definition ? definition : Map.of();
      if (item.isEmpty() && offset < segments.length - 1) {
        throw new IllegalArgumentException("Legacy collection traversal requires an item schema");
      }
      return base + "?.![" + flowPath("#this", segments, offset, item, collectionGuards, "#this") + "]";
    }
    if (schema.isEmpty()) {
      if (base.contains("#this")) {
        throw new IllegalArgumentException("Legacy nested collection traversal requires a property schema");
      }
      collectionGuards.add("${ " + safeBase + " instanceof T(java.util.List) }");
    }
    Map<?, ?> fields = schema.get("properties") instanceof Map<?, ?> properties ? properties : Map.of();
    Map<?, ?> nested = fields.get(segments[offset]) instanceof Map<?, ?> definition ? definition : Map.of();
    String safeNext = "(" + safeBase + " instanceof T(java.util.Map) ? " + safeBase + "['" + segments[offset] + "'] : null)";
    String next = flowPath(base + "['" + segments[offset] + "']", segments, offset + 1, nested, collectionGuards, safeNext);
    return "(" + base + " instanceof T(java.util.Map) ? " + next + " : null)";
  }
}
