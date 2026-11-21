package com.ouroboros.notification.util;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.InternalException;
import com.ouroboros.notification.adapter.NotificationChannelManagement;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class Classes {

  private static final ObjectMapper MAPPER = Json.mapperCopy();

  public static Map<String, Object> toManagementSchema(Class<?> clazz, Map<String, NotificationChannelManagement.Field> supplement) {
    return managementSchema(MAPPER.constructType(clazz), supplement, new HashSet<>());
  }

  private static Map<String, Object> managementSchema(JavaType type,
      Map<String, NotificationChannelManagement.Field> supplement, Set<Class<?>> path) {
    Class<?> raw = type.getRawClass();
    if (raw == Object.class) { return Map.of(); }
    if (raw == String.class || raw == char.class || raw == Character.class) { return new LinkedHashMap<>(Map.of("type", List.of("string", "null"))); }
    if (raw == boolean.class || raw == Boolean.class) { return new LinkedHashMap<>(Map.of("type", List.of("boolean", "null"))); }
    if (raw.isEnum()) {
      var values = new ArrayList<Object>(java.util.Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList());
      values.add(null);
      return new LinkedHashMap<>(Map.of("type", List.of("string", "null"), "enum", values));
    }
    if (raw == int.class || raw == long.class || raw == short.class || raw == byte.class || raw == Integer.class
        || raw == Long.class || raw == Short.class || raw == Byte.class || raw == java.math.BigInteger.class) {
      return new LinkedHashMap<>(Map.of("type", List.of("integer", "null")));
    }
    if (raw == double.class || raw == float.class || Number.class.isAssignableFrom(raw)) {
      return new LinkedHashMap<>(Map.of("type", List.of("number", "null")));
    }
    if (type.isMapLikeType()) {
      return new LinkedHashMap<>(Map.of("type", List.of("object", "null"), "additionalProperties",
          managementSchema(type.getContentType(), Map.of(), path)));
    }
    if (type.isCollectionLikeType() || type.isArrayType()) {
      return new LinkedHashMap<>(Map.of("type", List.of("array", "null"), "items", managementSchema(type.getContentType(), Map.of(), path)));
    }
    if (!path.add(raw)) { throw new InternalException("渠道字段存在递归类型，无法生成管理结构：" + raw.getName(), null); }
    try {
      var properties = new LinkedHashMap<String, Object>(); var required = new ArrayList<String>();
      for (var property : MAPPER.getDeserializationConfig().introspect(type).findProperties()) {
        if (!property.couldDeserialize()) { continue; }
        var child = new LinkedHashMap<>(managementSchema(property.getPrimaryType(), Map.of(), path));
        var field = supplement.get(property.getName());
        if (field != null) {
          child.put("title", field.label());
          if (field.required()) {
            required.add(property.getName());
            if (property.getRawPrimaryType() == String.class) {
              child.put("type", "string"); child.put("minLength", 1); child.put("pattern", "\\S");
            }
          }
          if (field.minimum() != null) { child.put("minimum", field.minimum()); }
          if (field.maximum() != null) { child.put("maximum", field.maximum()); }
        }
        properties.put(property.getName(), child);
      }
      if (!properties.keySet().containsAll(supplement.keySet())) {
        throw new InternalException("渠道管理描述引用了原 Java 类型不存在的字段：" + raw.getName(), null);
      }
      var schema = new LinkedHashMap<String, Object>();
      schema.put("type", path.size() > 1 ? List.of("object", "null") : "object");
      schema.put("properties", properties); schema.put("additionalProperties", false);
      if (!required.isEmpty()) { schema.put("required", required); }
      return schema;
    } finally { path.remove(raw); }
  }

  public static Map<String, Object> toMapDefine(Class<?> clazz) {
    Map<String, Object> fieldMap = new HashMap<>();
    Class<?> currentClass = clazz;

    while (currentClass != null && currentClass != Object.class) {
      Field[] fields = currentClass.getDeclaredFields();

      for (Field field : fields) {
        if (field.isSynthetic()) {
          continue;
        }

        String fieldName = field.getName();
        Class<?> fieldType = field.getType();

        if (isBasicType(fieldType)) {
          fieldMap.putIfAbsent(fieldName, fieldType.getSimpleName().toLowerCase());
        } else {
          fieldMap.putIfAbsent(fieldName, toMapDefine(fieldType));
        }
      }

      currentClass = currentClass.getSuperclass();
    }

    return fieldMap;
  }

  private static boolean isBasicType(Class<?> clazz) {
    return clazz.isPrimitive()
        || clazz == String.class
        || clazz == Integer.class
        || clazz == Long.class
        || clazz == Float.class
        || clazz == Double.class
        || clazz == Short.class
        || clazz == Byte.class
        || clazz == Character.class
        || clazz == Boolean.class;
  }
}
