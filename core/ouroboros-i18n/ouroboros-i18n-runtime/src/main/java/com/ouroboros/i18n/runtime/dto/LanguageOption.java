package com.ouroboros.i18n.runtime.dto;

/**
 * 语言选项
 */
public class LanguageOption {
  private String value;
  private String label;
  private String version;
  private Boolean isDefault;

  public LanguageOption() {
  }

  public LanguageOption(String value, String label) {
    this.value = value;
    this.label = label;
  }

  public LanguageOption(String value, String label, String version) {
    this.value = value;
    this.label = label;
    this.version = version;
  }

  public LanguageOption(String value, String label, String version, Boolean isDefault) {
    this.value = value;
    this.label = label;
    this.version = version;
    this.isDefault = isDefault;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }
}
