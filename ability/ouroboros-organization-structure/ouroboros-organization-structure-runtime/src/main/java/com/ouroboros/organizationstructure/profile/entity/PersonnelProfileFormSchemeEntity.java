package com.ouroboros.organizationstructure.profile.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;

@Model(rawName = "t_sys_personnel_profile_form_scheme", label = "人员档案表单方案")
@AuditFields
public class PersonnelProfileFormSchemeEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @PrimaryKey(generator = "snowflake")
  @Field
  private String id;

  @Field(nullable = true)
  private String appName;

  @Field
  private String name;

  @Field(nullable = true)
  private String description;

  @Field(type = "Boolean")
  private Boolean enabled;

  @Field(type = "Boolean")
  private Boolean isDefault;

  @Field(type = "Integer", nullable = true)
  private Integer priority;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> scope;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> matchingRules;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> createSchema;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> editSchema;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> detailSchema;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> legacySource;

  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private String updatedBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public Map<String, Object> getScope() {
    return scope == null ? Collections.<String, Object>emptyMap() : scope;
  }

  public void setScope(Map<String, Object> scope) {
    this.scope = scope == null ? null : new LinkedHashMap<String, Object>(scope);
  }

  public Map<String, Object> getMatchingRules() {
    return matchingRules == null ? Collections.<String, Object>emptyMap() : matchingRules;
  }

  public void setMatchingRules(Map<String, Object> matchingRules) {
    this.matchingRules = matchingRules == null ? null : new LinkedHashMap<String, Object>(matchingRules);
  }

  public Map<String, Object> getCreateSchema() {
    return createSchema == null ? Collections.<String, Object>emptyMap() : createSchema;
  }

  public void setCreateSchema(Map<String, Object> createSchema) {
    this.createSchema = createSchema == null ? null : new LinkedHashMap<String, Object>(createSchema);
  }

  public Map<String, Object> getEditSchema() {
    return editSchema == null ? Collections.<String, Object>emptyMap() : editSchema;
  }

  public void setEditSchema(Map<String, Object> editSchema) {
    this.editSchema = editSchema == null ? null : new LinkedHashMap<String, Object>(editSchema);
  }

  public Map<String, Object> getDetailSchema() {
    return detailSchema == null ? Collections.<String, Object>emptyMap() : detailSchema;
  }

  public void setDetailSchema(Map<String, Object> detailSchema) {
    this.detailSchema = detailSchema == null ? null : new LinkedHashMap<String, Object>(detailSchema);
  }

  public Map<String, Object> getLegacySource() {
    return legacySource == null ? Collections.<String, Object>emptyMap() : legacySource;
  }

  public void setLegacySource(Map<String, Object> legacySource) {
    this.legacySource = legacySource == null ? null : new LinkedHashMap<String, Object>(legacySource);
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
