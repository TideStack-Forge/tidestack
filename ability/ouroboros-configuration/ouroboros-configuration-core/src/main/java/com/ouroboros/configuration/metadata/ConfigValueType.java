package com.ouroboros.configuration.metadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public enum ConfigValueType {
  /**
   * 基础数据类型
   * 在此所有类必须拥有："静态valueOf"方法、"toString"方法
   */
  INT("int", Integer.class, Converter::toInteger, ConfigValueType::isValidInteger),
  LONG("long", Long.class, Converter::toLong, ConfigValueType::isValidLong),
  FLOAT("float", Float.class, Converter::toFloat, ConfigValueType::isValidFloat),
  DOUBLE("double", Double.class, Converter::toDouble, ConfigValueType::isValidDouble),
  BOOLEAN("boolean", Boolean.class, Converter::toBoolean, ConfigValueType::isValidBoolean),
  STRING("string", String.class, Converter::toString, ConfigValueType::isValidString),

  OBJECT("object", Object.class, ConfigValueType::toMap, ConfigValueType::isValidMap),
  MAP("map", Map.class, ConfigValueType::toMap, ConfigValueType::isValidMap),

  LIST("list", List.class, ConfigValueType::toList, ConfigValueType::isValidList),
  ARRAY("array", List.class, ConfigValueType::toList, ConfigValueType::isValidList);

  private final String name;

  private final Class<?> type;

  private final Function<String, Object> converter;

  private final Predicate<String> validator;

  ConfigValueType(String name, Class<?> type, Function<String, Object> converter, Predicate<String> validator) {
    this.name = name;
    this.type = type;
    this.converter = converter;
    this.validator = validator;
  }

  @JsonCreator
  public static ConfigValueType forValue(String name) {
    return Arrays.stream(values())
        .filter(i -> i.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElse(STRING);
  }

  private static boolean isValidInteger(String value) {
    try {
      Integer.parseInt(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidLong(String value) {
    try {
      Long.parseLong(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidFloat(String value) {
    try {
      Float.parseFloat(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidDouble(String value) {
    try {
      Double.parseDouble(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidBoolean(String value) {
    return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
  }

  private static boolean isValidString(String value) {
    return true;
  }

  private static boolean isValidMap(String value) {
    return true;
  }

  private static boolean isValidList(String value) {
    return true;
  }

  private static Map<String, Object> toMap(String value) {
    return Json.tryToMap(value).getOrElse(Collections::emptyMap);
  }

  private static List<Object> toList(String value) {
    return Json.tryToList(value).getOrElse(Collections::emptyList);
  }

  @JsonValue
  public String getName() {
    return name;
  }

  public Class<?> getType() {
    return type;
  }

  public boolean isValid(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof String valueString) {
      return isValid(valueString);
    }
    // 数值型配置按数值语义校验：JSON 中不带小数点的数字（如 50000）会被反序列化为 Integer，
    // 而配置类型可能是 double/long，直接做 Class.isAssignableFrom 会误判为类型不匹配，
    // 导致前端以整数形态提交时无法保存。这里放宽到「数值拓宽」，但仍拒绝会静默截断的收窄。
    if (value instanceof Number number && Number.class.isAssignableFrom(type)) {
      return isWideningNumber(number) && isValid(number.toString());
    }
    return type.isAssignableFrom(value.getClass());
  }

  /**
   * 判断数值是否可由当前配置类型无损承载（拓宽）。
   * <p>例如 {@code double} 可承载 {@code Integer}，但 {@code int} 不应承载 {@code Long}
   * （会静默截断，属于真实的类型不匹配）。
   *
   * @param value 待校验数值
   * @return 是否允许
   */
  private boolean isWideningNumber(Number value) {
    if (type == Double.class || type == Float.class) {
      return true;
    }
    if (type == Long.class) {
      return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }
    if (type == Integer.class) {
      return value instanceof Byte || value instanceof Short || value instanceof Integer;
    }
    return false;
  }

  public boolean isValid(String value) {
    return validator.test(value);
  }

  public <T> T convert(String value) {
    return (T) converter.apply(value);
  }

  public String toString(Object value) {
    if (value == null) {
      return null;
    }
    if (this == MAP || this == OBJECT || this == LIST || this == ARRAY) {
      if (value instanceof String strValue) {
        if (isValid(strValue)) {
          return strValue;
        }
        return this == LIST || this == ARRAY ? "[]" : "{}";
      }
      return Json.toJsonString(value);
    }
    return value.toString();
  }
}
