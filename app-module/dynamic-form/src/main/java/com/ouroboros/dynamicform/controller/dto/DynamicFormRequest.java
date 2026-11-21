package com.ouroboros.dynamicform.controller.dto;

import java.util.List;
import java.util.Map;

import com.ouroboros.dynamicform.model.FormField;

/**
 * 动态表单定义请求DTO
 * 用于add和update操作的请求体
 */
public class DynamicFormRequest {
  private String name;
  private String title;
  private List<FormField> fields;
  private Map<String, Object> options;

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

  public List<FormField> getFields() {
    return fields;
  }

  public void setFields(List<FormField> fields) {
    this.fields = fields;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  public void setOptions(Map<String, Object> options) {
    this.options = options;
  }
}
