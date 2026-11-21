package com.ouroboros.organizationstructure.profile.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;

/**
 * 人员档案模板字段定义
 */
public class PersonnelProfileFieldDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private String label;
  private String type;
  private Integer order;
  private Boolean required;
  private Boolean core;
  private Boolean hidden;
  private Map<String, Object> params;

  public PersonnelProfileFieldDefinition copy() {
    PersonnelProfileFieldDefinition copy = new PersonnelProfileFieldDefinition();
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

  public PersonnelProfileFieldDefinition merge(PersonnelProfileFieldOverride override) {
    PersonnelProfileFieldDefinition copy = copy();
    if (override == null) {
      return copy;
    }

    if (override.getLabel() != null) {
      copy.setLabel(override.getLabel());
    }
    if (override.getType() != null) {
      copy.setType(override.getType());
    }
    if (override.getOrder() != null) {
      copy.setOrder(override.getOrder());
    }
    if (override.getRequired() != null) {
      copy.setRequired(override.getRequired());
    }
    if (override.getCore() != null) {
      copy.setCore(override.getCore());
    }
    if (override.getHidden() != null) {
      copy.setHidden(override.getHidden());
    }
    if (override.getParams() != null && !override.getParams().isEmpty()) {
      Map<String, Object> mergedParams = new LinkedHashMap<String, Object>();
      if (copy.getParams() != null) {
        mergedParams.putAll(copy.getParams());
      }
      mergedParams.putAll(override.getParams());
      copy.setParams(mergedParams);
    }
    return copy;
  }

  public PersonnelProfileFieldDefinition merge(PersonnelProfileFieldDefinition override) {
    PersonnelProfileFieldDefinition copy = copy();
    if (override == null) {
      return copy;
    }

    if (override.getLabel() != null) {
      copy.setLabel(override.getLabel());
    }
    if (override.getType() != null) {
      copy.setType(override.getType());
    }
    if (override.getOrder() != null) {
      copy.setOrder(override.getOrder());
    }
    if (override.getRequired() != null) {
      copy.setRequired(override.getRequired());
    }
    if (override.getCore() != null) {
      copy.setCore(override.getCore());
    }
    if (override.getHidden() != null) {
      copy.setHidden(override.getHidden());
    }
    if (override.getParams() != null && !override.getParams().isEmpty()) {
      Map<String, Object> mergedParams = new LinkedHashMap<String, Object>();
      if (copy.getParams() != null) {
        mergedParams.putAll(copy.getParams());
      }
      mergedParams.putAll(override.getParams());
      copy.setParams(mergedParams);
    }
    return copy;
  }

  public FormItemNode toEditNode() {
    FormItemNode node = new FormItemNode();
    node.setName(name);
    node.setLabel(label);
    node.setType(type);
    if (required != null) {
      node.setRequired(required);
    }
    if (hidden != null) {
      node.setHidden(hidden);
    }
    if (params != null) {
      params.forEach(node::setProp);
    }
    return node;
  }

  public SchemaNode toDetailNode() {
    SchemaNode node = new SchemaNode();
    node.setType(resolveDetailType());
    node.setProp("name", name);
    node.setProp("label", label);
    if (params != null) {
      params.forEach(node::setProp);
    }
    return node;
  }

  private String resolveDetailType() {
    Object displayType = params == null ? null : params.get("displayType");
    if (displayType != null && String.valueOf(displayType).trim().length() > 0) {
      return String.valueOf(displayType);
    }
    return "static";
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
