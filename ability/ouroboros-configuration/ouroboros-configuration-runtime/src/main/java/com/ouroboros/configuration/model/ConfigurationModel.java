package com.ouroboros.configuration.model;

import java.io.Serializable;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;

/**
 * @author liansz
 */
public class ConfigurationModel implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 唯一键
   */
  private String key;

  /**
   * 值
   */
  private Object value;

  /**
   * 元数据模型
   */
  private ConfigurationMetadata metadata;

  /**
   * 值模型
   */
  private ConfigurationValueModel configValue;

  /** Effective revision token, including a deterministic token for an unpersisted default. */
  private String revision;

  public ConfigurationModel(String key) {
    this.key = key;
  }

  public ConfigurationModel(String key, Object value, ConfigurationMetadata metadataModel, ConfigurationValueModel configValue) {
    this.key = key;
    this.value = value;
    this.metadata = metadataModel;
    this.configValue = configValue;
  }

  public ConfigurationModel(String key, Object value, ConfigurationMetadata metadataModel,
                            ConfigurationValueModel configValue, String revision) {
    this(key, value, metadataModel, configValue);
    this.revision = revision;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public ConfigurationMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(ConfigurationMetadata metadata) {
    this.metadata = metadata;
  }

  public ConfigurationValueModel getConfigValue() {
    return configValue;
  }

  public void setConfigValue(ConfigurationValueModel configValue) {
    this.configValue = configValue;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }
}
