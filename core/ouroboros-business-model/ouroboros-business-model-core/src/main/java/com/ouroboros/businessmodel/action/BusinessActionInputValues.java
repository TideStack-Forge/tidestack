package com.ouroboros.businessmodel.action;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.exception.BusinessException;

/** Strict Schema preparation and isolation of mutable business containers. */
public final class BusinessActionInputValues {
  private BusinessActionInputValues() {
  }

  public static Map<String, Object> prepare(Map<String, Object> schema, Map<String, Object> input) {
    Map<String, Object> values = normalizeMap(schema, input, "");
    if (values.keySet().stream().anyMatch(key -> !"params".equals(key) && !"data".equals(key))) {
      throw invalid("", "仅支持 params/data 分区");
    }
    validateIdentities(schema, values, values, "");
    return values;
  }

  private static void validateIdentities(Object definition, Object value, Object root, String path) {
    if (!(definition instanceof Map<?, ?> schema)) {
      return;
    }
    if (schema.get("x-business-object-identity") instanceof String pointer) {
      if (!pointer.startsWith("/") || pointer.matches(".*~(?![01]).*")) {
        throw invalid(path, "对象身份指针无效");
      }
      Object expected = root;
      for (String segment : pointer.substring(1).split("/")) {
        expected = expected instanceof Map<?, ?> map ? map.get(segment.replace("~1", "/").replace("~0", "~")) : null;
      }
      boolean equal = value instanceof Number number && expected instanceof Number other
          ? new BigDecimal(number.toString()).compareTo(new BigDecimal(other.toString())) == 0
          : java.util.Objects.equals(value, expected);
      if (!equal) {
        throw invalid(path, "与声明的对象身份不一致");
      }
    }
    if (value instanceof Map<?, ?> values && schema.get("properties") instanceof Map<?, ?> properties) {
      properties.forEach((key, nested) -> {
        if (values.containsKey(key)) {
          validateIdentities(nested, values.get(key), root, path + "/" + escape(String.valueOf(key)));
        }
      });
    } else if (value instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        validateIdentities(schema.get("items"), list.get(index), root, path + "/" + index);
      }
    }
  }

  /** Unconstrained native resources are not serialized just to validate their enclosing object. */
  public static Object validationValue(Object definition, Object value) {
    Map<?, ?> schema = definition instanceof Map<?, ?> map ? map : Map.of();
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> replacement = null;
      Map<?, ?> properties = schema.get("properties") instanceof Map<?, ?> fields ? fields : Map.of();
      for (var entry : map.entrySet()) {
        Object nested = validationValue(properties.get(entry.getKey()), entry.getValue());
        if (nested != entry.getValue()) {
          if (replacement == null) { replacement = stringMap(map); }
          replacement.put(String.valueOf(entry.getKey()), nested);
        }
      }
      return replacement == null ? value : replacement;
    }
    if (value instanceof List<?> list) {
      List<Object> replacement = null;
      for (int index = 0; index < list.size(); index++) {
        Object nested = validationValue(schema.get("items"), list.get(index));
        if (nested != list.get(index)) {
          if (replacement == null) { replacement = new ArrayList<>(list); }
          replacement.set(index, nested);
        }
      }
      return replacement == null ? value : replacement;
    }
    if (isResource(value)) {
      if (!schema.isEmpty()) {
        throw invalid("", "原生资源只能用于无 JSON 约束的同步输入字段");
      }
      return Map.of();
    }
    return value;
  }

  private static boolean isResource(Object value) {
    return value instanceof java.io.InputStream || value instanceof java.io.File || value instanceof java.nio.file.Path
        || (value != null && isResourceType(value.getClass()));
  }

  private static boolean isResourceType(Class<?> type) {
    return "org.springframework.web.multipart.MultipartFile".equals(type.getName())
        || java.util.Arrays.stream(type.getInterfaces()).anyMatch(BusinessActionInputValues::isResourceType)
        || type.getSuperclass() != null && isResourceType(type.getSuperclass());
  }

  private static Object normalize(Object definition, Object value, String path) {
    if (value == null) { return null; }
    Map<?, ?> schema = definition instanceof Map<?, ?> map ? map : Map.of();
    if (value instanceof BusinessObjectReference reference) {
      return normalize(schema, Map.of("modelFullName", reference.modelFullName(), "id", reference.id()), path);
    }
    Object type = schema.get("type");
    if (type instanceof List<?> types && types.stream().filter(kind -> !"null".equals(kind)).count() == 1) {
      type = types.stream().filter(kind -> !"null".equals(kind)).findFirst().orElse(null);
    }
    if (value instanceof String text) {
      try {
        if ("integer".equals(type)) {
          return new BigDecimal(text).toBigIntegerExact();
        }
        if ("number".equals(type)) {
          return new BigDecimal(text);
        }
        if ("boolean".equals(type)) {
          if (!"true".equals(text) && !"false".equals(text)) {
            throw invalid(path, "必须为 true 或 false");
          }
          return Boolean.valueOf(text);
        }
      } catch (NumberFormatException | ArithmeticException error) {
        throw invalid(path, "数值格式无效");
      }
    }
    if (value instanceof Map<?, ?> map) {
      return normalizeMap(schema, map, path);
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (int index = 0; index < list.size(); index++) {
        result.add(normalize(schema.get("items"), list.get(index), path + "/" + index));
      }
      return Collections.unmodifiableList(result);
    }
    if (value instanceof Iterable<?> || value.getClass().isArray() && !(value instanceof byte[])) {
      return normalize(schema, snapshot(value), path);
    }
    Object projected = snapshot(value);
    return projected instanceof Map<?, ?> || projected instanceof List<?> ? normalize(schema, projected, path) : projected;
  }

  private static Map<String, Object> normalizeMap(Map<?, ?> schema, Map<?, ?> map, String path) {
    Map<?, ?> properties = schema.get("properties") instanceof Map<?, ?> fields ? fields : Map.of();
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, nested) -> {
      if (!(key instanceof String name)) { throw invalid(path, "对象属性名必须为字符串"); }
      if ("modelFullName".equals(name) && schema.get("x-business-object-ref") instanceof String model) {
        if (!model.equalsIgnoreCase(String.valueOf(nested))) { throw invalid(path, "对象引用模型不匹配"); }
      } else {
        result.put(name, normalize(properties.get(name), nested, path + "/" + escape(name)));
      }
    });
    properties.forEach((key, child) -> {
      String name = String.valueOf(key);
      if (!result.containsKey(name) && child instanceof Map<?, ?> field && field.containsKey("default")) {
        result.put(name, normalize(child, field.get("default"), path + "/" + escape(name)));
      }
    });
    return Collections.unmodifiableMap(result);
  }

  public static Object snapshot(Object value) {
    return copy(value, false);
  }

  public static Map<String, Object> snapshotMap(Map<String, Object> value) {
    Map<String, Object> result = new LinkedHashMap<>();
    value.forEach((key, nested) -> result.put(key, snapshot(nested)));
    return Collections.unmodifiableMap(result);
  }

  /** Flow scripts may modify work data without changing the validated input. */
  public static Object workCopy(Object value) {
    return copy(value, true);
  }

  private static Object copy(Object value, boolean mutable) {
    if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
        || value instanceof Character || value instanceof Enum<?>) {
      return value;
    }
    if (value instanceof BusinessObjectReference reference) {
      return copy(Map.of("modelFullName", reference.modelFullName(), "id", reference.id()), mutable);
    }
    if (value instanceof byte[] bytes) {
      return bytes.clone();
    }
    if (value instanceof java.util.Date date) {
      return date.clone();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      stringMap(map).forEach((key, nested) -> result.put(key, copy(nested, mutable)));
      return mutable ? result : Collections.unmodifiableMap(result);
    }
    if (value instanceof Iterable<?> iterable) {
      List<Object> result = new ArrayList<>();
      iterable.forEach(nested -> result.add(copy(nested, mutable)));
      return mutable ? result : Collections.unmodifiableList(result);
    }
    if (value.getClass().isArray() && !(value instanceof byte[])) {
      List<Object> result = new ArrayList<>();
      for (int index = 0; index < Array.getLength(value); index++) {
        result.add(copy(Array.get(value, index), mutable));
      }
      return mutable ? result : Collections.unmodifiableList(result);
    }
    if (isResource(value)) {
      return value;
    }
    if (value.getClass().isRecord()) {
      Map<String, Object> result = new LinkedHashMap<>();
      try {
        for (var component : value.getClass().getRecordComponents()) {
          var accessor = component.getAccessor();
          if (!accessor.canAccess(value) && !accessor.trySetAccessible()) {
            throw new BusinessException("业务输入 record 不可读取");
          }
          var property = accessor.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
          String name = property == null || property.value().isEmpty() ? component.getName() : property.value();
          Object nested = accessor.invoke(value);
          var include = value.getClass().getAnnotation(com.fasterxml.jackson.annotation.JsonInclude.class);
          if (nested != null || include == null || include.value() != com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) {
            result.put(name, nested);
          }
        }
      } catch (IllegalAccessException | InvocationTargetException error) {
        throw new BusinessException("业务输入 record 不可读取", error);
      }
      return copy(result, mutable);
    }
    // Files, dates and other platform values keep their official type; only declared DTO beans are projected.
    if (!value.getClass().getPackageName().startsWith("java.")
        && !value.getClass().getPackageName().startsWith("org.springframework.")) {
      try {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var property : Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors()) {
          if (property.getReadMethod() != null) {
            result.put(property.getName(), property.getReadMethod().invoke(value));
          }
        }
        if (!result.isEmpty()) {
          return copy(result, mutable);
        }
      } catch (IntrospectionException | IllegalAccessException | InvocationTargetException error) {
        throw new BusinessException("业务输入 DTO 不可读取", error);
      }
    }
    return value;
  }

  private static Map<String, Object> stringMap(Map<?, ?> values) {
    Map<String, Object> result = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      if (!(key instanceof String name)) {
        throw invalid("", "对象属性名必须为字符串");
      }
      result.put(name, value);
    });
    return result;
  }

  private static String escape(String name) {
    return name.replace("~", "~0").replace("/", "~1");
  }

  private static BusinessException invalid(String path, String reason) {
    return new BusinessException("业务 Action 输入 " + path + "：" + reason);
  }
}
