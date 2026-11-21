package com.ouroboros.dev.componentlib.dto;

import java.util.Collections;
import java.util.List;

public class ComponentDefineDTO {
  private String type;
  private String name;
  private String label;
  private String description;
  private List<String> tags;

  public String getType() {
    return type;
  }

  public ComponentDefineDTO setType(String type) {
    this.type = type;
    return this;
  }

  public String getName() {
    return name;
  }

  public ComponentDefineDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getLabel() {
    return label;
  }

  public ComponentDefineDTO setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ComponentDefineDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public List<String> getTags() {
    return tags == null ? Collections.emptyList() : tags;
  }

  public ComponentDefineDTO setTags(List<String> tags) {
    this.tags = tags;
    return this;
  }
}
