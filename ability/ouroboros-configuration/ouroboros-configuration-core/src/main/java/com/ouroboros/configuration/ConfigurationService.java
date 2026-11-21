package com.ouroboros.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;

/**
 * 配置服务
 *
 * @author liansz
 */
public interface ConfigurationService {

  /**
   * 通过配置名称获取配置
   *
   * @param key 配置唯一键
   * @return 配置值（按预期类型自动转换）
   */
  <T> Optional<T> get(String key);

  /** A consistent internal value/revision observation. Implementations must not assemble it from separate reads. */
  default Optional<ConfigurationSnapshot> getSnapshot(String key) {
    throw new PlatformException("配置服务未提供权威快照读取");
  }

  /** Configuration exposed to scripts or configurable logic flows, rather than trusted component code. */
  default <T> Optional<T> getForExecution(String key) {
    return get(key);
  }

  /**
   * 按范围获取配置值集合
   *
   * @param scope
   * @return
   */
  Map<String, Object> getValues(ConfigScope scope);

  /**
   * 配置项的权威变更标识。
   *
   * <p>返回的 token 在"有效值发生变化"时必然变化，且在值未变时保持稳定（跨重启、跨实例一致）。
   * 调用方用它做缓存键或变更检测，避免各自再算一份指纹——多份指纹会得到互相矛盾的结论。
   *
   * @param key 配置唯一键
   * @return 变更标识；配置既无持久化行也无元数据时为空
   */
  default Optional<String> getRevision(String key) {
    return Optional.empty();
  }

  /**
   * 配置项的持久化形态原始值（未经类型转换）。
   *
   * <p>用于需要区分"配置确实为空"与"配置内容无法解析"的场景：类型转换会把坏 JSON 静默降级为
   * 空集合，只看转换结果无法发现配置损坏。返回空表示该键没有持久化行（仍可能有元数据默认值）。
   *
   * @param key 配置唯一键
   * @return 原始存储值
   */
  default Optional<String> getStoredRawValue(String key) {
    return Optional.empty();
  }

  /**
   * 通过配置名称获取配置
   *
   * @param clazz 配置值类型
   * @param key   配置唯一键
   * @return 配置值（按预期类型自动转换）
   */
  default <T> Optional<T> get(Class<T> clazz, String key) {
    return get(key).flatMap(value -> toValue(clazz, value));
  }

  /**
   * 通过配置名称获取配置；不存在时返回默认值。
   *
   * @param key          配置唯一键
   * @param defaultValue 默认值
   * @return 配置值或默认值
   */
  default <T> T getOrDefault(String key, T defaultValue) {
    return this.<T>get(key).orElse(defaultValue);
  }

  /**
   * 通过配置名称获取指定类型配置；不存在或无法转换时返回默认值。
   *
   * @param clazz        配置值类型
   * @param key          配置唯一键
   * @param defaultValue 默认值
   * @return 配置值或默认值
   */
  default <T> T getOrDefault(Class<T> clazz, String key, T defaultValue) {
    return get(clazz, key).orElse(defaultValue);
  }

  /**
   * 通过键获取必填配置值。
   *
   * @param key 配置唯一键
   * @return 配置值（按预期类型自动转换）
   */
  default <T> T getRequired(String key) {
    return this.<T>get(key).orElseThrow(() -> new PlatformException("配置不存在或为空: " + key));
  }

  /**
   * 通过键获取指定类型的必填配置值。
   *
   * @param clazz 配置值类型
   * @param key   配置唯一键
   * @return 配置值（按指定类型自动转换）
   */
  default <T> T getRequired(Class<T> clazz, String key) {
    return get(clazz, key)
        .orElseThrow(() -> new PlatformException("配置不存在、为空或无法转换为 " + clazz.getSimpleName() + ": " + key));
  }

  /**
   * 通过键获取非空值
   *
   * @param key 配置唯一键
   * @return 配置值（按预期类型自动转换）
   * @deprecated 请使用 {@link #getRequired(String)}。
   */
  @Deprecated
  default <T> T getNotNullValue(String key) {
    return getRequired(key);
  }

  /**
   * 通过键获取对象配置，并转换为指定类型。
   *
   * @param clazz 配置对象类型
   * @param key   配置唯一键
   * @return 配置对象实例
   * @deprecated 请使用 {@link #get(Class, String)}。
   */
  @Deprecated
  default <T> Optional<T> getAs(Class<T> clazz, String key) {
    return get(clazz, key);
  }

  /**
   * 通过键获取对象列表配置，并转换为指定类型列表。
   *
   * @param clazz 配置对象类型
   * @param key   配置唯一键
   * @return 配置对象实例列表
   */
  default <T> Optional<List<T>> getList(Class<T> clazz, String key) {
    return getList(key).flatMap(list -> toValueList(clazz, list));
  }

  /**
   * 通过键获取对象列表配置，并转换为指定类型列表。
   *
   * @param clazz 配置对象类型
   * @param key   配置唯一键
   * @return 配置对象实例列表
   */
  @Deprecated
  default <T> Optional<List<T>> getListAs(Class<T> clazz, String key) {
    return getList(clazz, key);
  }

  /**
   * 通过键获取原始列表配置，保留列表元素的实际类型。
   *
   * @param key 配置唯一键
   * @return 原始配置列表
   */
  default Optional<List<Object>> getList(String key) {
    return get(key).flatMap(ConfigurationService::toList);
  }

  /**
   * 通过配置名称获取配置
   *
   * @param key 配置唯一键
   * @return 配置值（按预期类型自动转换）
   * @deprecated 请使用 {@link #get(String)}。
   */
  @Deprecated
  default <T> Optional<T> getByKey(String key) {
    return get(key);
  }

  /**
   * 通过配置名称获取配置
   *
   * @param clazz 配置值类型
   * @param key   配置唯一键
   * @return 配置值（按预期类型自动转换）
   * @deprecated 请使用 {@link #get(Class, String)}。
   */
  @Deprecated
  default <T> Optional<T> getByKey(Class<T> clazz, String key) {
    return get(clazz, key);
  }

  /**
   * 通过键获取非空值
   *
   * @param key 配置唯一键
   * @return 配置值（按预期类型自动转换）
   * @deprecated 请使用 {@link #getRequired(String)}。
   */
  @Deprecated
  default <T> T getNotNullValueByKey(String key) {
    return getRequired(key);
  }

  private static Optional<List<Object>> toList(Object value) {
    if (value instanceof List<?> list) {
      return Optional.of(new ArrayList<>(list));
    }
    if (value instanceof CharSequence jsonString) {
      return Json.tryToList(jsonString).toJavaOptional();
    }
    return Optional.empty();
  }

  private static <T> Optional<List<T>> toValueList(Class<T> clazz, List<Object> values) {
    List<T> result = new ArrayList<>(values.size());
    for (Object value : values) {
      Optional<T> convertedValue = toValue(clazz, value);
      if (convertedValue.isEmpty()) {
        return Optional.empty();
      }
      result.add(convertedValue.get());
    }
    return Optional.of(result);
  }

  private static <T> Optional<T> toValue(Class<T> clazz, Object value) {
    if (clazz.isInstance(value)) {
      return Optional.of(clazz.cast(value));
    }
    return toJsonString(value)
        .flatMap(jsonString -> Json.tryToBean(clazz, jsonString).toJavaOptional());
  }

  private static Optional<String> toJsonString(Object value) {
    if (value instanceof CharSequence jsonString) {
      return Optional.of(jsonString.toString());
    }
    return Json.tryToJsonString(value).toJavaOptional();
  }
}
