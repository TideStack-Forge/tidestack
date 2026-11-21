package com.ouroboros.organizationstructure;

import java.io.Serializable;

public class PositionDefinition implements Serializable {
  private static final long serialVersionUID = 1L;

  private String key;
  private String name;
  private String level;
  private String description;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
