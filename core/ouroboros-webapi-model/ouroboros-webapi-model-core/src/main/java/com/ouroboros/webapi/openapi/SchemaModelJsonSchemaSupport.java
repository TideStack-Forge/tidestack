package com.ouroboros.webapi.openapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.util.Json;

public final class SchemaModelJsonSchemaSupport {
  public static final String OPENAPI_31_BASE_DIALECT = "https://spec.openapis.org/oas/3.1/dialect/base";
  public static final String JSON_SCHEMA_DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";

  private SchemaModelJsonSchemaSupport() {
  }

  public static SchemaModel normalizeForOpenAPI31(SchemaModel schema) {
    if (schema == null) {
      return null;
    }

    SchemaModel normalized = schema.map(builder -> {
      builder.items(normalizeForOpenAPI31(schema.getItems()));
      builder.properties(normalizeMap(schema.getProperties()));
      builder.oneOf(normalizeList(schema.getOneOf()));
      builder.anyOf(normalizeList(schema.getAnyOf()));
      builder.allOf(normalizeList(schema.getAllOf()));
      builder.not(normalizeForOpenAPI31(schema.getNot()));
      builder.additionalProperties(normalizeAdditionalProperties(schema.getAdditionalProperties()));
      builder.nullable(null);
      if (schema.getTypes() != null) {
        builder.types(new ArrayList<String>(schema.getTypes()));
      }
    });

    if (!Boolean.TRUE.equals(schema.getNullable())) {
      return normalized;
    }

    if (normalized.getOneOf() != null && !normalized.getOneOf().isEmpty()) {
      return normalized.map(builder -> builder.oneOf(appendNullBranch(normalized.getOneOf())));
    }
    if (normalized.getAnyOf() != null && !normalized.getAnyOf().isEmpty()) {
      return normalized.map(builder -> builder.anyOf(appendNullBranch(normalized.getAnyOf())));
    }
    if (normalized.getType() != null || (normalized.getTypes() != null && !normalized.getTypes().isEmpty())) {
      return normalized.map(builder -> {
        builder.type(null);
        builder.types(appendNullType(normalized));
      });
    }

    return nullableWrapper(normalized);
  }

  public static Map<String, Object> toJsonSchemaMap(SchemaModel schema) {
    Map<String, Object> jsonSchema = castMap(removeNulls(Json.toMap(Json.toJsonString(normalizeForOpenAPI31(schema)))));
    LinkedHashMap<String, Object> withDialect = new LinkedHashMap<String, Object>();
    withDialect.put("$schema", JSON_SCHEMA_DRAFT_2020_12);
    if (jsonSchema != null) {
      withDialect.putAll(jsonSchema);
    }
    return withDialect;
  }

  private static Map<String, SchemaModel> normalizeMap(Map<String, SchemaModel> properties) {
    if (properties == null || properties.isEmpty()) {
      return properties;
    }
    LinkedHashMap<String, SchemaModel> normalized = new LinkedHashMap<String, SchemaModel>();
    properties.forEach((name, property) -> normalized.put(name, normalizeForOpenAPI31(property)));
    return normalized;
  }

  private static List<SchemaModel> normalizeList(List<SchemaModel> schemas) {
    if (schemas == null || schemas.isEmpty()) {
      return schemas;
    }
    ArrayList<SchemaModel> normalized = new ArrayList<SchemaModel>();
    schemas.forEach(schema -> normalized.add(normalizeForOpenAPI31(schema)));
    return normalized;
  }

  private static Object normalizeAdditionalProperties(Object additionalProperties) {
    if (additionalProperties instanceof SchemaModel) {
      return normalizeForOpenAPI31((SchemaModel) additionalProperties);
    }
    return additionalProperties;
  }

  private static Object removeNulls(Object value) {
    if (value instanceof Map<?, ?>) {
      LinkedHashMap<String, Object> cleaned = new LinkedHashMap<String, Object>();
      ((Map<?, ?>) value).forEach((key, entryValue) -> {
        if (entryValue != null) {
          cleaned.put(String.valueOf(key), removeNulls(entryValue));
        }
      });
      return cleaned;
    }
    if (value instanceof List<?>) {
      ArrayList<Object> cleaned = new ArrayList<Object>();
      ((List<?>) value).forEach(item -> {
        if (item != null) {
          cleaned.add(removeNulls(item));
        }
      });
      return cleaned;
    }
    return value;
  }

  private static List<SchemaModel> appendNullBranch(List<SchemaModel> branches) {
    ArrayList<SchemaModel> normalized = new ArrayList<SchemaModel>(branches);
    if (normalized.stream().noneMatch(SchemaModelJsonSchemaSupport::isNullSchema)) {
      normalized.add(nullSchema());
    }
    return normalized;
  }

  private static List<String> appendNullType(SchemaModel schema) {
    ArrayList<String> types = new ArrayList<String>();
    if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
      types.addAll(schema.getTypes());
    } else if (schema.getType() != null) {
      types.add(schema.getType());
    }
    if (!types.contains("null")) {
      types.add("null");
    }
    return types;
  }

  private static SchemaModel nullableWrapper(SchemaModel nonNullSchema) {
    return SchemaModel.builder()
        .title(nonNullSchema.getTitle())
        .description(nonNullSchema.getDescription())
        .defaultValue(nonNullSchema.getDefaultValue())
        .readOnly(nonNullSchema.getReadOnly())
        .writeOnly(nonNullSchema.getWriteOnly())
        .example(nonNullSchema.getExample())
        .deprecated(nonNullSchema.getDeprecated())
        .extraProps(nonNullSchema.getExtraProps())
        .anyOf(Arrays.asList(nonNullSchema, nullSchema()))
        .build();
  }

  private static boolean isNullSchema(SchemaModel schema) {
    if (schema == null) {
      return false;
    }
    if ("null".equals(schema.getType())) {
      return true;
    }
    return schema.getTypes() != null
        && schema.getTypes().size() == 1
        && "null".equals(schema.getTypes().get(0));
  }

  private static SchemaModel nullSchema() {
    return SchemaModel.builder().type("null").build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }
}
