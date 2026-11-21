package com.ouroboros.widget.dashboard.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(fullName = "UserDashboardTemplate", label = "用户仪表盘模板", rawName = "sys_user_dashboard_template")
public class UserDashboardTemplateModel {
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;
  @Field
  private String name;
  @Field(nullable = true)
  private String description = "";
  @Field
  private Boolean isDefault = false;
  @Field
  private Map<String, Object> template;
  @Field
  private String createdBy;
  @Field
  private LocalDateTime createdAt;
  @Field(nullable = true)
  private String updatedBy;
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  public String getId() {
    return id;
  }

  public UserDashboardTemplateModel setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public UserDashboardTemplateModel setName(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public UserDashboardTemplateModel setDescription(String description) {
    this.description = description;
    return this;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public UserDashboardTemplateModel setIsDefault(Boolean aDefault) {
    isDefault = aDefault;
    return this;
  }

  public Map<String, Object> getTemplate() {
    return template;
  }

  public UserDashboardTemplateModel setTemplate(Map<String, Object> template) {
    this.template = template;
    return this;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public UserDashboardTemplateModel setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public UserDashboardTemplateModel setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public UserDashboardTemplateModel setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public UserDashboardTemplateModel setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
