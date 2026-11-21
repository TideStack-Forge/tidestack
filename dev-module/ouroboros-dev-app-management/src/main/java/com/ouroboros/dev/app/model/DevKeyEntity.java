package com.ouroboros.dev.app.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_dev_app_dev_key", label = "开发应用 IDE 接入密钥表")
public class DevKeyEntity implements Serializable {
  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String appName;

  @Field
  private String userIdentity;

  @Field(size = 128)
  private String devKey;

  @Field
  private String runtimeStrategy;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private LocalDateTime expiresAt;

  @Field(nullable = true)
  private String description;

  @Field(nullable = true)
  private LocalDateTime lastRotatedAt;

  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private String updatedBy;

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

  public String getUserIdentity() {
    return userIdentity;
  }

  public void setUserIdentity(String userIdentity) {
    this.userIdentity = userIdentity;
  }

  public String getDevKey() {
    return devKey;
  }

  public void setDevKey(String devKey) {
    this.devKey = devKey;
  }

  public String getRuntimeStrategy() {
    return runtimeStrategy;
  }

  public void setRuntimeStrategy(String runtimeStrategy) {
    this.runtimeStrategy = runtimeStrategy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getLastRotatedAt() {
    return lastRotatedAt;
  }

  public void setLastRotatedAt(LocalDateTime lastRotatedAt) {
    this.lastRotatedAt = lastRotatedAt;
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
}
