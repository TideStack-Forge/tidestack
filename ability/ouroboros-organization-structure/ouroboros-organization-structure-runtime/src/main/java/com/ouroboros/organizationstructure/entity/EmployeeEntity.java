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
 * 员工存储实体 - 纯数据库映射，不包含业务逻辑
 *
 * @author liansz
 */
@Model(rawName = "t_sys_employee", label = "员工表")
@AuditFields
@LogicalDelete
public class EmployeeEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String name;

  @Field(nullable = true)
  private String gender;

  @Field(nullable = true)
  private String mobile;

  @Field(nullable = true)
  private String email;

  @Field(nullable = true)
  private String birthday;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String mobile) {
    this.mobile = mobile;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getBirthday() {
    return birthday;
  }

  public void setBirthday(String birthday) {
    this.birthday = birthday;
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
