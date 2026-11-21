package com.ouroboros.configuration.metadata;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.util.Maps;

public class ConfigurationGroupMetadata implements Serializable {

  private static final long serialVersionUID = 1L;

  private String path;

  private String title;

  private String icon;

  private Integer order;

  private String description;

  private Map<String, Object> extraProps = new LinkedHashMap<>();

  public String getPath() {
    return path;
  }

  public ConfigurationGroupMetadata setPath(String path) {
    this.path = path;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public ConfigurationGroupMetadata setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getIcon() {
    return icon;
  }

  public ConfigurationGroupMetadata setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public Integer getOrder() {
    return order;
  }

  public ConfigurationGroupMetadata setOrder(Integer order) {
    this.order = order;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ConfigurationGroupMetadata setDescription(String description) {
    this.description = description;
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  public ConfigurationGroupMetadata merge(ConfigurationGroupMetadata another) {
    if (another == null) {
      return this;
    }
    return new Merged(this, another);
  }

  static class Merged extends ConfigurationGroupMetadata {
    @JsonIgnore
    private final ConfigurationGroupMetadata metadata;
    @JsonIgnore
    private final ConfigurationGroupMetadata another;

    Merged(ConfigurationGroupMetadata metadata, ConfigurationGroupMetadata another) {
      this.metadata = metadata;
      this.another = another;
    }

    @Override
    public String getPath() {
      return another.getPath();
    }

    @Override
    public String getTitle() {
      return StringUtils.defaultIfEmpty(another.getTitle(), metadata.getTitle());
    }

    @Override
    public String getIcon() {
      return StringUtils.defaultIfEmpty(another.getIcon(), metadata.getIcon());
    }

    @Override
    public Integer getOrder() {
      return ObjectUtils.defaultIfNull(another.getOrder(), metadata.getOrder());
    }

    @Override
    public String getDescription() {
      return StringUtils.defaultIfEmpty(another.getDescription(), metadata.getDescription());
    }

    @JsonAnyGetter
    @Override
    public Map<String, Object> getExtraProps() {
      return Maps.merge(metadata.getExtraProps(), another.getExtraProps());
    }

    @Override
    public Optional<Object> getExtraProp(String key) {
      Object value = another.getExtraProp(key)
          .orElseGet(() -> metadata.getExtraProp(key).orElse(null));
      return Optional.ofNullable(value);
    }
  }
}
