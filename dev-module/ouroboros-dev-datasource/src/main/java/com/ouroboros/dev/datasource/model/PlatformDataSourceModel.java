package com.ouroboros.dev.datasource.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

/**
 * @author lianshuze
 **/
@Model(rawName = "t_platform_datasource", label = "平台数据源")
@AuditFields
@LogicalDelete
public class PlatformDataSourceModel {

  /**
   * 数据源ID
   */
  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 数据源名称（唯一标识）
   */
  @Field
  private String name;

  /**
   * 数据源标题
   */
  @Field
  private String title;

  /**
   * 数据源定义
   */
  @Field
  private Map<String, Map<String, Object>> definition;

  /**
   * 创建者
   */
  @Field(nullable = true)
  private String createdBy;

  /**
   * 创建时间
   */
  @Field(nullable = true)
  private LocalDateTime createdAt;

  /**
   * 更新者
   */
  @Field(nullable = true)
  private String updatedBy;

  /**
   * 更新时间
   */
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  /**
   * 删除者
   */
  @Field(nullable = true)
  private String deletedBy;

  /**
   * 删除时间
   */
  @Field(nullable = true)
  private LocalDateTime deletedAt;

  /**
   * 软删除状态
   */
  @JsonProperty("isDeleted")
  @Field(nullable = true, type = "Boolean")
  private boolean isDeleted;

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Map<String, Map<String, Object>> getDefinition() {
    return definition;
  }

  public void setDefinition(Map<String, Map<String, Object>> definition) {
    this.definition = definition;
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

  public String getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(String deletedBy) {
    this.deletedBy = deletedBy;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }
}
