package com.ouroboros.security.rbac.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

@Model(rawName = "t_sys_user", label = "用户表")
@AuditFields
@LogicalDelete
@com.ouroboros.data.annotation.DataPlugin(name = "AccountProtection")
public class UserEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @com.ouroboros.data.annotation.Unique(scope = com.ouroboros.data.model.UniquenessScope.ALL_RECORDS)
  @Field(extraProps = {"requireAfterBackfill", "true"})
  private String publicSubjectId;

  @Field(rawName = "display_name", label = "昵称", nullable = true)
  private String nickname;

  @Field(nullable = true)
  private String email;

  @Field(extraProps = {"requireAfterBackfill", "true"})
  private Boolean emailVerified;

  @Field(nullable = true)
  private String phoneNumber;

  @Field(extraProps = {"requireAfterBackfill", "true"})
  private Boolean phoneNumberVerified;

  @Field(type = "Map", nullable = true)
  private java.util.Map<String, Object> profileValues;

  @Field(extraProps = {"requireAfterBackfill", "true"})
  private Long profileRevision;

  @Field
  @PrimaryKey
  private String userIdentity;

  @Field
  private String password;

  @Field(nullable = true)
  private String avatar;

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

  @JsonProperty("isEnabled")
  @Field(nullable = true, type = "Boolean")
  private Boolean isEnabled = true;

  public UserEntity() {
  }

  public UserEntity(UserEntity entity) {
    setUserIdentity(entity.getUserIdentity());
    setPublicSubjectId(entity.getPublicSubjectId());
    setNickname(entity.getNickname());
    setEmail(entity.getEmail());
    setEmailVerified(entity.getEmailVerified());
    setPhoneNumber(entity.getPhoneNumber());
    setPhoneNumberVerified(entity.getPhoneNumberVerified());
    setProfileValues(entity.getProfileValues());
    setProfileRevision(entity.getProfileRevision());
    setPassword(entity.getPassword());
    setAvatar(entity.getAvatar());
    setCreatedBy(entity.getCreatedBy());
    setCreatedAt(entity.getCreatedAt());
    setUpdatedBy(entity.getUpdatedBy());
    setUpdatedAt(entity.getUpdatedAt());
    setDeletedBy(entity.getDeletedBy());
    setDeletedAt(entity.getDeletedAt());
    setDeleted(entity.isDeleted());
    setEnabled(entity.isEnabled());
  }

  public String getUserIdentity() {
    return userIdentity;
  }

  public void setUserIdentity(String userIdentity) {
    this.userIdentity = userIdentity;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
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

  @JsonProperty("isEnabled")
  public boolean isEnabled() {
    return isEnabled == null || isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public String getPublicSubjectId() { return publicSubjectId; }
  public void setPublicSubjectId(String value) { this.publicSubjectId = value; }

  public String getNickname() { return nickname; }
  public void setNickname(String value) { this.nickname = value; }

  public String getEmail() { return email; }
  public void setEmail(String value) { this.email = value; }

  public Boolean getEmailVerified() { return emailVerified; }
  public void setEmailVerified(Boolean value) { this.emailVerified = value; }

  public String getPhoneNumber() { return phoneNumber; }
  public void setPhoneNumber(String value) { this.phoneNumber = value; }

  public Boolean getPhoneNumberVerified() { return phoneNumberVerified; }
  public void setPhoneNumberVerified(Boolean value) { this.phoneNumberVerified = value; }

  public java.util.Map<String, Object> getProfileValues() { return profileValues; }
  public void setProfileValues(java.util.Map<String, Object> value) { this.profileValues = value == null ? null : new java.util.LinkedHashMap<>(value); }

  public Long getProfileRevision() { return profileRevision; }
  public void setProfileRevision(Long value) { this.profileRevision = value; }
}
