package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;

public class ConfigurationWorkbenchStatistics implements Serializable {

  private static final long serialVersionUID = 1L;

  private Integer totalItems;

  private Integer modifiableItems;

  private Integer readonlyItems;

  private Integer obsoleteItems;

  private Integer childGroups;

  public Integer getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(Integer totalItems) {
    this.totalItems = totalItems;
  }

  public Integer getModifiableItems() {
    return modifiableItems;
  }

  public void setModifiableItems(Integer modifiableItems) {
    this.modifiableItems = modifiableItems;
  }

  public Integer getReadonlyItems() {
    return readonlyItems;
  }

  public void setReadonlyItems(Integer readonlyItems) {
    this.readonlyItems = readonlyItems;
  }

  public Integer getObsoleteItems() {
    return obsoleteItems;
  }

  public void setObsoleteItems(Integer obsoleteItems) {
    this.obsoleteItems = obsoleteItems;
  }

  public Integer getChildGroups() {
    return childGroups;
  }

  public void setChildGroups(Integer childGroups) {
    this.childGroups = childGroups;
  }
}
