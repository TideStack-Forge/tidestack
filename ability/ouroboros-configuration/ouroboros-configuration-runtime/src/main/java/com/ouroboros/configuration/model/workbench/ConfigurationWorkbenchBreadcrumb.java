package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;

public class ConfigurationWorkbenchBreadcrumb implements Serializable {

  private static final long serialVersionUID = 1L;

  private String label;

  private String type;

  private String path;

  private String key;

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
}
