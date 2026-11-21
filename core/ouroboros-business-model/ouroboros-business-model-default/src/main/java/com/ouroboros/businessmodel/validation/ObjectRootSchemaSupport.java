package com.ouroboros.businessmodel.validation;

import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SpecificationVersion;
import com.ouroboros.util.Json;

/** Root-shape analysis; constraints on individual object values remain the Schema validator's responsibility. */
final class ObjectRootSchemaSupport {
  private enum Coverage { NONE, PARTIAL, ALL }

  private ObjectRootSchemaSupport() {
  }

  static boolean allowsObject(Map<String, Object> definition) {
    JsonNode root = Json.tryToJsonString(definition).flatMap(json -> Json.tryToBean(JsonNode.class, json)).get();
    var dialect = SpecificationVersion.fromDialectId(root.path("$schema").asText()).orElse(SpecificationVersion.DRAFT_7);
    return coverage(root, root, dialect, Collections.newSetFromMap(new IdentityHashMap<>())) != Coverage.NONE;
  }

  private static Coverage coverage(JsonNode schema, JsonNode root, SpecificationVersion dialect, Set<JsonNode> visiting) {
    if (schema.isBoolean()) {
      return schema.booleanValue() ? Coverage.ALL : Coverage.NONE;
    }
    if (!schema.isObject() || !visiting.add(schema)) {
      return Coverage.PARTIAL;
    }
    try {
      if (schema.has("$ref") && dialect.getOrder() < SpecificationVersion.DRAFT_2019_09.getOrder()) {
        return referenceCoverage(schema.get("$ref"), root, dialect, visiting);
      }
      String idKeyword = dialect == SpecificationVersion.DRAFT_4 ? "id" : "$id";
      if (schema.has(idKeyword) && !schema.path(idKeyword).asText().startsWith("#")) {
        root = schema;
        dialect = SpecificationVersion.fromDialectId(root.path("$schema").asText()).orElse(dialect);
      }
      Coverage result = Coverage.ALL;
      var fields = schema.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        JsonNode value = field.getValue();
        Coverage constraint = switch (field.getKey()) {
          case "type" -> containsObjectType(value) ? Coverage.ALL : Coverage.NONE;
          case "const" -> dialect.getOrder() < SpecificationVersion.DRAFT_6.getOrder() ? Coverage.ALL
              : value.isObject() ? Coverage.PARTIAL : Coverage.NONE;
          case "enum" -> containsObjectValue(value) ? Coverage.PARTIAL : Coverage.NONE;
          case "$ref" -> referenceCoverage(value, root, dialect, visiting);
          case "$dynamicRef", "$recursiveRef" -> Coverage.PARTIAL;
          case "not" -> negate(coverage(value, root, dialect, visiting));
          case "allOf", "anyOf", "oneOf" -> combinationCoverage(field.getKey(), value, root, dialect, visiting);
          case "if" -> dialect.getOrder() < SpecificationVersion.DRAFT_7.getOrder() ? Coverage.ALL
              : conditionalCoverage(schema, root, dialect, visiting);
          case "properties", "patternProperties", "required", "dependencies", "dependentRequired", "dependentSchemas"
              -> value.isEmpty() ? Coverage.ALL : Coverage.PARTIAL;
          case "additionalProperties", "unevaluatedProperties", "propertyNames"
              -> value.isBoolean() && value.booleanValue() || value.isObject() && value.isEmpty() ? Coverage.ALL : Coverage.PARTIAL;
          case "minProperties" -> value.asInt() <= 0 ? Coverage.ALL : Coverage.PARTIAL;
          case "maxProperties" -> Coverage.PARTIAL;
          // Annotations, definitions and assertions for other JSON types do not restrict object roots.
          default -> Coverage.ALL;
        };
        result = intersect(result, constraint);
      }
      return result;
    } finally {
      visiting.remove(schema);
    }
  }

  private static boolean containsObjectType(JsonNode type) {
    if (type.isTextual()) {
      return "object".equals(type.textValue());
    }
    for (JsonNode member : type) {
      if ("object".equals(member.textValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsObjectValue(JsonNode values) {
    for (JsonNode value : values) {
      if (value.isObject()) {
        return true;
      }
    }
    return false;
  }

  private static Coverage referenceCoverage(JsonNode reference, JsonNode root, SpecificationVersion dialect, Set<JsonNode> visiting) {
    String value = reference.asText();
    if ("#".equals(value)) {
      return coverage(root, root, dialect, visiting);
    }
    String fragment = value.startsWith("#") ? URI.create(value).getFragment() : null;
    return fragment != null && fragment.startsWith("/") ? coverage(root.at(fragment), root, dialect, visiting) : Coverage.PARTIAL;
  }

  private static Coverage combinationCoverage(String keyword, JsonNode branches, JsonNode root,
                                              SpecificationVersion dialect, Set<JsonNode> visiting) {
    Coverage result = "allOf".equals(keyword) ? Coverage.ALL : Coverage.NONE;
    int allCount = 0;
    boolean partial = false;
    for (JsonNode branch : branches) {
      Coverage current = coverage(branch, root, dialect, visiting);
      result = "allOf".equals(keyword) ? intersect(result, current) : union(result, current);
      allCount += current == Coverage.ALL ? 1 : 0;
      partial |= current == Coverage.PARTIAL;
    }
    if ("oneOf".equals(keyword)) {
      return allCount > 1 ? Coverage.NONE : partial ? Coverage.PARTIAL : result;
    }
    return result;
  }

  private static Coverage conditionalCoverage(JsonNode schema, JsonNode root, SpecificationVersion dialect, Set<JsonNode> visiting) {
    Coverage condition = coverage(schema.get("if"), root, dialect, visiting);
    Coverage thenResult = schema.has("then") ? coverage(schema.get("then"), root, dialect, visiting) : Coverage.ALL;
    Coverage elseResult = schema.has("else") ? coverage(schema.get("else"), root, dialect, visiting) : Coverage.ALL;
    return switch (condition) {
      case ALL -> thenResult;
      case NONE -> elseResult;
      case PARTIAL -> thenResult == elseResult ? thenResult : Coverage.PARTIAL;
    };
  }

  private static Coverage intersect(Coverage left, Coverage right) {
    return left == Coverage.NONE || right == Coverage.NONE ? Coverage.NONE
        : left == Coverage.ALL && right == Coverage.ALL ? Coverage.ALL : Coverage.PARTIAL;
  }

  private static Coverage union(Coverage left, Coverage right) {
    return left == Coverage.ALL || right == Coverage.ALL ? Coverage.ALL
        : left == Coverage.NONE && right == Coverage.NONE ? Coverage.NONE : Coverage.PARTIAL;
  }

  private static Coverage negate(Coverage value) {
    return switch (value) {
      case NONE -> Coverage.ALL;
      case ALL -> Coverage.NONE;
      case PARTIAL -> Coverage.PARTIAL;
    };
  }
}
