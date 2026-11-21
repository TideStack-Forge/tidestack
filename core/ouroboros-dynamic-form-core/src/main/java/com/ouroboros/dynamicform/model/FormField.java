package com.ouroboros.dynamicform.model;

import java.util.Map;

/**
 * 表单字段
 */
public class FormField {
  String name;
  String label;
  String type;
  Boolean required = false;
  Map<String, Object> params;
  Boolean deployed = false;
  Boolean filterable = false;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getRequired() {
    return required;
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }

  public Boolean getDeployed() {
    return deployed;
  }

  public void setDeployed(Boolean deployed) {
    this.deployed = deployed;
  }

  public Boolean getFilterable() {
    return filterable;
  }

  public void setFilterable(Boolean filterable) {
    this.filterable = filterable;
  }
}
