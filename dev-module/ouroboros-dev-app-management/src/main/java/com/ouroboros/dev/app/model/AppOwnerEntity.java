package com.ouroboros.dev.app.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_dev_app_owner", label = "开发应用所有者表")
public class AppOwnerEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String appName;

  @Field
  private String ownerUserIdentity;

  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime createdAt;

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

  public String getOwnerUserIdentity() {
    return ownerUserIdentity;
  }

  public void setOwnerUserIdentity(String ownerUserIdentity) {
    this.ownerUserIdentity = ownerUserIdentity;
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
}
