package com.ouroboros.businessmodel;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BusinessEventPayloadSchemaGenerator {
  private BusinessEventPayloadSchemaGenerator() {
  }

  static Map<String, Object> schema(Class<?> payloadType) {
    Object generated = schemaFor(payloadType, new HashSet<>());
    if (!(generated instanceof Map<?, ?> values)) {
      throw new IllegalArgumentException("Business Event payload root must be an object type");
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    values.forEach((key, value) -> schema.put(String.valueOf(key), value));
    return schema;
  }

  private static Object schemaFor(Type type, Set<Class<?>> path) {
    if (type instanceof ParameterizedType parameterizedType) {
      Class<?> rawType = rawClass(parameterizedType.getRawType());
      if (Collection.class.isAssignableFrom(rawType)) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "array");
        result.put("items", schemaFor(parameterizedType.getActualTypeArguments()[0], path));
        return result;
      }
      if (Map.class.isAssignableFrom(rawType)) {
        return Map.of("type", "object", "additionalProperties", true);
      }
      return schemaFor(rawType, path);
    }
    if (!(type instanceof Class<?> valueType)) {
      throw new IllegalArgumentException("Unsupported Business Event payload type: " + type.getTypeName());
    }
    if (valueType == String.class || valueType == Character.class || valueType == char.class) {
      return Map.of("type", "string");
    }
    if (valueType == Boolean.class || valueType == boolean.class) {
      return Map.of("type", "boolean");
    }
    if (valueType == Byte.class || valueType == byte.class || valueType == Short.class || valueType == short.class
        || valueType == Integer.class || valueType == int.class || valueType == Long.class || valueType == long.class
        || valueType == BigInteger.class) {
      return Map.of("type", "integer");
    }
    if (Number.class.isAssignableFrom(valueType) || valueType == float.class || valueType == double.class) {
      return Map.of("type", "number");
    }
    if (valueType == Instant.class || valueType == LocalDate.class || valueType == LocalDateTime.class
        || valueType == OffsetDateTime.class) {
      return Map.of("type", "string", "format", valueType == LocalDate.class ? "date" : "date-time");
    }
    if (valueType.isEnum()) {
      return Map.of("type", "string", "enum", java.util.Arrays.stream(valueType.getEnumConstants())
          .map(value -> ((Enum<?>) value).name()).toList());
    }
    if (valueType.isArray()) {
      return Map.of("type", "array", "items", schemaFor(valueType.getComponentType(), path));
    }
    if (!valueType.isRecord()) {
      throw new IllegalArgumentException("Business Event payload schema generation requires a record: "
          + valueType.getName());
    }
    if (!path.add(valueType)) {
      throw new IllegalArgumentException("Recursive Business Event payload records are not supported: "
          + valueType.getName());
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (RecordComponent component : valueType.getRecordComponents()) {
      properties.put(component.getName(), schemaFor(component.getGenericType(), path));
      required.add(component.getName());
    }
    path.remove(valueType);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", "object");
    result.put("properties", properties);
    result.put("required", required);
    result.put("additionalProperties", false);
    return result;
  }

  private static Class<?> rawClass(Type type) {
    if (type instanceof Class<?> value) {
      return value;
    }
    throw new IllegalArgumentException("Unsupported Business Event payload type: " + type.getTypeName());
  }
}
