package com.ouroboros.enterprise.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;

/**
 * 统一配置工作台结构化值解析器。
 *
 * <p>配置中心对 {@code list/map} 的持久化值在不同入口可能表现为已转换的 Java
 * 容器或 JSON 字符串。本类只负责把这两种表示收敛成严格的对象列表/对象，不提供
 * provider 专属字段别名，也不吞掉结构错误。
 */
public final class EnterpriseConfigurationValueParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private EnterpriseConfigurationValueParser() {
  }

  /**
   * 将配置值解析为对象列表。
   *
   * @param raw 原始配置值
   * @param key 配置 key（仅用于错误定位）
   * @return 每项均为字符串 key 的对象副本
   */
  public static List<Map<String, Object>> objectList(Object raw, String key) {
    if (raw == null) {
      return List.of();
    }
    Object value = raw;
    if (raw instanceof CharSequence text) {
      try {
        value = OBJECT_MAPPER.readValue(text.toString(), Object.class);
      } catch (Exception ex) {
        throw invalid(key, "JSON 解析失败", ex);
      }
    }
    if (!(value instanceof List<?> list)) {
      throw invalid(key, "必须是 JSON 数组");
    }
    List<Map<String, Object>> result = new ArrayList<>(list.size());
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw invalid(key, "数组元素必须是 JSON 对象");
      }
      result.add(stringKeyMap(map, key));
    }
    return List.copyOf(result);
  }

  /**
   * 将可选对象字段解析为字符串 key 的对象副本。
   */
  public static Map<String, Object> object(Object raw, String key) {
    if (raw == null) {
      return Map.of();
    }
    Object value = raw;
    if (raw instanceof CharSequence text) {
      try {
        value = OBJECT_MAPPER.readValue(text.toString(), Object.class);
      } catch (Exception ex) {
        throw invalid(key, "JSON 解析失败", ex);
      }
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw invalid(key, "必须是 JSON 对象");
    }
    return stringKeyMap(map, key);
  }

  public static String requiredText(Map<String, Object> object, String field, String key) {
    Object value = object.get(field);
    if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
      throw invalid(key, "字段 " + field + " 不能为空");
    }
    return text.toString();
  }

  public static String optionalText(Map<String, Object> object, String field) {
    Object value = object.get(field);
    if (value == null) {
      return null;
    }
    if (!(value instanceof CharSequence text)) {
      throw invalid(field, "字段必须是字符串");
    }
    String result = text.toString();
    return result.isBlank() ? null : result;
  }

  /** 将可选对象字段解析为字符串 key 的对象副本；缺省值为空对象。 */
  public static Map<String, Object> optionalObject(
      Map<String, Object> object, String field, String key) {
    Object value = object.get(field);
    return value == null ? Map.of() : object(value, key + "." + field);
  }

  /** 读取可选布尔字段，未配置时返回调用方指定的默认值。 */
  public static boolean optionalBoolean(
      Map<String, Object> object, String field, String key, boolean defaultValue) {
    Object value = object.get(field);
    if (value == null) {
      return defaultValue;
    }
    if (!(value instanceof Boolean result)) {
      throw invalid(key, "字段 " + field + " 必须是布尔值");
    }
    return result;
  }

  private static Map<String, Object> stringKeyMap(Map<?, ?> map, String key) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String field) || field.isBlank()) {
        throw invalid(key, "对象字段名必须是非空字符串");
      }
      result.put(field, entry.getValue());
    }
    return Collections.unmodifiableMap(result);
  }

  private static BusinessException invalid(String key, String reason) {
    return new BusinessException("[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
        + "] 统一配置 " + key + " 非法: " + reason);
  }

  private static BusinessException invalid(String key, String reason, Throwable cause) {
    return new BusinessException("[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
        + "] 统一配置 " + key + " 非法: " + reason, cause);
  }
}
