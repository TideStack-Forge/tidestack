package com.ouroboros.i18n.dev.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;

@JsonIgnoreProperties(ignoreUnknown = true)
public class I18nLocaleModel implements I18nLocale {
  private String locale;
  private String name;
  private String version;
  private Boolean enabled;
  private Boolean isDefault;
  private Map<String, Map<String, String>> messages;
  private Long timestamp;
  private TextDirection direction;

  @Override
  public String getLocale() {
    return locale;
  }

  public I18nLocaleModel setLocale(String locale) {
    this.locale = locale;
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  public I18nLocaleModel setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getVersion() {
    return version;
  }

  public I18nLocaleModel setVersion(String version) {
    this.version = version;
    return this;
  }

  @Override
  public Boolean getEnabled() {
    return enabled;
  }

  public I18nLocaleModel setEnabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  @Override
  public Boolean getIsDefault() {
    return isDefault;
  }

  public I18nLocaleModel setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
    return this;
  }

  @Override
  public Map<String, Map<String, String>> getMessages() {
    return messages;
  }

  public I18nLocaleModel setMessages(Map<String, Map<String, String>> messages) {
    this.messages = messages;
    return this;
  }

  @Override
  public TextDirection getDirection() {
    return direction != null ? direction : TextDirection.LTR;
  }

  public I18nLocaleModel setDirection(TextDirection direction) {
    this.direction = direction;
    return this;
  }

  @Override
  public Long getTimestamp() {
    return timestamp;
  }

  public I18nLocaleModel setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public Integer getMessageCount() {
    if (messages == null) {
      return 0;
    }
    return messages.values().stream()
        .mapToInt(entries -> entries == null ? 0 : entries.size())
        .sum();
  }
}
