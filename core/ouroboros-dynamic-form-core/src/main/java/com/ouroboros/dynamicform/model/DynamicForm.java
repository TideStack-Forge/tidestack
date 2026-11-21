package com.ouroboros.dynamicform.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.annotation.AutoIncrement;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/**
 * 动态表单定义
 */
@Model(rawName = "t_dynamic_form", label = "动态表单定义")
public class DynamicForm {
  @Field
  @PrimaryKey
  @AutoIncrement
  private Integer id;
  @Field(size = 50)
  private String name;
  @Field(size = 200)
  private String title;
  @Field(type = "List")
  private List<FormField> fields;
  @Field(type = "Map", nullable = true)
  private Map<String, Object> options;
  @Field(size = 20)
  private String status;
  @Field
  private LocalDateTime createdAt;
  @Field
  private LocalDateTime updatedAt;
  @Field(size = 50)
  private String createdBy;
  @Field(size = 50)
  private String updatedBy;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<FormField> getFields() {
    return Optional.ofNullable(fields).orElseGet(Collections::emptyList);
  }

  public void setFields(List<FormField> fields) {
    this.fields = fields;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  public Map<String, Object> getOptions() {
    return options;
  }

  public void setOptions(Map<String, Object> options) {
    this.options = options;
  }
}
