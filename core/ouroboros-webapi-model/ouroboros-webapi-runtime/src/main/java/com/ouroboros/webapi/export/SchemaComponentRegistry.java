package com.ouroboros.webapi.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.util.Json;
import com.ouroboros.webapi.openapi.ComponentsModel;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport;

class SchemaComponentRegistry {
  private final Map<String, SchemaModel> components = new LinkedHashMap<>();
  private final Map<String, String> fingerprintToName = new LinkedHashMap<>();
  private final Map<String, String> nameToFingerprint = new LinkedHashMap<>();
  private int generatedSchemaIndex = 1;

  SchemaModel rewriteForUsage(SchemaModel schema) {
    if (schema == null) {
      return null;
    }
    if (schema.getRef() != null) {
      return copySchema(schema);
    }

    SchemaModel copied = rewriteNested(copySchema(schema));
    if (!shouldExtract(copied)) {
      return copied;
    }

    String fingerprint = fingerprint(copied);
    String existingName = fingerprintToName.get(fingerprint);
    if (existingName != null) {
      return refSchema(existingName);
    }

    String componentName = uniqueName(candidateName(copied), fingerprint);
    components.put(componentName, copied);
    fingerprintToName.put(fingerprint, componentName);
    nameToFingerprint.put(componentName, fingerprint);
    return refSchema(componentName);
  }

  ComponentsModel buildComponents() {
    LinkedHashMap<String, SchemaModel> normalized = new LinkedHashMap<String, SchemaModel>();
    components.forEach((name, schema) -> normalized.put(name, SchemaModelJsonSchemaSupport.normalizeForOpenAPI31(schema)));
    return ComponentsModel.builder()
        .schemas(normalized)
        .build();
  }

  private SchemaModel rewriteNested(SchemaModel schema) {
    return schema.map(builder -> {
      builder.items(rewriteForUsage(schema.getItems()));
      builder.properties(rewriteSchemaMap(schema.getProperties()));
      builder.oneOf(rewriteSchemaList(schema.getOneOf()));
      builder.anyOf(rewriteSchemaList(schema.getAnyOf()));
      builder.allOf(rewriteSchemaList(schema.getAllOf()));
      builder.not(rewriteForUsage(schema.getNot()));
      builder.additionalProperties(rewriteAdditionalProperties(schema.getAdditionalProperties()));
    });
  }

  private Map<String, SchemaModel> rewriteSchemaMap(Map<String, SchemaModel> schemaMap) {
    if (schemaMap == null || schemaMap.isEmpty()) {
      return schemaMap;
    }
    LinkedHashMap<String, SchemaModel> rewritten = new LinkedHashMap<>();
    schemaMap.forEach((key, value) -> rewritten.put(key, rewriteForUsage(value)));
    return rewritten;
  }

  private List<SchemaModel> rewriteSchemaList(List<SchemaModel> schemas) {
    if (schemas == null || schemas.isEmpty()) {
      return schemas;
    }
    List<SchemaModel> rewritten = new ArrayList<>();
    schemas.forEach(schema -> rewritten.add(rewriteForUsage(schema)));
    return rewritten;
  }

  private Object rewriteAdditionalProperties(Object additionalProperties) {
    if (additionalProperties instanceof SchemaModel) {
      return rewriteForUsage((SchemaModel) additionalProperties);
    }
    return additionalProperties;
  }

  private boolean shouldExtract(SchemaModel schema) {
    if (schema == null || schema.getRef() != null) {
      return false;
    }
    if (schema.getEnumValues() != null && !schema.getEnumValues().isEmpty()) {
      return true;
    }
    if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
      return true;
    }
    if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
      return true;
    }
    if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
      return true;
    }
    if (schema.getNot() != null) {
      return true;
    }
    if ("object".equals(schema.getType())) {
      return (schema.getProperties() != null && !schema.getProperties().isEmpty())
          || schema.getAdditionalProperties() != null;
    }
    return false;
  }

  private String candidateName(SchemaModel schema) {
    String title = schema.getTitle();
    if (title != null) {
      String trimmed = title.trim();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return "GeneratedSchema" + generatedSchemaIndex++;
  }

  private String uniqueName(String baseName, String fingerprint) {
    String name = baseName;
    int suffix = 2;
    while (nameToFingerprint.containsKey(name) && !fingerprint.equals(nameToFingerprint.get(name))) {
      name = baseName + suffix++;
    }
    return name;
  }

  private String fingerprint(SchemaModel schema) {
    Map<String, Object> raw = Json.toMap(Json.toJsonString(schema));
    return Json.toJsonString(sortValue(raw));
  }

  private Object sortValue(Object value) {
    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((key, innerValue) -> sorted.put(String.valueOf(key), sortValue(innerValue)));
      return sorted;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      List<Object> sortedList = new ArrayList<>();
      list.forEach(item -> sortedList.add(sortValue(item)));
      return sortedList;
    }
    return value;
  }

  private SchemaModel copySchema(SchemaModel schema) {
    return Json.toBean(SchemaModel.class, Json.toJsonString(schema));
  }

  private SchemaModel refSchema(String componentName) {
    return SchemaModel.builder()
        .ref("#/components/schemas/" + componentName)
        .build();
  }
}
