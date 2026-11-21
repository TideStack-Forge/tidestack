package com.ouroboros.notification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ouroboros.util.JsonSchemas;
import com.ouroboros.util.jsonschema.JsonSchemaValidationResult;

@Component
public class NotificationPayloadSchemaValidator {

  /** JSON Schema 标准标识（draft 2020-12），非部署端点。 */
  private static final String JSON_SCHEMA_DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
  public JsonSchemaValidationResult validate(Map<String, Object> payloadSchema, Object payload) {
    if (payloadSchema == null || payloadSchema.isEmpty()) {
      return JsonSchemaValidationResult.valid();
    }
    Map<String, Object> rootSchema = new LinkedHashMap<>();
    rootSchema.put("$schema", JSON_SCHEMA_DRAFT_2020_12);
    rootSchema.put("type", "object");
    rootSchema.put("properties", normalizeSchemaValue(null, payloadSchema));
    return JsonSchemas.validateResult(rootSchema, payload);
  }

  private Object normalizeSchemaValue(String key, Object value) {
    if (value instanceof Map) {
      Map<String, Object> normalizedMap = new LinkedHashMap<>();
      ((Map<?, ?>) value).forEach((entryKey, entryValue) -> normalizedMap.put(
          String.valueOf(entryKey),
          normalizeSchemaValue(String.valueOf(entryKey), entryValue)));
      return normalizedMap;
    }
    if (value instanceof Collection) {
      List<Object> normalizedList = new ArrayList<>();
      for (Object item : (Collection<?>) value) {
        normalizedList.add(normalizeSchemaValue(null, item));
      }
      return normalizedList;
    }
    if ("type".equals(key) && value instanceof String) {
      return normalizeType((String) value);
    }
    return value;
  }

  private String normalizeType(String type) {
    String normalized = type == null ? null : type.trim();
    if (normalized == null) {
      return null;
    }
    if ("int".equalsIgnoreCase(normalized)) {
      return "integer";
    }
    if ("float".equalsIgnoreCase(normalized)) {
      return "number";
    }
    return normalized;
  }
}
