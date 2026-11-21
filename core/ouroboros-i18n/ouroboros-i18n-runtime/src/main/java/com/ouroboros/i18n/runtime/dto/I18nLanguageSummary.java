package com.ouroboros.i18n.runtime.dto;

import java.time.LocalDateTime;

/**
 * 语言摘要信息
 */
public class I18nLanguageSummary {
  private String locale;
  private String name;
  private Boolean enabled;
  private Boolean isDefault;
  private Integer messageCount;
  private String direction;
  private String version;
  private LocalDateTime updatedAt;
  private Boolean hasDbRecord;

  public I18nLanguageSummary() {
  }

  public I18nLanguageSummary(String locale, String name, Boolean enabled,
                             Boolean isDefault, Integer messageCount,
                             String direction, String version, LocalDateTime updatedAt,
                             Boolean hasDbRecord) {
    this.locale = locale;
    this.name = name;
    this.enabled = enabled;
    this.isDefault = isDefault;
    this.messageCount = messageCount;
    this.direction = direction;
    this.version = version;
    this.updatedAt = updatedAt;
    this.hasDbRecord = hasDbRecord;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Integer getMessageCount() {
    return messageCount;
  }

  public void setMessageCount(Integer messageCount) {
    this.messageCount = messageCount;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Boolean getHasDbRecord() {
    return hasDbRecord;
  }

  public void setHasDbRecord(Boolean hasDbRecord) {
    this.hasDbRecord = hasDbRecord;
  }
}
