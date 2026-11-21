package com.ouroboros.dev.projectgeneration.parameter;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;

public class ProjectTemplateParameterValidator {

  private static final BigInteger MAX_SAFE_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);
  private static final BigInteger MIN_SAFE_INTEGER = MAX_SAFE_INTEGER.negate();
  private static final Set<String> ROOT_KEYS = Set.of(
      "$schema", "type", "properties", "required", "additionalProperties", "x-ouroboros-project-root");
  private static final Set<String> PROPERTY_KEYS = Set.of(
      "type",
      "title",
      "description",
      "default",
      "enum",
      "minLength",
      "maxLength",
      "minimum",
      "maximum",
      "x-ouroboros-format"
  );
  private static final Set<String> FORMATS = Set.of(
      "npm-package-name",
      "js-identifier",
      "java-package",
      "java-class-name",
      "maven-group-id",
      "maven-artifact-id",
      "path-segment",
      "relative-path"
  );

  public Map<String, Object> normalize(JsonNode schema, JsonNode uiHints, Map<String, Object> parameters) {
    validateContract(schema, uiHints);
    Map<String, Object> supplied = parameters == null ? Map.of() : parameters;
    Map<String, JsonNode> properties = properties(schema);
    Set<String> required = required(schema);
    for (String name : supplied.keySet()) {
      if (!properties.containsKey(name)) {
        throw new IllegalArgumentException("Unknown project template parameter: " + name);
      }
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
      String name = property.getKey();
      JsonNode definition = property.getValue();
      Object value = supplied.containsKey(name) ? supplied.get(name) : defaultValue(definition);
      if (value == null) {
        if (required.contains(name)) {
          throw new IllegalArgumentException("Missing required project template parameter: " + name);
        }
        continue;
      }
      normalized.put(name, normalizeValue(name, definition, value));
    }
    return normalized;
  }

  public void validateContract(JsonNode schema, JsonNode uiHints) {
    requireRoot(schema);
    Map<String, JsonNode> properties = properties(schema);
    Set<String> required = required(schema);
    for (String name : required) {
      if (!properties.containsKey(name)) {
        throw new IllegalArgumentException("Required parameter is not declared: " + name);
      }
    }
    for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
      validateProperty(property.getKey(), property.getValue());
    }
    validateUiHints(properties, required, uiHints == null || uiHints.isMissingNode() ? null : uiHints);
  }

  private static void requireRoot(JsonNode schema) {
    if (schema == null || !schema.isObject()) {
      throw new IllegalArgumentException("Parameter contract root must be an object");
    }
    for (String key : iterable(schema.fieldNames())) {
      if (!ROOT_KEYS.contains(key)) {
        throw new IllegalArgumentException("Unsupported root schema keyword: " + key);
      }
    }
    if (!"object".equals(text(schema.path("type")))) {
      throw new IllegalArgumentException("Parameter contract root type must be object");
    }
    if (!schema.path("properties").isObject()) {
      throw new IllegalArgumentException("Parameter contract properties must be an object");
    }
    if (!schema.path("additionalProperties").isBoolean()
        || schema.path("additionalProperties").booleanValue()) {
      throw new IllegalArgumentException("Parameter contract must set additionalProperties=false");
    }
    String projectRoot = schema.path("x-ouroboros-project-root").asText(null);
    if (projectRoot == null || projectRoot.isBlank() || !schema.path("properties").has(projectRoot)) {
      throw new IllegalArgumentException("Parameter contract must declare a project root property");
    }
  }

  private static Map<String, JsonNode> properties(JsonNode schema) {
    Map<String, JsonNode> properties = new LinkedHashMap<>();
    schema.path("properties").fields().forEachRemaining(entry -> properties.put(entry.getKey(), entry.getValue()));
    return properties;
  }

  private static Set<String> required(JsonNode schema) {
    JsonNode required = schema.path("required");
    if (required.isMissingNode()) {
      return Set.of();
    }
    if (!required.isArray()) {
      throw new IllegalArgumentException("Parameter contract required must be an array");
    }
    Set<String> result = new HashSet<>();
    for (JsonNode item : required) {
      if (!item.isTextual()) {
        throw new IllegalArgumentException("Parameter contract required entries must be text");
      }
      result.add(item.textValue());
    }
    return result;
  }

  private static void validateProperty(String name, JsonNode property) {
    if (!property.isObject()) {
      throw new IllegalArgumentException("Parameter property must be an object: " + name);
    }
    for (String key : iterable(property.fieldNames())) {
      if (!PROPERTY_KEYS.contains(key)) {
        throw new IllegalArgumentException("Unsupported parameter schema keyword: " + key);
      }
    }
    String type = text(property.path("type"));
    if (!Set.of("string", "boolean", "integer").contains(type)) {
      throw new IllegalArgumentException("Unsupported parameter type: " + name);
    }
    if ("string".equals(type)) {
      JsonNode maxLength = property.path("maxLength");
      if (!maxLength.isIntegralNumber() || maxLength.asInt() < 1 || maxLength.asInt() > 1000) {
        throw new IllegalArgumentException("String parameter must declare maxLength <= 1000: " + name);
      }
    }
    JsonNode format = property.path("x-ouroboros-format");
    if (!format.isMissingNode() && (!format.isTextual() || !FORMATS.contains(format.textValue()))) {
      throw new IllegalArgumentException("Unsupported semantic format: " + name);
    }
    JsonNode defaultValue = property.path("default");
    if (!defaultValue.isMissingNode()) {
      normalizeJsonValue(name, property, defaultValue);
    }
  }

  private static void validateUiHints(Map<String, JsonNode> properties, Set<String> required, JsonNode uiHints) {
    if (uiHints == null || uiHints.isMissingNode()) {
      return;
    }
    if (!uiHints.isObject()) {
      throw new IllegalArgumentException("uiHints must be an object");
    }
    Map<String, String> edges = new HashMap<>();
    for (String name : iterable(uiHints.fieldNames())) {
      if (!properties.containsKey(name)) {
        throw new IllegalArgumentException("uiHints references unknown parameter: " + name);
      }
      JsonNode hint = uiHints.path(name);
      JsonNode visibleWhen = hint.path("visibleWhen");
      if (visibleWhen.isMissingNode()) {
        continue;
      }
      if (required.contains(name)) {
        throw new IllegalArgumentException("visibleWhen parameter must not be required: " + name);
      }
      String parameter = text(visibleWhen.path("parameter"));
      if (!properties.containsKey(parameter) || parameter.equals(name)) {
        throw new IllegalArgumentException("visibleWhen references invalid parameter: " + name);
      }
      requireVisibleWhenValueMatchesType(properties.get(parameter), visibleWhen);
      edges.put(name, parameter);
    }
    requireAcyclic(edges);
  }

  private static void requireVisibleWhenValueMatchesType(JsonNode controllingProperty, JsonNode visibleWhen) {
    String type = text(controllingProperty.path("type"));
    JsonNode equals = visibleWhen.path("equals");
    JsonNode in = visibleWhen.path("in");
    if (!equals.isMissingNode() == !in.isMissingNode()) {
      throw new IllegalArgumentException("visibleWhen must use exactly one of equals/in");
    }
    if (!equals.isMissingNode()) {
      normalizeJsonValue("visibleWhen", controllingProperty, equals);
      return;
    }
    if (!in.isArray() || in.isEmpty()) {
      throw new IllegalArgumentException("visibleWhen in must be a non-empty array");
    }
    for (JsonNode item : in) {
      normalizeJsonValue(type, controllingProperty, item);
    }
  }

  private static void requireAcyclic(Map<String, String> edges) {
    for (String start : edges.keySet()) {
      Set<String> seen = new HashSet<>();
      String current = start;
      while (edges.containsKey(current)) {
        current = edges.get(current);
        if (!seen.add(current)) {
          throw new IllegalArgumentException("visibleWhen graph must be acyclic");
        }
      }
    }
  }

  private static Object defaultValue(JsonNode definition) {
    JsonNode defaultValue = definition.path("default");
    if (defaultValue.isMissingNode()) {
      return null;
    }
    return toJavaValue(defaultValue);
  }

  private static Object normalizeValue(String name, JsonNode definition, Object value) {
    return normalizeJsonValue(name, definition, new com.fasterxml.jackson.databind.node.POJONode(value));
  }

  private static Object normalizeJsonValue(String name, JsonNode definition, JsonNode value) {
    String type = text(definition.path("type"));
    Object normalized = switch (type) {
      case "string" -> normalizeString(name, definition, value);
      case "boolean" -> {
        Object raw = toJavaValue(value);
        if (!(raw instanceof Boolean booleanValue)) {
          throw new IllegalArgumentException("Parameter must be boolean: " + name);
        }
        yield booleanValue;
      }
      case "integer" -> normalizeInteger(name, definition, value);
      default -> throw new IllegalArgumentException("Unsupported parameter type: " + name);
    };
    requireEnumMatch(name, definition, normalized);
    return normalized;
  }

  private static String normalizeString(String name, JsonNode definition, JsonNode value) {
    if (!value.isTextual() && !value.isPojo()) {
      throw new IllegalArgumentException("Parameter must be string: " + name);
    }
    Object raw = toJavaValue(value);
    if (!(raw instanceof String text)) {
      throw new IllegalArgumentException("Parameter must be string: " + name);
    }
    int maxLength = definition.path("maxLength").asInt();
    if (text.codePointCount(0, text.length()) > maxLength) {
      throw new IllegalArgumentException("Parameter string is too long: " + name);
    }
    ProjectTemplateIdentifierContracts.requireNoControlCharacters(text, name);
    requireSemanticFormat(name, text, definition.path("x-ouroboros-format").asText(null));
    return text;
  }

  private static Integer normalizeInteger(String name, JsonNode definition, JsonNode value) {
    Object raw = toJavaValue(value);
    Integer integer;
    if (raw instanceof Integer intValue) {
      integer = intValue;
    } else {
      if (raw instanceof Long longValue && longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
        integer = longValue.intValue();
      } else {
        throw new IllegalArgumentException("Parameter must be integer: " + name);
      }
    }
    if (definition.has("minimum") && integer < definition.path("minimum").asInt()) {
      throw new IllegalArgumentException("Parameter is below minimum: " + name);
    }
    if (definition.has("maximum") && integer > definition.path("maximum").asInt()) {
      throw new IllegalArgumentException("Parameter is above maximum: " + name);
    }
    return integer;
  }

  private static void requireEnumMatch(String name, JsonNode definition, Object normalized) {
    JsonNode enumValues = definition.path("enum");
    if (enumValues.isMissingNode()) {
      return;
    }
    if (!enumValues.isArray() || enumValues.isEmpty()) {
      throw new IllegalArgumentException("Parameter enum must be a non-empty array: " + name);
    }
    for (JsonNode enumValue : enumValues) {
      if (normalized.equals(toJavaValue(enumValue))) {
        return;
      }
    }
    throw new IllegalArgumentException("Parameter value is not in enum: " + name);
  }

  private static void requireSemanticFormat(String name, String value, String format) {
    if (format == null) {
      return;
    }
    switch (format) {
      case "path-segment" -> ProjectTemplateIdentifierContracts.requirePathSegment(value, name);
      case "npm-package-name" -> requireMatches(name, value, "^(@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*$");
      case "js-identifier" -> requireMatches(name, value, "^[A-Za-z_$][A-Za-z0-9_$]*$");
      case "java-package", "maven-group-id" -> requireMatches(name, value, "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");
      case "java-class-name" -> requireMatches(name, value, "^[A-Z][A-Za-z0-9]*$");
      case "maven-artifact-id" -> requireMatches(name, value, "^[a-z][a-z0-9-]*$");
      case "relative-path" -> ProjectTemplateIdentifierContracts.requireRelativePathParameter(value, name);
      default -> throw new IllegalArgumentException("Unsupported semantic format: " + format);
    }
  }

  private static void requireMatches(String name, String value, String pattern) {
    if (!value.matches(pattern)) {
      throw new IllegalArgumentException("Parameter semantic format is invalid: " + name);
    }
  }

  private static Object toJavaValue(JsonNode value) {
    if (value.isPojo()) {
      return ((com.fasterxml.jackson.databind.node.POJONode) value).getPojo();
    }
    if (value.isTextual()) {
      return value.textValue();
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isIntegralNumber()) {
      BigInteger number = value.bigIntegerValue();
      if (number.compareTo(MIN_SAFE_INTEGER) < 0 || number.compareTo(MAX_SAFE_INTEGER) > 0) {
        throw new IllegalArgumentException("Integer parameter exceeds JavaScript safe integer range");
      }
      if (number.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
          && number.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
        return number.intValue();
      }
      return number.longValue();
    }
    throw new IllegalArgumentException("Unsupported parameter value type");
  }

  private static String text(JsonNode node) {
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Expected text JSON node");
    }
    return node.textValue();
  }

  private static Iterable<String> iterable(java.util.Iterator<String> iterator) {
    return () -> iterator;
  }
}
