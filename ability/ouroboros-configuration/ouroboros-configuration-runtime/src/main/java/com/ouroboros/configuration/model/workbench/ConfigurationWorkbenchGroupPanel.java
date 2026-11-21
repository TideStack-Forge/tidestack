package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ConfigurationWorkbenchGroupPanel implements Serializable {

  private static final long serialVersionUID = 1L;

  private String path;

  private String title;

  private String icon;

  private String description;

  private Boolean virtualGroup;

  private List<ConfigurationWorkbenchBreadcrumb> breadcrumbs = new ArrayList<>();

  private List<ConfigurationWorkbenchGroupSummary> childGroups = new ArrayList<>();

  private List<ConfigurationWorkbenchItemSummary> items = new ArrayList<>();

  private ConfigurationWorkbenchStatistics statistics;

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

  public Boolean getVirtualGroup() {
    return virtualGroup;
  }

  public void setVirtualGroup(Boolean virtualGroup) {
    this.virtualGroup = virtualGroup;
  }

  public List<ConfigurationWorkbenchBreadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void setBreadcrumbs(List<ConfigurationWorkbenchBreadcrumb> breadcrumbs) {
    this.breadcrumbs = breadcrumbs;
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

  public ConfigurationWorkbenchStatistics getStatistics() {
    return statistics;
  }

  public void setStatistics(ConfigurationWorkbenchStatistics statistics) {
    this.statistics = statistics;
  }
}
