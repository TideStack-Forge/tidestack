package com.ouroboros.errorcode.model;

public class ErrorCodeDefinition {
  private Integer code;
  private String title;
  private String defaultMessageTemplate;
  private String bizCode;
  private String category;
  private String description;
  private Boolean enabled = true;

  public Integer getCode() {
    return code;
  }

  public ErrorCodeDefinition setCode(Integer code) {
    this.code = code;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public ErrorCodeDefinition setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getDefaultMessageTemplate() {
    return defaultMessageTemplate;
  }

  public ErrorCodeDefinition setDefaultMessageTemplate(String defaultMessageTemplate) {
    this.defaultMessageTemplate = defaultMessageTemplate;
    return this;
  }

  public String getBizCode() {
    return bizCode;
  }

  public ErrorCodeDefinition setBizCode(String bizCode) {
    this.bizCode = bizCode;
    return this;
  }

  public String getCategory() {
    return category;
  }

  public ErrorCodeDefinition setCategory(String category) {
    this.category = category;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ErrorCodeDefinition setDescription(String description) {
    this.description = description;
    return this;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public boolean isEnabled() {
    return !Boolean.FALSE.equals(enabled);
  }

  public ErrorCodeDefinition setEnabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }
}
