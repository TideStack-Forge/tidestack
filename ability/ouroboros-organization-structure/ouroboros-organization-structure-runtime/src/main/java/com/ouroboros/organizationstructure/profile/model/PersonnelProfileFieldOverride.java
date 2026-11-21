package com.ouroboros.organizationstructure.profile.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人员档案字段运行时覆盖配置
 */
public class PersonnelProfileFieldOverride implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private String label;
  private String type;
  private Integer order;
  private Boolean required;
  private Boolean core;
  private Boolean hidden;
  private Map<String, Object> params;

  public PersonnelProfileFieldOverride copy() {
    PersonnelProfileFieldOverride copy = new PersonnelProfileFieldOverride();
    copy.setName(name);
    copy.setLabel(label);
    copy.setType(type);
    copy.setOrder(order);
    copy.setRequired(required);
    copy.setCore(core);
    copy.setHidden(hidden);
    if (params != null) {
      copy.setParams(new LinkedHashMap<String, Object>(params));
    }
    return copy;
  }

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

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public Boolean getRequired() {
    return required;
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }

  public Boolean getCore() {
    return core;
  }

  public void setCore(Boolean core) {
    this.core = core;
  }

  public Boolean getHidden() {
    return hidden;
  }

  public void setHidden(Boolean hidden) {
    this.hidden = hidden;
  }

  public Map<String, Object> getParams() {
    return params == null ? Collections.<String, Object>emptyMap() : params;
  }

  public void setParams(Map<String, Object> params) {
    this.params = params;
  }
}
