package com.ouroboros.dev.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AppCategoryDTO {
  String parentPath = "";
  String name = "";
  String fullPath = "";
  String icon = "";
  Integer order = 0;
  List<AppCategoryDTO> children = new ArrayList<>();

  public String getParentPath() {
    return parentPath;
  }

  public void setParentPath(String parentPath) {
    this.parentPath = parentPath;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFullPath() {
    return fullPath;
  }

  public void setFullPath(String fullPath) {
    this.fullPath = fullPath;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public List<AppCategoryDTO> getChildren() {
    return children;
  }

  public void setChildren(List<AppCategoryDTO> children) {
    this.children = children;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public AppCategoryDTO clone() {
    AppCategoryDTO clone = new AppCategoryDTO();
    clone.setName(this.getName());
    clone.setFullPath(this.getFullPath());
    clone.setIcon(this.getIcon());
    clone.setOrder(this.getOrder());
    clone.setParentPath(this.getParentPath());
    clone.setChildren(this.getChildren() == null
        ? null
        : this.getChildren().stream().map(AppCategoryDTO::clone).collect(Collectors.toList()));
    return clone;
  }
}
