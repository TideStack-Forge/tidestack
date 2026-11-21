package com.ouroboros.i18n.runtime.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;

/**
 * 国际化语言环境实现类
 */
public class I18nLocaleImpl implements I18nLocale {

  private final String locale;
  private final String name;
  private final String version;
  private final Boolean enabled;
  private final Boolean isDefault;
  private final Map<String, Map<String, String>> messages;
  private final Long timestamp;
  private final TextDirection direction;

  @JsonCreator
  public I18nLocaleImpl(
      @JsonProperty("locale") String locale,
      @JsonProperty("name") String name,
      @JsonProperty("version") String version,
      @JsonProperty("enabled") Boolean enabled,
      @JsonProperty("isDefault") Boolean isDefault,
      @JsonProperty("messages") Map<String, Map<String, String>> messages,
      @JsonProperty("timestamp") Long timestamp,
      @JsonProperty("direction") TextDirection direction) {
    this.locale = locale;
    this.name = name;
    this.version = version;
    this.enabled = enabled;
    this.isDefault = isDefault;
    this.messages = messages;
    this.timestamp = timestamp;
    this.direction = direction;
  }

  @Override
  public String getLocale() {
    return locale;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public Boolean getEnabled() {
    return enabled;
  }

  @Override
  public Boolean getIsDefault() {
    return isDefault;
  }

  @Override
  public Map<String, Map<String, String>> getMessages() {
    return messages;
  }

  @Override
  public Long getTimestamp() {
    return timestamp;
  }

  @Override
  public TextDirection getDirection() {
    return direction != null ? direction : TextDirection.LTR;
  }
}
