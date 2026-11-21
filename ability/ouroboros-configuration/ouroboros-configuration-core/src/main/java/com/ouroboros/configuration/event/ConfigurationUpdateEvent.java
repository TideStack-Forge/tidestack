package com.ouroboros.configuration.event;

/**
 * @author liansz
 */
public class ConfigurationUpdateEvent<T> {

  /**
   * 配置唯一键
   */
  private final String key;

  /**
   * 配置值
   */
  private final T value;

  public ConfigurationUpdateEvent(String key, T value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public T getValue() {
    return value;
  }
}
