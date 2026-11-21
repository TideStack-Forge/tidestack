package com.ouroboros.widget.manage.pojo.dto;

import com.ouroboros.widget.WidgetDefinition;

public class WidgetSchemaManageDTO {
  private String id;
  private Boolean isFromWidgetSchema;
  private WidgetDefinition definition;
  private String createdAt;
  private String updatedAt;

  public String getId() {
    return id;
  }

  public WidgetSchemaManageDTO setId(String id) {
    this.id = id;
    return this;
  }

  public Boolean getFromWidgetSchema() {
    return isFromWidgetSchema;
  }

  public WidgetSchemaManageDTO setFromWidgetSchema(Boolean fromWidgetSchema) {
    isFromWidgetSchema = fromWidgetSchema;
    return this;
  }

  public WidgetDefinition getDefinition() {
    return definition;
  }

  public WidgetSchemaManageDTO setDefinition(WidgetDefinition definition) {
    this.definition = definition;
    return this;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public WidgetSchemaManageDTO setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public WidgetSchemaManageDTO setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
