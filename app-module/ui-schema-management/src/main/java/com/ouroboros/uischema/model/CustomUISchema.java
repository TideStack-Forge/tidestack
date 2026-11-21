package com.ouroboros.uischema.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.util.Maps;

@Model(rawName = "t_custom_ui_schema", label = "自定义UISchema")
public class CustomUISchema implements Serializable {
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;
  @Field(size = 200)
  private String path;
  @Field(type = "Map")
  private Map<String, Object> schema;
  @Field(defaultValueExpression = "''")
  private String description;
  @Field
  private LocalDateTime createdAt;
  @Field(nullable = true)
  private LocalDateTime updatedAt;
  @Field(size = 50)
  private String createdBy;
  @Field(size = 50, nullable = true)
  private String updatedBy;
  @Field(type = "Boolean", defaultValueExpression = "true")
  private Boolean enabled;

  public CustomUISchema() {
  }

  public CustomUISchema(CustomUISchema schema) {
    setId(schema.getId());
    setPath(schema.getPath());
    if (schema.getSchema() != null) {
      setSchema(Maps.deepClone(schema.getSchema()));
    }
    setDescription(schema.getDescription());
    setEnabled(schema.getEnabled());
    setCreatedBy(schema.getCreatedBy());
    setCreatedAt(schema.getCreatedAt());
    setUpdatedBy(schema.getUpdatedBy());
    setUpdatedAt(schema.getUpdatedAt());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Map<String, Object> getSchema() {
    return schema;
  }

  public void setSchema(Map<String, Object> schema) {
    this.schema = schema;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Boolean getEnabled() {
    return Optional.ofNullable(enabled).orElse(true);
  }

  public CustomUISchema setEnabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public CustomUISchema setDescription(String description) {
    this.description = description;
    return this;
  }
}
