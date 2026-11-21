package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationWorkbenchGroupSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  private String path;

  private String title;

  private String icon;

  private String description;

  private Integer order;

  private Integer totalItems;

  private Integer obsoleteItems;

  private Integer readonlyItems;

  private List<ConfigurationWorkbenchGroupSummary> childGroups = new ArrayList<>();

  private List<ConfigurationWorkbenchItemSummary> items = new ArrayList<>();

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
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

  public Integer getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(Integer totalItems) {
    this.totalItems = totalItems;
  }

  public Integer getObsoleteItems() {
    return obsoleteItems;
  }

  public void setObsoleteItems(Integer obsoleteItems) {
    this.obsoleteItems = obsoleteItems;
  }

  public Integer getReadonlyItems() {
    return readonlyItems;
  }

  public void setReadonlyItems(Integer readonlyItems) {
    this.readonlyItems = readonlyItems;
  }

  public List<ConfigurationWorkbenchGroupSummary> getChildGroups() {
    return childGroups;
  }

  public void setChildGroups(List<ConfigurationWorkbenchGroupSummary> childGroups) {
    this.childGroups = childGroups;
  }

  public List<ConfigurationWorkbenchItemSummary> getItems() {
    return items;
  }

  public void setItems(List<ConfigurationWorkbenchItemSummary> items) {
    this.items = items;
  }
}
