package com.ouroboros.enterprise.runtime.web;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ouroboros.exception.BusinessException;

/** Projects the provider's published field contract into the platform's existing Schema controls. */
public final class EnterpriseConnectionEditor {
  public Map<String, Object> render(Map<String, Object> connectionSchema) {
    Object rawFields = connectionSchema == null ? null : connectionSchema.get("fields");
    if (!(rawFields instanceof List<?> fields)) {
      throw new BusinessException(
          "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
              + "] provider connection schema 缺少 fields 数组");
    }
    List<Map<String, Object>> controls = new ArrayList<>();
    for (Object rawField : fields) {
      if (rawField instanceof String name && !name.isBlank()) {
        controls.add(Map.of(
            "type", "input-text",
            "name", name,
            "label", name));
        continue;
      }
      if (!(rawField instanceof Map<?, ?> field)) {
        throw new BusinessException(
            "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
                + "] provider connection schema 字段非法");
      }
      Object rawName = field.get("name");
      if (!(rawName instanceof String name) || name.isBlank()) {
        throw new BusinessException(
            "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
                + "] provider connection schema 字段缺少 name");
      }
      Map<String, Object> control = new LinkedHashMap<>();
      Object rawEditor = field.get("editor");
      if (rawEditor instanceof Map<?, ?> editor) {
        for (Map.Entry<?, ?> entry : editor.entrySet()) {
          if (entry.getKey() instanceof String key && !key.isBlank()
              && entry.getValue() != null) {
            control.put(key, entry.getValue());
          }
        }
        if (!(control.get("type") instanceof String)) {
          throw new BusinessException(
              "[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
                  + "] provider connection schema editor 缺少 type: " + name);
        }
      } else {
        control.put("type", amisType(field.get("type")));
      }
      control.put("name", name);
      control.put("label", stringOrDefault(field.get("label"), name));
      copyIfPresent(field, control, "required");
      copyIfPresent(field, control, "description");
      copyIfPresent(field, control, "placeholder");
      copyIfPresent(field, control, "options");
      copyIfPresent(field, control, "visibleOn");
      copyIfPresent(field, control, "multiple");
      copyIfPresent(field, control, "items");
      controls.add(Map.copyOf(control));
    }
    return Map.of("type", "group", "body", List.copyOf(controls));
  }

  private String amisType(Object type) {
    if (!(type instanceof String value) || value.isBlank()) {
      return "input-text";
    }
    return switch (value) {
      case "string" -> "input-text";
      case "boolean" -> "switch";
      case "integer", "number" -> "input-number";
      case "object" -> "json-editor";
      case "array" -> "combo";
      default -> value;
    };
  }

  private String stringOrDefault(Object value, String fallback) {
    return value instanceof String text && !text.isBlank() ? text : fallback;
  }

  private void copyIfPresent(
      Map<?, ?> source, Map<String, Object> target, String key) {
    if (source.containsKey(key) && source.get(key) != null) {
      target.put(key, source.get(key));
    }
  }
}
