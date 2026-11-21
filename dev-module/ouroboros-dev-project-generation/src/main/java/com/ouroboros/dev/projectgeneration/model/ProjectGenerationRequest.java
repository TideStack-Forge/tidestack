package com.ouroboros.dev.projectgeneration.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public record ProjectGenerationRequest(
    ProjectTemplateScope scope,
    String catalogRevision,
    String targetPlatformVersion,
    String templateId,
    String templateVersion,
    String compatibilityRowId,
    boolean allowPreview,
    Map<String, Object> parameters
) {

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final Set<String> NON_PARAMETER_FIELDS = Set.of(
      "appName",
      "scope",
      "catalogRevision",
      "targetPlatformVersion",
      "template",
      "templateId",
      "templateVersion",
      "compatibilityRowId",
      "allowPreview"
  );

  public static ProjectGenerationRequest fromPayload(JsonNode payload) {
    JsonNode source = payload == null || payload.isNull() ? JSON.createObjectNode() : payload;
    JsonNode template = source.path("template");
    return new ProjectGenerationRequest(
        ProjectTemplateScope.valueOf(requiredText(source, "scope")),
        requiredText(source, "catalogRevision"),
        requiredText(source, "targetPlatformVersion"),
        text(source, "templateId", text(template, "templateId", null)),
        text(source, "templateVersion", text(template, "templateVersion", null)),
        text(source, "compatibilityRowId", text(template, "compatibilityRowId", null)),
        source.path("allowPreview").asBoolean(false),
        parameters(source)
    );
  }

  private static Map<String, Object> parameters(JsonNode payload) {
    JsonNode nested = payload.path("parameters");
    if (nested.isObject()) {
      return JSON.convertValue(nested, JSON.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    payload.fields().forEachRemaining(entry -> {
      if (!NON_PARAMETER_FIELDS.contains(entry.getKey())) {
        result.put(entry.getKey(), JSON.convertValue(entry.getValue(), Object.class));
      }
    });
    return result;
  }

  private static String requiredText(JsonNode node, String field) {
    String value = text(node, field, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing project generation field: " + field);
    }
    return value;
  }

  private static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    return value.asText();
  }
}
