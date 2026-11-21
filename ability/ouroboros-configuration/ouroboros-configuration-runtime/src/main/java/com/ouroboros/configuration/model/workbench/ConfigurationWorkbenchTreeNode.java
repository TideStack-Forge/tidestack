package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationWorkbenchTreeNode implements Serializable {

  private static final long serialVersionUID = 1L;

  private String id;

  private String type;

  private String path;

  private String key;

  private String label;

  private String icon;

  private String description;

  private Integer order;

  private Boolean obsolete;

  private Boolean modifiable;

  private Boolean hasChildren;

  private List<ConfigurationWorkbenchTreeNode> children = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public Boolean getObsolete() {
    return obsolete;
  }

  public void setObsolete(Boolean obsolete) {
    this.obsolete = obsolete;
  }

  public Boolean getModifiable() {
    return modifiable;
  }

  public void setModifiable(Boolean modifiable) {
    this.modifiable = modifiable;
  }

  public Boolean getHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(Boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  public List<ConfigurationWorkbenchTreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<ConfigurationWorkbenchTreeNode> children) {
    this.children = children;
  }
}
