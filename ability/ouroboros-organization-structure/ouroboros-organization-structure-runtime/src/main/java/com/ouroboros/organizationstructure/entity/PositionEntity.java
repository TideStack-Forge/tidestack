package com.ouroboros.organizationstructure.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

/**
 * 岗位存储实体 - 纯数据库映射
 *
 * @author songmingxu
 */
@Model(rawName = "t_sys_position", label = "岗位表")
@AuditFields
@LogicalDelete
public class PositionEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String positionName;

  @Field(nullable = true)
  private String positionKey;

  @Field(nullable = true)
  private String level;

  @Field(nullable = true)
  private String description;

  @Field(nullable = true)
  private Integer headcount;

  @Field(nullable = true)
  private String parent;

  @Field(nullable = true)
  private String leaderEmployee;

  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private String updatedBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private String deletedBy;

  @Field(nullable = true)
  private LocalDateTime deletedAt;

  @JsonProperty("isDeleted")
  @Field(nullable = true, type = "Boolean")
  private boolean isDeleted;

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPositionName() {
    return positionName;
  }

  public String getPositionKey() {
    return positionKey;
  }

  public void setPositionKey(String positionKey) {
    this.positionKey = positionKey;
  }

  public void setPositionName(String positionName) {
    this.positionName = positionName;
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

  public Integer getHeadcount() {
    return headcount;
  }

  public void setHeadcount(Integer headcount) {
    this.headcount = headcount;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public String getLeaderEmployee() {
    return leaderEmployee;
  }

  public void setLeaderEmployee(String leaderEmployee) {
    this.leaderEmployee = leaderEmployee;
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

  @JsonProperty("isDeleted")
  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }
}
