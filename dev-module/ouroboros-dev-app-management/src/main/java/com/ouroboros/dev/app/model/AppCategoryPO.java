package com.ouroboros.dev.app.model;

import java.util.ArrayList;
import java.util.List;

public class AppCategoryPO {
  String name = "";
  String icon = "";
  Integer order = 0;
  List<AppCategoryPO> children = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public List<AppCategoryPO> getChildren() {
    return children;
  }

  public void setChildren(List<AppCategoryPO> children) {
    if (children == null) {
      return;
    }
    this.children = children;
  }
}
