package com.ouroboros.businessmodel.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public class BusinessModelFieldMeta extends MetaWithExtraProps {
  private String name;
  private String title;
  private String description;
  private String type;
  private String defaultValue;
  private Boolean isRequired = false;
  private Boolean isPrimaryKey = false;
  private Boolean isDisplayField = false;
  private Boolean isUnique = false;
  private List<String> validators = new ArrayList<>();
  private Map<String, Object> customInput;
  private Map<String, Object> customDisplay;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public Boolean getIsRequired() {
    return isRequired;
  }

  public void setIsRequired(Boolean required) {
    isRequired = required;
  }

  public Boolean getIsPrimaryKey() {
    return isPrimaryKey;
  }

  public void setIsPrimaryKey(Boolean primaryKey) {
    isPrimaryKey = primaryKey;
  }

  public Boolean getIsDisplayField() {
    return isDisplayField;
  }

  public void setIsDisplayField(Boolean isDisplayField) {
    this.isDisplayField = isDisplayField;
  }

  public Boolean getIsUnique() {
    return getIsPrimaryKey() || isUnique;
  }

  public void setIsUnique(Boolean isUnique) {
    this.isUnique = isUnique;
  }

  public List<String> getValidators() {
    return validators;
  }

  public void setValidators(List<String> validators) {
    this.validators = validators;
  }

  public void addValidator(String validator) {
    this.validators.add(validator);
  }

  public Map<String, Object> getCustomInput() {
    return customInput;
  }

  public void setCustomInput(Map<String, Object> customInput) {
    this.customInput = customInput;
  }

  public Map<String, Object> getCustomDisplay() {
    return customDisplay;
  }

  public void setCustomDisplay(Map<String, Object> customDisplay) {
    this.customDisplay = customDisplay;
  }
}
