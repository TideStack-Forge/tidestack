package com.ouroboros.documentmanagement.persistence.entity;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 档案库实体
 */
@Model(label = "档案库", rawName = "doc_library")
public class DocumentLibraryEntity {

  @PrimaryKey(generator = "snowflake")
  @Field(label = "ID")
  private String id;

  @Field(label = "名称", size = 255, nullable = false)
  private String name;

  @Field(label = "显示名称", size = 255)
  private String displayName;

  @Field(label = "描述", size = 1000, nullable = true)
  private String description;

  @Field(label = "拥有者", size = 100, nullable = false)
  private String owner;

  @Field(label = "管理员列表")
  private List<String> adminIds = new ArrayList<>();

  @Field(label = "最大存储空间限制(字节)", nullable = true, defaultValueExpression = "0")
  private Long maxSizeLimit;

  @Field(label = "最大文件数量限制", nullable = true, defaultValueExpression = "0")
  private Long maxFileCountLimit;

  @Field(label = "是否已删除", defaultValueExpression = "false")
  private Boolean deleted = false;

  @Field(label = "是否已禁用", defaultValueExpression = "false")
  private Boolean disabled = false;

  @Field(label = "是否已锁定", defaultValueExpression = "false")
  private Boolean locked = false;

  @Field(label = "是否启用回收站", defaultValueExpression = "true")
  private Boolean trashEnabled = true;

  @Field(label = "创建者", size = 100, nullable = false)
  private String createdBy;

  @Field(label = "创建时间", nullable = false)
  private LocalDateTime createdAt;

  @Field(label = "更新者", size = 100)
  private String updatedBy;

  @Field(label = "更新时间")
  private LocalDateTime updatedAt;

  // Getters and Setters

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public List<String> getAdminIds() {
    return adminIds;
  }

  public void setAdminIds(List<String> adminIds) {
    this.adminIds = adminIds;
  }

  public Long getMaxSizeLimit() {
    return maxSizeLimit;
  }

  public void setMaxSizeLimit(Long maxSizeLimit) {
    this.maxSizeLimit = maxSizeLimit;
  }

  public Long getMaxFileCountLimit() {
    return maxFileCountLimit;
  }

  public void setMaxFileCountLimit(Long maxFileCountLimit) {
    this.maxFileCountLimit = maxFileCountLimit;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
  }

  public Boolean getDisabled() {
    return disabled;
  }

  public void setDisabled(Boolean disabled) {
    this.disabled = disabled;
  }

  public Boolean getLocked() {
    return locked;
  }

  public void setLocked(Boolean locked) {
    this.locked = locked;
  }

  public Boolean getTrashEnabled() {
    return trashEnabled;
  }

  public void setTrashEnabled(Boolean trashEnabled) {
    this.trashEnabled = trashEnabled;
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
